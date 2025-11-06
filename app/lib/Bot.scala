package lib

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.madgag.github.apps.GitHubAppAuth
import com.madgag.scalagithub.model.{Account, GitHubApp}
import com.madgag.scalagithub.{AccountAccess, ClientWithAccess, GitHub, GitHubAppAccess, GitHubCredentials}
import play.api.Logging

import java.nio.file.Path
import scala.concurrent.{ExecutionContext, Future}

case class Identity(login: String, html_url: String) {
  val atLogin = s"@$login"
}

case class Bot(
  workingDir: Path,
  clientWithAccess: ClientWithAccess[GitHubAppAccess]
) {
  val github: GitHub = clientWithAccess.gitHub

  private val principal: GitHubApp = clientWithAccess.accountAccess.principal
  val identity: Identity = Identity(principal.slug, principal.html_url)
}

object Bot extends Logging {

  def forGithubApp(clientWithAccess: ClientWithAccess[GitHubAppAccess]): Bot = {
    val workingDir = Path.of("/tmp", "bot", "working-dir")
    Bot(workingDir, clientWithAccess)
  }
}
