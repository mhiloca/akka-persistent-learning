package part1recap

import scala.concurrent.Future
import scala.util.{Failure, Success}

object ScalaRecap extends App {

  val aCondition: Boolean = false
  def myFunction(x: Int): Int = {
    // code
    if (x > 4) 42 else 65
  }

  // Instructions vs expressions
  //types + type inference

  // OO features of Scala
  class Animal
  trait Carnivore {
    def eat(a: Animal): Unit
  }

  object Carnivore

  // generics
  abstract class MyList[+A]

  // method notations - infix notation
  1 + 2 // infix notation
  1.+(2)

  val anIncrementer: Int => Int = (x: Int) => x + 1

  anIncrementer(1)
  List(1, 2, 3).map(anIncrementer)

  // for-comprehensions

  // Monads: Option and Try

  // Pattern Matching
  val unknown: Any= 2
  val order = unknown match {
    case 1 => "first"
    case 2 => "second"
    case _ => "unknown"
  }

  try {
    // code that can throw an expection
    throw new RuntimeException
  } catch {
    case e: Exception => println("I caught one!")
  }

  /**
    * Scala advanced
    */

  // multithreading

  import scala.concurrent.ExecutionContext.Implicits.global
  val future = Future {
    // long computation here
    // executed on SOME other thread
    42
  }

  future.onComplete {
    case Success(value) => println(value)
    case Failure(e) => println("It didn't work")
  }

  // map, flatMap, filter + other niceties e.g. recover/ recoverWith

  val aPartialFunction: PartialFunction[Int, Int] = {
    case 1 => 42
    case 2 => 65
    case _ => 999
  }

  // type aliases
  type AkkaReceive = PartialFunction[Any, Unit]
  def receive: AkkaReceive = {
    case 1 => println("hello")
    case _ => println("confused...")
  }


  // Implicits
  implicit val timeout = 3000
  def setTimeout(f: () => Unit)(implicit timeout: Int) = f()
  setTimeout(() => println("timeout")) // I don't need to supply the other arg list, because it is injected by the compiler

  // conversions
  // 1) implicit methods
  case class Person(name: String) {
    def greet: String = s"Hi, my name is $name"
  }

  implicit def fromStringToPerson(name: String) = Person(name)

  "Peter".greet

  // fromStringToPerson("Peter").greet

  // 2) implicit classes

  implicit class Dog(name: String) {
    def bark = println("Woof!")
  }

  "Lassie".bark
  //new Dog("Lassie").bark

  // implicit organization
  // 1) local scope
  implicit val numberOrdering: Ordering[Int] = Ordering.fromLessThan(_ > _)
  List(1,2,3).sorted // the compiler automatically injects numberOrdering as the second param List(3, 2, 1)

  // 2) imported scope
  // 3) companion objects of the types involved in the call
  object Person {
    implicit val personOrdering: Ordering[Person] = Ordering.fromLessThan((a, b) => a.name.compareTo(b.name) < 0)
  }
  List(Person("Bob"), Person("Alice")).sorted // Person.personOrdering
  // => List(Person("Alice"), Person("Bob"))

}
