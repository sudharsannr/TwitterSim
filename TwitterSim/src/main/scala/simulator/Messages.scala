package simulator

import akka.actor.ActorRef

object Messages {

  val requestString : String = "REQUEST"
  val nServers : Int = 10
  val nClients : Int = 1000
  val maxNeighbors : Int = nClients - 1

  sealed trait TwitterMessage
  case object Init extends TwitterMessage
  case class Request(serverActor : ActorRef) extends TwitterMessage
  case class ClientInit(ipAddr : String) extends TwitterMessage
  case class ClientRequest(identifier : Int, requestStr : String) extends TwitterMessage
  case object Calculate extends TwitterMessage

}