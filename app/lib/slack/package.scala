package lib

import play.api.libs.json.{Json, OWrites}

package object slack {

  case class Attachment(fallback: String, fields: Seq[Attachment.Field])

  object Attachment {
    case class Field(title: String, value: String, short: Boolean)

    implicit val writesField: OWrites[Field] = Json.writes[Field]
    implicit val writesAttachment: OWrites[Attachment] = Json.writes[Attachment]
  }

  case class Message(text: String, username: Option[String], icon_url: Option[String], attachments: Seq[Attachment])

  implicit val writesMessage: OWrites[Message] = Json.writes[Message]
}
