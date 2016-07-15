package lib.actions

import com.madgag.github.Implicits._
import com.madgag.playgithub.auth.AuthenticatedSessions.AccessToken
import com.madgag.playgithub.auth.{Client, GHRequest}
import com.madgag.scalagithub.model.RepoId
import controllers.Application._
import controllers.{Auth, routes}
import lib._
import play.api.mvc.{ActionFilter, Result}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalax.file.ImplicitConversions._

object Actions {
  private val authScopes = Seq("repo")

  implicit val authClient: Client = Auth.authClient

  implicit val provider = AccessToken.FromSession

  val GitHubAuthenticatedAction = com.madgag.playgithub.auth.Actions.gitHubAction(authScopes, Bot.parentWorkDir.toPath)

  def repoAccessFilter(repoId: RepoId) = new ActionFilter[GHRequest] {
    override protected def filter[A](req: GHRequest[A]): Future[Option[Result]] = {
      for {
        user <- req.userF
        userViewOfRepo <- req.gitHub.getRepo(repoId).trying
      } yield {
        println(s"******* ${user.atLogin} ${userViewOfRepo.map(r => r.full_name + " " + r.permissions)}")
        if (userViewOfRepo.isSuccess) None else Some(
          Redirect(routes.Application.index()).flashing("message" -> s"You can't see a ${repoId.fullName} repo")
        )
      }
    }
  }

  def repoAuthenticated(repoId: RepoId) = GitHubAuthenticatedAction andThen repoAccessFilter(repoId)
}