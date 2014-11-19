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
      
    case RegisterClients(userList) =>
      printf("Registering clients")
      for(i <- 0 to userList.length - 1)
      {
        var userInst = userList(i).getReference
        userMap += (userInst -> userList(i))
        userInst ! "ACK"
      }

    case Tweet(tweet) =>
      var user = userMap(sender)
      user.addMessage(tweet)

    case Top(n) =>
      var user = userMap(sender)
      sender ! MessageList(user.getRecentMessages(n))
  }
}