package lib

import lib.Config.Checkpoint

sealed trait PullRequestCheckpointStatus {
  def labelFor(checkpointName: String) = getClass.getSimpleName.dropRight(1) + "-on-" + checkpointName
}

object PullRequestCheckpointStatus {
  val all = Set[PullRequestCheckpointStatus](Seen, Pending, Overdue)

  def fromLabels(labels: Set[String], checkpoint: Checkpoint): Option[PullRequestCheckpointStatus] =
    PullRequestCheckpointStatus.all.find(s => labels(s.labelFor(checkpoint.name)))
}

sealed trait NotSeenOnSite extends PullRequestCheckpointStatus

case object Seen extends PullRequestCheckpointStatus

case object Pending extends NotSeenOnSite

case object Overdue extends NotSeenOnSite
