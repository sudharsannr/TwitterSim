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
  var clientList = new Array[UserBase](Messages.nClients)
  for (i <- 0 to Messages.nClients - 1)
    clientList(i) = new UserBase(i, context.actorOf(Props(new Client(i : Int))))
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