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

object ClientApp extends App {
  val ipAddr : String = args(0)
  implicit val system = ActorSystem("TwitterClientActor", ConfigFactory.load("applicationClient.conf"))
  val clientActor = system.actorOf(Props[Client], name = "Client")
  clientActor ! ClientInit(ipAddr)
}

class Client extends Actor {
  var counter : Int = 0
  val serverRouter = context.actorOf(
    Props[Server].withRouter(RoundRobinRouter(Messages.nServers)), name = "ServerRouter")

  def receive = {

    case ClientInit(ipAddr) =>
      println("Accessing Server at " + ipAddr)
      //val remote = context.actorFor("akka.tcp://TwitterActor@" + ipAddr + "/user/master")
      for (i <- 1 until Messages.nClients + 1) {
        val identifier : String = Messages.clientString + "%010d".format(i)
        serverRouter ! ClientRequest(identifier, Messages.requestString)
      }

    case "ACK" =>
      println("Acknowledged " + counter)
      counter += 1
      // If all received, generate messages

  }
  
  /**
   * Utility function extract Option
   */
  def show(x : Option[Any]) = x match {
    case Some(s) => s
    case None => "?"
  }

}