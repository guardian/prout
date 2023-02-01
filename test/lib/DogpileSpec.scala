package lib

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.Executors
import java.util.concurrent.atomic.{AtomicLong, LongAdder}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class DogpileSpec extends AnyFlatSpec with Matchers {
  it should "not concurrently execute the side-effecting function" in {

    val executionCount = new LongAdder()
    val runningCounter = new AtomicLong(0)
    val clashCounter = new LongAdder()

    val dogpile = new Dogpile[String]({
      val numRunning = runningCounter.incrementAndGet()
      executionCount.increment()
      if (numRunning > 1) {
        clashCounter.increment()
      }
      Thread.sleep(10)
      runningCounter.decrementAndGet()
      Future.successful("OK")
    })

    implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4))

    val numExecutions = 20
    val allF = Future.traverse(1 to numExecutions)(_ => dogpile.doAtLeastOneMore())
    Await.ready(allF, 15.seconds)

    executionCount.intValue() should be <= numExecutions
    runningCounter.get() shouldBe 0
    clashCounter.longValue() shouldBe 0
  }
}
