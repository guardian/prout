package lib

import com.netaporter.uri.Uri

object Site {
  def from(url: Uri, label: Option[String]): Site = Site(url, label.getOrElse(url.host.get))
}

case class Site(url: Uri, label: String)
