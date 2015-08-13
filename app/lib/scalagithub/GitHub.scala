package lib.scalagithub

import java.time.{Instant, ZonedDateTime}
import java.util.concurrent.TimeUnit

import com.madgag.github.RepoId
import com.madgag.okhttpscala._
import com.squareup.okhttp.Request.Builder
import com.squareup.okhttp.{CacheControl, HttpUrl, OkHttpClient, Response}
import lib.gitgithub.GitHubCredentials
import play.api.http.Status._
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

case class CommitPointer(
  ref: String
)

object CommitPointer {
  implicit val readsCommitPointer = Json.reads[CommitPointer]
}

case class PullRequest(
  number: Int,
  merged_at: Option[ZonedDateTime],
  head: CommitPointer,
  base: CommitPointer
)

object PullRequest {
  implicit val readsPullRequest = Json.reads[PullRequest]
}

object RateLimit {
  case class Status(
    remaining: Int,
    reset: Instant
  )
}

case class RateLimit(
  consumed: Int,
  status: RateLimit.Status
)

case class GitHubResponse[Result](
  rateLimit: RateLimit,
  result: Result
)

class GitHub(ghCredentials: GitHubCredentials) {
  import PullRequest._

  def listPullRequests(repoId: RepoId)(implicit ec: ExecutionContext): Future[GitHubResponse[Seq[PullRequest]]] = {
    // https://api.github.com/repos/guardian/subscriptions-frontend/pulls?state=closed&sort=updated&direction=desc
    val url = new HttpUrl.Builder().scheme("https").host("api.github.com")
      .addPathSegment(s"repos")
      .addPathSegment(repoId.owner)
      .addPathSegment(repoId.name)
      .addPathSegment(s"pulls")
      .addQueryParameter("state", "closed")
      .addQueryParameter("sort", "updated")
      .addQueryParameter("direction", "desc")
      .build()

    val request = new Builder().url(url)
      .cacheControl(new CacheControl.Builder()
      .maxAge(0, TimeUnit.SECONDS)
      .build())
      .addHeader("Authorization", s"token ${ghCredentials.oauthAccessToken}").build()
    for {
      response <- ghCredentials.okHttpClient.execute(request)
    } yield {
      val json = Json.parse(response.body().byteStream())
      val result = json.validate[Seq[PullRequest]]

      val networkResponse = response.networkResponse()
      val consumedRateLimit = if (networkResponse.code == NOT_MODIFIED) 0 else 1
      val rateLimit = RateLimit(
        consumedRateLimit,
        rateLimitStatusFrom(networkResponse)
      )
      GitHubResponse(rateLimit, result.get)
    }
  }

  def rateLimitStatusFrom(response: Response) = RateLimit.Status(
    response.header("X-RateLimit-Remaining").toInt,
    Instant.ofEpochSecond(response.header("X-RateLimit-Reset").toLong)
  )
}
