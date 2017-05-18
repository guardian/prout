package lib.sentry

import org.eclipse.jgit.lib.ObjectId


case class PRSentryRelease(mergeCommit: ObjectId, projects: Seq[String]) {
  val version = mergeCommit.name

  def detailsMarkdown(org: String) = s"#### Sentry Release: ${projectsMarkdown(org)}"

  def projectsMarkdown(org: String): String =
    projects.map(project => s"[$project](${releasePageUrl(org, project)})").mkString(", ")

  def releasePageUrl(org: String, project: String)= s"https://sentry.io/$org/$project/releases/$version/"
  
}

