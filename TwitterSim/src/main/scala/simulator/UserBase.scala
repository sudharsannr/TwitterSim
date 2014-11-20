package simulator

import scala.util.Random
import akka.actor._
import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks._
import scala.collection.mutable.Queue
import scala.collection.mutable.Map

class UserBase(id : Int, actorRef : ActorRef) {
  val identifier : Int = id
  val actor : ActorRef = actorRef
  var followers : ListBuffer[ActorRef] = ListBuffer.empty[ActorRef]
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

  def getFollowers() : ListBuffer[ActorRef] = {
    return followers
  }

  def getMessages() : Queue[String] = {
    return messageQueue
  }

  def addFollower(follower : ActorRef) {
    followers += follower
  }

  def addMessage(message : String) {
    println("Adding message " + message + " to client#: " + id)
    messageQueue.enqueue(message)
  }

  def generateFollowers(usersCount : Int, mean : Int) {

    var r = new Random();
    var r1 = new Random();
    var usersMap : Map[Int, ListBuffer[Int]] = Map();
    var numberOfFollowers : Int = 0;
    var modulo = usersCount * mean
    var count : Int = 0
    for (i <- 0 until usersCount) {
      numberOfFollowers = r.nextInt(modulo);
      modulo = modulo - numberOfFollowers
      count += numberOfFollowers
      println("Num of followers for user " + i + ": " + numberOfFollowers)
      println("Total generated: " + count)
      var followers = new ListBuffer[Int]
      var j : Int = 0
      for (j <- 0 until numberOfFollowers) {
        followers += r1.nextInt(numberOfFollowers)
      }
      usersMap.put(i, followers);
    }
  }
}
