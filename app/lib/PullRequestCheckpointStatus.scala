package lib

import lib.Config.Checkpoint

sealed trait PullRequestCheckpointStatus {

  val name = getClass.getSimpleName.dropRight(1)

  def labelFor(checkpointName: String) = {
    name + "-on-" + checkpointName
  }

  val defaultColour: String
}

object PullRequestCheckpointStatus {
  val all = Set[PullRequestCheckpointStatus](Seen, Pending, Overdue)

  def fromLabels(labels: Set[String], checkpoint: Checkpoint): Option[PullRequestCheckpointStatus] =
    PullRequestCheckpointStatus.all.find(s => labels(s.labelFor(checkpoint.name)))
}

sealed trait NotSeenOnSite extends PullRequestCheckpointStatus

case object Seen extends PullRequestCheckpointStatus {
  override val defaultColour: String = "bfe5bf"
}

case object Pending extends NotSeenOnSite {
  override val defaultColour: String = "ededed"
}

case object Overdue extends NotSeenOnSite {
  override val defaultColour: String = "e11d21"
}
