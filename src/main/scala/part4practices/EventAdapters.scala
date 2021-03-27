package part4practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventSeq, ReadEventAdapter}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable
object EventAdapters extends App {
  // store for acoustic guitars

  val ACOUSTIC = "acoustic"
  val ELECTRIC = "electric"

  // DATA STRUCTURE
  case class Guitar(id: String, model: String, make: String, guitarType: String)

  // COMMAND
  case class AddGuitar(guitar: Guitar, quantity: Int)

  // EVENT
  case class GuitarAdded(guitarId: String, guitarModel: String, guitarMake: String, quantity: Int)
  case class GuitarAddedV2(guitarId: String, guitarModel: String, guitarMake: String, quantity: Int, guitarType: String)

  class InventoryManager extends PersistentActor with ActorLogging {
    override def persistenceId: String = "guitar-inventory-manager"

    val inventory: mutable.Map[Guitar, Int] = new mutable.HashMap[Guitar, Int]()

    override def receiveCommand: Receive = {
      case AddGuitar(guitar @ Guitar(id, model, make, guitarType), quantity) =>
        persist(GuitarAddedV2(id, model, make, quantity, guitarType)) { e =>
          addGuitarInventory(guitar, quantity)
          log.info(s"Added $quantity X $guitar to inventory")
        }
      case "print" =>
        log.info(s"Current inventory: $inventory")
    }

    override def receiveRecover: Receive = {
      case event @ GuitarAddedV2(id, model, make, quantity, guitarType) =>
        log.info(s"Recovered $event")
        val guitar = Guitar(id, model, make, guitarType)
        addGuitarInventory(guitar, quantity)
    }

    def addGuitarInventory(guitar: Guitar, quantity: Int): Option[Int] = {
      val existingQuantity = inventory.getOrElse(guitar, 0)
      inventory.put(guitar, existingQuantity + quantity)
    }
  }

  class GuitarReadEventAdapter extends ReadEventAdapter {
    /*
      when the actor is trying to recover
      journal -> serializer -> read event adapter -> actor

      with this, we only need to handle the last event in the receiveRecover method
      inside the PersistentActor model
     */
    override def fromJournal(event: Any, manifest: String): EventSeq = event match {
      case GuitarAdded(guitarId, guitarModel, guitarMake, quantity) =>
        EventSeq.single(GuitarAddedV2(guitarId, guitarModel, guitarMake, quantity, ACOUSTIC))
      case other => EventSeq.single(other)
    }

    // WriteEventAdapter - used for backwards compatibility
    // actor -> write event adapter -> serializer -> journal
    // EventAdapter
  }

  val system = ActorSystem("EventAdaptersDemo", ConfigFactory.load().getConfig("eventAdapters"))
  val inventoryManager = system.actorOf(Props[InventoryManager], "inventoryManager")

  val guitars = for (i <- 1 to 10) yield Guitar(s"$i", s"Hakker $i", "RockTheJVM", ACOUSTIC)
//  guitars.foreach { guitar =>
//    inventoryManager ! AddGuitar(guitar, 5)
//  }

  inventoryManager ! "print"
}
