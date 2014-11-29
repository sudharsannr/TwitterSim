package simulator

import akka.actor._
import scala.collection.mutable.MutableList
import scala.util.Random
import java.util.concurrent.LinkedBlockingQueue

class User(identifier : Int) extends Serializable {

  var name : String = _
  var messageRate : Int = 0
  var followers = new MutableList[Int]()
  var mentions = new LinkedBlockingQueue[String](Messages.maxBufferSize)
  var messageQueue = new LinkedBlockingQueue[String](Messages.maxBufferSize)
  var notifications = new LinkedBlockingQueue[String](Messages.maxBufferSize)

  override def equals(o : Any) = o match {
    case that : User => that.name.equals(this.name)
    case _ => false
  }

  override def hashCode = identifier.hashCode

  override def toString() : String = {
    return identifier.toString + " " + name + " " + messageRate.toString + " " + followers.size
  }

  def getRecentMessages() : List[String] = {
    return messageQueue.toArray().toList.asInstanceOf[List[String]]
  }

  def getRecentMentions() : List[String] = {
    return mentions.toArray().toList.asInstanceOf[List[String]]
  }

  def getRecentNotifications() : List[String] = {
    return notifications.toArray().toList.asInstanceOf[List[String]]
  }

  def getID() : Int = {
    return identifier
  }

  def getName() : String = {
    return name
  }

  def isFollowing(user : User) : Boolean = {
    user.getFollowers().contains(identifier)
  }

  def isFollowed(user : User) : Boolean = {
    followers.contains(user.getID())
  }

  def getFollowers() : MutableList[Int] = {
    return followers
  }

  def getMessageRate() : Int = {
    return messageRate
  }

  def addFollower(followerID : Int) {
    followers += followerID
  }

  def setMessageRate(newmessageRate : Int) {
    messageRate = newmessageRate
  }

  def setUserName(newUserName : String) {
    name = newUserName
  }

  def setRandomName() {
    name = Random.alphanumeric.take(4 + Random.nextInt(12)).mkString
  }

  def addMessage(message : String) {
    if (messageQueue.size() >= Messages.maxBufferSize)
      messageQueue.poll()
    messageQueue.offer(message)
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
    return messageQueue
  }

}
