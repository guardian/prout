package lib.travis

import java.net.URLEncoder
import akka.agent.Agent
import com.madgag.okhttpscala._
import com.madgag.scalagithub.model.Repo
import com.netaporter.uri.Uri
import com.typesafe.scalalogging.LazyLogging
import okhttp3.Request.Builder
import okhttp3._
import play.api.libs.json.Json.toJson
import play.api.libs.json._
import scala.collection.convert.decorateAsJava._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object OkHttp {
  val client = new OkHttpClient
}

sealed trait TravisApi extends LazyLogging {

  val baseEndpoint: Uri

  val JsonMediaType = MediaType.parse("application/json; charset=utf-8")

  val DefaultJsonApiHeaders: Seq[(String, String)] = Seq(
    "User-Agent" -> "Travis access by Prout (https://github.com/guardian/prout)",
    "Content-Type" -> "application/json",
    "Accept" -> "application/vnd.travis-ci.2+json"
  )

  def getJson[Resp](path: String, extraHeaders: Seq[(String, String)])(implicit rResp: Reads[Resp]): Future[JsResult[Resp]] = readResponseFrom(
    new Builder().url(baseEndpoint + path)
      .headers(headersContainer(DefaultJsonApiHeaders))
      .get()
      .build())

  def postJson[Req, Resp](path: String, reqBody: Req, extraHeaders: Seq[(String, String)] = Seq.empty)(implicit wReq: Writes[Req], rResp: Reads[Resp]): Future[JsResult[Resp]] = {
    readResponseFrom(new Builder().url(baseEndpoint + path)
      .headers(headersContainer(DefaultJsonApiHeaders ++ extraHeaders))
      .post(RequestBody.create(JsonMediaType, toJson(reqBody).toString))
      .build())
  }

  def headersContainer(headers: Seq[(String, String)]) = Headers.of(headers.toMap.asJava)

  def readResponseFrom[Resp](request: Request)(implicit rResp: Reads[Resp]): Future[JsResult[Resp]] = {
    logger.info(s"${request.method()} - ${request.url.encodedPath}")
    OkHttp.client.execute(request) { resp => Json.parse(resp.body().byteStream()).validate[Resp] }
  }

  case class AuthRequest(github_token: String)

  object AuthRequest {
    implicit val writesAuthRequest = Json.writes[AuthRequest]
  }

  case class AuthResponse(access_token: String)

  case class Build(id: Int, config: JsValue, state: String)

  object Build {
    implicit val readsBuild = Json.reads[Build]
  }

  case class Commit(sha: String, message: String)

  object Commit {
    implicit val readsCommit = Json.reads[Commit]
  }

  case class BuildResponse(build: Build, commit: Commit)

  object BuildResponse {
    implicit val readsBuildResponse = Json.reads[BuildResponse]
  }

  object AuthResponse {
    implicit val readsAuthRequest = Json.reads[AuthResponse]
  }

  def auth(githubToken: String) = postJson[AuthRequest, AuthResponse]("/auth/github", AuthRequest(githubToken))

  def build(buildId: String, authTokenSupplier: AuthTokenSupplier[String]) = for {
    authToken <- authTokenSupplier.get()
    response <- getJson[BuildResponse](
      s"/builds/$buildId",
      Seq(
        "Authorization" -> s"token $authToken",
        "Travis-API-Version" -> "3"
      )
    )
  } yield response

  def requestBuild(repoId: String, travis: TravisCI, message: String, buildBranch: String, authTokenSupplier: AuthTokenSupplier[String]) = {
    val bodyJson = Json.obj(
      "request" -> Json.obj(
        "message" -> message,
        "branch" -> buildBranch,
        "config" -> travis.config
      )
    )

    for {
      authToken <- authTokenSupplier.get()
      response <- postJson[JsValue, JsValue](
        s"/repo/${URLEncoder.encode(repoId, "UTF-8")}/requests",
        bodyJson,
        Seq(
          "Authorization" -> s"token $authToken",
          "Travis-API-Version" -> "3"
        )
      )
    } yield response
  } andThen { case respTry => logger.info(s"requestBuild on $repoId response=$respTry") }

  def authSupplier(githubToken: String) = new AuthTokenSupplier[String](auth(githubToken).map(_.get.access_token))

}

case object TravisCiEnvironment extends TravisApi {
  override val baseEndpoint = Uri.parse("https://api.travis-ci.org")
}

case object TravisProEnvironment extends TravisApi {
  override val baseEndpoint = Uri.parse("https://api.travis-ci.com")
}

class TravisApiClient(githubToken: String) extends LazyLogging {

  val ciAuth = TravisCiEnvironment.authSupplier(githubToken)
  val proAuth = TravisProEnvironment.authSupplier(githubToken)

  case class TravisEnvironment(travisApi: TravisApi, authTokenSupplier: AuthTokenSupplier[String])

  def chooseEnvironment(repo: Repo): TravisEnvironment = {
    if (repo.`private`) {
      TravisEnvironment(TravisProEnvironment, proAuth)
    } else {
      TravisEnvironment(TravisCiEnvironment, ciAuth)
    }
  }

  def build(repo: Repo, buildId: String) = {
    val environment: TravisEnvironment = chooseEnvironment(repo)
    environment.travisApi.build(buildId, environment.authTokenSupplier)
  }

  def requestBuild(repo: Repo, travis: TravisCI, message: String) = {
    val environment: TravisEnvironment = chooseEnvironment(repo)
    environment.travisApi.requestBuild(repo.full_name, travis, message, repo.default_branch, environment.authTokenSupplier)
  }

}

class AuthTokenSupplier[T](generateToken: => Future[T]) {

  private val agent: Agent[Future[T]] = Agent(generateToken)

  /**
    * If no refresh is currently running, start a new one, return that future
    * If a refresh is currently running, return the existing future
    */
  def refresh(): Future[T] = for {
    refreshAlteringFuture <- agent.alter { currentRefresh => if (currentRefresh.isCompleted) generateToken else currentRefresh }
    refreshFuture <- refreshAlteringFuture
  } yield refreshFuture

  /*
   * If a refresh is currently running, return the refresh future
   * If a token is present, return it
   * Otherwise, return a refresh()
   */
  def get(): Future[T] = agent.get()
}