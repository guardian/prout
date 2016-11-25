package lib

import com.madgag.scalagithub.GitHub._
import com.madgag.scalagithub.commands.CreateComment
import com.madgag.scalagithub.model._
import com.typesafe.scalalogging.LazyLogging
import controllers.Api
import lib.labels.{Fail, Pass}
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results.Ok
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._

case class TestResult(slug: String, status: String, build: String, screencast: String, checkpoint: String)

object TestResult {
  implicit val travisTestResultReads = Json.reads[TestResult]
}

object TestFeedback extends LazyLogging {
  implicit val checkpointSnapshoter = Api.checkpointSnapshoter
  private implicit val github = Bot.github

  def notifyGitHub(result: TestResult): Future[Result] =
    for {
      repo <- github.getRepo(RepoId.from(result.slug))
      repoSnaphot <- RepoSnapshot(repo)
      pr <- repoSnaphot.latestSeenPrByCheckpoint(result.checkpoint)
      comment = buildComment(result)
      _ <- pr.comments2.create(CreateComment(comment))
      _ <- setTestResultLabels(pr, result)
    } yield {
      logger.info(s"Test result posted on PR:  ${pr.number}, ${pr.title}, ${pr.merged_at}")
      Ok(s"${pr.number}")
    }

  private def setTestResultLabels(pr: PullRequest, result: TestResult) = Future {
    val checkpoint = result.checkpoint

    if (testPassed(result)) {
      val labelledState = new LabelledState(pr, _ == Fail.labelFor(checkpoint))
      labelledState.updateLabels(Set(Pass.labelFor(checkpoint)))
    }
    else {
      val labelledState = new LabelledState(pr, _ == Pass.labelFor(checkpoint))
      labelledState.updateLabels(Set(Fail.labelFor(checkpoint)))
    }
  }

  private def buildComment(result: TestResult): String = {
    val detailsLink = s"[Details](https://travis-ci.org/${result.slug}/builds/${result.build})"

    val screencastLink = s"[Screencast](https://saucelabs.com/tests/${result.screencast})"

    val testsPassedMsg =
      s"""
        | :white_check_mark: Post-deployment testing passed! | ${screencastLink} | ${detailsLink}
        | -------------------------------------------------- | ----------------- | --------------
      """.stripMargin

    val testsFailedMsg =
      s"""
         | :x: Post-deployment testing failed! | ${screencastLink} | ${detailsLink}
         | ----------------------------------- | ----------------- | --------------
      """.stripMargin

    if (testPassed(result))
      testsPassedMsg
    else
      testsFailedMsg
  }

  private def testPassed(result: TestResult): Boolean =
    result.status match {
      case "0" => true
      case _ => false
    }
}
