package part2eventsourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotSelectionCriteria}
import com.typesafe.config.ConfigFactory

object RecoveryDemo extends App {

  case class Command(contents: String)
  case class Event(id: Int, contents: String)

  class RecoveryActor extends PersistentActor with ActorLogging {
    override def persistenceId: String = "recovery-actor"

    override def receiveCommand: Receive = {
      case Command(contents) => context.become(online(0))
    }

    def online(latestPersistedEventId: Int): Receive = {
      case Command(contents) =>
        persist(Event(latestPersistedEventId, contents)) { event =>
          log.info(s"Successfully persisted $event, recovery is ${if (this.recoveryFinished) "" else "NOT"} finished.")
          context.become(online(latestPersistedEventId +1))
        }
    }

    override def receiveRecover: Receive = {
      case RecoveryCompleted =>
        //additional initialization
        log.info("I have finished recovering")
      case Event(id, contents) =>
//        if (contents.contains("314"))
//          throw new RuntimeException("I can't take this anymore!")
        log.info(s"Recovered: $contents, recovery is ${if (this.recoveryFinished) "" else "NOT"} finished.")
        context.become(online(id + 1))
        /*
          this will NOT change the event handler during recovery
          AFTER RECOVERY - the "normal" handler will be the result of ALL the stacking the context.becomes.
         */
    }

    override def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      log.error("I failed at recovery")
      super.onRecoveryFailure(cause, event)
    }

//    override def recovery: Recovery = Recovery(toSequenceNr = 100)

//    override def recovery: Recovery = Recovery(fromSnapshot = SnapshotSelectionCriteria.Latest)

//    override def recovery: Recovery = Recovery.none
  }


  val system = ActorSystem("RecoveryActorDemo", ConfigFactory.load().getConfig("rtjvmDemo"))
  val recoveryActor = system.actorOf(Props[RecoveryActor], "recoveryActor")

  /*
    Stashing commands
   */
//  for (i <- 1 to 1000) {recoveryActor ! Command(s"command: $i")}

  // ALL COMMANDS SENT DURING RECOVERY ARE STASHED

  /*
    2 - FAILURE DURING RECOVERY
    - onRecoveryFailure + the actor STOP
    (because if there is a failure during recovery, the actor cannot be trusted anymore)
   */

  /*
    3 - customizing recovery
    - DO NOT persist more events after a customized _incomplete_ recovery
      because you might corrupt messages in the mean time
   */

  /*
    4 - Recovery Status or KNOWING when you are done recovering
    - getting a signal when you are done recovery
   */

  /*
    5 - Stateless actors
   */

  recoveryActor ! Command("special command 1")
  recoveryActor ! Command("special command 2")
}
