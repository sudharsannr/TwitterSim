package simulator

import akka.actor.{ Actor, ActorSystem, Props, Scheduler, Cancellable, PoisonPill }
import com.typesafe.config.ConfigFactory
import simulator.Messages.{ Init, Tweet, Top, ScheduleClient, MessageList, PrintMessages, RegisterClients, ClientCompleted, ShutDown, PrintMentions, PrintNotifications, TopMentions, TopNotifications, MentionList, NotificationList }
import scala.util.Random
import scala.util.control.Breaks._
import scala.collection.mutable.{ ListBuffer, ArrayBuffer }
import scala.io.Source
import scala.concurrent.duration._
import akka.routing.{ FromConfig, RoundRobinRouter, Broadcast }

object ClientApp extends App {

  // TODO Stop when complete
  //val ipAddr : String = args(0)
  val ipAddr : String = "127.0.0.1:8248"
  val system = ActorSystem("TwitterClientActor", ConfigFactory.load("applicationClient.conf"))
  //val serverActor = system.actorOf(Props[Server])
  val sActor = system.actorFor("akka.tcp://TwitterActor@" + ipAddr + "/user/Server")
  val serverVector = Vector.fill(Messages.nServers)(sActor)
  val serverActor = system.actorOf(Props.empty.withRouter(RoundRobinRouter(routees = serverVector)), "serverRouter")
  val interActor = system.actorOf(Props(new Interactor()))
  var nRequests : Int = 0
  val startTime = java.lang.System.currentTimeMillis()
  interActor ! Init

}

class Interactor() extends Actor {
  var serverActor = ClientApp.serverActor
  var clientList = new Array[User](Messages.nClients)
  var nMessages : Int = 0
  var cancelMap : Map[User, Cancellable] = Map()
  var nCompleted : Int = 0
  var queueCount : Int = 0
  val actorSys = ClientApp.system
  var limitReached : Boolean = false
  import actorSys.dispatcher
  for (i <- 0 to clientList.length - 1)
    clientList(i) = new User(i, context.actorOf(Props(new Client(i : Int))))
  //generateFollowers(, Messages.mean)
  readFollowersStats(clientList.length)
  readUserRateStats(clientList.length)
  //  for (user <- clientList)
  //    println(user)
  //  for(user <- clientList)
  //  {
  //	  printf(user.getName() + ":")
  //	  for(follower <- user.getFollowers())
  //		  printf(follower.getName() + ", ")
  //	  println()
  //  }

  def receive = {

    case Init =>
      for (curUser <- clientList)
        serverActor ! RegisterClients(curUser)

    case ScheduleClient(identifier) =>
      if (!limitReached) {
        //println("Scheduling client " + identifier)
        val curUser = clientList(identifier)
        val cancellable = actorSys.scheduler.schedule(0.milliseconds, curUser.getMsgRate.milliseconds)(sendMsg(curUser))
        cancelMap += (curUser -> cancellable)
      }

    case PrintMessages =>
      println("Printing messages")
      var i:Int = 0
      for (i <- 0 to clientList.length - 1) {
      	println(i)
      	clientList(i).getReference() ! Top(Messages.maxBufferSize)
      }
        

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
      if (nCompleted == clientList.length) {
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
    println(nMessages)
    nMessages += 1
    
    if (nMessages >= Messages.msgLimit) {
      println("Limit reached!")
      limitReached = true
      for (cancellable <- cancelMap.values)
        cancellable.cancel()
      directMessage()
      reTweet()

    }
    else if (nMessages < Messages.msgLimit) {
      val curSec = java.lang.System.currentTimeMillis()
      val curTime = ((curSec - ClientApp.startTime).toDouble) / 1000
      if (curTime >= Messages.peakStart && curTime < Messages.peakEnd) {
        for (i <- 0 to Messages.peakScale) {
          var rndTweet = randomTweet(curUser)
          curUser.getReference() ! Tweet(rndTweet)
        }
        nMessages += Messages.peakScale - 1
      }
      else {
        var rndTweet = randomTweet(curUser)
        //println(curUser + " ---> " + rndTweet)
        curUser.getReference() ! Tweet(rndTweet)
      }
    }
  }

  def directMessage() {
    println("Direct Message")
    val rand = Random
    // No of direct message = no of clients
    for (user <- clientList) {
      val followers = user.getFollowers()
      if (followers.size != 0) {
        val count = rand.nextInt(followers.size)
        for (i <- 0 until count) {
          val toAddr = "dm @" + followers(rand.nextInt(followers.size)).getName()
          println(i)
          user.getReference() ! Tweet(toAddr + " " + randomString(140 - toAddr.length() - 1))
        }
      }
    }
  }

  def reTweet() {
    //No of retweets = no of clients
    println("ReTweet")
    val rtKeys = Messages.rtKeys
    val rand = Random
    val nClients = clientList.size
    for (i <- 0 to nClients) {
      val curUser = clientList(rand.nextInt(nClients))
      val twUser = clientList(rand.nextInt(nClients))
      println(i)
      if (!curUser.equals(twUser)) {
        var rtIdx = rand.nextInt(rtKeys.size)
        var tweet = ""
        if (rtIdx == 0)
          tweet = rtKeys(rtIdx) + twUser.getName() + " "
        else
          tweet = " " + rtKeys(rtIdx) + twUser.getName()

        //FIXME Bug when checking if retweet + tweet <= 140 and infinite loop
        val messages = twUser.getMessages().toArray
        if (messages.size > 0) {
          val pickIdx = rand.nextInt(messages.size)
          //println(pickIdx + " vs " + messages.size)
          val tweetString = messages(pickIdx).toString()
          if (tweetString.size <= 140 + tweet.size) {
            if (rtIdx == 0)
              tweet = tweet + tweetString
            else
              tweet = tweetString + tweet
          }
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
        var nMentions = 1 + Random.nextInt(Messages.maxMentions)
        RandomPicker.pickRandom(TweetStrAtPos) match {
          case TweetStrAtPos.atBeginning =>
            RandomPicker.pickRandom(TweetStrTo) match {
              case TweetStrTo.toFollower =>
                var followers = curUser.getFollowers()
                var r = new Random()
                var nFollowers = followers.length
                if (nFollowers == 0) {
                  var handler = StringBuilder.newBuilder
                  for (i <- 0 until nMentions) {
                    var idx = r.nextInt(clientList.length)
                    while (idx == curUser.getID())
                      idx = r.nextInt(clientList.length)
                    handler.++=("@").++=(clientList(idx).getName()).++=(" ")
                  }
                  if (tweetLength <= handler.length)
                    tweetLength = 140
                  return handler.mkString + randomString(tweetLength - handler.length)
                }
                else {
                  var handler = StringBuilder.newBuilder
                  var followerList = ArrayBuffer.empty[User]
                  var i : Int = 0
                  while (i < nMentions) {
                    var follower = followers(r.nextInt(nFollowers))
                    if (!followerList.contains(follower)) {
                      followerList.+=(follower)
                      i += 1
                    }
                  }
                  for (follower <- followerList)
                    handler.++=("@").++=(follower.getName()).++=(" ")
                  if (tweetLength <= handler.length)
                    tweetLength = 140
                  return handler.mkString + randomString(tweetLength - handler.length)
                }
              case TweetStrTo.toRandomUser =>
                var r = new Random()
                //var handler = StringBuilder.newBuilder
                var handler : String = ""
                for (i <- 0 until nMentions) {
                  var idx = r.nextInt(clientList.length)
                  while (idx == curUser.getID())
                    idx = r.nextInt(clientList.length)
                  //handler.++=("@").++=(clientList(idx).getName()).++=(" ")
                    handler += "@" + clientList(idx).getName() + " "
                }
                if (tweetLength <= handler.length)
                  tweetLength = 140
                return handler.mkString + randomString(tweetLength - handler.length)
            }
          case TweetStrAtPos.atNotBeginning =>
            RandomPicker.pickRandom(TweetStrTo) match {
              case TweetStrTo.toFollower =>
                var followers = curUser.getFollowers()
                var r = new Random()
                var nFollowers = followers.length
                //var handler = StringBuilder.newBuilder
                var handler:String = ""
                var remChars = -1
                if (nFollowers == 0) {
                  while (remChars < 1) {
                    for (i <- 0 until nMentions) {
                      var idx = r.nextInt(clientList.length)
                      while (idx == curUser.getID())
                        idx = r.nextInt(clientList.length)
                      //handler.++=("@").++=(clientList(idx).getName()).++=(" ")
                        handler += "@" + clientList(idx).getName() + " "
                    }
                    if (tweetLength <= handler.length)
                      tweetLength = 140
                    remChars = tweetLength - handler.length - 1
                  }
                  var str1Len = r.nextInt(remChars)
                  var str1 : String = randomString(str1Len)
                  var str2 : String = ""
                  var splitIdx = remChars - str1Len
                  if (splitIdx > 0)
                    str2 = randomString(splitIdx)
                  //return str1 + " " + handler.toString + str2
                  return str1 + " " + handler + str2
                }
                else {
                  var remChars = -1
                  while (remChars < 1) {
                    //var handler = StringBuilder.newBuilder
                  	var handler:String = ""
                    var followerList = ArrayBuffer.empty[User]
                    var i : Int = 0
                    while (i < nMentions) {
                      var follower = followers(r.nextInt(nFollowers))
                      if (!followerList.contains(follower)) {
                        followerList.+=(follower)
                        i += 1
                      }
                    }
                    for (follower <- followerList)
                      //handler.++=("@").++=(follower.getName()).++=(" ")
                      handler += "@" + follower.getName() + " "
                    if (tweetLength <= handler.length)
                      tweetLength = 140
                    remChars = tweetLength - handler.length - 1
                    if (remChars == 0 && tweetLength < 140)
                      remChars += Messages.avgTweetLength
                  }
                  var str1Len = r.nextInt(remChars)
                  var str1 : String = randomString(str1Len)
                  var str2 : String = ""
                  var splitIdx = remChars - str1Len
                  if (splitIdx > 0)
                    str2 = randomString(splitIdx)
                  //return str1 + " " + handler.toString + str2
                    return str1 + " " + handler + str2
                }
              case TweetStrTo.toRandomUser =>
                var remChars = -1
                var r = new Random()
                //var handler : StringBuilder = null
                var handler : String = ""
                while (remChars < 1) {
                  //handler = StringBuilder.newBuilder
                	handler = ""
                  for (i <- 0 until nMentions) {
                    var idx = r.nextInt(clientList.length)
                    while (idx == curUser.getID())
                      idx = r.nextInt(clientList.length)
                    //handler.++=("@").++=(clientList(idx).getName())
                      handler += "@" + clientList(idx).getName()
                  }
                  if (tweetLength <= handler.length)
                    tweetLength = 140
                  remChars = tweetLength - handler.length - 1
                  if (remChars == 0 && tweetLength < 140)
                    remChars += Messages.avgTweetLength
                }
                var str1Len = r.nextInt(remChars)
                var str1 : String = randomString(str1Len)
                var str2 : String = ""
                var splitIdx = remChars - str1Len
                if (splitIdx > 0)
                  str2 = randomString(splitIdx)
                //return str1 + " " + handler.toString + str2
                  return str1 + " " + handler + str2
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
    var targetLength = length
    while (targetLength > 0) {
      val newString = r.alphanumeric.take(1 + r.nextInt(targetLength)).mkString
      if (!Messages.keyWords.contains(newString))
        sb.append(newString)
      if (sb.length < targetLength)
        sb.append(" ")
      targetLength -= sb.length
    }
    return sb.toString
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
        var id = r1.nextInt(usersCount)
        while (id == user.identifier) {
          id = r1.nextInt(usersCount)
        }
        user.addFollower(clientList(id))
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
      ClientApp.interActor ! ScheduleClient(identifier)

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
