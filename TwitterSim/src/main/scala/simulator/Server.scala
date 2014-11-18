package simulator

import akka.actor._

import scala.concurrent.duration._
import scala.concurrent.duration.Duration
import akka.routing.RoundRobinRouter
import Messages.Calculate
import Messages.Work
import Messages.WorkComplete
import Messages.ClientRequest
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Queue

object ServerApp extends App {

  val system = ActorSystem("TwitterActor")
  val server = system.actorOf(Props[Server], name = "Server")
  var nRequests : Int = 0
  server ! Calculate
}

class Server extends Actor {
  var userMap : Map[Int, UserBase] = Map();
  def receive = {

    case Calculate =>
      printf("Server started!")

    case ClientRequest(identifier, requestString) =>
      printf("Received request from " + identifier + " " + ServerApp.nRequests + "\n")
      var user = new UserBase(identifier, sender)
      userMap += (identifier -> user)
      ServerApp.nRequests += 1
      if (ServerApp.nRequests == 100) {
        printf("Got all requests")
        for ((client, userInst) <- userMap) {
          userInst.getReference() ! "ACK"
        }
      }
  }
}

class UserBase(id : Int, actorRef : ActorRef) {
  val identifier : Int = id
  val actor : ActorRef = actorRef
  var followers : ListBuffer[ActorRef] = ListBuffer.empty[ActorRef]
  var messageQueue : Queue[String] = new Queue[String]

  //TODO Define Top 100 messages
  def getID() : Int =
  {
    return id
  }
  
  def getReference() : ActorRef =
  {
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
    messageQueue.enqueue(message)
  }
}