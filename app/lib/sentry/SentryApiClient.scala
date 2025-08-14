package lib.sentry

import com.madgag.okhttpscala._
import io.lemonlabs.uri.Uri
import com.typesafe.scalalogging.LazyLogging
import lib.librato.LibratoApiClient
import lib.sentry.model.CreateRelease
import okhttp3.Request.Builder
import okhttp3._
import play.api.Configuration
import play.api.libs.json.Json.{stringify, toJson}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SentryApiClient(token: String , val org: String) extends LazyLogging {

  val okHttpClient = new OkHttpClient

  val baseEndpoint = Uri.parse("https://sentry.io/api/0/")

  val JsonMediaType = MediaType.parse("application/json")

  def createRelease(createReleaseCommand: CreateRelease): Future[_] = {

    val request = new Builder().url(s"$baseEndpoint/organizations/$org/releases/")
      .header("Authorization", s"Bearer $token")
      .post(RequestBody.create(stringify(toJson(createReleaseCommand)), JsonMediaType))
      .build()

    val responseF = okHttpClient.execute(request)(resp => logger.info(resp.body().string()))
    responseF.onComplete {
      tr => logger.info("Response from Sentry: " + tr)
    }
    responseF
  }

}

object SentryApiClient {
  def instanceOptFrom(config: Configuration): Option[SentryApiClient] = for {
    org <- config.getOptional[String]("sentry.org")
    token <- config.getOptional[String]("sentry.token")
  } yield new SentryApiClient(token,org)
}
