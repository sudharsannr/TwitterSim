package simulator

import akka.actor.ActorRef
import scala.collection.mutable.ListBuffer

object Messages {

  val requestString : String = "REQUEST"
  val nServers : Int = 10
  val nClients : Int = 100
  val maxNeighbors : Int = nClients - 1
  val msgLimit : Int = 1000

  sealed trait TwitterMessage
  case object Init extends TwitterMessage
  case object Request extends TwitterMessage
  case class RegisterClients(clientList : Array[UserBase]) extends TwitterMessage
  case class ClientInit(ipAddr : String) extends TwitterMessage
  case class Tweet(tweet : String) extends TwitterMessage
  case class Top(n : Int) extends TwitterMessage
  case class MessageList(msgList : ListBuffer[String]) extends TwitterMessage
  case object RouteClients extends TwitterMessage
  case object Calculate extends TwitterMessage

}