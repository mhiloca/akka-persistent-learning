package part2eventsourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

object Snapshots extends App {

  // COMMANDS
  case class ReceiveMessage(contents: String) // message FROM your contact
  case class SendMessage(contents: String) // message TO your contact

  // EVENTS
  case class ReceivedMessageRecord(id: Int, contents: String)
  case class SentMessageRecord(id: Int, contents: String)

  object Chat {
    def props(owner: String, contact: String): Props = Props(new Chat(owner, contact))
  }

  class Chat(owner: String, contact: String) extends PersistentActor with ActorLogging {
    val MAX_MESSAGES = 10

    var commandsWithoutCheckpoints = 0
    var currentMessageId = 0
    /*
     a queue to hold the last 10 messages in the following format:
     whenever you send a message (owner, contents)
     whenever you receive a message (contact, contents)
     */
    val lastMessages = new mutable.Queue[(String, String)]()

    override def persistenceId: String = s"$owner-$contact-chat"

    override def receiveCommand: Receive = {
      case ReceiveMessage(contents) =>
        persist(ReceivedMessageRecord(currentMessageId, contents)) { e =>
          log.info(s"Received message: $contents")
          handleMessages(contact, contents)
          currentMessageId += 1
          maybeCheckpoint()

        }
      case SendMessage(contents) =>
        persist(SentMessageRecord(currentMessageId, contents)) { e =>
          log.info(s"Sent message: $contents")
          handleMessages(owner, contents)
          currentMessageId += 1
          maybeCheckpoint()
        }
      case "print" => log.info(s"Most recent messages: $lastMessages")
        // snapshot-related messages
      case SaveSnapshotSuccess(metadata) => log.info(s"Saving snapshot succeeded: $metadata")
      case SaveSnapshotFailure(metadata, throwable) => log.warning(s"Saving snapshot $metadata because of $throwable")
    }

    override def receiveRecover: Receive = {
      case ReceivedMessageRecord(id, contents) =>
        log.info(s"Recovered received message #$id: $contents")
        handleMessages(contact, contents)
        currentMessageId = id
      case SentMessageRecord(id, contents) =>
        log.info(s"Recovered sent message #$id: $contents")
        handleMessages(owner, contents)
        currentMessageId = id
      case SnapshotOffer(metadata, contents) => // special message
        log.info(s"Recovered snapshot: $metadata")
        contents.asInstanceOf[mutable.Queue[(String, String)]].foreach(lastMessages.enqueue(_))
    }

    def handleMessages(sender: String, contents: String): Unit = {
      if (lastMessages.size >= MAX_MESSAGES) {
        lastMessages.dequeue()
      }
      lastMessages.enqueue((sender, contents))
    }

    def maybeCheckpoint(): Unit = {
      commandsWithoutCheckpoints += 1
      if (commandsWithoutCheckpoints >= MAX_MESSAGES) {
        log.info("Saving checkpoint")
        // allows persisting anything that is a serializable to a dedicated persistent store
        saveSnapshot(lastMessages) // asynchronous operation
        commandsWithoutCheckpoints = 0
      }
    }

  }

  val system = ActorSystem("SnapshotsDemo", ConfigFactory.load().getConfig("rtjvmDemo"))
  val chat = system.actorOf(Chat.props("daniel123", "martin345"))
  for (i <- 1 to 10000) {
    chat ! ReceiveMessage(s"Akka Rocks $i")
    chat ! SendMessage(s"Akka Rules $i")
  }

//  chat ! "print"

  // snapshots

  /*
    event 1
    event 2
    event 3
    snapshot 1
    event 4
    event 5
    snapshot 2
    event 5
    event 6
   */

  /*
    pattern:
    - after each persist, maybe save a snapshot (logic is up to you)
    - if you save a snapshot, you should handle the SnapshotOffer message in receiveRecover method
    - [optional, but best practice] - handle SnapshotSuccess and SnapshotFailure in receiveCommand method
   */
}
