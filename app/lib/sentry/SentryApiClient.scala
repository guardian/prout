package lib.sentry

import com.madgag.okhttpscala._
import io.lemonlabs.uri.Url
import com.typesafe.scalalogging.LazyLogging
import io.lemonlabs.uri.Url
import lib.sentry.model.CreateRelease
import okhttp3.Request.Builder
import okhttp3._
import play.api.Configuration
import play.api.libs.json.Json.{stringify, toJson}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SentryApiClient(credentials: SentryApiClient.Credentials) extends LazyLogging {

  val okHttpClient = new OkHttpClient

  val baseEndpoint = Url.parse("https://sentry.io/api/0/")

  val JsonMediaType = MediaType.parse("application/json")

  def createRelease(createReleaseCommand: CreateRelease): Future[_] = {

    val request = new Builder().url(s"$baseEndpoint/organizations/${credentials.org}/releases/")
      .header("Authorization", s"Bearer ${credentials.token}")
      .post(RequestBody.create(JsonMediaType, stringify(toJson(createReleaseCommand))))
      .build()

    val responseF = okHttpClient.execute(request)(resp => logger.info(resp.body().string()))
    responseF.onComplete {
      tr => logger.info("Response from Sentry: " + tr)
    }
    responseF
  }

}


object SentryApiClient {

  case class Credentials(token: String, org: String)

  def credentialsFor(config: Configuration): Option[Credentials] = for {
    org <- config.getOptional[String]("sentry.org")
    token <- config.getOptional[String]("sentry.token")
  } yield Credentials(token,org)

}
