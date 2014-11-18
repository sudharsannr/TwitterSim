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
import akka.actor.ActorRef

object ClientApp extends App {
  val ipAddr : String = args(0)
  implicit val system = ActorSystem("TwitterClientActor", ConfigFactory.load("applicationClient.conf"))
  val serverActor = system.actorOf(Props[Server])
  val interActor = system.actorOf(Props(new Interactor(serverActor)))
  interActor ! Init
}

class Interactor(serverActor : ActorRef) extends Actor {
  var worker : IndexedSeq[ActorRef] = null
  worker = (0 to Messages.nClients - 1).map(i => context.actorOf(Props(new Client(i : Int))))

  def receive = {
    case Init =>
      for (i <- 0 to Messages.nClients - 1)
        worker(i) ! Request(serverActor)
  }
}

class Client(identifier : Int) extends Actor {
  def receive = {
    case Request(serverActor) =>
      println(identifier + " sending request")
      serverActor ! ClientRequest(identifier, Messages.requestString)
      
    case "ACK" =>
      println("Acknowledged by server")
  }
}