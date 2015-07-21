package lib.travis

import play.api.libs.json.{JsObject, Json}

case class TravisCI(config: JsObject)

object TravisCI {
  implicit val readsTravisCI = Json.reads[TravisCI]
}