package lib

sealed trait PullRequestDeploymentStatus {
  def labelFor(site: Site) = getClass.getSimpleName.dropRight(1) + "-on-" + site.label
}

object PullRequestDeploymentStatus {
  val all = Set[PullRequestDeploymentStatus](Seen, Pending, Overdue)

  def fromLabels(labels: Set[String], site: Site): Option[PullRequestDeploymentStatus] =
    PullRequestDeploymentStatus.all.find(s => labels(s.labelFor(site)))
}

sealed trait NotSeenOnSite extends PullRequestDeploymentStatus

case object Seen extends PullRequestDeploymentStatus

case object Pending extends NotSeenOnSite

case object Overdue extends NotSeenOnSite
