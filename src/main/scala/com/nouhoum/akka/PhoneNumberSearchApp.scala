package com.nouhoum.akka

import scala.io.Source
import akka.actor.{Actor, Props, ActorRef, ActorSystem}
import akka.routing.RoundRobinRouter

/**
 * This is an Akka Application which parses files to
 * extract (french) phone numbers. It has been written for my blog
 * at blog.nouhoumtraore.com
 *
 * Usage: PhoneNumberSearchApp file1 file2 file3 ..... fileN
 *
 * @author Nouhoum TRAORE (@nouhoumtraore)
 */

object PhoneNumberSearchApp extends App {
  if (args.length == 0) {
    printUsage()
  } else {
    go(args)
  }

  def go(args: Array[String]) = {
    val start = System.currentTimeMillis
    println("=========================")

    val data =
      (for (file <- args) yield Source.fromFile(file).mkString).toList

    val system = ActorSystem("phone-number")
    val phoneNumberParser: ActorRef = system.actorOf(Props[Master], "phone-number-parser")

    phoneNumberParser ! Task("Toto")

    phoneNumberParser ! StartProcessing(data, 3)

    println("Total time : " + (System.currentTimeMillis - start) + " ms")
    println("=========================")
  }

  private def printUsage() {
    println( """
    Please give files to parse at command line!
    Usage: PhoneNumberSearchApp file1 file2 file3 ..... fileN""")
  }

  type PhoneNumber = String

  sealed trait Message

  case class Task(data: String) extends Message

  case class Result(numbers: Set[PhoneNumber]) extends Message

  case class StartProcessing(contents: List[String], numberOfWorkers: Int)

  /**
   * A worker parses a file and returns found phone numbers as
   * a set to the master actor.
   */
  class Worker extends Actor {
    val PhoneRegex = "0[1-9]([ .-]?[0-9]{2}){4}".r

    def receive = {
      case Task(data) => sender ! Result(phoneNumbersIn(data))
    }

    override def preStart() = {
      println("=> preStart() of the Worker")
    }

    override def postStop() = {
      println("=> postStop() of the Worker")
    }

    private def phoneNumbersIn(data: String): Set[PhoneNumber] = PhoneRegex.findAllIn(data).toSet
  }

  class Master extends Actor {
    var phoneNumbers = Set[PhoneNumber]()
    var dataSize = 0
    var processedDataSize = 0

    def receive = {
      case StartProcessing(contents, numberOfWorkers) =>
        dataSize = contents.length
        val router = context.system.actorOf(Props[Worker].withRouter(RoundRobinRouter(nrOfInstances = numberOfWorkers)))
        contents.foreach(content => router ! Task(content))

      case Result(intermediateNumbers) =>
        phoneNumbers = phoneNumbers ++ intermediateNumbers
        processedDataSize += 1

        if (processedDataSize == dataSize) {
          println("All data processed : ")
          phoneNumbers.foreach(println)
          context.system.shutdown()
        }
    }

    override def preStart() = {
      println("=> preStart() of the Master")
    }

    override def postStop() = {
      println("=> postStop() of the Master")
    }
  }

}