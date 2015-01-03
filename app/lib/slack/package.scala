package lib

import play.api.libs.json.Json

package object slack {

  case class Attachment(fallback: String, fields: Seq[Attachment.Field])

  object Attachment {
    case class Field(title: String, value: String, short: Boolean)

    implicit val writesField = Json.writes[Field]
    implicit val writesAttachment = Json.writes[Attachment]
  }

  case class Message(text: String, username: Option[String], icon_url: Option[String], attachments: Seq[Attachment])

  implicit val writesMessage = Json.writes[Message]
}
