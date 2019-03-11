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

import JobcoinClient.AddressesResponse

class JobcoinClient(config: Config)(implicit materializer: Materializer) {
  private val wsClient = StandaloneAhcWSClient()
  private val apiAddressesUrl = config.getString("jobcoin.apiAddressesUrl")

  def testGet(): Future[AddressesResponse] = async {
    val response = await {
      wsClient
        .url(apiAddressesUrl + "/Alice")
        .get()
    }

    response
      .body[JsValue]
      .validate[AddressesResponse]
      .get
  }
}

object JobcoinClient {
  case class AddressesResponse(balance: String, transactions: Array[Transaction])
  case class Transaction(timestamp: String, fromAddress: Option[String], toAddress: String, amount: String)
  object AddressesResponse {
    implicit val jsonReads: Reads[AddressesResponse] = Json.reads[AddressesResponse]
  }
  object Transaction {
    implicit val jsonReads: Reads[Transaction] = Json.reads[Transaction]
  }
}
