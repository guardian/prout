package lib.actions

import cats.effect.unsafe.implicits.global
import com.madgag.github.Implicits.*
import com.madgag.playgithub.auth.AuthenticatedSessions.AccessToken
import com.madgag.playgithub.auth.AuthenticatedSessions.AccessToken.Provider
import com.madgag.playgithub.auth.GHRequest
import com.madgag.scalagithub.GitHub
import com.madgag.scalagithub.model.RepoId
import controllers.routes
import lib.*
import play.api.mvc.Results.Redirect
import play.api.mvc.*

import scala.concurrent.{ExecutionContext, Future}

class Actions(
  bot: Bot,
  bodyParser: BodyParser[AnyContent]
)(using
  authClient: com.madgag.playgithub.auth.Client,
  ec: ExecutionContext,
  gf: GitHub.Factory
) {
  private val authScopes = Seq("repo")

  implicit val provider: Provider = AccessToken.FromSession

  val GitHubAuthenticatedAction: ActionBuilder[GHRequest, AnyContent] =
    com.madgag.playgithub.auth.Actions.gitHubAction(authScopes, bodyParser)

  def repoAccessFilter(repoId: RepoId): ActionFilter[GHRequest] = new ActionFilter[GHRequest] {
    def executionContext = ec

    override protected def filter[A](req: GHRequest[A]): Future[Option[Result]] = {
      (for {
        user <- req.gitHub.getUser()
        userViewOfRepo <- req.gitHub.getRepo(repoId)
      } yield {
        println(s"******* ${user.result.atLogin} ${userViewOfRepo.map(r => r.full_name + " " + r.permissions)}")
        None
      }).recover {
        case _ => 
          Some(Redirect(routes.Application.index()).flashing("message" -> s"You can't see a ${repoId.fullName} repo"))
      }
    }.unsafeToFuture()
  }

  def repoAuthenticated(repoId: RepoId) = GitHubAuthenticatedAction andThen repoAccessFilter(repoId)
}