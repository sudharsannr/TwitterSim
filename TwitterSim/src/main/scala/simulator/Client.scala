package simulator

import akka.actor.{ Actor, ActorSystem, Props, Scheduler, Cancellable, PoisonPill, ActorRef, Terminated }
import com.typesafe.config.ConfigFactory
import simulator.Messages._
import scala.util.Random
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.concurrent.duration._
import akka.routing.{ FromConfig, RoundRobinRouter, Broadcast, RandomRouter }
import scala.actors.threadpool.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import spray.http._
import spray.client.pipelining._
import scala.collection.mutable.MutableList

object ClientApp extends App {

  //TODO Param
  val ipAddr : String = "127.0.0.1:8248"
  implicit val system = ActorSystem("TwitterClientActor", ConfigFactory.load("applicationClient.conf"))
  val sActor = system.actorFor("akka.tcp://TwitterActor@" + ipAddr + "/user/Server")
  val serverActor = system.actorOf(Props.empty.withRouter(RoundRobinRouter(routees = Vector.fill(Messages.nServers)(sActor))), "serverRouter")
  val interActor = system.actorOf(Props(new Interactor(serverActor)))
  import system.dispatcher
  val pipeline = sendReceive
  interActor ! Init

}

class Interactor(serverActor : ActorRef) extends Actor {

  val startTime = java.lang.System.currentTimeMillis()
  val usersCount = Messages.nClients
  var nMessages = new AtomicInteger()
  var queueCount = new AtomicInteger()
  var nCompleted = new AtomicInteger()
  var limitReached = new AtomicBoolean()
  var userMap : Map[ActorRef, User] = Map()
  var userList = new Array[User](usersCount)
  var cancelMap : Map[ActorRef, Cancellable] = Map()
  var restCancellables : Array[Cancellable] = new Array[Cancellable](2)
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

  var r = new Random()
  var r1 = new Random()

  //restCancellables(0) = context.system.scheduler.schedule(0.second, 1.second)(postREST(userList(r1.nextInt(nClients - 1))))
  //restCancellables(1) = context.system.scheduler.schedule(0.milliseconds, 30.milliseconds)(getAllTweets(r.nextInt(nClients - 1)))

  def receive = {

    case Init =>
      userMap.foreach { case (k, v) => ClientApp.serverActor ! RegisterClients(k, v) }

    case ScheduleClient =>
      if (!limitReached.get()) {
        val curUser = userMap(sender)
        //println("Scheduling client " + curUser.getID())
        var clientActor = sender
        var cancellable = context.system.scheduler.schedule(0.seconds, curUser.getMessageRate().seconds)(sendMsg(clientActor, curUser))
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
          case default =>
        }
      }

    case Terminated(ref) =>
      userMap -= ref
      if (userMap.isEmpty) {
        println("Client message queue processed!")
        //ClientApp.serverActor ! Broadcast(PoisonPill)
        //context.system.shutdown()
        //getAllTweets()
        //RestClient()

      }

  }

  def sendMsg(clientActor : ActorRef, curUser : User) = {
    //println(nMessages)
    // Comparison greater when the message rate during peak exceeds the set limit
    if (nMessages.get() >= Messages.msgLimit) {
      synchronized {
        println("Limit reached!")
        for (cancellable <- cancelMap.values)
          cancellable.cancel()
        //restCancellables.foreach(c => c.cancel())
        if (!limitReached.get()) {
          limitReached.set(true)
          directMessage()
          reTweet()
        }
      }
    }

    else if (nMessages.get() < Messages.msgLimit) {

      val curSec = java.lang.System.currentTimeMillis()
      val curTime = ((curSec - startTime).toDouble) / 1000
      if (curTime >= Messages.peakStart && curTime < Messages.peakEnd) {
        for (i <- 0 to Messages.peakScale) {
          var rndTweet = randomTweet(curUser)
          curUser.addMessage(rndTweet)
          clientActor ! Tweet(rndTweet)
        }
        nMessages.addAndGet(Messages.peakScale - 1)
      }

      else {
        nMessages.incrementAndGet()
        var rndTweet = randomTweet(curUser)
        curUser.addMessage(rndTweet)
        clientActor ! Tweet(rndTweet)
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
            val tweetStr = toAddr + " " + randomString(140 - toAddr.length() - 1)
            actor ! Tweet(tweetStr)
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
        if (tweet.length() > 0)
          revMap.get(curUser).get ! Tweet(tweet)
      }
    }
    self ! PrintMessages
  }

  def randomTweet(curUser : User) : String = {
    //println("Inside randomtweet")
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
        //println("Without @")
        randomString(tweetLength)

      case TweetStrAt.withAt =>
        //println("With @")
        var nMentions = 1 + Random.nextInt(Messages.maxMentions)
        RandomPicker.pickRandom(TweetStrAtPos) match {

          case TweetStrAtPos.atBeginning =>
            //println("@ beginning")
            RandomPicker.pickRandom(TweetStrTo) match {

              case TweetStrTo.toFollower =>
                //println("to follower")
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
                  if (handler.length > tweetLength)
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
                //println("to random")
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
            //println("@ not beginning")
            RandomPicker.pickRandom(TweetStrTo) match {

              case TweetStrTo.toFollower =>
                //println("to follower")
                var followers = curUser.getFollowers()
                var nFollowers = followers.length
                // No followers
                if (nFollowers == 0) {
                  //println("zero followers")
                  randStringtoRandUser(nMentions, tweetLength, r, curUser)
                }

                // Followers present
                else {
                  //println("legit")
                  randStringtoFollower(nMentions, tweetLength, r, curUser, followers.asInstanceOf[MutableList[Int]])
                }

              case TweetStrTo.toRandomUser =>
                //println("to random")
                randStringtoRandUser(nMentions, tweetLength, r, curUser)
            }
        }
    }
  }

  def randStringtoFollower(nMention : Int, tweetLen : Int, r : Random, curUser : User, followers : MutableList[Int]) : String = {
    var nMentions = nMention
    var tweetLength = tweetLen
    if (nMentions > usersCount)
      nMentions = usersCount
    var followersCount = followers.length
    if (nMentions > followersCount)
      nMentions = followersCount
    var followerList = ArrayBuffer.empty[Int]
    var i : Int = 0
    while (i < nMentions) {
      var fIdx = followers(r.nextInt(followersCount))
      if (!followerList.contains(fIdx)) {
        followerList.+=(fIdx)
        i += 1
      }
    }

    var handleLength = 0
    for (fIdx <- followerList)
      handleLength += userList(fIdx).getName().length()

    if (tweetLength < handleLength)
      tweetLength = 140

    // Pick the user index split
    var splitIdx = r.nextInt(nMentions)
    var str1 = new StringBuilder()
    var str3 = new StringBuilder()
    for (i <- 0 until splitIdx)
      str1.++=(userList(followerList(i)).getName()).++=(" ")
    for (i <- splitIdx until nMentions)
      str3.++=(userList(followerList(i)).getName()).++=(" ")
    var str2 = randomString(tweetLength - (str1.length + str3.length))
    return str1.toString + str2 + str3.toString
  }

  def randStringtoRandUser(nMention : Int, tweetLen : Int, r : Random, curUser : User) : String = {
    var nMentions = nMention
    var tweetLength = tweetLen
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
      }
    }

    var handleLength = 0
    for (fIdx <- followerList)
      handleLength += userList(fIdx).getName().length()

    if (tweetLength < handleLength)
      tweetLength = 140

    // Pick the user index split
    var splitIdx = r.nextInt(nMentions)
    var str1 = new StringBuilder()
    var str3 = new StringBuilder()
    for (i <- 0 until splitIdx)
      str1.++=(userList(followerList(i)).getName()).++=(" ")
    for (i <- splitIdx until nMentions)
      str3.++=(userList(followerList(i)).getName()).++=(" ")
    var str2 = randomString(tweetLength - (str1.length + str3.length))
    return str1.toString + str2 + str3.toString
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
      //noOfFollowers = r.nextInt(maxFollowers - minFollowers + 1) + minFollowers
      noOfFollowers = 10
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

  def RestClient() {
    for (i <- 0 until usersCount) {
      val result = ClientApp.pipeline(Get("http://localhost:8080/get/tweets/" + i))
      result.foreach { response =>
        println(s"Request completed with status ${response.status}, Client: ${i} and content:\n${response.entity.asString}")
      }
    }
  }

  def postREST(curUser : User) {
    //println("Posting via REST API")
    var tweetStr = randomTweet(curUser).replaceAll(" ", "%20")
    val clientId = curUser.getID()
    val result = ClientApp.pipeline(Post("http://localhost:8080/post/add?id?=" + clientId + "&message=" + tweetStr))
    result.foreach { response =>
      //println(s"Request completed with status ${response.status} and content:\n${response.entity.asString}")
    }
  }

  def getAllTweets(clientId : Int) {
    val result = ClientApp.pipeline(Get("http://localhost:8080/get/tweets/" + clientId))
    result.foreach { response =>
      println(s"Request completed with status ${response.status} and content:\n${response.entity.asString}")
    }

  }

}

class UserActor extends Actor {

  def receive = {

    case "ACK" =>
      ClientApp.interActor ! ScheduleClient

    case Tweet(tweet) =>
      //println("Sending message " + tweet)
      ClientApp.serverActor ! Tweet(tweet)

    case Top(n) =>
      ClientApp.serverActor ! Top(n)

    case TopMentions(n) =>
      ClientApp.serverActor ! TopMentions(n)

    case TopNotifications(n) =>
      ClientApp.serverActor ! TopNotifications(n)

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
