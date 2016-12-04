package com.example

import java.util.concurrent.TimeUnit
import java.util.{Date, Random}

import akka.actor.Actor.Receive
import akka.actor._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.duration.Duration

case class SequencedMessage(correlationId: String, index: Int, total: Int)

case class ResequencedMessages(dispatchableIndex: Int, sequencedMessages: Array[SequencedMessage]) {
  def advancedTo(dispatchableIndex: Int) = {
    ResequencedMessages(dispatchableIndex, sequencedMessages)
  }
}

object ResequencerDriver extends CompletableApp(10) {
  val sequencedMessageConsumer = system.actorOf(Props[SequencedMessageConsumer], "sequencedMessageConsumer")
  val resequencerConsumer = system.actorOf(Props(classOf[ResequencerConsumer], sequencedMessageConsumer), "resequencerConsumer")
  val chaosRouter = system.actorOf(Props(classOf[ChaosRouter], resequencerConsumer), "chaosRouter")
  
  for (index <- 1 to 5) chaosRouter ! SequencedMessage("ABC", index, 5)
  for (index <- 1 to 5) chaosRouter ! SequencedMessage("XYZ", index, 5)
  
  awaitCompletion
  println("Resequencer: is completed.")
}

class ChaosRouter(consumer: ActorRef) extends Actor {
  val random = new Random((new Date()).getTime)
  
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
    
    resequencedMessages.sequencedMessages.foreach { sequencedMessage =>
      if (sequencedMessage.index == dispatchableIndex) {
        actualConsumer ! sequencedMessage
        
        dispatchableIndex += 1
      }
    }
    
    resequenced(correlationId) =
      resequencedMessages.advancedTo(dispatchableIndex)
  }
  
  def dummySequencedMessages(count: Int): Seq[SequencedMessage] = {
    for {
      index <- 1 to count
    } yield {
      SequencedMessage("", -1, count)
    }
  }
  
  def receive = {
    case unsequencedMessage: SequencedMessage =>
      println(s"ResequencerConsumer: received: $unsequencedMessage")
      resequence(unsequencedMessage)
      dispatchAllSequenced(unsequencedMessage.correlationId)
      removeCompleted(unsequencedMessage.correlationId)
    case message: Any =>
      println(s"ResequencerConsumer: received unexpected: $message")
  }
  
  def removeCompleted(correlationId: String) = {
    val resequencedMessages = resequenced(correlationId)
    
    if (resequencedMessages.dispatchableIndex > resequencedMessages.sequencedMessages(0).total) {
      resequenced.remove(correlationId)
      println(s"ResequencerConsumer: removed completed: $correlationId")
    }
  }
  
  def resequence(sequencedMessage: SequencedMessage) = {
    if (!resequenced.contains(sequencedMessage.correlationId)) {
      resequenced(sequencedMessage.correlationId) =
        ResequencedMessages(1, dummySequencedMessages(sequencedMessage.total).toArray)
    }
    
    resequenced(sequencedMessage.correlationId)
    	.sequencedMessages
    	.update(sequencedMessage.index - 1, sequencedMessage)
  }
}

class SequencedMessageConsumer extends Actor {
  def receive = {
    case sequencedMessage: SequencedMessage =>
      println(s"SequencedMessageConsumer: received: $sequencedMessage")
      ResequencerDriver.completedStep()
    case message: Any =>
      println(s"SequencedMessageConsumer: received unexpected: $message")
  }
}
