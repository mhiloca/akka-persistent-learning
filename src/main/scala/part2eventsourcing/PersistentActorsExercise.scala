package part2eventsourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import com.typesafe.config.ConfigFactory

object PersistentActorsExercise extends App {

  /*
    Persistent actor for a voting station
    Keep:
      - the citizens who voted
      - the poll: mapping between a candidate and the number of received votes

    The actor must be able to recover is state if it's shutdown or restarted
   */

  // COMMANDS
  case class Vote(citizenPID: String, candidate: String)

  // Special messages
  case object Shutdown

  // EVENTS
  case class VoteRecorded(id: Int, citizenPID: String, candidate: String)

  class VotingStation extends PersistentActor with ActorLogging {
    var latestVotingStationId = 0
    var citizensWhoVoted: Set[String] = Set()
    var votingPoll: Map[String, Int] = Map()

    override def persistenceId: String = "voting-station"

    override def receiveCommand: Receive = {
      case Vote(citizenPID, candidate) =>
        if (!citizensWhoVoted.contains(citizenPID)) {
          persist(VoteRecorded(latestVotingStationId, citizenPID, candidate)) { e =>
            handleInternalState(citizenPID, candidate)
            latestVotingStationId += 1
            log.info(s"Persisted voting #${e.id}: where citizen ${e.citizenPID} voted for ${e.candidate}")
          }
        } else {
          log.warning(s"$citizenPID is trying to vote multiple times")
        }
      case Shutdown => context.stop(self)
      case "print" => println(s"Citizens: $citizensWhoVoted\nPoll: $votingPoll")
    }

    override def receiveRecover: Receive = {
      case VoteRecorded(id, citizenPID, candidate) =>
        log.info(s"Recovered vote #$id, where citizen $citizenPID voted for $candidate")
        handleInternalState(citizenPID, candidate)
        latestVotingStationId = id
    }

    def handleInternalState(citizenPID: String, candidate: String): Unit = {
      citizensWhoVoted = citizensWhoVoted + citizenPID
      val candidateVotes = votingPoll.getOrElse(candidate, 0)
      votingPoll = votingPoll + (candidate -> (candidateVotes + 1))
    }
  }

  val system = ActorSystem("VotingStationDemo", ConfigFactory.load().getConfig("votingDemo"))
  val votingStation = system.actorOf(Props[VotingStation], "votingStation")

  val votesMap = Map[String, String](
    ("Maria-123", "Martin"),
    ("Carlos-234", "Jonas"),
    ("João-345", "Martin"),
    ("Leonardo-789", "Jonas"),
    ("Micheline-987", "Martin"),
    ("Micheline-987", "Micheline")
  )

//  votesMap foreach {vote => votingStation ! Vote(vote._1, vote._2)}
  votingStation ! "print"
}
