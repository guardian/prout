package lib.labels

import lib.Config.Checkpoint

sealed trait CheckpointTestStatus extends PullRequestLabel {
  def labelFor(checkpointName: String) = {
    name + "-in-" + checkpointName
  }
}

object CheckpointTestStatus {
  val all = Set[CheckpointTestStatus](Pass, Fail)

  def fromLabels(labels: Set[String], checkpoint: Checkpoint): Option[CheckpointTestStatus] =
    CheckpointTestStatus.all.find(s => labels(s.labelFor(checkpoint.name)))
}

case object Pass extends CheckpointTestStatus {
  override val defaultColour: String = "bfe5bf"
}

case object Fail extends CheckpointTestStatus {
  override val defaultColour: String = "e11d21"
}
