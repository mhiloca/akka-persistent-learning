package part2eventsourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

object PersistentActorsExerciseTeachersVersion extends App {

  case class Vote(citizenPID: String, candidate: String)

  class VotingStation extends PersistentActor with ActorLogging {

    // ignore the mutable state for now
    val citizens: mutable.Set[String] = new mutable.HashSet[String]()
    val poll: mutable.Map[String, Int] = new mutable.HashMap[String, Int]()
    override def persistenceId: String = "simple-voting-station"

    override def receiveCommand: Receive = {
      case vote @ Vote(citizenPID, candidate) =>
        if (!citizens.contains(vote.citizenPID)) {
          // COMMAND sourcing (because we are not persisting any event, but the command itself)
          persist(vote) { _ =>
            handleInternalStateChange(citizenPID, candidate)
            log.info(s"Persisted: $vote")
          }

        } else {
          log.warning(s"$citizenPID is trying to vote twice")
        }
      case "print" =>
        println(s"Current state: Citizens: $citizens\nPoll: $poll")
    }

    override def receiveRecover: Receive = {
      case vote @ Vote(citizenPID, candidate) =>
        log.info(s"Recovered: $vote")
        handleInternalStateChange(citizenPID, candidate)
    }

    def handleInternalStateChange(citizenPID: String, candidate: String): Unit = {
      if (!citizens.contains(citizenPID)) {
        citizens.add(citizenPID)
        val votes = poll.getOrElse(candidate, 0)
        poll.put(candidate, votes + 1)
      }
    }
  }

  val system = ActorSystem("PersistentActorsExerciseTeachersVersion", ConfigFactory.load().getConfig("votingDemo"))
  val votingStation = system.actorOf(Props[VotingStation], "simpleVotingStation")

  val votesMap = Map[String, String] (
    "Alice" -> "Martin",
    "Bob"-> "Roland",
    "Charlie" -> "Martin",
    "David" -> "Jonas",
    "Daniel" -> "Martin"
  )

  votesMap.keys.foreach { citizen =>
    votingStation ! Vote(citizen, votesMap(citizen))
  }

  votingStation ! Vote("Daniel", "Daniel")
  votingStation ! "print"
}
