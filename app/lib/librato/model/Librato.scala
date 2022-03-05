package lib.librato.model

import java.time.Instant

import io.lemonlabs.uri.Url
import play.api.libs.json.{JsNumber, JsString, Json, Writes}

case class Link(
  rel: String,
  href: Url,
  label: Option[String] = None
)

case class Annotation(
  title: String,
  description: Option[String] = None,
  source: Option[String] = None,
  start_time: Option[Instant] = None,
  end_time: Option[Instant] = None,
  links: Seq[Link] = Seq.empty
)

object Annotation {
  implicit val writesUri = new Writes[Url] {
    def writes(uri: Url) = JsString(uri.toString)
  }

  implicit val writesLink = Json.writes[Link]

  implicit val writesInstant = new Writes[Instant] {
    def writes(instant: Instant) = JsNumber(instant.getEpochSecond)
  }

  implicit val writesAnnotation = Json.writes[Annotation]
}