package lib

import org.kohsuke.github.{GHIssueComment, GHPullRequest}
import scala.collection.convert.wrapAsScala._

object TestFeedback {
  /**
   * Notifies GitHub of the result of post-deployment Travis test by posting
   * the result as a comment on the original pull request.
   *
   * @param r Travis test result data
   * @return newly posted comment
   */
  def notify(r: TravisTestResult): GHIssueComment = {
    prByCommit(r).comment(buildResultComment(r))
  }

  /* Finds the pull request corresponding to merge commit SHA */
  private def prByCommit(r: TravisTestResult): GHPullRequest = {
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
  private def buildResultComment(r: TravisTestResult) = {
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
