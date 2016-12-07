package lib.labels

import lib.Config.Checkpoint

sealed trait PullRequestCheckpointTestStatus extends PullRequestLabel {
  def labelFor(checkpointName: String) = {
    name + "-in-" + checkpointName
  }
}

object PullRequestCheckpointTestStatus {
  val all = Set[PullRequestCheckpointTestStatus](Pass, Fail)

  def fromLabels(labels: Set[String], checkpoint: Checkpoint): Option[PullRequestCheckpointTestStatus] =
    PullRequestCheckpointTestStatus.all.find(s => labels(s.labelFor(checkpoint.name)))
}

case object Pass extends PullRequestCheckpointTestStatus {
  override val defaultColour: String = "bfe5bf"
}

case object Fail extends PullRequestCheckpointTestStatus {
  override val defaultColour: String = "e11d21"
}
