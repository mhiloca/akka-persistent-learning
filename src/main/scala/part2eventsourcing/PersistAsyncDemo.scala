package part2eventsourcing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor
import com.typesafe.config.ConfigFactory

object PersistAsyncDemo extends App {

  case class Command(contents: String)
  case class Event(contents: String)

  object CriticalStreamProcessor {
    def props(eventAggregator: ActorRef): Props = Props(new CriticalStreamProcessor((eventAggregator)))
  }

  class CriticalStreamProcessor(eventAggregator: ActorRef) extends PersistentActor with ActorLogging {

    override def persistenceId: String = "critical-stream-processor"

    override def receiveCommand: Receive = {
      case Command(contents) =>
        eventAggregator ! s"Processing $contents"
        persistAsync(Event(contents)) /* TIME GAP */ { event =>
          eventAggregator ! event
        }

        // some actual computation
        val processedContents = contents + "_processed"
        persistAsync(Event(processedContents)) {event =>
          eventAggregator ! event
        }
    }

    override def receiveRecover: Receive = {
      case message =>
        log.info(s"Recovered: $message")
    }
  }

  class EventAggregator extends Actor with ActorLogging {
    override def receive: Receive = {
      case message =>
        log.info(s"$message")
    }
  }

  val system = ActorSystem("PersistAsynDemo", ConfigFactory.load().getConfig("rtjvmDemo"))
  val eventAggregator = system.actorOf(Props[EventAggregator], "eventAggregator")
  val criticalStreamProcessor = system.actorOf(CriticalStreamProcessor.props(eventAggregator), "streamProcessor")

  criticalStreamProcessor ! Command("command 1")
  criticalStreamProcessor ! Command("command 2")

  /*
    persistAsync vs persist
    - performance:
      - persist: you will need to wait for the entire command to be processed before another command
                 ordering guaranteed
      - persistAsync: in high-throughput environments
      but whenever you need to keep the ordering of events
   */

}
