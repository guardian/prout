package lib.okgithub

import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.squareup.okhttp.{Request, OkHttpClient}
import lib.RepoFullName
import lib.okhttpscala._
import play.api.libs.json.Json
import scala.concurrent.Future


/*
state	string	Either open, closed, or all to filter by state. Default: open
head	string	Filter pulls by head user and branch name in the format of user:ref-name. Example: github:new-script-format.
base	string	Filter pulls by base branch name. Example: gh-pages.
sort	string	What to sort results by. Can be either created, updated, popularity (comment count) or long-running (age, filtering by pulls updated in the last month). Default: created
direction	string
 */

case class PRListParams(
  state: Option[String],
  head: Option[String],
  base: Option[String],
  sort: Option[String],
  direction: Option[String]
)

class GitHub(client: OkHttpClient) {

  def listPullRequests(repo: RepoFullName, params: PRListParams): Future[GHResponse[Seq[PullRequest]]] = {

    val u = Uri.parse(s"https://api.github.com/repos/$repo/pulls")

    Json.toJson(params)

    val req = new Request.Builder().url(u).build()

    client.execute(req).map {
      resp =>
        val jsValue = Json.parse(resp.body().bytes())
        val jsResult = Json.fromJson[Seq[PullRequest]](jsValue)
        new GHResponse(jsResult.get)
    }
  }
}
