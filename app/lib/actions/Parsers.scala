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

  def githubHookRepository: BodyParser[RepoId] = tolerantXHubSigned[RepoId](RepoSecretKey.sharedSecretForRepo, "json", 128 * 1024, "Invalid Json") {
    (requestHeader, bytes) => parseGitHubHookJson(Json.parse(bytes))
  }

  def parseGitHubHookJson(jsValue: JsValue): RepoId =
    (jsValue \ "repository" \ "full_name").validate[String].map(RepoId.from).get

  def githubHookJson(sharedSecret: String) = tolerantXHubSigned[JsValue](_ => sharedSecret, "json", 128 * 1024, "Invalid Json") {
    (requestHeader, bytes) => Json.parse(bytes)
  }

  type FullBodyParser[+A] = (RequestHeader, Array[Byte]) => Either[Result, A]

  /*
  The 'X-Hub-Signature' header is defined by the PubSubHubbub Protocol:

  https://pubsubhubbub.googlecode.com/git/pubsubhubbub-core-0.4.html#authednotify ("8. Authenticated Content Distribution")

   */
  def tolerantXHubSigned[A](sharedSecret: A => String, name: String, maxLength: Int, errorMessage: String)(parser: (RequestHeader, Array[Byte]) => A): BodyParser[A] =
    tolerantBodyParser[A]("json", maxLength, "Invalid Json") { (request, bytes) =>
      val xHubSignature = request.headers("X-Hub-Signature").replaceFirst("sha1=", "")
      val res: A = parser(request, bytes)
      assertSecureEquals(xHubSignature, sign(bytes, sharedSecret(res).getBytes))
      res
    }

  def tolerantBodyParser[A](name: String, maxLength: Int, errorMessage: String)(parser: (RequestHeader, Array[Byte]) => A): BodyParser[A] =
    BodyParser(name + ", maxLength=" + maxLength) { request =>
      import play.api.libs.iteratee.Execution.Implicits.trampoline

      import scala.util.control.Exception._

      val bodyParser: Iteratee[Array[Byte], Either[Result, Either[Future[Result], A]]] =
        Traversable.takeUpTo[Array[Byte]](maxLength).transform(
          Iteratee.consume[Array[Byte]]().map { bytes =>
            allCatch[A].either {
              parser(request, bytes)
            }.left.map {
              case NonFatal(e) =>
                Future.successful(Results.BadRequest(errorMessage))
              case t => throw t
            }
          }
        ).flatMap(Iteratee.eofOrElse(Results.EntityTooLarge))

      bodyParser.mapM {
        case Left(tooLarge) => Future.successful(Left(tooLarge))
        case Right(Left(badResult)) => badResult.map(Left.apply)
        case Right(Right(body)) => Future.successful(Right(body))
      }
    }


  def sign(message: Array[Byte], key: Array[Byte]): String = {
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(key, "HmacSHA1"))
    Codecs.toHexString(mac.doFinal(message))
  }
}
