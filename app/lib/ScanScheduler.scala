package lib

import akka.agent.Agent
import com.github.nscala_time.time.Imports._
import lib.Implicits._
import org.joda.time.{DateTime, Instant}
import org.kohsuke.github.GitHub
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Akka

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ScanScheduler(repoFullName: RepoFullName,
                    checkpointSnapshoter: CheckpointSnapshoter,
                    conn: => GitHub) { selfScanScheduler =>

  val logger = Logger(getClass)

  val droid = new Droid

  val earliestFollowUpScanTime = Agent(Instant.now)

  private val dogpile = new Dogpile(Delayer.delayTheFuture {
    val summariesF = droid.scan(conn.getRepository(repoFullName.text))(checkpointSnapshoter)
    for (summaries <- summariesF) {
      logger.info(s"$selfScanScheduler : ${summaries.size} summaries for ${repoFullName.text}:\n${summaries.map(s => s"#${s.pr.getNumber} ${s.stateChange}").mkString("\n")}")

      val overdueTimes = summaries.collect {
        case summary => summary.soonestPendingCheckpointOverdueTime
      }.flatten

      if (overdueTimes.nonEmpty) {
        val nextOverdue: Instant = overdueTimes.min
        earliestFollowUpScanTime.send {
          oldFollowupTime =>
            val now = DateTime.now
            if (now > oldFollowupTime || nextOverdue < oldFollowupTime) {
              Akka.system.scheduler.scheduleOnce((now to nextOverdue).duration) {
                scan()
              }
              nextOverdue
            } else oldFollowupTime
        }
      }
    }
    summariesF
  })

  def scan(): Future[immutable.Seq[PullRequestCheckpointsSummary]] = dogpile.doAtLeastOneMore()

}
