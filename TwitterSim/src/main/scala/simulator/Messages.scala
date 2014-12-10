package simulator

import akka.actor.ActorRef
import scala.collection.mutable.ListBuffer

object Messages {

  val requestString : String = "REQUEST"
  val nServers : Int = 4
  val nClients : Int = 100
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
  val peakScale : Int = 1
  val chunkSize : Int = 100

  sealed trait TwitterMessage
  case object Init extends TwitterMessage
  case class RegisterClients(actor : ActorRef, user : User) extends TwitterMessage
  case object ScheduleClient extends TwitterMessage
  case object RequestMessageQueue extends TwitterMessage
  case object Request extends TwitterMessage
  case object PrintMessages extends TwitterMessage
  case object PrintNotifications extends TwitterMessage
  case object PrintMentions extends TwitterMessage
  case class ClientInit(ipAddr : String) extends TwitterMessage
  case class Tweet(tweet : String) extends TwitterMessage
  case class Top(n : Int) extends TwitterMessage
  case class TopNotifications(n : Int) extends TwitterMessage
  case class TopMentions(n : Int) extends TwitterMessage
  case class MessageList(msgList : List[String], id : Int) extends TwitterMessage
  case class MentionList(msgList : List[String], id : Int) extends TwitterMessage
  case class NotificationList(msgList : List[String], id : Int) extends TwitterMessage
  case object Start extends TwitterMessage
  case object ShutDown extends TwitterMessage
  case object ClientCompleted extends TwitterMessage
  
}