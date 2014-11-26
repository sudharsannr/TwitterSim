package simulator

import scala.util.Random
import akka.actor._
import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks._
import scala.collection.mutable.Queue
import scala.collection.mutable.Map
import java.util.LinkedList
import java.util.concurrent.LinkedBlockingQueue

class User(id : Int, actorRef : ActorRef) extends Serializable {
  val identifier : Int = id
  var userName : String = Random.alphanumeric.take(4 + Random.nextInt(12)).mkString
  val actor : ActorRef = actorRef
  var msgRate : Int = 0
  var followers : ListBuffer[User] = ListBuffer.empty[User]
  var following : ListBuffer[User] = ListBuffer.empty[User]
  val maxSize = Messages.maxBufferSize
  var mentions = new LinkedBlockingQueue[String](maxSize)
  var messageQueue = new LinkedBlockingQueue[String](maxSize)
  var notifications = new LinkedBlockingQueue[String](maxSize)

  override def equals(o : Any) = o match {
    case that : User => that.userName.equals(this.userName)
    // println("That Username: ============"+that.userName)
    case _ => false

  }

  override def hashCode = identifier.hashCode

  override def toString() : String = {
    return identifier.toString + " " + userName + " " + msgRate.toString
  }

  def getRecentMessages(n : Int) : ListBuffer[String] = {
    var msgList : ListBuffer[String] = ListBuffer.empty[String]
    var i = 0
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
    }
    return msgList
  }

  def getRecentMentions(n : Int) : ListBuffer[String] = {
    var msgList : ListBuffer[String] = ListBuffer.empty[String]
    var i = 0
    while (i < n && !mentions.isEmpty) {
      var msg = mentions.remove()
      //TODO Message queue has null
      if (null != msg) {
        msgList += msg
        i += 1
      }
    }
    return msgList
  }

  def getRecentNotifications(n : Int) : ListBuffer[String] = {
    var msgList : ListBuffer[String] = ListBuffer.empty[String]
    var i = 0
    while (i < n && !notifications.isEmpty) {
      var msg = notifications.remove()
      //TODO Message queue has null
      if (null != msg) {
        msgList += msg
        i += 1
      }
    }
    return msgList
  }

  def getID() : Int = {
    return id
  }

  def getName() : String = {
    return userName
  }

  def getReference() : ActorRef = {
    return actor
  }

  def isFollowing(user : User) : Boolean = {
    user.getFollowers().contains(this)
  }

  def isFollowed(user : User) : Boolean = {
    getFollowers().contains(user)
  }

  def getFollowers() : ListBuffer[User] = {
    return followers
  }

  def getFollowing() : ListBuffer[User] = {
    return following
  }

  def getMsgRate() : Int = {
    return msgRate
  }

  def addFollower(follower : User) {
    followers += follower
  }

  def addFollowing(followingUsers : User) {
    following += followingUsers
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
    if(mentions.size() >= maxSize)
      mentions.poll()
    mentions.offer(message)
  }

  def addNotification(message : String) {
    if(notifications.size() >= maxSize)
      notifications.poll()
    notifications.offer(message)
  }
  
  def getMessages() : LinkedBlockingQueue[String] = {
    if(messageQueue.size() >= maxSize)
      messageQueue.poll()
    return messageQueue
  }

}
