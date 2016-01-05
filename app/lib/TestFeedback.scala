package lib

import org.eclipse.jgit.revwalk.RevCommit
import org.kohsuke.github.{GHIssueComment, GHPullRequest}
import scala.collection.convert.wrapAsScala._
import scalax.file.ImplicitConversions._
import scalax.file.Path
import com.madgag.git._

/** @param r Travis test result data */
case class TestFeedback(r: TravisTestResult) {

  /**
    * Notifies GitHub of the result of post-deployment Travis test by posting
    * the result as a comment on the original pull request, and setting test
    * result label.
    *
    * @return newly posted comment
    */
  def notifyGitHub: GHIssueComment = {
    val pr = prByCommit
    pr.setLabels(resultLabels: _*)
    pr.comment(resultComment)
  }

  /* Finds the pull request corresponding to merge commit SHA */
  private def prByCommit: GHPullRequest = {
    // get parent commits of merge commit
    val parentCommits = gitHub.getRepository(r.repoSlug).getCommit(r.commit).getParentSHA1s

    // merged + closed ensures its post-deployment test
    val searchResults = gitHub.searchIssues.isMerged.isClosed.q(parentCommits.last).list()
    assert(searchResults.getTotalCount == 1)
    val pr = searchResults.asList().head
    assert(pr.isPullRequest)
    gitHub.getRepository(r.repoSlug).getPullRequest(pr.getNumber)
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

  private def resultLabels: Seq[String] = {
    val labels = prByCommit.getLabels.toList.map(label => label.getName)

    r.testResult match {
      case "0" => {
        val allLabelsButFail = labels.filterNot(l => l == Fail.labelFor(checkpoint))
        (Pass.labelFor(checkpoint) :: allLabelsButFail).toSeq
      }
      case _ => {
        val allLabelsButPass = labels.filterNot(l => l == Pass.labelFor(checkpoint))
        (Fail.labelFor(checkpoint) :: allLabelsButPass).toSeq
      }
    }
  }

  /* Checkpoint (stage) from .prout.json */
  private def checkpoint: String = {
    val gitRepo = RepoUtil.getGitRepo(
      Bot.parentWorkDir / Path.fromString(r.repoSlug),
      gitHub.getRepository(r.repoSlug).gitHttpTransportUrl,
      Some(Bot.githubCredentials.git))

    implicit val (revWalk, reader) = gitRepo.singleThreadedReaderTuple

    lazy val masterCommit:RevCommit =
      gitRepo.resolve(gitHub.getRepository(r.repoSlug).getDefaultBranch).asRevCommit

    lazy val config = ConfigFinder.config(masterCommit)

    config.checkpointsByName.keySet.head
  }

  private val gitHub = Bot.githubCredentials.conn()
}

/**
 * Post-deployment test result data from Travis
 *
 * @param repoSlug owner/repo
 * @param commit SHA
 * @param testResult build result
 * @param buildId build ID
 * @param screencastId Remote Web Driver session ID
 */
case class TravisTestResult(repoSlug: String, commit: String,
                            testResult: String, buildId: String,
                            screencastId: String)
