package lib

import com.madgag.scalagithub.GitHub._
import com.madgag.scalagithub.commands.CreateComment
import com.madgag.scalagithub.model._
import com.netaporter.uri.Uri
import com.typesafe.scalalogging.LazyLogging
import lib.labels.{Fail, Pass}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.util.Success

object TestingInProduction extends LazyLogging {
  implicit val github = Bot.github

  def updateFor(masterStatus: CombinedStatus, seenPr: PullRequest, checkpoint: String): Future[Unit] =
    buildTriggeredByProut(masterStatus).map { proutBuildStatusOpt =>
      proutBuildStatusOpt.foreach(status => setTestResult(seenPr, checkpoint, status))
    }

  private def buildTriggeredByProut(masterStatus: CombinedStatus): Future[Option[CombinedStatus.Status]] =
    completedTravisBuildStatus(masterStatus) match {
      case Some(buildStatus) =>
        val buildId = Uri.parse(buildStatus.target_url).pathParts.last.part
        RepoSnapshot.travisApiClient.completedProutBuild(buildId).map { proutBuild =>
          if (proutBuild)
            Some(buildStatus)
          else
            None
        }

      case None => Future.successful(None)
    }

  private def completedTravisBuildStatus(masterStatus: CombinedStatus) =
    masterStatus.statuses.find(status => status.context.startsWith("continuous-integration/travis-ci") && Set("success", "failure").contains(status.state))

  private def setTestResult(pr: PullRequest, checkpoint: String, status: CombinedStatus.Status) = {
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
