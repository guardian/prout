package lib.actions

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import com.madgag.scalagithub.model.RepoId
import play.api.Logger
import play.api.libs.json.JsValue

object Parsers {
  def constantTimeEquals(a: String, b: String): Boolean = {
    MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8))
  }

  def assertSecureEquals(s1: String, s2: String) = {
    assert(constantTimeEquals(s1, s2), "HMAC signatures did not match")
    Logger.debug("HMAC Signatures matched!")
  }

  def parseGitHubHookJson(jsValue: JsValue): RepoId =
    (jsValue \ "repository" \ "full_name").validate[String].map(RepoId.from).get

}
