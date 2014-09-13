package lib.gitgithub

trait LabelMapping[S] {
  def labelsFor(s: S): Set[String]

  def stateFrom(labels: Set[String]): S
}
