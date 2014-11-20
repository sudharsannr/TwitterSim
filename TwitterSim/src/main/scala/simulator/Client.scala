package simulator

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import akka.actor.Props
import akka.actor.Actor
import simulator.Messages.RegisterClients
import simulator.Messages.Init
import scala.util.Random
import simulator.Messages.Tweet
import simulator.Messages.Top
import simulator.Messages.RouteClients
import simulator.Messages.MessageList
import scala.collection.mutable.ListBuffer

object ClientApp extends App {

  val ipAddr : String = args(0)
  implicit val system = ActorSystem("TwitterClientActor", ConfigFactory.load("applicationClient.conf"))
  val serverActor = system.actorOf(Props[Server])
  val interActor = system.actorOf(Props(new Interactor()))
  var nRequests : Int = 0
  interActor ! Init

}

class Interactor() extends Actor {
  var serverActor = ClientApp.serverActor
  var clientList = new Array[User](Messages.nClients)
  for (i <- 0 to Messages.nClients - 1)
    clientList(i) = new User(i, context.actorOf(Props(new Client(i : Int))))
  generateFollowers(Messages.nClients, Messages.mean)
  for(user <- clientList)
  {
	  printf(user.getID() + ":")
	  for(follower <- user.getFollowers())
		  printf(follower.getID() + ", ")
	  println()
  }

  def receive = {

    case Init =>
      serverActor ! RegisterClients(clientList)

    case RouteClients =>
      val rand = new Random();
      for (i <- 0 to Messages.msgLimit - 1) {
        var randomTweet = randomString(140)
        clientList(rand.nextInt(Messages.nClients)).getReference() ! Tweet(randomTweet)
      }
      for (i <- 0 to Messages.nClients - 1) {
        clientList(i).getReference() ! Top(100)
      }
      context.stop(self)
  }

  def randomString(length : Int) : String = {
    val r = new scala.util.Random
    val sb = new StringBuilder
    for (i <- 1 to length) {
      sb.append(r.nextPrintableChar)
    }
    return sb.toString
  }

  def generateFollowers(usersCount : Int, mean : Int) {
    var r = new Random()
    var nFollowers : Int = 0
    for (i <- 0 until usersCount - 1) {
      nFollowers = r.nextInt(Messages.avgFollowers);
      val user = clientList(i)
      for (f <- 0 until nFollowers + 1)
      {
        val fIdx = r.nextInt(usersCount)
        //TODO Lame plug to ignore ith user being its own follower; use more robust logic
        if(fIdx != i)
        	user.addFollower(clientList(fIdx))
      }
    }
  }
  
}

class Client(identifier : Int) extends Actor {
  var serverActor = ClientApp.serverActor
  def receive = {

    case "ACK" =>
      println("Acknowledged by server")
      ClientApp.nRequests += 1
      if (ClientApp.nRequests == Messages.nClients) {
        ClientApp.interActor ! RouteClients
      }

    case Tweet(tweet) =>
      serverActor ! Tweet(tweet)

    case Top(n) =>
      serverActor ! Top(n)

    case MessageList(msgList) =>
      println("Received top messages for client: " + identifier)
      msgList.foreach(println)
  }

}