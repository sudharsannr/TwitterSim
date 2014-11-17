package simulator

import akka.actor._

import scala.concurrent.duration._
import scala.concurrent.duration.Duration
import akka.routing.RoundRobinRouter
import Messages.Calculate
import Messages.Work
import Messages.WorkComplete
import Messages.ClientRequest
import scala.collection.mutable.ListBuffer

object ServerApp extends App {

  val system = ActorSystem("TwitterActor")
  val server = system.actorOf(Props[Server], name = "Server")
  var nRequests : Int = 0
  server ! Calculate
}

class Server extends Actor {

  val clientRouter = context.actorOf(Props[Client].withRouter(RoundRobinRouter(Messages.nClients)),
      name = "ClientRouter")
  var followers : Map[String, ListBuffer[String]] = Map();
  def receive = {

    case Calculate =>
      printf("Server started!")
    
    case ClientRequest(identifier, requestString) =>
      printf("Received request from " + identifier +" " +  ServerApp.nRequests + "\n")
      followers += (identifier -> ListBuffer.empty[String])
      ServerApp.nRequests += 1
      sender ! "ACK"
      /*if(ServerApp.nRequests == 100)
      {
        printf("Shutting down!")
        context.system.shutdown()
      }*/

  }
  

}