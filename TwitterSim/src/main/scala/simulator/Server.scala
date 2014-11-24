package simulator

import akka.actor.{ ActorSystem, Props, Actor, ActorRef }
import simulator.Messages.{ RegisterClients, Tweet, Top, MessageList, Start, TopMentions, TopNotifications, MentionList, NotificationList }
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

/**
 * Functionalities = Homepage / Mentions feed / Notifications
 * 1. any messages that I type goes to my followers.
 * 2. mention of @username always goes to mention feed of username
 * 3. @ at beginning : any conversations between two persons p1 and p2 goes to homepage of followers of both p1 and p2
 * 	  @ not at beginning : source person p1's followers will have this message
 *    .@  : same as above
 * 4. direct message: send message to followers using dm @username message string => done
 * 5. retweet: RT @srcusername same message or same message via @srcusername => done
 * 6. hashtag
 */
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
      val rtPattern = "via @\\w+$".r
      // Direct messaging
      if (tweet.startsWith("dm ")) {
        val toHandler = ("@\\w+".r findFirstIn tweet).mkString
        if (toHandler.length() > 0) {
          val toUser = new User(0, null)
          toUser.setUserName(toHandler.substring(1))
          ServerShare.usersList(ServerShare.usersList.indexOf(toUser)).addNotification(tweet)
        }
      }
      // Retweet with rt or via parameter
      else if (tweet.startsWith(Messages.rtKeys(0)) || ("" != (rtPattern findFirstIn tweet).mkString)) {
        for (follower <- user.getFollowers())
          follower.addMessage(tweet)
      }
      else {
        var mentions = findMentions(tweet)
        for (usersMentioned <- mentions) {

          var tempUser = new User(0, null)
          tempUser.setUserName(usersMentioned)

          // Functionality 2
          var mentionedUserObj = ServerShare.usersList(ServerShare.usersList.indexOf(tempUser))
          //TODO Check if correct
          mentionedUserObj.addMention(tweet)

          var mutualFollowers = findMutualFollowers(mentionedUserObj, user)
          for (mFollowers <- mutualFollowers) {
            mFollowers.addMessage(tweet)
          }

          // Functionality 1
          for (follower <- user.getFollowers())
            follower.addMessage(tweet)

        }
        user.addMessage(tweet)

      }

    case Top(n) =>
      var user = ServerShare.userMap(sender)
      sender ! MessageList(user.getRecentMessages(n))

    case TopMentions(n) =>
      var user = ServerShare.userMap(sender)
      sender ! MentionList(user.getRecentMentions(n))

    case TopNotifications(n) =>
      var user = ServerShare.userMap(sender)
      sender ! NotificationList(user.getRecentNotifications(n))

  }

  def findMutualFollowers(mentionUserObj : User, tweeterObj : User) : ListBuffer[User] = {

    var mentionedFollowers = mentionUserObj.getFollowers()
    var tweetersFollowers = tweeterObj.getFollowers()
    var mutualFollowers : ListBuffer[User] = ListBuffer.empty[User]

    //n^2 logic.
    for (tFollowers <- tweetersFollowers) {
      for (mFollowers <- mentionedFollowers) {
        if (tFollowers.equals(mFollowers)) {
          //TODO @sarghau check if this condition is valid. Resulted in same follower added to list
          if (!mutualFollowers.contains(tFollowers))
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
