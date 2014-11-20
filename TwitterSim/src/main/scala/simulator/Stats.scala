package simulator

import scala.util.Random
import scala.swing.ScrollPane.BarPolicy.Value

object TweetStrAt extends Enumeration {
  type TweetStrAt = Value
  val withAt, withoutAt = Value
}

object TweetStrAtPos extends Enumeration {
  type TweetStrAtPos = Value
  val atBeginning, atNotBeginning = Value
}

object TweetStrTo extends Enumeration {
  type TweetStrTo = Value
  val toFollower, toRandomUser = Value
}

object RandomPicker {
  def pickRandom(en : Enumeration) : Any = {
    val r = new Random
    val enValues = en.values
    val retVal = enValues.toSeq(r.nextInt(enValues.size))
    return retVal
  }
}