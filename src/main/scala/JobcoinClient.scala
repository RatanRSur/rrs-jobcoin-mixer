package com.gemini.jobcoin

import play.api.libs.ws._
import play.api.libs.ws.ahc._
import play.api.libs.ws.JsonBodyReadables._
import play.api.libs.ws.JsonBodyWritables._
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import com.typesafe.config.Config
import akka.stream.Materializer

import scala.async.Async._
import scala.concurrent.Future

import DefaultBodyReadables._
import scala.concurrent.ExecutionContext.Implicits._


class JobcoinClient(config: Config)(implicit materializer: Materializer) {
  import MixerActor._
  import JobcoinClient._
  import Transaction.jsonWrites.writes

  private val wsClient = StandaloneAhcWSClient()
  private val apiAddressesUrl = config.getString("jobcoin.apiAddressesUrl")

  def getBalance(addr: String): Future[BalanceUpdate] = async {
    val response = await {
      wsClient
        .url(s"$apiAddressesUrl/$addr")
        .get()
    }.body[JsValue]
     .validate[AddressesResponse]
     .get

    BalanceUpdate(addr, BigDecimal(response.balance))
  }

  def transfer(from: String, to: String, amount: BigDecimal): Future[PayoutUpdate] = async {
    val transactionSucceeded = await {
      wsClient
        .url(s"$apiAddressesUrl/transactions")
        .post(writes(Transaction(from, to, amount.toString)))
    }.body[JsValue]
      .validate[TransactionSucceeded]
      .isSuccess

    if (!transactionSucceeded) {
      throw new Exception("Insufficient funds")
    }
    PayoutUpdate(from, amount)
  }


}

object JobcoinClient {
  case class AddressesResponse(balance: String, transactions: Array[TimestampedTransaction])
  case class TimestampedTransaction(timestamp: String, from: Option[String], to: String, amount: String)
  case class Transaction(from: String, to: String, amount: String)
  case class TransactionSucceeded(status: String)
  object AddressesResponse {
    implicit val jsonReads: Reads[AddressesResponse] = Json.reads[AddressesResponse]
  }
  object TimestampedTransaction {
    implicit val jsonReads: Reads[TimestampedTransaction] = Json.reads[TimestampedTransaction]
  }
  object Transaction {
    implicit val jsonWrites: Writes[Transaction] = Json.format[Transaction]
  }
  object TransactionSucceeded {
    implicit val jsonReads: Reads[TransactionSucceeded] = Json.reads[TransactionSucceeded]
  }
}
