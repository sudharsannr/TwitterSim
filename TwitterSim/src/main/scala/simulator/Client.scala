package simulator

import akka.actor.Actor

import akka.actor.actorRef2Scala
import java.security.MessageDigest
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import akka.actor.Props
import Messages.ClientInit
import Messages.ClientRequest
import akka.routing.RoundRobinRouter
import Messages.Calculate
import Messages.Init
import Messages.Request
import Messages.RouteClients
import Messages.Tweet
import Messages.Top
import Messages.MessageList
import akka.actor.ActorRef
import scala.util.Random

object ClientApp extends App {
  val ipAddr : String = args(0)
  implicit val system = ActorSystem("TwitterClientActor", ConfigFactory.load("applicationClient.conf"))
  val serverActor = system.actorOf(Props[Server])
  val interActor = system.actorOf(Props(new Interactor()))
  interActor ! Init
}

class Interactor() extends Actor {
  var serverActor = ClientApp.serverActor
  var worker : IndexedSeq[ActorRef] = null
  worker = (0 to Messages.nClients - 1).map(i => context.actorOf(Props(new Client(i : Int))))
  def receive = {
    case Init =>
      for (i <- 0 to Messages.nClients - 1)
        worker(i) ! Request
    case RouteClients =>
      val rand = new Random();
      for (i <- 0 to Messages.msgLimit - 1) {
        var randomTweet = randomString(140)
        worker(rand.nextInt(Messages.nClients)) ! Tweet(randomTweet)
      }

      for (i <- 0 to Messages.nClients - 1) {
        worker(i) ! Top(100)
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
    case Request =>
      println(identifier + " sending request")
      serverActor ! ClientRequest(identifier, Messages.requestString)

    case "ACK" =>
      println("Acknowledged by server")
      ServerApp.nRequests -= 1
      if (ServerApp.nRequests == 0) {
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