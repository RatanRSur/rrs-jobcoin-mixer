package com.gemini.jobcoin

import akka.actor._
import com.typesafe.config.Config
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object MixerActor {
  case class AccountAssociation(depositAddress: String, destinationAddresses: Array[String])
  case class Deposited(depositAddress: String, amount: BigDecimal)
  case object PollDepositAddresses
  case class BalanceUpdate(address: String, amount: BigDecimal)
  case class PayoutUpdate(address: String, amount: BigDecimal)
}

class MixerActor(val client: JobcoinClient, config: Config) extends Actor with Timers with ActorLogging {
  import MixerActor._
  import akka.pattern.pipe
  implicit val ec: ExecutionContext = context.dispatcher

  val poolAddress = config.getString("jobcoin.poolAddress")
  var accountAssociations = Map.empty[String, Array[String]]
  var payoutsRemaining = Map.empty[String, BigDecimal]

  timers.startPeriodicTimer("polling", PollDepositAddresses, 1.seconds)

  def receive = {
    case AccountAssociation(deposit, dests) => {
      accountAssociations += (deposit -> dests)
    }
    case PollDepositAddresses => {
      accountAssociations.keys.foreach { addr =>
        client.getBalance(addr.toString).pipeTo(self)
      }
    }
    case BalanceUpdate(addr, amount) => {
      println(s"$addr received deposit of $amount")
      if (amount > 0) {
        client.transfer(addr, poolAddress, amount).pipeTo(self)
      }
    }
    case PayoutUpdate(from, amount) => {
      val currentPayout = payoutsRemaining.getOrElse(from, BigDecimal(0))
      payoutsRemaining = payoutsRemaining.updated(from, currentPayout + amount)
      print(s"payout update for $from, was $currentPayout, now ${payoutsRemaining(from)}")
    }
  }
}
