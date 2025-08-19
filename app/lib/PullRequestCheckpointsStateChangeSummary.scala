package lib

import com.github.nscala_time.time.Imports._
import com.madgag.scala.collection.decorators._
import com.madgag.scalagithub.model.PullRequest
import lib.Config.Checkpoint
import lib.gitgithub.StateSnapshot
import lib.labels.{Pending, PullRequestCheckpointStatus, Seen}
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit

case class PRCheckpointState(statusByCheckpoint: Map[String, PullRequestCheckpointStatus]) {

  val isEmpty: Boolean = statusByCheckpoint.isEmpty

  val checkpointsByStatus: Map[PullRequestCheckpointStatus, Set[String]] =
    statusByCheckpoint.groupUp(_._2)(_.keySet).withDefaultValue(Set.empty)

  val states: Set[PullRequestCheckpointStatus] = checkpointsByStatus.keySet
  def all(s: PullRequestCheckpointStatus): Boolean = states.forall(_ == s)
  def has(s: PullRequestCheckpointStatus) = states.contains(s)
  val hasStateForCheckpointsWhichHaveAllBeenSeen: Boolean = states == Set(Seen)

  def hasSeen(checkpoint: Checkpoint): Boolean = checkpointsByStatus(Seen).contains(checkpoint.name)

  def updateWith(newCheckpointStatus: Map[String, PullRequestCheckpointStatus]) =
    PRCheckpointState(newCheckpointStatus ++ statusByCheckpoint.view.filterKeys(checkpointsByStatus(Seen)))

  def changeFrom(oldState: PRCheckpointState): Map[String, PullRequestCheckpointStatus] =
    (statusByCheckpoint.toSet -- oldState.statusByCheckpoint.toSet).toMap
}

case class PRCommitVisibility(seen: Set[RevCommit], unseen: Set[RevCommit])


object PRCheckpointDetails {
  def apply(
           pr: PullRequest,
           snapshots: Set[CheckpointSnapshot],
           gitRepo: Repository
           ): PRCheckpointDetails = {

    val everythingByCheckpoint: Map[Checkpoint, EverythingYouWantToKnowAboutACheckpoint] =
      (for (snapshot <- snapshots) yield {
        snapshot.checkpoint -> EverythingYouWantToKnowAboutACheckpoint(pr,snapshot,gitRepo)
      }).toMap

    PRCheckpointDetails(pr, everythingByCheckpoint)
  }
}

case class PRCheckpointDetails(
  pr: PullRequest,
  everythingByCheckpoint: Map[Checkpoint, EverythingYouWantToKnowAboutACheckpoint]
) {
  val everythingByCheckpointName: Map[String, EverythingYouWantToKnowAboutACheckpoint] =
    for ((c, e) <- everythingByCheckpoint) yield c.name -> e

  val checkpointStatusByName: Map[String, PullRequestCheckpointStatus] =
    everythingByCheckpointName.mapV(_.checkpointStatus)

  val checkpointsByState: Map[PullRequestCheckpointStatus, Set[Checkpoint]] =
    everythingByCheckpoint.values.groupBy(_.checkpointStatus).mapV(_.map(_.snapshot.checkpoint).toSet)

  val soonestPendingCheckpointOverdueTime: Option[java.time.Instant] = {
    implicit val periodOrdering: Ordering[Period] = Ordering.by[Period, Duration](_.toStandardDuration)

    checkpointsByState.get(Pending).map(_.flatMap(_.details.overdueInstantFor(pr)).min)
  }

}

case class PullRequestCheckpointsStateChangeSummary(
  prCheckpointDetails: PRCheckpointDetails,
  oldState: PRCheckpointState
) extends StateSnapshot[PRCheckpointState] {

  val checkpointStatuses: PRCheckpointState = oldState.updateWith(prCheckpointDetails.checkpointStatusByName)

  override val newPersistableState: PRCheckpointState = checkpointStatuses

  val newlyMerged: Boolean = oldState.isEmpty && !newPersistableState.isEmpty

  val changed: Set[EverythingYouWantToKnowAboutACheckpoint] =
    checkpointStatuses.changeFrom(oldState).keySet.map(prCheckpointDetails.everythingByCheckpointName)

  val changedByState: Map[PullRequestCheckpointStatus, Set[EverythingYouWantToKnowAboutACheckpoint]] =
    changed.groupBy(_.checkpointStatus)

}
