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
    if (result.status == "0") {
      val labelledState = new LabelledState(pr, _ == Fail.labelFor(result.checkpoint))
      labelledState.updateLabels(Set(Pass.labelFor(result.checkpoint)))
    }
    else {
      val labelledState = new LabelledState(pr, _ == Pass.labelFor(result.checkpoint))
      labelledState.updateLabels(Set(Fail.labelFor(result.checkpoint)))
    }
  }

  private def buildComment(result: TestResult): String =
    if (result.status == "0")
      s"""
         | :white_check_mark: Post-deployment testing passed! | ${s"[Screencast](https://saucelabs.com/tests/${result.screencast})"} | ${s"[Details](https://travis-ci.org/${result.slug}/builds/${result.build})"}
         | -------------------------------------------------- | ----------------- | --------------
      """.stripMargin
    else
      s"""
         | :x: Post-deployment testing failed! | ${s"[Screencast](https://saucelabs.com/tests/${result.screencast})"} | ${s"[Details](https://travis-ci.org/${result.slug}/builds/${result.build})"}
         | ----------------------------------- | ----------------- | --------------
      """.stripMargin
}
