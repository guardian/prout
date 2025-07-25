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
    installationId: String,
    githubAppAuth: GithubAppAuth
  )(implicit ec: ExecutionContext): Future[Bot] = {
    val workingDir = Path.of("/tmp", "bot", "working-dir")

    val accessTokenResponseF = githubAppAuth.getInstallationAccessToken(installationId)
    (for {
      accessTokenResponse <- accessTokenResponseF
      app <- githubAppAuth.getAuthenticatedApp()
    } yield {
      val credentials: GitHubCredentials =
        GitHubCredentials.forAccessKey(accessTokenResponse.token, workingDir).get

      val github = new GithubAppGithub(credentials)

      Bot(
        workingDir,
        github,
        credentials.git,
        Identity(app.slug, app.html_url)
      )
    }).recover { case ex =>
      logger.error("Failed to authenticate with GitHub app", ex)
      throw ex
    }
  }
}
