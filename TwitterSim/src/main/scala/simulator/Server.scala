package simulator

import akka.actor.{ActorSystem, Props, Actor, ActorRef}
import simulator.Messages.{RegisterClients, Tweet, Top, MessageList, Start, ShutDown}
import scala.collection.mutable.ListBuffer

object ServerApp extends App {

  val system = ActorSystem("TwitterActor")
  val server = system.actorOf(Props[Server], name = "Server")
  var nRequests : Int = 0
  server ! Start

}

object ServerShare {
  var userMap : Map[ActorRef, User] = Map();
}

class Server extends Actor {

  def receive = {

    case Start =>
      println("Server started!")

    case RegisterClients(userList) =>
      println("Registering clients")
      for (curUser <- userList) {
        var userActor = curUser.getReference()
        ServerShare.userMap += (userActor -> curUser)
        userActor ! "ACK"
      }

    case Tweet(tweet) =>
      //println("Received " + tweet)
      var user = ServerShare.userMap(sender)
      var mentions = findMentions(tweet)
      for (usersMentioned <- mentions) {
        var userFollowers = user.getFollowers()     
        var userFollowing = user.getFollowing()     
        var mentionUserObj = getUserFromFollowers(usersMentioned, userFollowers, userFollowing)
        if (mentionUserObj != null)
          mentionUserObj.addMessage(tweet)
        //else find that user
      }
      user.addMessage(tweet)

    case Top(n) =>
      var user = ServerShare.userMap(sender)
      sender ! MessageList(user.getRecentMessages(n))
      
    case ShutDown =>
      context.system.shutdown()
  }

  
  def getUserFromFollowers(usersMentioned : String, userFollowers : ListBuffer[User], userFollowing : ListBuffer[User]) : User = {
    for (users <- userFollowers) {
      if (users.equals(usersMentioned)) {
        return users
      }
    }
    
    for (users <- userFollowing) {
      if (users.equals(usersMentioned)) {
        return users
      }
    }
    
    return null
  }
  

  def findMentions (tweet : String) : ListBuffer[String] = {
    var tweetArr = tweet.toCharArray()
    var mentions = new ListBuffer[String]
    var j : Int = 0
    while (j < tweet.length()) {
      var mentionedAt : String = ""
      if (tweetArr.array(j) == '@') {
        j = j+1
        while (tweetArr.array(j) != ' ') {
          mentionedAt += tweetArr.array(j)
          j = j+1
        }
        mentions += mentionedAt
      }
      j = j+1          
    }
    
    return mentions
  }
}
