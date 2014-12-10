package simulator

import akka.actor.{ ActorSystem, Props, Actor, ActorRef }
import com.typesafe.config.ConfigFactory
import simulator.Messages._
import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks._
import scala.collection.mutable.HashSet
import scala.concurrent.duration._

object ServerApp extends App {

  val system = ActorSystem("TwitterActor")
  val server = system.actorOf(Props[Server], name = "Server")
  var nRequests : Int = 0

}

object ServerShare {
  var userMap : Map[ActorRef, User] = Map();
  var usersList : ListBuffer[User] = ListBuffer.empty[User]
  var messagesReceived : Int = 0
  var nReceived : Int = 0
  var averageRec : Int = 0
  var timeElapsed : Int = 0
  var totalMessagesRec : Int = 0

//  import ServerApp.system.dispatcher
//  ServerApp.system.scheduler.schedule(0.seconds, 1.seconds)(printServerHandledMessages())

  //FIXME Doesn't cound valid
  def printServerHandledMessages() {
    println("Messages received from clients: " + ServerShare.messagesReceived + " per sec")
    ServerShare.timeElapsed += 1
    ServerShare.totalMessagesRec += ServerShare.messagesReceived
    ServerShare.averageRec = ServerShare.totalMessagesRec / ServerShare.timeElapsed
    ServerShare.messagesReceived = 0
    println("Average msg per sec: " + ServerShare.averageRec)
    println("Time Elapsed: " + ServerShare.timeElapsed)
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

  println("Server started!")
  def receive = {

    case RegisterClients(actor, curUser) =>
      println("Registering client " + curUser.getID())
      ServerShare.userMap += (actor -> curUser)
      ServerShare.usersList += curUser
      actor ! "ACK"

    case Tweet(tweet) =>
      println("Received tweet " + tweet)
      ServerShare.messagesReceived += 1
      var user = ServerShare.userMap(sender)
      val rtPattern = "via @\\w+$".r
      // Direct messaging
      if (tweet.startsWith("dm ")) {
        val toHandler = ("@\\w+".r findFirstIn tweet).mkString
        if (toHandler.length() > 0) {
          val toUser = new User(0)
          toUser.setUserName(toHandler.substring(1))
          ServerShare.usersList(ServerShare.usersList.indexOf(toUser)).addNotification(tweet)
        }
      }
      // Retweet with rt or via parameter
      else if (tweet.startsWith(Messages.rtKeys(0)) || ("" != (rtPattern findFirstIn tweet).mkString)) {
        println("Handling retweet")
        for (follower <- user.getFollowers())
          //TODO Test
          ServerShare.usersList(follower).addMessage(tweet)
      }
      // Normal tweet processing
      else {
        var index = tweet.indexOf('@')
        var followersSet : HashSet[User] = HashSet()
        var username = findMentionedUsername(tweet, index + 1)
        if (index == 0) {
          var mentionedUser = new User(0)
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

            var mentionedExtraUsers = new User(0)
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
            ServerShare.usersList(eachFollower).addMessage(tweet)
          }

          index = index + 1 + username.length()
          var newIndex = tweet.indexOf('@', index)
          while (newIndex != -1) {

            username = findMentionedUsername(tweet, newIndex + 1)
            index = newIndex + username.length() + 1

            var mentionedExtraUsers = new User(0)
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
      sender ! MessageList(user.getRecentMessages(), user.getID())

    case TopMentions(n) =>
      var user = ServerShare.userMap(sender)
      sender ! MentionList(user.getRecentMentions(), user.getID())

    case TopNotifications(n) =>
      var user = ServerShare.userMap(sender)
      sender ! NotificationList(user.getRecentNotifications(), user.getID())

  }

  def findMentionedUsername(tweet : String, index : Int) : String = {
    var tweetArr = tweet.toCharArray()
    var i : Int = index
    var username : String = ""

    // TODO Avoid break logics
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

  //FIXME Does not return anything
  def findMutualFollowers(mentionUserObj : User, tweeterObj : User) : List[User] = {

    var mentionedFollowers = mentionUserObj.getFollowers()
    var tweetersFollowers = tweeterObj.getFollowers()
    var mutualFollowers = List.empty[User]
    var mutualFollowerMap : Map[User, Int] = Map()

    for (tFollowers <- tweetersFollowers) {
      val curUser = getUser(tFollowers)
      if (!mutualFollowerMap.contains(curUser)) {
        mutualFollowerMap += (curUser -> 1)
      }
    }

    for (mFollowers <- mentionedFollowers) {
      val curUser = getUser(mFollowers)
      if (!mutualFollowerMap.contains(curUser)) {
        mutualFollowerMap += (curUser -> 1)
      }
    }

    return mutualFollowerMap.keySet.toList

  }

  def getUser(id : Int) : User = {
    ServerShare.usersList.foreach {
      cur =>
        if (cur.getID() == id)
          return cur
    }
    return null
  }
}
