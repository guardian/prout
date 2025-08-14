package lib.librato

import com.madgag.okhttpscala._
import io.lemonlabs.uri.Uri
import com.typesafe.scalalogging.LazyLogging
import lib.librato.model.Annotation
import okhttp3.Request.Builder
import okhttp3._
import play.api.Configuration
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
      .post(RequestBody.create(stringify(toJson(annotation)), JsonMediaType))
      .build()

    val responseF = okHttpClient.execute(request)(resp => logger.info(resp.body().string()))
    responseF.onComplete {
      tr => logger.info("Response from Librato: " + tr)
    }
    responseF
  }

}


object LibratoApiClient {

  def instanceOptFrom(config: Configuration): Option[LibratoApiClient] = for {
    userId <- config.getOptional[String]("librato.userId")
    token <- config.getOptional[String]("librato.token")
  } yield new LibratoApiClient(userId,token)

}
