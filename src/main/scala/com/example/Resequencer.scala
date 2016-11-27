package com.example

import java.util.{Date, Random}

import akka.actor._

case class SequencedMessage(correlationId: String, index: Int, total: Int)

case class ResequencedMessages(dispatchableIndex: Int, sequencedMessages: Array[SequencedMessage]) {
  def advancedTo(dispatchableIndex: Int) = {
    ResequencedMessages(dispatchableIndex, sequencedMessages)
  }
}

object ResequencerDriver extends CompletableApp(10) {
}

class ChaosRouter(consumer: ActorRef) extends Actor {
  val random = new Random((new Date()).getTime)
}