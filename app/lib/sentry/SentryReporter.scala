package lib.sentry

import com.madgag.git._
import com.madgag.scalagithub.model.PullRequest
import sttp.model.*
import lib.sentry.model.CreateRelease
import lib.{PullRequestCheckpointsStateChangeSummary, RepoLevelDetails, RepoSnapshot, UpdateReporter}
import org.eclipse.jgit.revwalk.RevWalk
import play.api.Logging

import scala.concurrent.Future

class SentryReporter(
  sentry: SentryApiClient
) extends UpdateReporter with Logging {
  override def report(repoSnapshot: RepoSnapshot, pr: PullRequest, checkpointsChangeSummary: PullRequestCheckpointsStateChangeSummary): Unit = {
    if (checkpointsChangeSummary.newlyMerged) {
      logger.info(s"action taking: ${pr.prId} is newly merged")
      val repoLevelDetails = repoSnapshot.repoLevelDetails

      for {
        sentryRelease <- sentryReleaseOption(repoSnapshot, pr)
      } {
        val ref = lib.sentry.model.Ref(
          repoLevelDetails.repo.repoId,
          sentryRelease.mergeCommit,
          sentryRelease.mergeCommit.asRevCommit(new RevWalk(repoLevelDetails.gitRepo.getObjectDatabase.threadLocalResources.reader())).getParents.headOption)

        logger.info(s"${pr.prId.slug} : ref=$ref")

        sentry.createRelease(CreateRelease(
          sentryRelease.version,
          Some(sentryRelease.version),
          Some(Uri.unsafeParse(pr.html_url)),
          sentryRelease.projects,
          refs=Seq(ref)
        ))
      }
    }
  }

  private def sentryReleaseOption(repoSnapshot: RepoSnapshot, pr: PullRequest): Option[PRSentryRelease] = {
    val sentryProjects = for {
      configs <- repoSnapshot.activeConfByPullRequest.get(pr).toSeq
      config <- configs
      sentryConf <- config.sentry.toSeq
      sentryProject <- sentryConf.projects
    } yield sentryProject

    val sentryReleaseOpt = for {
      mergeCommit <- pr.merge_commit_sha if sentryProjects.nonEmpty
    } yield PRSentryRelease(mergeCommit, sentryProjects)
    sentryReleaseOpt
  }
}
