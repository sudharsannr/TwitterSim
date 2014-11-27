package simulator

import akka.actor.ActorRef
import scala.collection.mutable.ListBuffer

object Messages {

  val requestString : String = "REQUEST"
  val nServers : Int = 20
  val nClients : Int = 10000
  val maxNeighbors : Int = nClients - 1
  val msgLimit : Int = 10000
  val mean : Int = 200
  val avgFollowers : Int = 50
  val avgTweetLength : Int = 28
  val maxBufferSize : Int = 100
  val keyWords = List("rt", "dm")
  val rtKeys = List("rt @", "via @")
  val maxMentions : Int = 6
  val peakStart : Int = 20
  val peakEnd : Int = 40
  val peakScale : Int = 10
  val chunkSize : Int = 100

  sealed trait TwitterMessage
  case object Init extends TwitterMessage
  case class RequestRegister(actor : ActorRef, curUser : User) extends TwitterMessage
  case class Register(identifier : Int, user : User) extends TwitterMessage
  case object Request extends TwitterMessage
  case object PrintMessages extends TwitterMessage
  case object PrintNotifications extends TwitterMessage
  case object PrintMentions extends TwitterMessage
  case class RegisterClients(curUser : User) extends TwitterMessage
  case class ClientInit(ipAddr : String) extends TwitterMessage
  case class Tweet(tweet : String) extends TwitterMessage
  case class Top(n : Int) extends TwitterMessage
  case class TopNotifications(n : Int) extends TwitterMessage
  case class TopMentions(n : Int) extends TwitterMessage
  case class MessageList(msgList : List[String]) extends TwitterMessage
  case class MentionList(msgList : List[String]) extends TwitterMessage
  case class NotificationList(msgList : List[String]) extends TwitterMessage
  case class ScheduleClient(identifier : Int) extends TwitterMessage
  case object Start extends TwitterMessage
  case object ClientCompleted extends TwitterMessage

}