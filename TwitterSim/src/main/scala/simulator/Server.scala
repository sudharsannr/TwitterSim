package simulator

import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Actor
import akka.actor.ActorRef
import simulator.Messages.RegisterClients
import simulator.Messages.Tweet
import simulator.Messages.Top
import simulator.Messages.MessageList
import simulator.Messages.Calculate
import scala.collection.mutable.ListBuffer

object ServerApp extends App {

  val system = ActorSystem("TwitterActor")
  val server = system.actorOf(Props[Server], name = "Server")
  var nRequests : Int = 0
  server ! Calculate

}

class Server extends Actor {

  var userMap : Map[ActorRef, User] = Map();
  def receive = {

    case Calculate =>
      println("Server started!")

    case RegisterClients(userList) =>
      println("Registering clients")
      for (curUser <- userList) {
        var userActor = curUser.getReference()
        userMap += (userActor -> curUser)
        userActor ! "ACK"
      }

    case Tweet(tweet) =>
      var user = userMap(sender)
      user.addMessage(tweet)

    case Top(n) =>
      var user = userMap(sender)
      sender ! MessageList(user.getRecentMessages(n))
  }

}
