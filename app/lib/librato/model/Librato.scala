package lib.librato.model

import java.time.Instant
import io.lemonlabs.uri.Uri
import play.api.libs.json.{JsNumber, JsString, Json, OWrites, Writes}

case class Link(
  rel: String,
  href: Uri,
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
  implicit val writesUri: Writes[Uri] = new Writes[Uri] {
    def writes(uri: Uri) = JsString(uri.toString)
  }

  implicit val writesLink: OWrites[Link] = Json.writes[Link]

  implicit val writesInstant: Writes[Instant] = new Writes[Instant] {
    def writes(instant: Instant) = JsNumber(instant.getEpochSecond)
  }

  implicit val writesAnnotation: OWrites[Annotation] = Json.writes[Annotation]
}