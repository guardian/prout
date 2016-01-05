package lib

import lib.Config.Checkpoint

sealed trait PullRequestCheckpointTestStatus {

  val name = getClass.getSimpleName.dropRight(1)

  def labelFor(checkpointName: String) = {
    name + "-in-" + checkpointName
  }

  val defaultColour: String
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
