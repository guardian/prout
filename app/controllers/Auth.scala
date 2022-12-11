package controllers

import com.madgag.playgithub.auth.{AuthController, Client}
import lib.GithubAppConfig

case class Auth(
  authClient: Client,
  controllerComponents: ControllerAppComponents
) extends AuthController {

}
