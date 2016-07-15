package lib

import java.time.Instant
import java.time.Instant.now
import java.time.temporal.ChronoUnit.MINUTES

import akka.agent.Agent
import com.madgag.github.Implicits._
import com.madgag.scalagithub.GitHub
import com.madgag.scalagithub.model.RepoId
import com.madgag.time.Implicits._
import lib.labels.Seen
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Akka

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class ScanScheduler(repoId: RepoId,
                    checkpointSnapshoter: CheckpointSnapshoter,
                    conn: GitHub) { selfScanScheduler =>

  val droid = new Droid

  val earliestFollowUpScanTime = Agent(now)

  private val dogpile = new Dogpile(Delayer.delayTheFuture {
    Logger.debug(s"In the dogpile for $repoId...")
    for {
      repo <- conn.getRepo(repoId)
      summariesF = droid.scan(repo)(checkpointSnapshoter)
      summariesTry <- summariesF.trying
    } yield {
      summariesTry match {
        case Failure(e) =>
          Logger.error(s"Scanning $repoId failed", e)
        case Success(summaries) =>
          Logger.info(s"$selfScanScheduler : ${summaries.size} summaries for ${repoId.fullName}:\n${summaries.map(s => s"#${s.prCheckpointDetails.pr.prId.slug} changed=${s.changed.map(_.snapshot.checkpoint.name)}").mkString("\n")}")

          val scanTimeForUnseenOpt = summaries.find(!_.checkpointStatuses.all(Seen)).map(_ => now.plus(1L, MINUTES))

          val overdueTimes = summaries.collect {
            case summary => summary.prCheckpointDetails.soonestPendingCheckpointOverdueTime
          }.flatten

          val candidateFollowUpScanTimes = overdueTimes ++ scanTimeForUnseenOpt

          if (candidateFollowUpScanTimes.nonEmpty) {
            val earliestCandidateScanTime: Instant = candidateFollowUpScanTimes.min
            earliestFollowUpScanTime.send {
              oldFollowupTime =>
                if (now.isAfter(oldFollowupTime) || earliestCandidateScanTime.isBefore(oldFollowupTime)) {
                  Akka.system.scheduler.scheduleOnce(java.time.Duration.between(now, earliestCandidateScanTime)) {
                    scan()
                  }
                  earliestCandidateScanTime
                } else oldFollowupTime
            }
          }
      }
      summariesTry.get
    }
  })

  def scan(): Future[Seq[PullRequestCheckpointsStateChangeSummary]] = dogpile.doAtLeastOneMore()

}
