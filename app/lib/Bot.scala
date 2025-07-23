package lib

import com.madgag.scalagithub.model.{GitHubApp, User}
import com.madgag.scalagithub.{GitHub, GitHubCredentials}
import okhttp3.Request
import org.eclipse.jgit.transport.CredentialsProvider
import play.api.Logging
import play.api.libs.ws.WSClient

import java.nio.file.Path
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

case class Identity(login: String, html_url: String) {
  val atLogin = s"@$login"
}

case class Bot(
    workingDir: Path,
    github: GitHub,
    git: CredentialsProvider,
    identity: Identity
)

class GithubAppGithub(ghCredentials: GitHubCredentials) extends GitHub(ghCredentials) {

  override def addAuth(builder: Request.Builder): Request.Builder = builder
    .addHeader("Authorization", s"Bearer ${ghCredentials.accessKey}")
}

object Bot extends Logging {
  def forAccessToken(accessToken: String)(implicit ec: ExecutionContext): Bot = {
    val workingDir = Path.of("/tmp", "bot", "working-dir")

    val credentials: GitHubCredentials =
      GitHubCredentials.forAccessKey(accessToken, workingDir).get

    val github: GitHub = new GitHub(credentials)
    val user: User = Await.result(github.getUser().map(_.result), 3.seconds)
    logger.info(s"Token gives GitHub user ${user.atLogin}")

    Bot(
      workingDir,
      github,
      credentials.git,
      Identity(user.login, user.html_url)
    )
  }

  def forGithubApp(
      appClientId: String,
      installationId: String,
      privateKey: String,
      wsClient: WSClient
  )(implicit ec: ExecutionContext): Future[Bot] = {
    val workingDir = Path.of("/tmp", "bot", "working-dir")

    GithubAppAuth
      .getInstallationAccessToken(appClientId, installationId, privateKey, wsClient)
      .map { accessToken =>
        logger.info(s"Successfully obtained installation access token for GitHub app $appClientId")

        val credentials: GitHubCredentials =
          GitHubCredentials.forAccessKey(accessToken, workingDir).get

        val github = new GithubAppGithub(credentials)
        val app: GitHubApp =
          Await.result(GithubAppAuth.getAuthenticatedApp(appClientId, privateKey, wsClient), 3.seconds)

        Bot(
          workingDir,
          github,
          credentials.git,
          Identity(app.slug, app.html_url)
        )
      }
      .recover { case ex =>
        logger.error("Failed to authenticate with GitHub app", ex)
        throw ex
      }
  }
}
