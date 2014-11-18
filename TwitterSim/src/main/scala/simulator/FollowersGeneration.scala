package simulator;
import scala.util.Random
import scala.collection.mutable.Map;
import scala.collection.mutable.ListBuffer;

object FollowersGeneration extends App {

  generateFollowers (usersCount = 1000000)
  
  def generateFollowers (usersCount : Int) {
    
    var r = new Random();
    var r1 = new Random();
    var usersMap : Map[Int, ListBuffer[Int]] = Map();
    var numberOfFollowers : Int = 0;
    
    for (i <- 0 until usersCount) {
      numberOfFollowers = r.nextInt(usersCount/100);
      var followers = new ListBuffer[Int]
      var j : Int = 0
      for (j <- 0 until numberOfFollowers) {
        followers += numberOfFollowers
      }  
      usersMap.put(i, followers);
    } 
  }
  
}