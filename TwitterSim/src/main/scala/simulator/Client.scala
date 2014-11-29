package simulator

import akka.actor.{ Actor, ActorSystem, Props, Scheduler, Cancellable, PoisonPill, ActorRef, Terminated }
import com.typesafe.config.ConfigFactory
import simulator.Messages._
import scala.util.Random
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.concurrent.duration._
import akka.routing.{ FromConfig, RoundRobinRouter, Broadcast }
import scala.actors.threadpool.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

//FIXME Load balancing to be done
//FIXME StackOverflow error for actors >= 5000
object ClientApp extends App {

  //TODO Param
  val ipAddr : String = "127.0.0.1:8248"
  val system = ActorSystem("TwitterClientActor", ConfigFactory.load("applicationClient.conf"))
  val sActor = system.actorFor("akka.tcp://TwitterActor@" + ipAddr + "/user/Server")
  val serverActor = system.actorOf(Props.empty.withRouter(RoundRobinRouter(routees = Vector.fill(Messages.nServers)(sActor))), "serverRouter")
  val interActor = system.actorOf(Props[Interactor])
  interActor ! Init

}

class Interactor extends Actor {

  val startTime = java.lang.System.currentTimeMillis()
  val usersCount = Messages.nClients
  var nMessages = new AtomicInteger()
  var queueCount = new AtomicInteger()
  var nCompleted = new AtomicInteger()
  var limitReached = new AtomicBoolean()
  var userMap : Map[ActorRef, User] = Map()
  var userList = new Array[User](usersCount)
  var cancelMap : Map[ActorRef, Cancellable] = Map()
  implicit val ev = context.dispatcher

  for (i <- 0 to usersCount - 1) {
    val curUser = new User(i)
    curUser.setRandomName()
    userList(i) = curUser
    val curActor = context.actorOf(Props[UserActor])
    userMap += (curActor -> curUser)
    context.watch(curActor)
  }

  readFollowersStats(usersCount)
  readUserRateStats(usersCount)
  //printUsers(userList)

  def receive = {

    case Init =>
      userMap.foreach { case (k, v) => ClientApp.serverActor ! RegisterClients(k, v) }

    case ScheduleClient =>
      if (!limitReached.get()) {
        val curUser = userMap(sender)
        println("Scheduling client " + curUser.getID())
        var cancellable = context.system.scheduler.schedule(0.seconds, curUser.getMessageRate().seconds)(sendMsg(sender, curUser))
        cancelMap += (sender -> cancellable)
      }

    case PrintMessages =>
      println("Printing messages")
      userMap.foreach { case (k, v) => k ! Top(Messages.maxBufferSize) }

    case PrintNotifications =>
      println("Printing notificatins")
      userMap.foreach { case (k, v) => k ! TopNotifications(Messages.maxBufferSize) }

    case PrintMentions =>
      println("Printing mentions")
      userMap.foreach { case (k, v) => k ! TopMentions(Messages.maxBufferSize) }

    case ClientCompleted =>
      if (nCompleted.incrementAndGet() == usersCount) {
        nCompleted.set(0)
        queueCount.incrementAndGet() match {
          case 1 => self ! PrintNotifications
          case 2 => self ! PrintMentions
          case 3 =>
        }
      }

    case Terminated(ref) =>
      userMap -= ref
      if (userMap.isEmpty) {
        println("Shutting down")
        ClientApp.serverActor ! Broadcast(PoisonPill)
        context.system.shutdown()
      }

  }

  def sendMsg(actor : ActorRef, curUser : User) = {
    nMessages.incrementAndGet()
    println(nMessages)
    // Comparison greater when the message rate during peak exceeds the set limit
    if (nMessages.get() >= Messages.msgLimit) {
      println("Limit reached!")
      if (!limitReached.get()) {
        for (cancellable <- cancelMap.values)
          cancellable.cancel()
      }
      limitReached.set(true)
      directMessage()
      reTweet()
    }

    else if (nMessages.get() < Messages.msgLimit) {

      val curSec = java.lang.System.currentTimeMillis()
      val curTime = ((curSec - startTime).toDouble) / 1000
      if (curTime >= Messages.peakStart && curTime < Messages.peakEnd) {
        for (i <- 0 to Messages.peakScale) {
          var rndTweet = randomTweet(curUser)
          actor ! Tweet(rndTweet)
        }
        nMessages.addAndGet(Messages.peakScale - 1)
      }

      else {
        var rndTweet = randomTweet(curUser)
        curUser.addMessage(rndTweet)
        actor ! Tweet(rndTweet)
      }
    }
  }

  def directMessage() {
    println("Direct Message")
    val rand = Random
    // No of direct message = no of clients
    userMap.foreach {
      case (actor, user) =>
        val followers = user.getFollowers()
        if (followers.size > 0) {
          val count = rand.nextInt(followers.size)
          for (i <- 0 until count) {
            val toAddr = "dm @" + userList(followers(rand.nextInt(followers.size))).getName()
            actor ! Tweet(toAddr + " " + randomString(140 - toAddr.length() - 1))
          }
        }
    }
  }

  def reTweet() {
    //No of retweets = no of clients
    println("ReTweet")
    val rtKeys = Messages.rtKeys
    val rand = Random
    val nClients = userList.size
    val revMap = (Map() ++ userMap.map(_.swap))
    for (i <- 0 until nClients) {

      val curUser = userList(rand.nextInt(nClients))
      val twUser = userList(rand.nextInt(nClients))

      if (!curUser.equals(twUser)) {
        var rtIdx = rand.nextInt(rtKeys.size)
        var tweet = ""
        val messages = twUser.getMessages().toArray()
        if (messages.size > 0) {
          val pickIdx = rand.nextInt(messages.size)
          val tweetString = messages(pickIdx).toString()
          if (rtIdx == 0)
            tweet = rtKeys(rtIdx) + twUser.getName() + " " + tweetString
          else
            tweet = tweetString + " " + rtKeys(rtIdx) + twUser.getName()
        }
        revMap.get(curUser).get ! Tweet(tweet)
      }
    }
    self ! PrintMessages
  }

  def randomTweet(curUser : User) : String = {

    /**
     * Tweet cases
     * 	without @
     *  with @
     *  	position beginning
     *   		to follower
     *     		to random user
     *      position not at beginning
     *      	to follower
     *       	to random user
     */
    var tweetLength = Messages.avgTweetLength + Random.nextInt(140 - Messages.avgTweetLength + 1)
    var r = new Random()
    RandomPicker.pickRandom(TweetStrAt) match {
      case TweetStrAt.withoutAt =>
        randomString(tweetLength)

      case TweetStrAt.withAt =>
        var nMentions = 1 + Random.nextInt(Messages.maxMentions)
        RandomPicker.pickRandom(TweetStrAtPos) match {

          case TweetStrAtPos.atBeginning =>
            RandomPicker.pickRandom(TweetStrTo) match {

              case TweetStrTo.toFollower =>
                var followers = curUser.getFollowers()
                var nFollowers = followers.length
                var handler = new StringBuilder()
                // No followers
                if (nFollowers == 0) {
                  // Count correction
                  if (nMentions > usersCount)
                    nMentions = usersCount
                  for (i <- 0 until nMentions) {
                    var idx = r.nextInt(usersCount)
                    while (idx == curUser.getID())
                      idx = r.nextInt(usersCount)
                    handler.++=("@").++=(userList(idx).getName()).++=(" ")
                  }
                  if (tweetLength <= handler.length)
                    tweetLength = 140
                }
                // Followers present
                else {
                  var followerList = ArrayBuffer.empty[Int]
                  // Count correction
                  if (nMentions > followers.length)
                    nMentions = followers.length
                  var i : Int = 0
                  while (i < nMentions) {
                    var follower = followers(r.nextInt(nFollowers))
                    if (!followerList.contains(follower)) {
                      followerList.+=(follower)
                      handler.++=("@").++=(userList(follower).getName()).++=(" ")
                      i += 1
                    }
                  }
                  // If handlers exceed average tweet length, set to maximum capacity
                  if (handler.length > tweetLength)
                    tweetLength = 140
                }
                return handler.mkString + randomString(tweetLength - handler.length)

              case TweetStrTo.toRandomUser =>
                var handler = new StringBuilder()
                // Count correction
                if (nMentions > usersCount)
                  nMentions = usersCount
                var followerList = ArrayBuffer.empty[Int]
                var i : Int = 0
                while (i < nMentions) {
                  var idx = r.nextInt(usersCount)
                  while (idx == curUser.getID())
                    idx = r.nextInt(usersCount)
                  if (!followerList.contains(idx)) {
                    followerList.+=(idx)
                    i += 1
                    handler.++=("@").++=(userList(idx).getName()).++=(" ")
                  }
                }
                if (handler.length > tweetLength)
                  tweetLength = 140
                return handler.mkString + randomString(tweetLength - handler.length)
            }

          case TweetStrAtPos.atNotBeginning =>
            RandomPicker.pickRandom(TweetStrTo) match {

              case TweetStrTo.toFollower =>
                var followers = curUser.getFollowers()
                var nFollowers = followers.length
                var handler = new StringBuilder()
                var remChars = -1
                // No followers
                if (nFollowers == 0) {
                  if (nMentions > usersCount)
                    nMentions = usersCount
                  var followerList = ArrayBuffer.empty[Int]
                  var i : Int = 0
                  while (remChars < 1) {
                    while (i < nMentions) {
                      var idx = r.nextInt(usersCount)
                      while (idx == curUser.getID())
                        idx = r.nextInt(usersCount)
                      if (!followerList.contains(idx)) {
                        followerList.+=(idx)
                        handler.++=("@").++=(userList(idx).getName()).++=(" ")
                        i += 1
                      }
                    }
                    if (tweetLength <= handler.length)
                      tweetLength = 140
                    remChars = tweetLength - handler.length - 1
                  }

                  // Pick the string split
                  var str1Len = r.nextInt(remChars)
                  var str1 : String = randomString(str1Len)
                  var str2 : String = ""
                  var splitIdx = remChars - str1Len
                  if (splitIdx > 0)
                    str2 = randomString(splitIdx)
                  return str1 + " " + handler.toString + str2
                }

                // Followers present
                else {
                  var remChars = -1
                  // Count correction
                  if (nMentions > nFollowers)
                    nMentions = nFollowers
                  while (remChars < 1) {
                    var handler = new StringBuilder()
                    var followerList = ArrayBuffer.empty[Int]
                    var i : Int = 0
                    while (i < nMentions) {
                      var follower = followers(r.nextInt(nFollowers))
                      if (!followerList.contains(follower)) {
                        followerList.+=(follower)
                        handler.++=("@").++=(userList(follower).getName()).++=(" ")
                        i += 1
                      }
                    }
                    if (handler.length > tweetLength)
                      tweetLength = 140
                    remChars = tweetLength - handler.length - 1
                    // Increase the messsage length if closer on completion
                    if (remChars == 0 && tweetLength < 140)
                      remChars += Messages.avgTweetLength
                  }

                  // Find split index for string
                  var str1Len = r.nextInt(remChars)
                  var str1 : String = randomString(str1Len)
                  var str2 : String = ""
                  var splitIdx = remChars - str1Len
                  if (splitIdx > 0)
                    str2 = randomString(splitIdx)
                  return str1 + " " + handler.toString + str2
                }

              case TweetStrTo.toRandomUser =>
                var remChars = -1
                var handler = new StringBuilder()
                var followerList = ArrayBuffer.empty[Int]
                // Count correction
                if (nMentions > usersCount)
                  nMentions = usersCount
                var i : Int = 0

                // FIXME Possible infinite loop
                while (remChars < 1) {
                  while (i < nMentions) {
                    var idx = r.nextInt(usersCount)
                    while (idx == curUser.getID())
                      idx = r.nextInt(usersCount)
                    if (!followerList.contains(idx)) {
                      followerList.+=(idx)
                      i += 1
                      handler.++=("@").++=(userList(idx).getName())
                    }
                  }
                  if (tweetLength <= handler.length)
                    tweetLength = 140
                  remChars = tweetLength - handler.length - 1
                  if (remChars == 0 && tweetLength < 140)
                    remChars += Messages.avgTweetLength
                }

                // Find the string split
                var str1Len = r.nextInt(remChars)
                var str1 : String = randomString(str1Len)
                var str2 : String = ""
                var splitIdx = remChars - str1Len
                if (splitIdx > 0)
                  str2 = randomString(splitIdx)
                return str1 + " " + handler.toString + str2
            }
        }
    }
  }

  def randomString(length : Int) : String = {
    val sb = new StringBuilder
    if (length <= 0)
      return sb.toString
    val r = new scala.util.Random
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
      if (startIdx < endIdx)
        setUserRate(minRate.toInt, maxRate.toInt, startIdx, endIdx)
      startIdx = endIdx
    }

  }

  def setUserRate(minRate : Int, maxRate : Int, startIdx : Int, endIdx : Int) {
    var r = new Random();
    for (i <- startIdx until endIdx) {
      var newRate = minRate + r.nextInt(maxRate - minRate + 1)
      if (newRate == 0)
        newRate += 1
      userList(i).setMessageRate(newRate)
    }
  }

  def readFollowersStats(usersCount : Int) {
    val filename = "followers_stats.txt"
    var line : String = ""
    Source.fromFile(filename).getLines.foreach { line =>
      val tempArr = line.split(" ")
      val minMaxArr = tempArr.array(0).split("-")
      val percentage = tempArr.array(1).toDouble
      val minFollowers = minMaxArr.array(0).toInt
      val maxFollowers = minMaxArr.array(1).toInt
      if (minFollowers <= usersCount && maxFollowers <= usersCount) {
        val nUsers = ((percentage / 100) * usersCount).toInt
        followersGeneration(minFollowers, maxFollowers, nUsers)
      }
    }
  }

  def followersGeneration(minFollowers : Int, maxFollowers : Int, nUsers : Int) {
    var r = new Random();
    var r1 = new Random();
    var noOfFollowers : Int = 0
    for (i <- 0 until nUsers) {
      noOfFollowers = r.nextInt(maxFollowers - minFollowers + 1) + minFollowers
      val user = userList(i)
      for (j <- 0 until noOfFollowers) {
        var id = r1.nextInt(usersCount)
        while (id == i)
          id = r1.nextInt(usersCount)
        user.addFollower(id)
      }
    }
  }

  def printUsers(clientList : Array[User]) {
    for (user <- clientList)
      println(user.toString)
    for (user <- clientList) {
      print(user.getID() + ": ")
      for (follower <- user.getFollowers())
        printf(follower + ", ")
      println()
    }
  }

}

class UserActor extends Actor {
  var serverActor : ActorRef = _

  def receive = {

    case "ACK" =>
      serverActor = sender
      ClientApp.interActor ! ScheduleClient

    case Tweet(tweet) =>
      serverActor ! Tweet(tweet)

    case Top(n) =>
      serverActor ! Top(n)

    case TopMentions(n) =>
      serverActor ! TopMentions(n)

    case TopNotifications(n) =>
      serverActor ! TopNotifications(n)

    case MessageList(msgList, id) =>
      println("Printing messages for user " + id)
      msgList.foreach(println)
      ClientApp.interActor ! ClientCompleted

    case NotificationList(msgList, id) =>
      println("Printing notifications for user " + id)
      msgList.foreach(println)
      ClientApp.interActor ! ClientCompleted

    case MentionList(msgList, id) =>
      println("Printing mentions for user " + id)
      msgList.foreach(println)
      context.stop(self)
  }

}