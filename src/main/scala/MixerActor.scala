package com.gemini.jobcoin

import akka.actor._
import com.typesafe.config.Config
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object MixerActor {
  case class AccountAssociation(depositAddress: String, destinationAddresses: Array[String])
  case class Deposited(depositAddress: String, amount: BigDecimal)
  case object PollDepositAddresses
  case object Payout
}

class MixerActor(val client: JobcoinClient, config: Config) extends Actor with Timers with ActorLogging {
  log.debug("Starting Mixer Actor")
  import JobcoinClient._
  import MixerActor._
  import akka.pattern.pipe
  implicit val ec: ExecutionContext = context.dispatcher

  val poolAddress = config.getString("jobcoin.poolAddress")
  var accountAssociations = Map.empty[String, Array[String]]
  var payoutsRemaining = Map.empty[String, BigDecimal]

  log.debug("Starting Deposit Address Polling")
  var addressesBeingPolled = Set.empty[String]
  timers.startPeriodicTimer("polling", PollDepositAddresses, 1.seconds)
  timers.startPeriodicTimer("paying out", Payout, 2.seconds)

  def receive = {
    case AccountAssociation(deposit, dests) => {
      log.debug(s"Received association $deposit -> ${dests.mkString(",")}")
      accountAssociations += (deposit -> dests)
    }
    case PollDepositAddresses => {
      accountAssociations.keys.foreach { addr =>
        log.debug(s"Beginning transfer from $addr to $poolAddress")
        if (!addressesBeingPolled.contains(addr)) {
          addressesBeingPolled += addr
          client.transferAll(addr, poolAddress).pipeTo(self)
        }
      }
    }
    case Transaction(from, to, amount) => {
      log.debug(s"confirmed: $from to $to for $amount")
      val (userAccount, signedAmount) = if (from == poolAddress) {
        (to, -amount)
      } else {
        addressesBeingPolled -= from
        (from, amount)
      }
      payoutsRemaining += (userAccount ->
        (payoutsRemaining.getOrElse(userAccount, BigDecimal(0)) + signedAmount))
      assume(payoutsRemaining(userAccount) >= 0)
    }
    case Payout => {
      accountAssociations.foreach { case (addr, dests) =>
        val numberOfShares = dests.size
        val payOutPerShare = payoutsRemaining.getOrElse(addr, BigDecimal(0)) / numberOfShares
        dests.foreach( dest => client.transfer(poolAddress, dest, payOutPerShare).pipeTo(self))
      }
    }
  }
}
