package lib.sentry

import cats.effect.IO
import cats.effect.kernel.Resource
import com.typesafe.scalalogging.LazyLogging
import lib.sentry.SentryApiClient.{JsonMediaType, baseEndpoint}
import lib.sentry.model.CreateRelease
import play.api.Configuration
import play.api.libs.json.Json.{stringify, toJson}
import sttp.client4.*
import sttp.client4.httpclient.cats.HttpClientCatsBackend
import sttp.model.*
import sttp.model.Header.contentType
import sttp.model.MediaType.ApplicationJson

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class SentryApiClient(token: String , val org: String) extends LazyLogging {

  val client: Resource[IO, WebSocketBackend[IO]] = HttpClientCatsBackend.resource[IO]()

  def createRelease(createReleaseCommand: CreateRelease): IO[_] = {
    client.use(quickRequest.auth.bearer(token)
      .headers(JsonMediaType).body(stringify(toJson(createReleaseCommand)))
      .post(baseEndpoint.addPath("organizations", org, "releases")).send)
  }

}

object SentryApiClient {
  val baseEndpoint: Uri = uri"https://sentry.io/api/0/"

  val JsonMediaType: Header = contentType(ApplicationJson)
  
  def instanceOptFrom(config: Configuration): Option[SentryApiClient] = for {
    org <- config.getOptional[String]("sentry.org")
    token <- config.getOptional[String]("sentry.token")
  } yield new SentryApiClient(token,org)
}
