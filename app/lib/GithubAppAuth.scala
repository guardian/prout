package lib

import com.madgag.scalagithub.model.GitHubApp
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm.RS256
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.{PEMKeyPair, PEMParser}
import play.api.Logging
import play.api.libs.ws.WSClient

import java.io.StringReader
import java.security.PrivateKey
import java.time.Instant
import java.util.Date
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Using}

// https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-json-web-token-jwt-for-a-github-app
// https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/authenticating-as-a-github-app-installation#generating-an-installation-access-token
// https://github.com/octokit
object GithubAppAuth extends Logging {

  private def parsePrivateKey(privateKeyPem: String): Try[PrivateKey] = {
    val converter = new JcaPEMKeyConverter()
    Using(new PEMParser(new StringReader(privateKeyPem))) { pemParser =>
      val keyObject = pemParser.readObject()
      converter.getPrivateKey(keyObject.asInstanceOf[PEMKeyPair].getPrivateKeyInfo)
    }
  }

  private def createJwt(appClientId: String, privateKeyPem: String, now: Instant): Try[String] = {
    parsePrivateKey(privateKeyPem).map(privateKey =>
      Jwts
        .builder()
        .setIssuer(appClientId)
        .setIssuedAt(Date.from(now.minusSeconds(60)))
        .setExpiration(Date.from(now.plusSeconds(600)))
        .signWith(privateKey, RS256)
        .compact()
    )
  }

  def getInstallationAccessToken(
      appClientId: String,
      installationId: String,
      privateKey: String,
      wsClient: WSClient
  )(implicit ec: ExecutionContext): Future[String] = {
    val url = s"https://api.github.com/app/installations/$installationId/access_tokens"

    for {
      jwt <- Future.fromTry(createJwt(appClientId, privateKey, Instant.now()))
      accessToken <- wsClient
        .url(url)
        .addHttpHeaders(
          "Accept" -> "application/vnd.github+json",
          "Authorization" -> s"Bearer $jwt",
          "X-GitHub-Api-Version" -> "2022-11-28"
        )
        .post("")
        .map { response =>
          if (response.status == 201) {
            (response.json \ "token").as[String]
          } else {
            logger.error(s"Failed to get installation access token: ${response.status} - ${response.body}")
            throw new RuntimeException(s"Failed to get installation access token: ${response.status}")
          }
        }
    } yield accessToken
  }

  def getAuthenticatedApp(
      appClientId: String,
      privateKey: String,
      wsClient: WSClient
  )(implicit ec: ExecutionContext): Future[GitHubApp] = {
    val url = s"https://api.github.com/app"

    for {
      jwt <- Future.fromTry(createJwt(appClientId, privateKey, Instant.now()))
      accessToken <- wsClient
        .url(url)
        .addHttpHeaders(
          "Accept" -> "application/vnd.github+json",
          "Authorization" -> s"Bearer $jwt",
          "X-GitHub-Api-Version" -> "2022-11-28"
        )
        .get()
        .map { response =>
          if (response.status == 200)
            response.json.as[GitHubApp]
          else {
            logger.error(s"Failed to get installation access token: ${response.status} - ${response.body}")
            throw new RuntimeException(s"Failed to get installation access token: ${response.status}")
          }
        }
    } yield accessToken
  }
}
