package lib

import com.madgag.git._
import com.madgag.scalagithub.GitHub._
import com.madgag.scalagithub.commands.CreateComment
import com.madgag.scalagithub.model._
import lib.RepoSnapshot.mergedPullRequestsFor
import lib.labels.{Fail, Pass, Seen}
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.Result
import play.api.mvc.Results.Ok

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scalax.file.ImplicitConversions._
import scalax.file.Path

case class TestFeedback(r: TravisTestResult) {

  /** Post a comment and set labels of post-deployment Travis CI test result
    * on the last released PR. */
  def notifyGitHub(): Future[Result] = {
    for {
      pr <- latestReleasedPullRequestF
      comment <- postTestResultComment(pr)
      labelsF <- setTestResultLabels(pr)
      label <- labelsF
    } yield {
      Logger.info(
        s"Test result posted on PR:  ${pr.number}, ${pr.title}, ${pr.merged_at}")

      val resultJson = Json.parse(s"""
      {
        "pr_num" : "${pr.number}",
        "test_result" : "${r.testResult}"
      }
      """)

      Ok(resultJson)
    }
  }

  private implicit val github = Bot.github

  private val githubRepo =
    Await.result(github.getRepo(RepoId.from(r.repoSlug)), 30.seconds)

  // Released PR = closed + merged + seen
  private def latestReleasedPullRequestF: Future[PullRequest] = {

    def isSeen(pr: PullRequest): Future[Boolean] = {
      for {
        labels <- pr.labels.list().all()
      } yield {
        labels.exists(l => l.name == Seen.labelFor(checkpoint)) // i.e., has Seen-on-PROD label
      }
    }

    // merged_at is always defined for merged PRs, so it is ok to call 'get' on an Option here
    implicit def timeOfMergeDescendingOrdering: Ordering[PullRequest] =
      Ordering.fromLessThan(_.merged_at.get isAfter _.merged_at.get)

    for {
      mergedPRs <- mergedPullRequestsFor(githubRepo)
      seenPRs <- Future.traverse(mergedPRs){ pr => isSeen(pr).map(b => pr -> b) }.map(_.collect{ case (pr, true) => pr })
    } yield {
      seenPRs.sorted.head // last released PR
    }
  }

  private def postTestResultComment(pr: PullRequest) =
    pr.comments2.create(CreateComment(s"$resultComment"))

  private def setTestResultLabels(pr: PullRequest) = {

    for {
      labels <- pr.labels.list().all()
    } yield {

      val currentLabels = labels.map(_.name)

      val updatedLabels = r.testResult match {
        case "0" => { // test passed
          val allLabelsButFail = currentLabels.filterNot(l => l == Fail.labelFor(checkpoint))
          allLabelsButFail :+ Pass.labelFor(checkpoint)
        }
        case _ => { // test failed
          val allLabelsButPass = currentLabels.filterNot(l => l == Pass.labelFor(checkpoint))
          allLabelsButPass :+ Fail.labelFor(checkpoint)
        }
      }

      pr.labels.replace(updatedLabels)
    }

  }

  /* Builds either failure or success comment */
  private def resultComment: String = {
    val detailsLink =
      s"[Details](https://travis-ci.org/${r.repoSlug}/builds/${r.buildId})"

    val screencastLink = s"[Screencast](https://saucelabs.com/tests/${r.screencastId})"

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

    r.testResult match {
      case "0" => testsPassedMsg
      case _ => testsFailedMsg
    }
  }


  /* Checkpoint (stage) from .prout.json */
  private def checkpoint: String = {

    val gitRepo = RepoUtil.getGitRepo(
      Bot.parentWorkDir / Path.fromString(r.repoSlug),
      githubRepo.clone_url,
      Some(Bot.githubCredentials.git))

    implicit val repoThreadLocal = gitRepo.getObjectDatabase.threadLocalResources

    lazy val masterCommit:RevCommit =
      gitRepo.resolve(githubRepo.default_branch).asRevCommit(new RevWalk(repoThreadLocal.reader()))


    lazy val config = ConfigFinder.config(masterCommit)

    config.checkpointsByName.keySet.head
  }
}

/**
 * Post-deployment test result data from Travis CI
 *
 * @param repoSlug owner/repo
 * @param commit SHA
 * @param testResult build result
 * @param buildId build ID
 * @param screencastId Remote Web Driver session ID
 */
case class TravisTestResult(
    repoSlug: String,
    commit: String,
    testResult: String,
    buildId: String,
    screencastId: String)
