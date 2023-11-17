package lib

import org.apache.pekko.actor.ActorSystem

import java.time.Instant
import java.time.Instant.now
import java.time.temporal.ChronoUnit.MINUTES
import com.madgag.github.Implicits._
import com.madgag.scalagithub.GitHub
import com.madgag.scalagithub.model.RepoId
import com.madgag.time.Implicits._
import lib.labels.Seen
import play.api.Logging
import play.api.libs.concurrent.Pekko

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

object ScanScheduler {
  class Factory(
    droid: Droid,
    conn: GitHub,
    actorSystem: ActorSystem,
    delayer: Delayer
  ) extends Logging {
    def createFor(repoId: RepoId): ScanScheduler = {
      logger.info(s"Creating scheduler for $repoId")
      new ScanScheduler(
        repoId,
        droid,
        actorSystem,
        delayer
      )
    }
  }
}

class ScanScheduler(
  repoId: RepoId,
  droid: Droid,
  actorSystem: ActorSystem,
  delayer: Delayer
) extends Logging { selfScanScheduler =>

  val earliestFollowUpScanTime: AtomicReference[Instant] = new AtomicReference(Instant.now())

  private val dogpile = new Dogpile(delayer.delayTheFuture {
    logger.info(s"In the dogpile for $repoId...")
    val summariesF = droid.scan(repoId)
    for {
      summariesTry <- summariesF.trying
    } yield {
      summariesTry match {
        case Failure(e) =>
          logger.error(s"Scanning $repoId failed", e)
        case Success(summaries) =>
          logger.info(s"$selfScanScheduler : ${summaries.size} summaries for ${repoId.fullName}:\n${summaries.map(s => s"#${s.prCheckpointDetails.pr.prId.slug} changed=${s.changed.map(_.snapshot.checkpoint.name)}").mkString("\n")}")

          val scanTimeForUnseenOpt = summaries.find(!_.checkpointStatuses.all(Seen)).map(_ => now.plus(1L, MINUTES))

          val overdueTimes = summaries.collect {
            case summary => summary.prCheckpointDetails.soonestPendingCheckpointOverdueTime
          }.flatten

          val candidateFollowUpScanTimes = overdueTimes ++ scanTimeForUnseenOpt

          if (candidateFollowUpScanTimes.nonEmpty) {
            val earliestCandidateScanTime: Instant = candidateFollowUpScanTimes.min
            earliestFollowUpScanTime.updateAndGet {
              oldFollowupTime =>
                if (now.isAfter(oldFollowupTime) || earliestCandidateScanTime.isBefore(oldFollowupTime)) {
                  actorSystem.scheduler.scheduleOnce(java.time.Duration.between(now, earliestCandidateScanTime)) {
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

  def scan(): Future[Seq[PullRequestCheckpointsStateChangeSummary]] = {
    logger.info(s"Asked for a scan on $repoId...")
    dogpile.doAtLeastOneMore()
  }

}
