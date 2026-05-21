package lib

import cats.*
import cats.data.*
import cats.syntax.all.*
import cats.effect.IO
import org.apache.pekko.actor.ActorSystem

import java.time.{Duration, Instant}
import java.time.Instant.now
import java.time.temporal.ChronoUnit.MINUTES
import com.madgag.github.Implicits.*
import com.madgag.scalagithub.GitHub
import com.madgag.scalagithub.model.RepoId
import com.madgag.time.Implicits.*
import lib.ScanScheduler.LongestScanInterval
import lib.labels.Seen
import play.api.Logging
import play.api.libs.concurrent.Pekko

import java.time.Duration.ofMinutes
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.math.Ordering.Implicits.*
import scala.util.{Failure, Success}

object ScanScheduler {
  val LongestScanInterval: Duration = ofMinutes(1)

  class Factory(
    droid: Droid,
    conn: GitHub,
    actorSystem: ActorSystem
  ) extends Logging {
    def createFor(repoId: RepoId): ScanScheduler = {
      logger.info(s"Creating scheduler for $repoId")
      new ScanScheduler(repoId, droid, actorSystem)
    }
  }
  
  def nextScanTimeFor(summaries: Iterable[PRCheckpointDetails]): Option[Instant] = for {
    soonestOverdueTime: Instant <- summaries.flatMap(_.soonestPendingCheckpointOverdueTime).minOption
  } yield soonestOverdueTime min now.plus(LongestScanInterval)
}


/**
 * For a given repo, we need to know:
 * - When we last scanned it (so we don't waste resources scanning it too often)
 * - When PRs with unseen checkpoints will become overdue
 * - When we've already scheduled a scan in the future...
 * 
 * How do we de-duplicate in-flight requests...?
 */
class ScanScheduler(repoId: RepoId, droid: Droid, actorSystem: ActorSystem) extends Logging { selfScanScheduler =>

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
        case Success(summaries: Iterable[PullRequestCheckpointsStateChangeSummary]) =>
          logger.info(s"$selfScanScheduler : ${summaries.size} summaries for ${repoId.fullName}:\n${summaries.map(s => s"#${s.prCheckpointDetails.pr.prId.slug} changed=${s.changed.map(_.snapshot.checkpoint.name)}").mkString("\n")}")

          for {
            nextScanTime <- nextScanTimeFor(summaries.map(_.prCheckpointDetails))
          } yield {
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

  def scan(): IO[Seq[PullRequestCheckpointsStateChangeSummary]] = {
    logger.info(s"Asked for a scan on $repoId...")
    dogpile.doAtLeastOneMore()
  }

}
