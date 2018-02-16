package lib.travis

import com.madgag.scalagithub.model.Status
import com.netaporter.uri.Uri
import com.typesafe.scalalogging.LazyLogging
import lib.PostDeployActions.GitHubCompletionStates
import play.api.libs.json.{JsObject, Json}

case class TravisCI(config: JsObject)

object TravisCI extends LazyLogging {
  implicit val readsTravisCI = Json.reads[TravisCI]

  def buildIdFrom(buildStatus: Status) = Uri.parse(buildStatus.target_url).pathParts.last.part

  def extractStatusesOfCompletedTravisBuildsFrom(statuses: Seq[Status]): Seq[Status] = statuses.filter {
    status => status.context.startsWith("continuous-integration/travis-ci") && GitHubCompletionStates.contains(status.state)
  }

}