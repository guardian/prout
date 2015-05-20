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
import scala.util.{Failure, Success}

class ScanScheduler(repoFullName: RepoFullName,
                    checkpointSnapshoter: CheckpointSnapshoter,
                    conn: => GitHub) { selfScanScheduler =>

  val droid = new Droid

  val earliestFollowUpScanTime = Agent(Instant.now)

  private val dogpile = new Dogpile(Delayer.delayTheFuture {
    val summariesF = droid.scan(conn.getRepository(repoFullName.text))(checkpointSnapshoter)
    for (summariesTry <- summariesF.trying) {
      summariesTry match {
        case Failure(e) =>
          Logger.error(s"Scanning $repoFullName failed", e)
        case Success(summaries) =>
          Logger.info(s"$selfScanScheduler : ${summaries.size} summaries for ${repoFullName.text}:\n${summaries.map(s => s"#${s.pr.getNumber} ${s.stateChange}").mkString("\n")}")

          val scanTimeForUnseenOpt = summaries.find(!_.checkpointStatuses.all(Seen)).map(_ => Instant.now + 1.minute)

          val overdueTimes = summaries.collect {
            case summary => summary.soonestPendingCheckpointOverdueTime
          }.flatten

          val candidateFollowUpScanTimes = overdueTimes ++ scanTimeForUnseenOpt

          if (candidateFollowUpScanTimes.nonEmpty) {
            val earliestCandidateScanTime: Instant = candidateFollowUpScanTimes.min
            earliestFollowUpScanTime.send {
              oldFollowupTime =>
                val now = DateTime.now
                if (now > oldFollowupTime || earliestCandidateScanTime < oldFollowupTime) {
                  Akka.system.scheduler.scheduleOnce((now to earliestCandidateScanTime).duration) {
                    scan()
                  }
                  earliestCandidateScanTime
                } else oldFollowupTime
            }
          }
      }
    }
    summariesF
  })

  def scan(): Future[immutable.Seq[PullRequestCheckpointsSummary]] = dogpile.doAtLeastOneMore()

}
