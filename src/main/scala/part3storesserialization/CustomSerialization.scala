package part3storesserialization

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.serialization.Serializer
import com.typesafe.config.ConfigFactory

// COMMAND
case class RegisterUser(email: String, name: String)

// EVENT
case class UserRegistered(id: Int, email: String, name: String)

// SERIALIZER
class UserRegistrationSerializer extends Serializer {

  val SEPARATOR = "//"
  override def identifier: Int = 1234

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event @ UserRegistered(id, email, name) =>
      println(s"Serializing $event")
      s"[$id$SEPARATOR$email$SEPARATOR$name]".getBytes()
    case _ =>
      throw new IllegalArgumentException("only user registration events supported in this serializer")
  }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val string = new String(bytes)
    val values = string.substring(1, string.length() -1).split(SEPARATOR)
    val id = values(0).toInt
    val email = values(1)
    val name = values(2)

    val result = UserRegistered(id, email, name)
    println(s"Deserialized: $string to $result")
    result
  }

  override def includeManifest: Boolean = false
  /*
    if this method returns true, then we will pass the class
    the Array[Bytes] should return
    in our example:
      toBinary(o): UserRegistered(id, email, name) -> Array[Bytes]
      fromBinary(bytes, manifest): Array[Bytes] -> UserRegistered(id, email, name)
    but if it returns false, the manifest param in the fromBinary should be None

   */
}

class UserRegistrationActor extends PersistentActor with ActorLogging {
  override def persistenceId: String = "user-registration"
  var currentId = 0

  override def receiveCommand: Receive = {
    case RegisterUser(email, name) =>
      persist(UserRegistered(currentId, email, name)) { e =>
        currentId += 1
        log.info(s"Persisted: $e")
      }
  }

  override def receiveRecover: Receive = {
    case event @ UserRegistered(id, _, _) =>
      log.info(s"Recovered: $event")
      currentId = id
  }
}

object CustomSerialization extends App {

  /*
    send a command to the actor
      actor call persist
      serializer calls toBinary and the event into bytes
      the journal writes the bytes

    whe the actor call for a recovery
      the journal sends back the bytes
      serializer calls fromBinary and turn the bytes back into the event
      actor replays the events
   */

  val system = ActorSystem("CustomSerializationDemo", ConfigFactory.load().getConfig("customSerializerDemo"))
  val userActor = system.actorOf(Props[UserRegistrationActor], "userActor")

  for (i <- 1 to 10) {
    userActor ! RegisterUser(s"user_$i@rtjvm.com", s"User_$i")
  }

}
