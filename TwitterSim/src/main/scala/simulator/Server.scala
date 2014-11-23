package simulator

import akka.actor.{ ActorSystem, Props, Actor, ActorRef }
import simulator.Messages.{ RegisterClients, Tweet, Top, MessageList, Start }
import scala.collection.mutable.ListBuffer

object ServerApp extends App {

  val system = ActorSystem("TwitterActor")
  val server = system.actorOf(Props[Server], name = "Server")
  var nRequests : Int = 0
  server ! Start

}

object ServerShare {
  var userMap : Map[ActorRef, User] = Map();
  var usersList : ListBuffer[User] = ListBuffer.empty[User]
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
        ServerShare.usersList += curUser
        userActor ! "ACK"
      }

    case Tweet(tweet) =>
      //println("Received " + tweet)
      var user = ServerShare.userMap(sender)
      var mentions = findMentions(tweet)
      for (usersMentioned <- mentions) {

        var tempUser = new User(0, null)
        tempUser.setUserName(usersMentioned)
        var mentionedUserObj = usersList(usersList.indexOf(tempUser))
        mentionUserObj.addMessage(tweet)

        var mutualFollowers = findMutualFollowers(mentionUserObj, user)
        for (mFollowers <- mutualFollowers) {
          mFollowers.addMessage(tweet)
        }

      }
      user.addMessage(tweet)

    case Top(n) =>
      var user = ServerShare.userMap(sender)
      sender ! MessageList(user.getRecentMessages(n))

  }

  def findMutualFollowers(mentionUserObj : User, tweeterObj : User) : ListBuffer[User] = {

    var mentionedFollowers = mentionUserObj.getFollowers()
    var tweetersFollowers = tweeterObj.getFollowers()
    var mutualFollowers : ListBuffer[User] = ListBuffer.empty[User]

    //n^2 logic.
    for (tFollowers <- tweetersFollowers) {
      for (mFollowers <- mentionedFollowers) {
        if (tFollowers.equals(mFollowers)) {
          mutualFollowers += tFollowers
        }
      }
    }

    return mutualFollowers

  }

  def findMentions(tweet : String) : ListBuffer[String] = {
    var tweetArr = tweet.toCharArray()
    var mentions = new ListBuffer[String]
    var j : Int = 0
    while (j < tweet.length()) {
      var mentionedAt : String = ""
      if (tweetArr.array(j) == '@') {
        j = j + 1
        while (tweetArr.array(j) != ' ') {
          mentionedAt += tweetArr.array(j)
          j = j + 1
        }
        mentions += mentionedAt
      }
      j = j + 1
    }

    return mentions
  }
}
