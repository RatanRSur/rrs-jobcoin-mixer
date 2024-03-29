package com.gemini.jobcoin

import akka.actor._
import com.typesafe.config.Config
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object MixerActor {
  type SourceAddress = String
  type DestinationAddress = String
  case class AccountAssociation(
      depositAddress: SourceAddress,
      destinationAddresses: Set[DestinationAddress]
  )
  case class Deposited(depositAddress: SourceAddress, amount: BigDecimal)
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
  var sourceToDestMap = Map.empty[SourceAddress, Set[DestinationAddress]]
  var destToSourceMap = Map.empty[DestinationAddress, SourceAddress]
  var payoutsRemaining = Map.empty[SourceAddress, BigDecimal]

  log.debug("Starting Deposit Address Spilling")
  timers.startPeriodicTimer("spilling", SpillDepositAddresses, 1.seconds)
  timers.startPeriodicTimer("paying out", Payout, 3.seconds)

  def receive = {
    case AccountAssociation(deposit, dests) => {
      log.debug(s"Received association $deposit -> ${dests.mkString(",")}")
      sourceToDestMap += (deposit -> dests)
      destToSourceMap ++= dests.map(dest => (dest -> deposit))
    }
    case SpillDepositAddresses => {
      sourceToDestMap.keys.foreach { addr =>
        client.transferAll(addr, poolAddress).pipeTo(self)
      }
    }
    // This message is received when the client confirms that a transaction went through
    case Transaction(from, to, amount) => {
      log.debug(s"confirmed: $from to $to for $amount")
      val (userSourceAddr, signedAmount) = if (from == poolAddress) {
        // get the original address this came from and reduce the remaining payout
        (destToSourceMap(to), -amount)
      } else {
        // from is the original address so we increase the remaining payout
        (from, amount)
      }
      payoutsRemaining += (userSourceAddr -> (payoutsRemaining.getOrElse(
        userSourceAddr,
        BigDecimal(0)
      ) + signedAmount))
      assume(
        payoutsRemaining(userSourceAddr) >= 0,
        s"$userSourceAddr has invalid payout: ${payoutsRemaining(userSourceAddr)}"
      )
    }
    case Payout => {
      val payoutAtom = config.getInt("jobcoin.payoutAtom") // how much to pay every account every epoch
      sourceToDestMap.foreach {
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
