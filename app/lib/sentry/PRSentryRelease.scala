package lib.sentry

import org.eclipse.jgit.lib.ObjectId


case class PRSentryRelease(mergeCommit: ObjectId, projects: Seq[String]) {
  val version = mergeCommit.name
}

