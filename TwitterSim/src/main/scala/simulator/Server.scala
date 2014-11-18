package simulator

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.duration.Duration
import akka.routing.RoundRobinRouter
import Messages.Calculate
import Messages.ClientRequest
import Messages.Tweet
import Messages.Top
import Messages.MessageList
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Queue
import scala.collection.mutable.Map
import scala.util.Random
import scala.util.control.Breaks._

object ServerApp extends App {

  val system = ActorSystem("TwitterActor")
  val server = system.actorOf(Props[Server], name = "Server")
  var nRequests : Int = 0
  server ! Calculate
}

class Server extends Actor {
  var userMap : Map[ActorRef, UserBase] = Map();
  def receive = {

    case Calculate =>
      printf("Server started!")

    case ClientRequest(identifier, requestString) =>
      printf("Received request from " + identifier + " " + ServerApp.nRequests + "\n")
      var user = new UserBase(identifier, sender)
      userMap += (sender -> user)
      ServerApp.nRequests += 1
      if (ServerApp.nRequests == Messages.nClients) {
        printf("Got all requests")
        for ((actor, userInst) <- userMap) {
          userInst.getReference() ! "ACK"
        }
      }

    case Tweet(tweet) =>
      var user = userMap(sender)
      user.addMessage(tweet)

    case Top(n) =>
      var user = userMap(sender)
      sender ! MessageList(user.getRecentMessages(n))
  }
}

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

  def generateFollowers(usersCount : Int) {

    var r = new Random();
    var r1 = new Random();
    var usersMap : Map[Int, ListBuffer[Int]] = Map();
    var numberOfFollowers : Int = 0;

    for (i <- 0 until usersCount) {
      numberOfFollowers = r.nextInt(usersCount)
      var followers = new ListBuffer[Int]
      var j : Int = 0
      for (j <- 0 until numberOfFollowers) {
        followers += r1.nextInt(numberOfFollowers)
      }
      usersMap.put(i, followers)
    }
  }
}
