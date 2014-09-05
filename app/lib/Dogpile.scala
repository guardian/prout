package lib

import akka.agent.Agent

import scala.concurrent.ExecutionContext.Implicits.global

class Dogpile(thing: => Unit) {
  val agent = Agent(0)

  def doAtLeastOneMore() {
    val numberOfLastCompletedRunWhenReceivingRequest = agent.get()
    agent.sendOff { numOfLastCompletedRun =>
      println(s"handling request for a run after $numberOfLastCompletedRunWhenReceivingRequest")
      if (numOfLastCompletedRun <= numberOfLastCompletedRunWhenReceivingRequest + 1) {
        println(s"executing request for a run after $numberOfLastCompletedRunWhenReceivingRequest")
        thing
        println(s"finished executing request for a run after $numberOfLastCompletedRunWhenReceivingRequest")
        numOfLastCompletedRun + 1
      } else numOfLastCompletedRun
    }
  }
}

