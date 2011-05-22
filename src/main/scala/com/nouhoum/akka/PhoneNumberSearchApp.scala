package com.nouhoum.akka

import akka.routing.Routing.Broadcast
import akka.dispatch.CompletableFuture
import akka.dispatch.Future
import scala.io.Source
import akka.routing.{ CyclicIterator, Routing }
import akka.actor.{ Actor, ActorRef, PoisonPill }
import Actor._

/**
 * This is an Akka Application which parses files to
 * extract (french) phone numbers. It has been written for my blog
 * at blog.nouhoumtraore.com
 * 
 * Usage PhoneNumberSearchApp file1 file2 file3 ..... fileN
 * 
 * @author Nouhoum TRAORE (@nouhoumtraore)
 */
object PhoneNumberSearchApp extends App {
  if (args.length == 0)
    println("Please give files to parse at command line!")
  else
    go(args)

  def go(args: Array[String]) = {
	var start = System.currentTimeMillis
    println("=========================")
   
    val tasks =
      for {
        file <- args
        fileContent = Source.fromFile(file).mkString
      } yield Task(fileContent)

    println("Task as String ==> " + tasks.toString)
    val master = actorOf(new Master(tasks)).start()

    println("Waiting for the master response....")
    val response = master !! tasks

    println("Response arrived!!! = " + response + "\n====The following numbers has been found==== \n")
    response match {
    	case Some(numbers:Set[Any]) =>
    		numbers foreach {println(_)}
    	case None => println("No phone number found")
    }
    println("Total time : " + (System.currentTimeMillis - start) + " ms")
    println("=========================")
  }

  abstract class Message
  case class Task(fileContent: String) extends Message
  case class Result(numbers: Set[String]) extends Message

  class Master(tasks: Seq[Task]) extends Actor {
    var responseCount = 0
    var sender: Option[CompletableFuture[Any]] = _
    var phoneNumbers = Set[String]()
    //We create and start worker actors. 
    //An actor for parsing each file content
    val taskNumber = tasks.length
    val workers = Vector.fill(taskNumber)(actorOf[Worker].start())

    //We create the load balancer with a round round robin strategy
    val loadBalancer = Routing.loadBalancerActor(CyclicIterator(workers))

    def receive = {
      case Result(numbers) =>
        println("Received result = " + numbers)
        numbers foreach { println(_) }
        phoneNumbers = phoneNumbers ++ numbers
        responseCount += 1

        if (responseCount == taskNumber) {
          sender.foreach(_.completeWithResult(phoneNumbers))
          loadBalancer ! Broadcast(PoisonPill)
          loadBalancer ! PoisonPill
          self.stop()
        }
      case "Special" =>
        self reply "This is the response to special"
      case tasks: Array[Task] =>
        println("Received task list size = " + tasks.length)
        tasks foreach { println(_) }
        sender = self.senderFuture

        for (task <- tasks) {
          loadBalancer ! task
          Work.taskCount += 1
        }
    }
    
    override def postStop() = {
    	println("=> Master stopped!")
    }
  }

  object Work {
    var taskCount = 0
  }

  /**
   * A worker parses a file and returns found phone numbers as
   * a set to the master actor.
   */
  class Worker extends Actor {
	val phoneRegex = "0[1-9]([ .-]?[0-9]{2}){4}".r
	
    def receive = {
      case Task(fileContent) =>
        println("Doing hard work!!...")
        var phoneNumbers = Set[String]()
        for(phoneNumber <- phoneRegex findAllIn fileContent) {
        	phoneNumbers += phoneNumber
        }
        self reply Result(phoneNumbers)
    }

    override def preStart() = {
      println("=> Worker pre start step!!")
    }

    override def postStop() = {
      println("=> Worker post stop step!!")
    }
  }
}