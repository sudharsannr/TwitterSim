package simulator

import akka.actor.{ Actor, ActorSystem, Props, Scheduler, Cancellable, PoisonPill }
import com.typesafe.config.ConfigFactory
import simulator.Messages.{ Init, Tweet, Top, RouteClients, MessageList, PrintMessages, RegisterClients, ClientCompleted, ShutDown, PrintMentions, PrintNotifications, TopMentions, TopNotifications, MentionList, NotificationList }
import scala.util.Random
import scala.util.control.Breaks._
import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.concurrent.duration._
import akka.routing.{ FromConfig, RoundRobinRouter, Broadcast }

object ClientApp extends App {

  // TODO Stop when complete
  //val ipAddr : String = args(0)
  val system = ActorSystem("TwitterClientActor", ConfigFactory.load("applicationClient.conf"))
  //val serverActor = system.actorOf(Props[Server])
  val serverActor = system.actorOf(Props[Server].withRouter(RoundRobinRouter(nrOfInstances = 4)), "serverRouter")
  val interActor = system.actorOf(Props(new Interactor()))
  var nRequests : Int = 0
  interActor ! Init

}

class Interactor() extends Actor {
  var serverActor = ClientApp.serverActor
  var clientList = new Array[User](Messages.nClients)
  var nMessages : Int = 0
  var cancelMap : Map[User, Cancellable] = Map()
  var nCompleted : Int = 0
  var queueCount : Int = 0
  for (i <- 0 to Messages.nClients - 1)
    clientList(i) = new User(i, context.actorOf(Props(new Client(i : Int))))
  //generateFollowers(Messages.nClients, Messages.mean)
  readFollowersStats(Messages.nClients)
  readUserRateStats(Messages.nClients)
  /*for (user <- clientList)
    println(user)*/
  /*for(user <- clientList)
  {
	  printf(user.getName() + ":")
	  for(follower <- user.getFollowers())
		  printf(follower.getName() + ", ")
	  println()
  }*/

  def receive = {

    case Init =>
      serverActor ! RegisterClients(clientList)

    case RouteClients =>
      val rand = new Random();
      val actorSys = ClientApp.system
      import actorSys.dispatcher
      for (curUser <- clientList) {
        val intervalRate = curUser.getMsgRate.milliseconds
        val cancellable = actorSys.scheduler.schedule(0.milliseconds, intervalRate)(sendMsg(curUser))
        cancelMap += (curUser -> cancellable)
      }
    /*for (i <- 0 to Messages.msgLimit - 1) {
        val curUser = clientList(rand.nextInt(Messages.nClients))
        var rndTweet = randomTweet(curUser)
        curUser.getReference() ! Tweet(rndTweet)
      }*/

    case PrintMessages =>
      println("Printing messages")
      for (i <- 0 to Messages.nClients - 1)
        clientList(i).getReference() ! Top(Messages.maxBufferSize)

    case PrintNotifications =>
      println("Printing notificatins")
      for (user <- clientList)
        user.getReference() ! TopNotifications(Messages.maxBufferSize)

    case PrintMentions =>
      println("Printing mentions")
      for (user <- clientList)
        user.getReference() ! TopMentions(Messages.maxBufferSize)

    case ClientCompleted =>
      nCompleted += 1
      if (nCompleted == Messages.nClients) {
        nCompleted = 0
        queueCount += 1
        queueCount match {
          case 1 => self ! PrintNotifications
          case 2 => self ! PrintMentions
          case 3 =>
            serverActor ! Broadcast(PoisonPill)
            context.system.shutdown()
        }
      }

  }

  def sendMsg(curUser : User) = {
    //println(nMessages)
    nMessages += 1
    if (nMessages == Messages.msgLimit) {
      println("Limit reached!")
      for (cancellable <- cancelMap.values)
        cancellable.cancel()
      directMessage()
      reTweet()
    }
    if (nMessages < Messages.msgLimit) {
      var rndTweet = randomTweet(curUser)
      //println(curUser + " ---> " + rndTweet)
      curUser.getReference() ! Tweet(rndTweet)
    }
  }

  def directMessage() {
    val rand = Random
    // No of direct message = no of clients
    for (user <- clientList) {
      val followers = user.getFollowers()
      if (followers.size != 0) {
        val count = rand.nextInt(followers.size)
        for (i <- 0 until count) {
          val toAddr = "dm @" + followers(rand.nextInt(followers.size)).getName()
          user.getReference() ! Tweet(toAddr + " " + randomString(140 - toAddr.length() - 1))
        }
      }
    }
  }

  def reTweet() {
    //No of retweets = no of clients
    val rtKeys = Messages.rtKeys
    val rand = Random
    val nClients = clientList.size
    for (i <- 0 to nClients) {
      val curUser = clientList(rand.nextInt(nClients))
      val twUser = clientList(rand.nextInt(nClients))
      if (!curUser.equals(twUser)) {
        var rtIdx = rand.nextInt(rtKeys.size)
        var tweet = ""
        if (rtIdx == 0)
          tweet = rtKeys(rtIdx) + twUser.getName() + " "
        else
          tweet = " " + rtKeys(rtIdx) + twUser.getName()

        breakable {
          while (true) {
            val messages = twUser.getMessages()
            if (messages.size > 0) {
              val pickIdx = rand.nextInt(messages.size)
              //println(pickIdx + " vs " + messages.size)
              val tweetString = messages.get(pickIdx)
              if (tweetString.size + tweet.size <= 140) {
                if (rtIdx == 0)
                  tweet = tweet + tweetString
                else
                  tweet = tweetString + tweet
                break
              }
            }
          }
          throw new Exception("Exception at retweeting string")
        }
        curUser.getReference() ! Tweet(tweet)
      }
    }
    self ! PrintMessages
  }

  def randomTweet(curUser : User) : String = {

    var tweetLength = Messages.avgTweetLength + Random.nextInt(140 - Messages.avgTweetLength + 1)
    RandomPicker.pickRandom(TweetStrAt) match {
      case TweetStrAt.withoutAt => randomString(tweetLength)
      case TweetStrAt.withAt =>
        RandomPicker.pickRandom(TweetStrAtPos) match {
          case TweetStrAtPos.atBeginning =>
            RandomPicker.pickRandom(TweetStrTo) match {
              case TweetStrTo.toFollower =>
                var followers = curUser.getFollowers()
                var r = new Random()
                var nFollowers = followers.length
                if (nFollowers == 0) {
                  while (true) {
                    var idx = r.nextInt(Messages.nClients)
                    if (idx != curUser.getID()) {
                      var handler = StringBuilder.newBuilder.++=("@").++=(clientList(idx).getName())
                      return handler.mkString + " " + randomString(tweetLength - handler.length)
                    }
                  }
                  throw new Exception("Exception at infinite while")
                }
                else {
                  var randVal = r.nextInt(nFollowers)
                  var follower = followers(randVal)
                  var handler = StringBuilder.newBuilder.++=("@").++=(follower.getName())
                  return handler.mkString + " " + randomString(tweetLength - handler.length)
                }
              case TweetStrTo.toRandomUser =>
                var r = new Random()
                while (true) {
                  var idx = r.nextInt(Messages.nClients)
                  if (idx != curUser.getID()) {
                    var handler = StringBuilder.newBuilder.++=("@").++=(clientList(idx).getName())
                    return handler.mkString + " " + randomString(tweetLength - handler.length)
                  }
                }
                throw new Exception("Exception at infinite while")
            }
          case TweetStrAtPos.atNotBeginning =>
            RandomPicker.pickRandom(TweetStrTo) match {
              case TweetStrTo.toFollower =>
                var followers = curUser.getFollowers()
                var r = new Random()
                var nFollowers = followers.length
                if (nFollowers == 0) {
                  while (true) {
                    var idx = r.nextInt(Messages.nClients)
                    if (idx != curUser.getID()) {
                      var handler = StringBuilder.newBuilder.++=("@").++=(clientList(idx).getName())
                      var remChars = tweetLength - handler.length - 1
                      var str1Len = r.nextInt(remChars) + 1
                      var str1 : String = randomString(str1Len)
                      var str2 : String = ""
                      var splitIdx = remChars - str1Len
                      if (splitIdx > 0)
                        str2 = randomString(splitIdx - 1)
                      return str1 + " " + handler.toString + " " + str2
                    }
                  }
                  throw new Exception("Exception at infinite while")
                }
                else {
                  var randVal = r.nextInt(nFollowers)
                  var follower = followers(randVal)
                  var handler = StringBuilder.newBuilder.++=("@").++=(follower.getName())
                  var remChars = tweetLength - handler.length - 1
                  var str1Len = r.nextInt(remChars) + 1
                  var str1 : String = randomString(str1Len)
                  var str2 : String = ""
                  var splitIdx = remChars - str1Len
                  if (splitIdx > 0)
                    str2 = randomString(splitIdx - 1)
                  return str1 + " " + handler.toString + " " + str2
                }
              case TweetStrTo.toRandomUser =>
                var r = new Random()
                while (true) {
                  var idx = r.nextInt(Messages.nClients)
                  if (idx != curUser.getID()) {
                    var handler = StringBuilder.newBuilder.++=("@").++=(clientList(idx).getName())
                    var remChars = tweetLength - handler.length - 1
                    var str1Len = r.nextInt(remChars) + 1
                    var str1 : String = randomString(str1Len)
                    var str2 : String = ""
                    var splitIdx = remChars - str1Len
                    if (splitIdx > 0)
                      str2 = randomString(splitIdx - 1)
                    return str1 + " " + handler.toString + " " + str2
                  }
                }
                throw new Exception("Exception at infinite while")
            }
        }
    }

  }

  def randomString(length : Int) : String = {
    val sb = new StringBuilder
    if (length <= 0)
      return sb.toString
    val r = new scala.util.Random
    /*for (i <- 1 to length) {
      breakable {
        while (true) {
          var char = r.nextPrintableChar
          if (char.!=('@')) {
            sb.append(char)
            break
          }
        }
        throw new Exception("Exception at infinite while")
      }
    }*/
    breakable {
      while (true) {
        val newString = r.alphanumeric.take(length).mkString
        if (!Messages.keyWords.contains(newString)) {
          sb.append(newString)
          break
        }
      }
      throw new Exception("Exception at generating string")
    }
    return sb.toString
  }

  def generateFollowers(usersCount : Int, mean : Int) {
    var r = new Random()
    var nFollowers : Int = 0
    for (i <- 0 until usersCount - 1) {
      nFollowers = r.nextInt(Messages.avgFollowers);
      val user = clientList(i)
      for (f <- 0 until nFollowers + 1) {
        breakable {
          while (true) {
            val fIdx = r.nextInt(usersCount)
            if (fIdx != i) {
              user.addFollower(clientList(fIdx))
              break
            }
          }
          throw new Exception("Exception at infinite while")
        }
      }
    }
  }

  def readUserRateStats(usersCount : Int) {
    val filename = "userRate_stats.txt"
    var line : String = ""
    var startIdx : Int = 0
    var endIdx : Int = 0
    Source.fromFile(filename).getLines.foreach { line =>
      var tempArr = line.split(" ")
      var minMaxArr = tempArr.array(0).split("-")
      var percentage = tempArr.array(1)
      var minRate = minMaxArr.array(0)
      var maxRate = minMaxArr.array(1)
      var nUsers : Int = (((percentage.toDouble / 100) * usersCount).ceil).toInt
      endIdx = startIdx + nUsers
      if (endIdx > usersCount)
        endIdx = usersCount
      setUserRate(minRate.toInt, maxRate.toInt, startIdx, endIdx)
      startIdx = endIdx
    }

  }

  def setUserRate(minRate : Int, maxRate : Int, startIdx : Int, endIdx : Int) {
    var r = new Random();
    val avgRate = minRate + (maxRate - minRate) / 2
    for (i <- startIdx until endIdx) {
      var newRate = minRate + r.nextInt(maxRate - minRate + 1)
      if (newRate == 0)
        newRate += 1
      clientList(i).setMessageRate(newRate)
    }
  }

  def readFollowersStats(usersCount : Int) {
    val filename = "followers_stats.txt"
    var line : String = ""

    Source.fromFile(filename).getLines.foreach { line =>
      var tempArr = line.split(" ")
      var minMaxArr = tempArr.array(0).split("-")
      var percentage = tempArr.array(1)
      var minFollowers = minMaxArr.array(0)
      var maxFollowers = minMaxArr.array(1)

      FollowersGeneration(usersCount, minFollowers.toInt, maxFollowers.toInt, percentage.toDouble)
    }

  }

  def FollowersGeneration(usersCount : Int, minFollowers : Int, maxFollowers : Int, followersPercentage : Double) {

    var r = new Random();
    var r1 = new Random();
    var noOfFollowers : Int = 0

    var users : Double = (followersPercentage / 100) * usersCount
    var temp : Int = users.toInt

    for (i <- 0 until temp) {

      if (minFollowers == 0)
        noOfFollowers = r.nextInt(maxFollowers)
      else
        noOfFollowers = r.nextInt(minFollowers) + maxFollowers

      var j : Int = 0
      val user = clientList(i)

      for (j <- 0 until noOfFollowers) {
        val id = r1.nextInt(usersCount)
        user.addFollower(clientList(id))
        val following = clientList(id)
        following.addFollowing(user)
      }
    }

  }

}

class Client(identifier : Int) extends Actor {
  var serverActor = ClientApp.serverActor
  def receive = {

    case "ACK" =>
      println("Client " + identifier + " activated")
      ClientApp.nRequests += 1
      if (ClientApp.nRequests == Messages.nClients) {
        ClientApp.interActor ! RouteClients
      }

    case Tweet(tweet) =>
      serverActor ! Tweet(tweet)

    case Top(n) =>
      serverActor ! Top(n)

    case TopMentions(n) =>
      serverActor ! TopMentions(n)

    case TopNotifications(n) =>
      serverActor ! TopNotifications(n)

    case MessageList(msgList) =>
      println("Received top messages for client: " + identifier)
      msgList.foreach(println)
      ClientApp.interActor ! ClientCompleted

    case MentionList(msgList) =>
      println("Received top mentions for client: " + identifier)
      msgList.foreach(println)
      ClientApp.interActor ! ClientCompleted

    case NotificationList(msgList) =>
      println("Received top notifications for client: " + identifier)
      msgList.foreach(println)
      ClientApp.interActor ! ClientCompleted

  }

}
