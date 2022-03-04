package lib.actions

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import com.madgag.scalagithub.model.RepoId
import play.api.Logger
import play.api.libs.iteratee.{Iteratee, Traversable}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.{Codecs, Crypto}
import play.api.mvc.{BodyParser, RequestHeader, Result, Results}

import scala.concurrent.Future
import scala.util.control.NonFatal

object RepoSecretKey {
  def sharedSecretForRepo(repo: RepoId) = {
    val signature = Crypto.sign("GitHub-Repo:"+repo.fullName)
    Logger.debug(s"Repo $repo signature $signature")
    signature
  }
}

object Parsers {

  def assertSecureEquals(s1: String, s2: String) = {
    assert(Crypto.constantTimeEquals(s1, s2), "HMAC signatures did not match")
    Logger.debug("HMAC Signatures matched!")
  }

  def parseGitHubHookJson(jsValue: JsValue): RepoId =
    (jsValue \ "repository" \ "full_name").validate[String].map(RepoId.from).get

  type FullBodyParser[+A] = (RequestHeader, Array[Byte]) => Either[Result, A]

  def sign(message: Array[Byte], key: Array[Byte]): String = {
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(key, "HmacSHA1"))
    Codecs.toHexString(mac.doFinal(message))
  }
}
