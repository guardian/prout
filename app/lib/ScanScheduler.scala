package lib

import akka.agent.Agent
import com.github.nscala_time.time.Imports._
import lib.Config.Checkpoint
import lib.Implicits._
import org.joda.time.{DateTime, Instant}
import play.api.Play.current
import play.api.libs.concurrent.Akka

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ScanScheduler(repoFullName: RepoFullName) {

  val droid = new Droid

  implicit val checkpointSnapshoter: Checkpoint => Future[CheckpointSnapshot] = CheckpointSnapshot(_)

  val earliestFollowUpScanTime = Agent(Instant.now)

  private val dogpile = new Dogpile({
    val summariesF = droid.scan(Bot.githubCredentials.conn().getRepository(repoFullName.text))
    for (summaries <- summariesF) {
      val overdueTimes = summaries.collect {
        case summary => summary.soonestPendingCheckpointOverdueTime
      }.flatten

      if (overdueTimes.nonEmpty) {
        val nextOverdue: Instant = overdueTimes.min
        earliestFollowUpScanTime.send {
          oldFollowupTime =>
            if (Instant.now > oldFollowupTime || nextOverdue < oldFollowupTime) {
              Akka.system.scheduler.scheduleOnce((DateTime.now to nextOverdue).duration) {
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
