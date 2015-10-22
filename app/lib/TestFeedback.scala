package lib

import org.kohsuke.github.{GHIssueComment, GHPullRequest}
import play.api.Logger
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._

object TestFeedback {
  /**
   * Notifies GitHub of the result of post-deployment Travis test by posting
   * the result as a comment on the original pull request.
   *
   * @param r Travis test result data
   * @return newly posted comment
   */
  def notify(r: TravisTestResult): Future[GHIssueComment] = {
    for {
      pr <- prByCommit(r)
      msg <- buildResultComment(r)
    } yield pr.comment(msg)
  }

  /* Finds the pull request corresponding to merge commit SHA */
  private def prByCommit(r: TravisTestResult): Future[GHPullRequest] = {
    // get parent commits of merge commit
    val parentCommitsF = Future[java.util.List[String]] {
      gitHub.getRepository(r.repoSlug).getCommit(r.commit).getParentSHA1s()
    }

    // merged + closed ensures its post-deployment test
    val searchResultsF = for {
      parentCommits <- parentCommitsF
    } yield {
        val parentCommit = parentCommits.last
        gitHub.searchIssues.isMerged.isClosed.q(parentCommit).list()
      }

    for {
      searchResults <- searchResultsF
    } yield {
      assert(searchResults.getTotalCount == 1)
      val pr = searchResults.asList().head
      assert(pr.isPullRequest)
      gitHub.getRepository(r.repoSlug).getPullRequest(pr.getNumber)
    }
  }

  /* Builds either failure or success comment */
  private def buildResultComment(r: TravisTestResult) = Future[String] {
    val detailsLink =
      s"[Details](https://travis-ci.org/${r.repoSlug}/builds/${r.buildId})"

    val comment = r.testResult match {
      case "0" => s":clap: Post-deployment testing passed! ${detailsLink}"
      case _ => s":cry: Post-deployment testing failed! ${detailsLink}"
    }
    comment
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
 */
case class TravisTestResult(repoSlug: String, commit: String,
                            testResult: String, buildId: String)
