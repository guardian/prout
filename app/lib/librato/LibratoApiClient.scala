package lib.librato

import com.madgag.okhttpscala._
import com.netaporter.uri.Uri
import com.typesafe.scalalogging.LazyLogging
import lib.librato.model.Annotation
import okhttp3.Request.Builder
import okhttp3._
import play.api.libs.json.Json.{stringify, toJson}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class LibratoApiClient(username: String, token: String) extends LazyLogging {

  val okHttpClient = new OkHttpClient

  val baseEndpoint = Uri.parse("https://metrics-api.librato.com/v1")

  val JsonMediaType = MediaType.parse("application/json")

  def createAnnotation(name: String, annotation: Annotation): Future[_] = {

    val request = new Builder().url(s"$baseEndpoint/annotations/$name")
      .header("Authorization", Credentials.basic(username, token))
      .post(RequestBody.create(JsonMediaType, stringify(toJson(annotation))))
      .build()

    val responseF = okHttpClient.execute(request)(resp => logger.info(resp.body().string()))
    responseF.onComplete {
      tr => logger.info("Response from Librato: " + tr)
    }
    responseF
  }

}


object LibratoApiClient {

  import play.api.Play.current
  val config = play.api.Play.configuration

  val instanceOpt = for {
    userId <- config.getString("librato.userId")
    token <- config.getString("librato.token")
  } yield new LibratoApiClient(userId,token)

}
