package lib

import com.madgag.scalagithub.GitHub._
import com.madgag.scalagithub.commands.CreateComment
import com.madgag.scalagithub.model._
import com.typesafe.scalalogging.LazyLogging
import controllers.Api
import lib.labels.{Fail, Pass}
import play.api.mvc.Result
import play.api.mvc.Results.Ok
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._

object TestFeedback extends LazyLogging {
  implicit val checkpointSnapshoter = Api.checkpointSnapshoter
  private implicit val github = Bot.github

  def notifyGitHub(repoId: RepoId): Future[Result] =
    for {
      repo <- github.getRepo(repoId)
      repoSnaphot <- RepoSnapshot(repo)
      pr <- repoSnaphot.latestSeenPr
      masterStatus <- repo.combinedStatusFor(repo.default_branch)
    } yield {
      masterStatus.statuses.find(_.context.startsWith("continuous-integration/travis-ci")).foreach { testStatus =>
        pr.comments2.create(CreateComment(buildComment(testStatus.state, testStatus.target_url, repoSnaphot.latestSeenCheckpoint)))
        setTestResultLabels(pr, testStatus.state, repoSnaphot.latestSeenCheckpoint)
      }
      Ok
    }

  private def setTestResultLabels(pr: PullRequest, testResult: String, checkpoint: String) =
    if (testResult == "success") {
      val labelledState = new LabelledState(pr, _ == Fail.labelFor(checkpoint))
      labelledState.updateLabels(Set(Pass.labelFor(checkpoint)))
    }
    else {
      val labelledState = new LabelledState(pr, _ == Pass.labelFor(checkpoint))
      labelledState.updateLabels(Set(Fail.labelFor(checkpoint)))
    }


  private def buildComment(testResult: String, details: String, checkpoint: String): String =
    if (testResult == "success")
      s":white_check_mark: Testing in $checkpoint passed! [Details]($details)"
    else
      s":x: Testing in $checkpoint failed! [Details]($details)"
}
