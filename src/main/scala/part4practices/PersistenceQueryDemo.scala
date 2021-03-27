package part4practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.journal.{Tagged, WriteEventAdapter}
import akka.persistence.query.{NoOffset, PersistenceQuery}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import jnr.ffi.Struct.Offset

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

object PersistenceQueryDemo extends App {

  val system = ActorSystem("PersistenceQueryDemo", ConfigFactory.load().getConfig("persistenceQuery"))

  // read journal
  val readJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  // get me all the persistence Ids that are available in the journal
  val persistenceIds = readJournal.persistenceIds() // this is a infinite stream query
  // if there's the need for a finite query => currentPersistenceIds()

  // boilerplate so far
  implicit val materializer = ActorMaterializer()(system)
//  persistenceIds.runForeach { persistenceId =>
//    println(s"Found persistence ID: $persistenceId")
//  }

  class SimplePersistenceActor extends PersistentActor with ActorLogging {
    override def persistenceId: String = "persistence-query-id-1"

    override def receiveCommand: Receive = {
      case m => persist(m) { _ =>
        log.info(s"Persisted $m")
      }
    }

    override def receiveRecover: Receive = {
      case e => log.info(s"Recovered $e")
    }
  }

  val simpleActor = system.actorOf(Props[SimplePersistenceActor], "simplePersistentActor")
  import system.dispatcher
  system.scheduler.scheduleOnce(5 seconds) {
    val message = "Hello, a second time"
    simpleActor ! message
  }

  // events by persistence Id
  val events = readJournal.eventsByPersistenceId("persistence-query-id-1", 0, Long.MaxValue)
  events.runForeach { event =>
    println(s"Read event: $event")
  }

  // events by tags
  val genres = Array("pop", "rock", "jazz", "disco", "hip-hop")
  case class Song(artist: String, title: String, genre: String)
  // command
  case class Playlist(songs: List[Song])
  // event
  case class PlaylistPurchase(id: Int, songs: List[Song])

  class MusicStoreCheckoutActor extends PersistentActor with ActorLogging {
    override def persistenceId: String = "music-store-checkout"

    var latestPlaylistId = 0

    override def receiveCommand: Receive = {
      case Playlist(songs) =>
        persist(PlaylistPurchase(latestPlaylistId, songs)) { _ =>
          log.info(s"User purchased: $songs")
          latestPlaylistId += 1
        }
    }

    override def receiveRecover: Receive = {
      case event @ PlaylistPurchase(id, _) =>
        log.info(s"Recovered $event")
        latestPlaylistId = id
    }
  }

  class MusicStoreEventAdapter extends WriteEventAdapter {
    override def manifest(event: Any): String = ""

    override def toJournal(event: Any): Any = event match {
      case event @ PlaylistPurchase(_, songs) =>
        val genres = songs.map(_.genre).toSet
        Tagged(event, genres)
      case event => event
    }
  }

  val musicStoreActor = system.actorOf(Props[MusicStoreCheckoutActor], "musicStoreActor")
  val r = new Random
  for (_ <- 1 to 10 ) {
    val maxSongs = r.nextInt(5)
    val songs = for (i <- 1 to maxSongs) yield {
      val randomGenre = genres(r.nextInt(5))
      Song(s"Artist $i", s"My love song $i", randomGenre)
    }
    musicStoreActor ! Playlist(songs.toList)
  }

  val rockPlaylists = readJournal.eventsByTag("rock", NoOffset)

  rockPlaylists.runForeach { playlist =>
    println(s"Found a playlist with a rock song: $playlist ")
  }
}
