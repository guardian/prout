package lib.sentry

import com.madgag.okhttpscala._
import io.lemonlabs.uri.Url
import com.typesafe.scalalogging.LazyLogging
import lib.sentry.model.CreateRelease
import okhttp3.Request.Builder
import okhttp3._
import play.api.libs.json.Json.{stringify, toJson}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SentryApiClient(token: String , val org: String) extends LazyLogging {

  val okHttpClient = new OkHttpClient

  val baseEndpoint = Url.parse("https://sentry.io/api/0/")

  val JsonMediaType = MediaType.parse("application/json")

  def createRelease(createReleaseCommand: CreateRelease): Future[_] = {

    val request = new Builder().url(s"$baseEndpoint/organizations/$org/releases/")
      .header("Authorization", s"Bearer $token")
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

  import play.api.Play.current
  val config = play.api.Play.configuration

  lazy val instanceOpt = for {
    org <- config.getString("sentry.org")
    token <- config.getString("sentry.token")
  } yield new SentryApiClient(token,org)

}
