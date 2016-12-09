package controllers

import com.madgag.scalagithub.model.RepoId
import lib._
import play.api.mvc._

object TestingInProduction extends Controller {

  def testFeedback(repoId: RepoId) = Action.async { implicit request =>
    TestFeedback.notifyGitHub(repoId)
  }

}
