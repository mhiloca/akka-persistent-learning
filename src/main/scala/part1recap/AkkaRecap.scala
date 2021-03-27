package part1recap

import akka.actor.Status.Success
import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, PoisonPill, Props, Stash, SupervisorStrategy}
import akka.pattern.BackoffSupervisor.RestartCount
import akka.util.Timeout

object AkkaRecap extends App {

  class SimpleActor extends Actor with ActorLogging with Stash {
    override def receive: Receive = {
      case "create child" =>
        val child = context.actorOf(Props[SimpleActor], "child")
        child ! "I have created you"
      case "stashThis" =>
        stash()
      case "change handler NOW" =>
        unstashAll()
        context.become(anotherHandler)
      case "change" => context.become(anotherHandler)
      case message => println(s"I have received a message: $message")
    }

    def anotherHandler: Receive = {
      case message => println(s"In another receive handler: $message")
    }

    override def preStart(): Unit = {
      log.info("I'm starting now")
    }

    override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _: RuntimeException => Restart
      case _ => Stop
    }

  }

  // actor encapsulation
  val system = ActorSystem("ActorSystemDemo")
  // #1: you can only instantiate an actor via actor system
  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")
  // #2: sending message
  simpleActor ! "Hello"

  /*
    - messages are sent asynchronously
    - many actors (in the millions) can share a few dozens threads
    - each message is handled ATOMICALLY
    - no need for locks
   */

  // changing actor behavior + stashing
  // actors can spawn other actors
  // guardians: /system, /user (parent of every single actor that we create as programmers), / = root guardian

  // actors have a defined lifecycle: they can be started, stopped, suspended, resumed, restarted

  // stopping actors - context.stop
  simpleActor ! PoisonPill

  // logging
  // supervision

  // configure Akka infrastructure dispatchers, routers, mailboxes

  // schedulers
  import scala.concurrent.duration._
  import scala.language.postfixOps
  import system.dispatcher

  system.scheduler.scheduleOnce(2 seconds) {
    simpleActor ! "delayed happy birthday"
  }

  // Akka patterns including FSM + ask pattern
  import akka.pattern.{ask, pipe}
  implicit val timeout = Timeout(3 seconds)
  val future = simpleActor ? "question"

  // the pipe pattern
  val anotherActor = system.actorOf(Props[SimpleActor], "anotherSimpleActor")
  future.mapTo[String].pipeTo(anotherActor)
}

