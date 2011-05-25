package com.nouhoum.akka

import akka.routing.Routing.Broadcast
import akka.dispatch.{CompletableFuture, Future}
import scala.io.Source
import akka.routing.{ CyclicIterator, Routing }
import akka.actor.{ Actor, ActorRef, PoisonPill }
import Actor._

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
    println("Please give files to parse at command line!")
    println("Usage: PhoneNumberSearchApp file1 file2 file3 ..... fileN")
  } else
    go(args)

  def go(args: Array[String]) = {
	  var start = System.currentTimeMillis
    println("=========================")
   
    val tasks =
      for {
        file <- args
        fileContent = Source.fromFile(file).mkString
      } yield Task(fileContent)

    val master = actorOf(new Master(tasks.length)).start()

    println("Waiting for the master response....")
    val response = master !! tasks

    println("====The following numbers has been found====")
    
    response match {
    	case Some(numbers) =>
    	  numbers match {
    	    case numbersAsIter:Iterable[Any] =>
       	    println(numbersAsIter mkString("\n"))
       	  case _ @response =>
       	    println("Unknown response format : " + response)
    	  }    		
    	case None => println("No phone number found")
    }
    
    println("Total time : " + (System.currentTimeMillis - start) + " ms")
    println("=========================")
  }

  abstract class Message
  case class Task(fileContent: String) extends Message
  case class Result(numbers: Set[String]) extends Message

  class Master(nbOfWorkers: Int) extends Actor {
    var responseCount = 0
    var senderFuture: Option[CompletableFuture[Any]] = _
    var phoneNumbers = Set[String]()
    //We create and start worker actors. 
    //An actor for parsing each file content
    val workers = Vector.fill(nbOfWorkers)(actorOf[Worker].start())

    //We create the load balancer with a round round robin strategy
    val loadBalancer = Routing.loadBalancerActor(CyclicIterator(workers))

    def receive = {
      case Result(intermediateNumbers) =>
        phoneNumbers = phoneNumbers ++ intermediateNumbers
        responseCount += 1

        if (responseCount == nbOfWorkers) {
          senderFuture.foreach(_.completeWithResult(phoneNumbers))
          loadBalancer ! Broadcast(PoisonPill)
          loadBalancer ! PoisonPill
          self.stop()
        }

      case tasks: Array[Task] =>
        println("Received task list size = " + tasks.length)
        //tasks foreach { println(_) }
        senderFuture = self.senderFuture

        for (task <- tasks) {
          loadBalancer ! task
          Work.taskCount += 1
        }
    }
    
    override def preStart() = {
      println("=> preStart() of the Master")
    }
    
    override def postStop() = {
    	println("=> postStop() of the Master")
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
        println("Worker doing hard work!!...")
        var phoneNumbers = Set[String]()
        for(phoneNumber <- phoneRegex findAllIn fileContent) {
        	phoneNumbers += phoneNumber
        }
        self reply Result(phoneNumbers)
    }

    override def preStart() = {
      println("=> preStart() of the Worker")
    }
    
    override def postStop() = {
      println("=> postStop() of the Worker")
    }
  }
}
