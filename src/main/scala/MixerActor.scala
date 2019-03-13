package com.gemini.jobcoin

import akka.actor._
import com.typesafe.config.Config
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object MixerActor {
  case class AccountAssociation(depositAddress: String, destinationAddresses: Set[String])
  case class Deposited(depositAddress: String, amount: BigDecimal)
  case object SpillDepositAddresses
  case object Payout
}

class MixerActor(val client: JobcoinClient, config: Config)
    extends Actor
    with Timers
    with ActorLogging {
  log.debug("Starting Mixer Actor")
  import JobcoinClient._
  import MixerActor._
  import akka.pattern.pipe
  implicit val ec: ExecutionContext = context.dispatcher

  val poolAddress = config.getString("jobcoin.poolAddress")
  var accountAssociations = Map.empty[String, Set[String]]
  var payoutsRemaining = Map.empty[String, BigDecimal]

  log.debug("Starting Deposit Address Spilling")
  var addressesBeingSpilled = Set.empty[String]
  timers.startPeriodicTimer("spilling", SpillDepositAddresses, 1.seconds)
  timers.startPeriodicTimer("paying out", Payout, 2.seconds)

  def receive = {
    case AccountAssociation(deposit, dests) => {
      log.debug(s"Received association $deposit -> ${dests.mkString(",")}")
      accountAssociations += (deposit -> dests)
    }
    case SpillDepositAddresses => {
      accountAssociations.keys.foreach { addr =>
        if (!addressesBeingSpilled.contains(addr)) {
          log.debug(s"Beginning transfer from $addr to $poolAddress")
          addressesBeingSpilled += addr
          client.transferAll(addr, poolAddress).pipeTo(self)
        }
      }
    }
    // This message is received when the client confirms that a transaction went through
    case Transaction(from, to, amount) => {
      log.debug(s"confirmed: $from to $to for $amount")
      val (userAccount, signedAmount) = if (from == poolAddress) {
        (to, -amount)
      } else {
        addressesBeingSpilled -= from
        (from, amount)
      }
      payoutsRemaining += (userAccount ->
        (payoutsRemaining.getOrElse(userAccount, BigDecimal(0)) + signedAmount))
      assume(payoutsRemaining(userAccount) >= 0)
    }
    case Payout => {
      val payoutAtom = 10 // how much to pay every account every epoch
      accountAssociations.foreach {
        case (addr, dests) =>
          val payoutRemaining = payoutsRemaining.getOrElse(addr, BigDecimal(0))
          val maxNumberOfAccountsToPayTo = (payoutRemaining / payoutAtom).toInt
          val accountsToPayTo = dests.take(maxNumberOfAccountsToPayTo)
          accountsToPayTo.foreach { dest =>
            client.transfer(poolAddress, dest, payoutAtom).pipeTo(self)
          }
      }
    }
  }
}
