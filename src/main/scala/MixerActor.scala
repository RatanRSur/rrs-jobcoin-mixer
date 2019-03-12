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
  log.debug("Starting Mixer Actor")
  import MixerActor._
  import akka.pattern.pipe
  implicit val ec: ExecutionContext = context.dispatcher

  val poolAddress = config.getString("jobcoin.poolAddress")
  var accountAssociations = Map.empty[String, Array[String]]
  var payoutsRemaining = Map.empty[String, BigDecimal]

  log.debug("Starting Deposit Address Polling")
  timers.startPeriodicTimer("polling", PollDepositAddresses, 1.seconds)

  def receive = {
    case AccountAssociation(deposit, dests) => {
      log.debug(s"Received association $deposit -> ${dests.mkString(",")}")
      accountAssociations += (deposit -> dests)
    }
    case PollDepositAddresses => {
      accountAssociations.keys.foreach { addr =>
        log.debug(s"Getting balance for $addr")
        client.getBalance(addr.toString).pipeTo(self)
      }
    }
    case BalanceUpdate(addr, amount) => {
      if (amount > 0) {
        log.debug(s"$addr received deposit of $amount, transferring to $poolAddress")
        client.transfer(addr, poolAddress, amount).pipeTo(self)
      }
    }
    case PayoutUpdate(from, amount) => {
      val currentPayout = payoutsRemaining.getOrElse(from, BigDecimal(0))
      payoutsRemaining = payoutsRemaining.updated(from, currentPayout + amount)
      log.debug(s"payout update for $from, was $currentPayout, now ${payoutsRemaining(from)}")
    }
  }
}
