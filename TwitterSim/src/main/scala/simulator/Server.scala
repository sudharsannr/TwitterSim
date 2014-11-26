package simulator

import akka.actor.{ ActorSystem, Props, Actor, ActorRef }
import com.typesafe.config.ConfigFactory
import simulator.Messages.{ RegisterClients, Tweet, Top, MessageList, Start, TopMentions, TopNotifications, MentionList, NotificationList }
import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks._
import scala.collection.mutable.HashSet
import scala.concurrent.duration._

object ServerApp extends App {

  val system = ActorSystem("TwitterActor")
  val server = system.actorOf(Props[Server], name = "Server")
  var nRequests : Int = 0
  server ! Start

}

object ServerShare {
  var userMap : Map[ActorRef, User] = Map();
  var usersList : ListBuffer[User] = ListBuffer.empty[User]
  var messagesReceived : Int = 0
  var nReceived : Int = 0
  import ServerApp.system.dispatcher
  ServerApp.system.scheduler.schedule(0.seconds, 1.seconds)(printServerHandledMessages())
  def printServerHandledMessages() {
    println("Messages received from clients: " + ServerShare.messagesReceived + " per sec")
  }
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
 */
class Server extends Actor {

  def receive = {

    case Start =>
      println("Server started!")

    case RegisterClients(curUser) =>
      println("Registering clients")
      var userActor = curUser.getReference()
      ServerShare.userMap += (userActor -> curUser)
      ServerShare.usersList += curUser
      userActor ! "ACK"

    case Tweet(tweet) =>
      ServerShare.messagesReceived += 1
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

        var index = tweet.indexOf('@')
        var followersSet : HashSet[User] = HashSet()
        var username = findMentionedUsername(tweet, index + 1)
        if (index == 0) {
          var mentionedUser = new User(0, null)
          mentionedUser.setUserName(username)
          var mentionedUserObj = ServerShare.usersList(ServerShare.usersList.indexOf(mentionedUser))

          //mentioned person's mentions tab
          mentionedUserObj.addMention(tweet)

          //If mentioned person follows the tweeter, add it to mentioned person's timeline
          if (mentionedUserObj.isFollowed(user)) {
            //Mentioned users timeline
            mentionedUserObj.addMessage(tweet)

            //senders timeline
            user.addMessage(tweet)

            //find mutual followers
            var mutualFollowers = findMutualFollowers(mentionedUserObj, user)
            for (mFollowers <- mutualFollowers) {
              //mutual followers timeline
              followersSet += mFollowers
              //mFollowers.addMessage(tweet)
            }
          }

          index = index + 1 + username.length()
          var newIndex = tweet.indexOf('@', index)

          while (newIndex != -1) {
            username = findMentionedUsername(tweet, newIndex + 1)
            index = newIndex + username.length() + 1

            var mentionedExtraUsers = new User(0, null)
            mentionedExtraUsers.setUserName(username)

            var mentionedExtraUsersObj = ServerShare.usersList(ServerShare.usersList.indexOf(mentionedExtraUsers))

            mentionedExtraUsersObj.addMention(tweet)
            if (mentionedExtraUsersObj.isFollowed(user)) {
              mentionedExtraUsersObj.addMessage(tweet)

              var mutualFollowers1 = findMutualFollowers(mentionedExtraUsersObj, user)
              for (m1Followers <- mutualFollowers1) {
                //mutual followers timeline
                followersSet += m1Followers
                //mFollowers.addMessage(tweet)
              }

            }

            newIndex = tweet.indexOf('@', index)
          }

          for (m2Followers <- followersSet) {
            m2Followers.addMessage(tweet)
          }

        }
        else if (index > 0) {

          //senders timeline
          user.addMessage(tweet)

          //Sender's followers timelines
          var followers = user.getFollowers()
          for (eachFollower <- followers) {
            eachFollower.addMessage(tweet)
          }

          index = index + 1 + username.length()
          var newIndex = tweet.indexOf('@', index)
          while (newIndex != -1) {

            username = findMentionedUsername(tweet, newIndex + 1)
            index = newIndex + username.length() + 1

            var mentionedExtraUsers = new User(0, null)
            mentionedExtraUsers.setUserName(username)
            var mentionedExtraUsersObj = ServerShare.usersList(ServerShare.usersList.indexOf(mentionedExtraUsers))

            if (!user.isFollowed(mentionedExtraUsersObj)) {
              mentionedExtraUsersObj.addMention(tweet)
            }

            newIndex = tweet.indexOf('@', index)
          }

        }
        else {
          user.addMessage(tweet)
        }

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

  def findMentionedUsername(tweet : String, index : Int) : String = {
    var tweetArr = tweet.toCharArray()
    var i : Int = index
    var username : String = ""

    breakable {
      for (i <- index until tweet.length) {
        if (tweetArr.array(i) != ' ') {
          username += tweetArr.array(i)
        }
        else {
          break
        }

      }
    }

    return username
  }

  def findMutualFollowers(mentionUserObj : User, tweeterObj : User) : ListBuffer[User] = {

    var mentionedFollowers = mentionUserObj.getFollowers()
    var tweetersFollowers = tweeterObj.getFollowers()
    var mutualFollowers : ListBuffer[User] = ListBuffer.empty[User]
    var mutualFollowerMap : Map[User, Int] = Map()

    for (tFollowers <- tweetersFollowers) {
      if (!mutualFollowerMap.contains(tFollowers)) {
        mutualFollowerMap += (tFollowers -> 1)
      }
    }

    for (mFollowers <- mentionedFollowers) {
      if (!mutualFollowerMap.contains(mFollowers)) {
        mutualFollowerMap += (mFollowers -> 1)
      }
    }

    return mutualFollowers

  }
}
