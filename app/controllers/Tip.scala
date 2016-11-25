package controllers

import lib._
import play.api.mvc._

object Tip extends Controller {

  def testFeedback() = Action.async(parse.json) { implicit request =>
    TestFeedback.notifyGitHub(request.body.as[TestResult])
  }

}
