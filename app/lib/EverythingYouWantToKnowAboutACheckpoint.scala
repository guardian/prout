package lib

import com.madgag.git._
import com.madgag.scalagithub.model.PullRequest
import lib.labels.{Overdue, Pending, PullRequestCheckpointStatus, Seen}
import org.eclipse.jgit.lib.{ObjectId, Repository}
import org.eclipse.jgit.revwalk.RevWalk
import play.api.Logger

object EverythingYouWantToKnowAboutACheckpoint {
  def apply(pr: PullRequest, snapshot: CheckpointSnapshot, gitRepo: Repository): EverythingYouWantToKnowAboutACheckpoint = {
    val timeBetweenMergeAndSnapshot = java.time.Duration.between(pr.merged_at.get.toInstant, snapshot.time)

    def prCommitsSeenAndNotSeen(siteCommitId: ObjectId): PRCommitVisibility = {
      implicit val repoThreadLocal = gitRepo.getObjectDatabase.threadLocalResources
      implicit val w: RevWalk = new RevWalk(repoThreadLocal.reader())
      val siteCommit = siteCommitId.asRevCommit

      val (prCommitsSeenOnSite, prCommitsNotSeen) = pr.availableTipCommits.partition(prCommit => w.isMergedInto(prCommit.asRevCommit, siteCommit))
      if (prCommitsSeenOnSite.nonEmpty && prCommitsNotSeen.nonEmpty) {
        Logger.info(s"prCommitsSeenOnSite=${prCommitsSeenOnSite.map(_.name)} prCommitsNotSeen=${prCommitsNotSeen.map(_.name)}")
      }
      PRCommitVisibility(prCommitsSeenOnSite, prCommitsNotSeen)
    }

    val prVis = for {
      commitIdOpt <- snapshot.commitIdTry.toOption
      siteCommitId <- commitIdOpt
    } yield prCommitsSeenAndNotSeen(siteCommitId)

    val isVisibleOnSite = prVis.exists(_.seen.nonEmpty)

    val currentStatus: PullRequestCheckpointStatus =
      if (isVisibleOnSite) Seen else {
        val overdueThreshold = snapshot.checkpoint.overdueInstantFor(pr)
        if (overdueThreshold.exists(_ isBefore snapshot.time)) Overdue else Pending
      }

    EverythingYouWantToKnowAboutACheckpoint(snapshot, prVis, currentStatus)
  }
}

case class EverythingYouWantToKnowAboutACheckpoint(
   snapshot: CheckpointSnapshot,
   commitVisibility: Option[PRCommitVisibility],
   checkpointStatus: PullRequestCheckpointStatus
)