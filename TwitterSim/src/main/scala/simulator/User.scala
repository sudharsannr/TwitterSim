package simulator

import scala.util.Random
import akka.actor._
import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks._
import scala.collection.mutable.Queue
import scala.collection.mutable.Map

class User(id : Int, actorRef : ActorRef) {
  val identifier : Int = id
  val actor : ActorRef = actorRef
  var followers : ListBuffer[User] = ListBuffer.empty[User]
  var messageQueue : Queue[String] = new Queue[String]

  def getRecentMessages(n : Int) : ListBuffer[String] = {
    var msgList : ListBuffer[String] = ListBuffer.empty[String]
    breakable {
      for (i <- 0 to n - 1) {
        if (messageQueue.isEmpty)
          break
        msgList += messageQueue.dequeue()
      }
    }
    return msgList
  }
  
  def getID() : Int = {
    return id
  }

  def getReference() : ActorRef = {
    return actor
  }

  def getFollowers() : ListBuffer[User] = {
    return followers
  }

  def getMessages() : Queue[String] = {
    return messageQueue
  }

  def addFollower(follower : User) {
    followers += follower
  }

  def addMessage(message : String) {
    println("Adding message " + message + " to client#: " + id)
    messageQueue.enqueue(message)
  }

}
