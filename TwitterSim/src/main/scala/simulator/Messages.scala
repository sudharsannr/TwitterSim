package simulator

object Messages {

  val clientString : String = "Client"
  val requestString : String = "REQUEST"
  val nServers : Int = 10
  val nClients : Int = 100
  val maxNeighbors : Int = nClients - 1

  sealed trait TwitterMessage
  case class ClientInit(ipAddr : String) extends TwitterMessage
  case class ClientRequest(identifier : String, requestStr : String) extends TwitterMessage

  case object Calculate extends TwitterMessage

  case class Work(strLen : Int,
    nLeadingZeroes : Int) extends TwitterMessage

  case class WorkCli(strLen : Int,
    nLeadingZeroes : Int) extends TwitterMessage

  case class WorkComplete(returnFlag : Int)

  case class WorkCompleteCli(returnFlag : Int)

  case class Timer(currentTimeMillis : Long)

}