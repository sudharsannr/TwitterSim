package simulator

import akka.actor.{ ActorSystem, Props, Actor, ActorRef }
import com.typesafe.config.ConfigFactory
import simulator.Messages._
import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks._
import scala.collection.mutable.HashSet
import scala.concurrent.duration._
import org.json4s.native.Serialization
import org.json4s.native.Serialization._
import org.json4s.ShortTypeHints
import spray.routing._
import spray._
import spray.http.MediaTypes
import akka.pattern.ask
import akka.util.Timeout


object ServerApp extends App with SimpleRoutingApp{

  implicit val system = ActorSystem("TwitterActor")
  implicit val server = system.actorOf(Props[Server], name = "Server")
  var nRequests : Int = 0
	implicit val timeout = Timeout(1.second)
  import system.dispatcher
  
	def getJson(route: Route) = get {
    respondWithMediaType(MediaTypes.`application/json`) { route }
  }

   lazy val serverRoute = {
    get {
      path("get" / "tweets") {
        complete {
        	//Server.toJson(Server.getUserList(1))
        	(server ? getUserObj(1)).mapTo[User].map(s => Server.toJson(s))
        }
      }
    }
  }

  startServer(interface = "localhost", port = 8080) {
    serverRoute
  }
  
}

object Server {
  private var userMap : Map[ActorRef, User] = Map();
  var usersList : ListBuffer[User] = ListBuffer.empty[User]
  private var messagesReceived : Int = 0
  private var nReceived : Int = 0
  private var averageRec : Int = 0
  private var timeElapsed : Int = 0
  private var totalMessagesRec : Int = 0

  //  import ServerApp.system.dispatcher
  //  ServerApp.system.scheduler.schedule(0.seconds, 1.seconds)(printServerHandledMessages())

  //FIXME Doesn't cound valid
  def printServerHandledMessages() {
    println("Messages received from clients: " + Server.messagesReceived + " per sec")
    Server.timeElapsed += 1
    Server.totalMessagesRec += Server.messagesReceived
    Server.averageRec = Server.totalMessagesRec / Server.timeElapsed
    Server.messagesReceived = 0
    println("Average msg per sec: " + Server.averageRec)
    println("Time Elapsed: " + Server.timeElapsed)
  }
  
  def getUserList(id : Int) : User = {
  	return Server.usersList(id)
  }
  
  
  import org.json4s.native.Serialization.write
  import org.json4s.{FieldSerializer, DefaultFormats}
  //private implicit val formats = Serialization.formats(ShortTypeHints(List(classOf[Server])))
  private implicit val formats = DefaultFormats + FieldSerializer[User]()
  def toJson(users: ListBuffer[User]): String = writePretty(users)
  def toJson(amber: User): String = writePretty(amber)
  
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

  	case getUserObj(id) =>
  		sender ! Server.usersList(id)
  	
    case RegisterClients(actor, curUser) =>
      println("Registering client " + curUser.getID())
      Server.userMap += (actor -> curUser)
      Server.usersList += curUser
      actor ! "ACK"

    case Tweet(tweet) =>
      Server.messagesReceived += 1
      println("Received tweet " + tweet)
      var user = Server.userMap(sender)
      val rtPattern = "via @\\w+$".r
      // Direct messaging
      if (tweet.startsWith("dm ")) {
        val toHandler = ("@\\w+".r findFirstIn tweet).mkString
        if (toHandler.length() > 0) {
          val toUser = new User(0)
          toUser.setUserName(toHandler.substring(1))
          Server.usersList(Server.usersList.indexOf(toUser)).addNotification(tweet)
        }
      }
      // Retweet with rt or via parameter
      else if (tweet.startsWith(Messages.rtKeys(0)) || ("" != (rtPattern findFirstIn tweet).mkString)) {
        for (follower <- user.getFollowers())
          //TODO Test
          Server.usersList(follower).addMessage(tweet)
      }
      else {

        var index = tweet.indexOf('@')
        var followersSet : HashSet[User] = HashSet()
        var username = findMentionedUsername(tweet, index + 1)
        if (index == 0) {
          var mentionedUser = new User(0)
          mentionedUser.setUserName(username)
          var mentionedUserObj = Server.usersList(Server.usersList.indexOf(mentionedUser))

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

            var mentionedExtraUsersObj = Server.usersList(Server.usersList.indexOf(mentionedExtraUsers))

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
            Server.usersList(eachFollower).addMessage(tweet)
          }

          index = index + 1 + username.length()
          var newIndex = tweet.indexOf('@', index)
          while (newIndex != -1) {

            username = findMentionedUsername(tweet, newIndex + 1)
            index = newIndex + username.length() + 1

            var mentionedExtraUsers = new User(0)
            mentionedExtraUsers.setUserName(username)
            var mentionedExtraUsersObj = Server.usersList(Server.usersList.indexOf(mentionedExtraUsers))

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
      var user = Server.userMap(sender)
      sender ! MessageList(user.getRecentMessages(), user.getID())

    case TopMentions(n) =>
      var user = Server.userMap(sender)
      sender ! MentionList(user.getRecentMentions(), user.getID())

    case TopNotifications(n) =>
      var user = Server.userMap(sender)
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
    Server.usersList.foreach {
      cur =>
        if (cur.getID() == id)
          return cur
    }
    return null
  }
}
