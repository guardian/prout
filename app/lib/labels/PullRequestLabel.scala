package lib.labels

trait PullRequestLabel {
  val name = getClass.getSimpleName.dropRight(1)

  def labelFor(checkpointName: String): String

  val defaultColour: String
}
