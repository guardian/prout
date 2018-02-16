package lib.travis

import java.net.URLEncoder

import com.madgag.okhttpscala._
import com.madgag.scalagithub.model.Repo
import com.netaporter.uri.Uri
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import lib.travis.TravisApi.BuildResponse.CompletedBuildStates
import lib.travis.TravisApi._
import okhttp3.Request.Builder
import okhttp3._
import play.api.libs.json.Json.{prettyPrint, toJson}
import play.api.libs.json._

import scala.collection.convert.decorateAsJava._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object OkHttp {
  val client = new OkHttpClient
}

object TravisApi {

  import play.api.Play.current

  private val config: Config = play.api.Play.configuration.underlying

  def clientFor(offering: TravisCIOffering): TravisApiClient = {
    val authToken = config.getString(s"travis.token.${offering.name}")
    TravisApiClient(offering, authToken)
  }

  def clientFor(repo: Repo): TravisApiClient = TravisApi.clientFor(TravisCIOffering.forRepo(repo))


  val DefaultJsonApiHeaders: Seq[(String, String)] = Seq(
    "User-Agent" -> "Travis access by Prout (https://github.com/guardian/prout)",
    "Content-Type" -> "application/json",
    "Travis-API-Version" -> "3"
  )

  case class Commit(sha: String, message: String)

  object Commit {
    implicit val readsCommit = Json.reads[Commit]
  }

  case class BuildResponse(id: Long, state: String, commit: Commit) {
    val isCompleted: Boolean = CompletedBuildStates.contains(state)
  }

  object BuildResponse {

    val CompletedBuildStates = Set("passed", "failed")

    implicit val readsBuildResponse = Json.reads[BuildResponse]
  }
}


case class TravisApiClient(offering: TravisCIOffering, authToken: String) extends LazyLogging {

  val baseEndpoint: Uri = offering.baseApiEndpoint

  val headers = Headers.of((DefaultJsonApiHeaders :+ "Authorization" -> s"token $authToken").toMap.asJava)

  def getJson[Resp](path: String)(implicit rResp: Reads[Resp]): Future[JsResult[Resp]] = readResponseFrom(
    new Builder().url(baseEndpoint + path)
    .headers(headers)
    .get()
    .build())

  def postJson[Req, Resp](path: String, reqBody: Req)(implicit wReq: Writes[Req], rResp: Reads[Resp]): Future[JsResult[Resp]] = {
    readResponseFrom(new Builder().url(baseEndpoint + path)
      .headers(headers)
      .post(RequestBody.create(JsonMediaType, toJson(reqBody).toString))
      .build())
  }

  def readResponseFrom[Resp](request: Request)(implicit rResp: Reads[Resp]): Future[JsResult[Resp]] = {
    val requestDescription = s"${request.method()} - ${request.url.encodedPath}"
    logger.info(requestDescription)
    OkHttp.client.execute(request) { resp =>
      val json = Json.parse(resp.body().byteStream())
      val jsResult = json.validate[Resp]
      if (jsResult.isError) {
        logger.error(s"$requestDescription got an invalid response ($jsResult):\n${prettyPrint(json)}")
      }
      jsResult
    }
  }

  def build(buildId: String) = getJson[BuildResponse](s"/build/$buildId")

  def requestBuild(repoId: String, travis: TravisCI, message: String, buildBranch: String) = {
    val bodyJson = Json.obj(
      "request" -> Json.obj(
        "message" -> message,
        "branch" -> buildBranch,
        "config" -> travis.config
      )
    )

    postJson[JsValue, JsValue](s"/repo/${URLEncoder.encode(repoId, "UTF-8")}/requests", bodyJson)
  } andThen { case respTry => logger.info(s"requestBuild on $repoId response=$respTry") }

}

sealed trait TravisCIOffering {
  val name: String
  val baseApiEndpoint: Uri
}

object TravisCIOffering {
  case object OpenSource extends TravisCIOffering {
    val name = "opensource"
    val baseApiEndpoint = Uri.parse("https://api.travis-ci.org")
  }

  case object Commercial extends TravisCIOffering {
    val name = "commercial"
    val baseApiEndpoint = Uri.parse("https://api.travis-ci.com")
  }

  def forRepo(repo: Repo): TravisCIOffering = if (repo.`private`) Commercial else OpenSource
}

