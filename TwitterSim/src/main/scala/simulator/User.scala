package simulator

import akka.actor._
import scala.collection.mutable.MutableList
import scala.collection.mutable.Queue
import scala.util.Random
import java.util.LinkedList
import java.util.Arrays
import java.util.concurrent.LinkedBlockingQueue

class User(identifier : Int) extends Serializable{
  var userName : String = Random.alphanumeric.take(4 + Random.nextInt(12)).mkString
  var msgRate : Int = 0
  @transient
  var followers : MutableList[Int] = new MutableList[Int]()
  var mentions = new LinkedBlockingQueue[String](Messages.maxBufferSize)
  var messageQueue = new LinkedBlockingQueue[String](Messages.maxBufferSize)
  var notifications = new LinkedBlockingQueue[String](Messages.maxBufferSize)

  override def equals(o : Any) = o match {
    case that : User => that.userName.equals(this.userName)
    // println("That Username: ============"+that.userName)
    case _ => false

  }

  override def hashCode = identifier.hashCode

  override def toString() : String = {
    return identifier.toString + " " + userName + " " + msgRate.toString + " " +followers.size
    
  }

  def getRecentMessages(n : Int) : List[String] = {
    var msgList : List[String] = List.empty[String]
    msgList = messageQueue.toArray().toList.asInstanceOf[List[String]]
    /*var i = 0
    if (!messageQueue.isEmpty) {
      var tempQueue = messageQueue.toArray
      while (i < n && i < tempQueue.size) {
        var msg = tempQueue(i).toString()
        //TODO Message queue has null
        if (null != msg) {
          msgList += msg
          i += 1
        }
      }
    }*/
    return msgList
  }

  def getRecentMentions(n : Int) : List[String] = {
    var msgList : List[String] = List.empty[String]
    msgList = mentions.toArray().toList.asInstanceOf[List[String]]
    /*var i = 0
    while (i < n && !mentions.isEmpty) {
      var msg = mentions.remove()
      //TODO Message queue has null
      if (null != msg) {
        msgList += msg
        i += 1
      }
    }*/
    return msgList
  }

  def getRecentNotifications(n : Int) : List[String] = {
    var msgList : List[String] = List.empty[String]
    msgList = notifications.toArray().toList.asInstanceOf[List[String]]
    /*var i = 0
    while (i < n && !notifications.isEmpty) {
      var msg = notifications.remove()
      //TODO Message queue has null
      if (null != msg) {
        msgList += msg
        i += 1
      }
    }*/
    return msgList
  }

  def getID() : Int = {
    return identifier
  }

  def getName() : String = {
    return userName
  }

  def isFollowing(user : User) : Boolean = {
    user.getFollowers().contains(this.identifier)
  }

  def isFollowed(user : Int) : Boolean = {
    followers.contains(user)
  }

  def getFollowers() : MutableList[Int] = {
    return followers
  }

  def getMsgRate() : Int = {
    return msgRate
  }

  def addFollower(follower : Int) {
    followers += follower
  }

  def addMessage(message : String) {
    messageQueue.offer(message)
  }

  def setMessageRate(newMsgRate : Int) {
    msgRate = newMsgRate
  }

  def setUserName(newUserName : String) {
    userName = newUserName
  }

  def addMention(message : String) {
    if (mentions.size() >= Messages.maxBufferSize)
      mentions.poll()
    mentions.offer(message)
  }

  def addNotification(message : String) {
    if (notifications.size() >= Messages.maxBufferSize)
      notifications.poll()
    notifications.offer(message)
  }

  def getMessages() : LinkedBlockingQueue[String] = {
    if (messageQueue.size() >= Messages.maxBufferSize)
      messageQueue.poll()
    return messageQueue
  }

}
