/*
 * Copyright 2014 The Guardian
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package lib

import cats.effect.IO
import cats.effect.kernel.Resource
import lib.CheckpointSnapshot.{BadResponse, NetworkFailure, RequestFailure}

import java.time.Instant
import java.time.Instant.now
import scala.jdk.CollectionConverters.*
import sttp.client4.*
import sttp.model.*
import lib.Config.Checkpoint
import lib.SSL.InsecureSocketFactory
import org.eclipse.jgit.lib.{AbbreviatedObjectId, ObjectId}
import sttp.client4.httpclient.cats.HttpClientCatsBackend

import java.net.http.HttpClient
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.*
import scala.util.Try
import scala.util.matching.Regex
import java.io.FileInputStream
import java.security.{KeyStore, SecureRandom}
import java.security.cert.X509Certificate
import javax.net.ssl.*

trait CheckpointSnapshoter {
  def snapshot(checkpoint: Checkpoint): IO[Either[RequestFailure, Set[AbbreviatedObjectId]]]
}

object CheckpointSnapshoter extends CheckpointSnapshoter {

  val client: Resource[IO, WebSocketBackend[IO]] = HttpClientCatsBackend.resource[IO]()

  val insecureClient: Resource[IO, WebSocketBackend[IO]] = {
    val sslContext: SSLContext = SSLContext.getInstance("TLS")
    sslContext.init(null, Array(SSL.TrustEveryoneTrustManager), null)
    HttpClientCatsBackend.resourceUsingClient[IO](HttpClient.newBuilder().sslContext(sslContext).build())
  }

  val hexRegex: Regex = """\b\p{XDigit}{40}\b""".r

  def snapshot(checkpoint: Checkpoint): IO[Either[RequestFailure, Set[AbbreviatedObjectId]]] = {
    val clientForCheckpoint = if (checkpoint.sslVerification) client else insecureClient

    clientForCheckpoint.use(basicRequest.get(checkpoint.url).send).map {
      resp => resp.body.left.map(_ => BadResponse(resp.code)).map { body =>
        val responseValues: Set[String] = resp.headers.map(_.value).toSet + body
        for {
          responseValue <- responseValues
          commitText <- hexRegex.findAllIn(responseValue)
        } yield AbbreviatedObjectId.fromString(commitText)
      }
    }.recover { case t => Left(NetworkFailure(t)) }
  }
}

case class CheckpointSnapshot(
  checkpoint: Checkpoint,
  commitIdTry: Either[RequestFailure, Option[ObjectId]],
  time: Instant = now
)

object CheckpointSnapshot {
  sealed trait RequestFailure
  
  case class BadResponse(statusCode: StatusCode) extends RequestFailure
  case class NetworkFailure(t: Throwable) extends RequestFailure
}