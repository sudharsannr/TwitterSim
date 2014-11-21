package simulator

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import akka.actor.Props
import akka.actor.Actor
import simulator.Messages.RegisterClients
import simulator.Messages.Init
import scala.util.Random
import simulator.Messages.Tweet
import simulator.Messages.Top
import simulator.Messages.RouteClients
import simulator.Messages.MessageList
import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.util.control.Breaks._

object ClientApp extends App {

  //val ipAddr : String = args(0)
  implicit val system = ActorSystem("TwitterClientActor", ConfigFactory.load("applicationClient.conf"))
  val serverActor = system.actorOf(Props[Server])
  val interActor = system.actorOf(Props(new Interactor()))
  var nRequests : Int = 0
  interActor ! Init

}

class Interactor() extends Actor {
  var serverActor = ClientApp.serverActor
  var clientList = new Array[User](Messages.nClients)
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
      for (i <- 0 to Messages.msgLimit - 1) {
        val curUser = clientList(rand.nextInt(Messages.nClients))
        var rndTweet = randomTweet(curUser)
        curUser.getReference() ! Tweet(rndTweet)
      }
      for (i <- 0 to Messages.nClients - 1) {
        clientList(i).getReference() ! Top(100)
      }
      println("Complete!")
    //context.stop(self)
    //context.system.shutdown()
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
    sb.append(r.alphanumeric.take(length).mkString)
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
    println("--- " + startIdx + " " + endIdx)
    var r = new Random();
    val avgRate = minRate + (maxRate - minRate) / 2
    for (i <- startIdx until endIdx) {
      val newRate = minRate + r.nextInt(maxRate - minRate + 1)
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
        user.addFollower(clientList(r1.nextInt(usersCount)))
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

    case MessageList(msgList) =>
      println("Received top messages for client: " + identifier)
      msgList.foreach(println)
  }

}
