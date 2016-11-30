package controllers

import lib._
import play.api.mvc._

object TestingInProduction extends Controller {

  def testFeedback() = Action.async { implicit request =>
    TestFeedback.notifyGitHub()
  }

}
