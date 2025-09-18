package lib

import com.madgag.github.apps.GitHubAppAuth
import com.madgag.scalagithub.model.Account
import com.madgag.scalagithub.{AccountAccess, GitHub, GitHubCredentials}
import play.api.Logging

import java.nio.file.Path
import scala.concurrent.{ExecutionContext, Future}

case class Identity(login: String, html_url: String) {
  val atLogin = s"@$login"
}

case class Bot(
  workingDir: Path,
  accountAccess: AccountAccess,
  identity: Identity
) {
  val github = accountAccess.gitHub
}

object Bot extends Logging {

  def forGithubApp(
    githubAppAuth: GitHubAppAuth
  )(implicit ec: ExecutionContext): Future[Bot] = {
    val workingDir = Path.of("/tmp", "bot", "working-dir")

    (for {
      app <- githubAppAuth.getAuthenticatedApp()
      installationAccess <- githubAppAuth.accessSoleInstallation()
    } yield Bot(
      workingDir,
      installationAccess.accountAccess(),
      Identity(app.slug, app.html_url)
    )
    ).recover { case ex =>
      logger.error("Failed to authenticate with GitHub app", ex)
      throw ex
    }
  }
}
