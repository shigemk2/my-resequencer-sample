package com.example

import java.util.concurrent.TimeUnit
import java.util.{Date, Random}

import akka.actor._

import scala.concurrent.duration.Duration

case class SequencedMessage(correlationId: String, index: Int, total: Int)

case class ResequencedMessages(dispatchableIndex: Int, sequencedMessages: Array[SequencedMessage]) {
  def advancedTo(dispatchableIndex: Int) = {
    ResequencedMessages(dispatchableIndex, sequencedMessages)
  }
}

object ResequencerDriver extends CompletableApp(10) {
}

class ChaosRouter(consumer: ActorRef) extends Actor {
  val random = new Random(new Date().getTime)

  def receive = {
    case sequencedMessage: SequencedMessage =>
      val millis = random.nextInt(100) + 1
      println(s"ChaosRouter: delaying delivery of $sequencedMessage for $millis milliseconds")
      val duration = Duration.create(millis, TimeUnit.MILLISECONDS)
      context.system.scheduler.scheduleOnce(duration, consumer, sequencedMessage)
    case message: Any =>
      println(s"ChaosRouter: received unexpected: $message")
  }
}

class ResequencerConsumer(actualConsumer: ActorRef) extends Actor {
  val resequenced = scala.collection.mutable.Map[String, ResequencedMessages]()

  def dispatchAllSequenced(correlationId: String) = {
    val resequencedMessages = resequenced(correlationId)
    var dispatchableIndex = resequencedMessages.dispatchableIndex

    resequencedMessages.sequencedMessages.foreach { sequenceMessage =>

      if (sequenceMessage.index == dispatchableIndex) {
        actualConsumer ! sequenceMessage

        dispatchableIndex += 1
      }
    }

    resequenced(correlationId) =
      resequencedMessages.advancedTo(dispatchableIndex)
  }
}