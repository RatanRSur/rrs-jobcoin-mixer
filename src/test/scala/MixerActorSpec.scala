package com.gemini.jobcoin

import org.scalatest._
import akka.actor._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._

import MixerActor._

class MixerActorSpec extends FlatSpec {

  // test fixture
  def f = new {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    val config = ConfigFactory.load()
    val client = new JobcoinClient(config)
    val mixer = system.actorOf(Props(new MixerActor(client)), "mixer")
  }

  //"MixerActor" should "make a naive split of the incoming transactions" in {
    //val mixer = f.mixer
    //mixer ! AccountAssociation("depositAddr", Array("destA", "destB","destC"))
    //mixer ! AccountAssociation("depositAddr2", Array("destD", "destE","destF"))

    //val p = TestProbe()(f.system)
    //p.send(mixer, Deposited("depositAddr", 59.4))
    //p.send(mixer, Deposited("depositAddr2", 20.1))
    //val received = p.expectMsgAllClassOf[Transaction](1.seconds)
    //assert(received.length === 6)
  //}

}
