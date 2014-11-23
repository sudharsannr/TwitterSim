package simulator

import scala.util.Random
import akka.actor._
import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks._
import scala.collection.mutable.Queue
import scala.collection.mutable.Map

class User(id : Int, actorRef : ActorRef) {
  val identifier : Int = id
  val userName : String = Random.alphanumeric.take(4 + Random.nextInt(12)).mkString
  val actor : ActorRef = actorRef
  var msgRate : Int = 0
  var followers : ListBuffer[User] = ListBuffer.empty[User]
  var following : ListBuffer[User] = ListBuffer.empty[User]
  var messageQueue : Queue[String] = new Queue[String]

  override def equals(o : Any) = o match {
    case that : User => that.userName.equals(this.userName)
    case _ => false
  }

  override def hashCode = identifier.hashCode

  override def toString() : String = {
    return identifier.toString + " " + userName + " " + msgRate.toString
  }

  def getRecentMessages(n : Int) : ListBuffer[String] = {
    var msgList : ListBuffer[String] = ListBuffer.empty[String]
    var i = 0
      while (i < n && !messageQueue.isEmpty) {
        msgList += messageQueue.dequeue()
        i += 1
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

  def getMessages() : Queue[String] = {
    return messageQueue
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
    messageQueue.enqueue(message)
  }

  def setMessageRate(newMsgRate : Int) {
    msgRate = newMsgRate
  }

}
