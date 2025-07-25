package lib

import com.madgag.scalagithub.model.GitHubApp
import play.api.Logging
import play.api.libs.json.{Json, Reads}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

// https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-json-web-token-jwt-for-a-github-app

class GithubAppAuth(jwts: GitHubAppJWTs, wsClient: WSClient) extends Logging {

  private def addHeaders(wsRequest: WSRequest): WSRequest = wsRequest.addHttpHeaders(
    "Accept" -> "application/vnd.github+json",
    "Authorization" -> s"Bearer ${jwts.currentJWT()}",
    "X-GitHub-Api-Version" -> "2022-11-28"
  )

  def request[T: Reads](path: String, req: WSRequest => Future[WSResponse], successStatusCode: Int)(implicit ec: ExecutionContext): Future[T] =
    req(addHeaders(wsClient.url(s"https://api.github.com/$path"))).map { response =>
      if (response.status == successStatusCode) response.json.as[T] else {
        logger.error(s"Failed to get installation access token: ${response.status} - ${response.body}")
        throw new RuntimeException(s"Failed to get installation access token: ${response.status}")
      }
    }

  /**
   * https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/authenticating-as-a-github-app-installation#generating-an-installation-access-token
   * https://docs.github.com/en/rest/apps/apps?apiVersion=2022-11-28#create-an-installation-access-token-for-an-app
   */
  def getInstallationAccessToken(installationId: String)(implicit ec: ExecutionContext): Future[InstallationTokenResponse] =
    request[InstallationTokenResponse](s"app/installations/$installationId/access_tokens", _.post(""), 201)

  /**
   * https://docs.github.com/en/rest/apps/apps?apiVersion=2022-11-28#get-the-authenticated-app
   */
  def getAuthenticatedApp()(implicit ec: ExecutionContext): Future[GitHubApp] =
    request[GitHubApp](s"app", _.get(), 200)

}

case class InstallationTokenResponse(
  token: String,
  expires_at: Instant
)

object InstallationTokenResponse {
  implicit val reads: Reads[InstallationTokenResponse] = Json.reads
}