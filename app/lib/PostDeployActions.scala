package lib

import com.madgag.scalagithub.GitHub._
import com.madgag.scalagithub.commands.CreateComment
import com.madgag.scalagithub.model._
import com.typesafe.scalalogging.LazyLogging
import lib.labels.{Fail, Pass}
import lib.travis.TravisCI.{buildIdFrom, extractStatusesOfCompletedTravisBuildsFrom}
import lib.travis.{TravisApi, TravisCI}
import play.api.libs.json.JsSuccess

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.util.Success

object PostDeployActions extends LazyLogging {
  implicit val github = Bot.github

  val TriggerdByProutMsg = "Triggered by Prout"

  def updateFor(repo: Repo, statuses: Seq[Status], seenPr: PullRequest, checkpoint: String): Future[Unit] =
    githubStatusForLatestCompletedBuildTriggeredByProut(repo, statuses).map { proutBuildStatusOpt =>
      proutBuildStatusOpt.foreach(status => setTestResult(seenPr, checkpoint, status))
    }

  def isCompletedProutBuild(repo: Repo, buildId: String): Future[Boolean] = TravisApi.clientFor(repo).build(buildId).map {
    _ match {
      case JsSuccess(buildResponse, _) =>
        buildResponse.commit.message == TriggerdByProutMsg && buildResponse.isCompleted
      case _ =>
        logger.error("Could not get build info from Travis")
        false // assume false for now
    }
  }

  def githubStatusForLatestCompletedBuildTriggeredByProut(repo: Repo, allStatuses: Seq[Status]): Future[Option[Status]] = {
    val githubStatusesOfCompletedTravisBuilds = extractStatusesOfCompletedTravisBuildsFrom(allStatuses)
    (Future.traverse(githubStatusesOfCompletedTravisBuilds) {
      githubStatus =>
        isCompletedProutBuild(repo, buildIdFrom(githubStatus)).map { proutBuild =>
          if (proutBuild) Some(githubStatus) else None
        }
    }).map { statusesFromCompletedBuildsTriggeredByProut =>
      val latestStatusForACompletedProutBuildOpt = statusesFromCompletedBuildsTriggeredByProut.flatten.sortBy(_.updated_at.toInstant).lastOption
      logger.info(s"repo=${repo.repoId.fullName} latest-completed-prout-triggered-build=${latestStatusForACompletedProutBuildOpt.map(_.target_url)} (${statusesFromCompletedBuildsTriggeredByProut.length}/${githubStatusesOfCompletedTravisBuilds.length}/${allStatuses.length})")
      latestStatusForACompletedProutBuildOpt
    }
  }

  val GitHubCompletionStates = Set("success", "failure")

  private def setTestResult(pr: PullRequest, checkpoint: String, status: Status) = {
    val (labelToSet, labelToRemove) =
      if (status.state == "success")
        (Pass.labelFor(checkpoint), Fail.labelFor(checkpoint))
      else
        (Fail.labelFor(checkpoint), Pass.labelFor(checkpoint))

    pr.labels.list().all().map(_.map(_.name).toSet).map { labels =>
      if (!labels.contains(labelToSet)) { // state has changed
        val labelledState = new LabelledState(pr, _ == labelToRemove)
        labelledState.updateLabels(Set(labelToSet))
        pr.comments2.create(CreateComment(buildComment(status.state, status.target_url, checkpoint))).andThen {
          case Success(_) => logger.info(s"Testing in production status set: repo=${pr.baseRepo.repoId.fullName} pr=${pr.number} status=$labelToSet")
        }
      }
    }
  }

  private def buildComment(testResult: String, details: String, checkpoint: String): String =
    if (testResult == "success")
      s":white_check_mark: Testing in $checkpoint passed! [Details]($details)"
    else
      s":x: Testing in $checkpoint failed! [Details]($details)"
}
