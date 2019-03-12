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
  import JobcoinClient._
  import Transaction.jsonWrites.writes

  private val wsClient = StandaloneAhcWSClient()
  private val apiAddressesUrl = config.getString("jobcoin.apiAddressesUrl")
  private val apiTransactionsUrl = config.getString("jobcoin.apiTransactionsUrl")

  def getBalance(addr: String): Future[BigDecimal] = async {
    val response = await {
      wsClient
        .url(s"$apiAddressesUrl/$addr")
        .get()
    }.body[JsValue]
      .validate[AddressesResponse]
      .get

    BigDecimal(response.balance)
  }

  def transfer(from: String, to: String, amount: BigDecimal): Future[Transaction] = async {
    val transactionSucceeded = await {
      wsClient
        .url(apiTransactionsUrl)
        .post(writes(Transaction(from, to, amount)))
    }.status == 200

    if (transactionSucceeded) Transaction(from, to, amount) else await(transfer(from, to, amount))
  }

  def transferAll(from: String, to: String): Future[Transaction] = async {
    val balance = await(getBalance(from))
    await(transfer(from, to, balance))
  }

}

object JobcoinClient {
  case class AddressesResponse(balance: String, transactions: Array[TimestampedTransaction])
  case class TimestampedTransaction(
      timestamp: String,
      fromAddress: Option[String],
      toAddress: String,
      amount: String
  )
  case class Transaction(fromAddress: String, toAddress: String, amount: BigDecimal)
  object AddressesResponse {
    implicit val jsonReads: Reads[AddressesResponse] = Json.reads[AddressesResponse]
  }
  object TimestampedTransaction {
    implicit val jsonReads: Reads[TimestampedTransaction] = Json.reads[TimestampedTransaction]
  }
  object Transaction {
    implicit val jsonWrites: Writes[Transaction] = Json.format[Transaction]
  }
}
