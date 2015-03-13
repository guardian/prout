package lib.okgithub

import com.netaporter.uri.Uri
import play.api.libs.json.Json.reads


case class PullRequest(
  id: Int,
  url: Uri,
  html_url: Uri
) {

}

object PullRequest {
  val readsPullRequest = reads[PullRequest]
}