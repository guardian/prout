package lib.actions

import com.madgag.scalagithub.model.RepoId
import play.api.libs.json.JsValue

object Parsers {

  def parseGitHubHookJson(jsValue: JsValue): RepoId =
    (jsValue \ "repository" \ "full_name").validate[String].map(RepoId.from).get

}
