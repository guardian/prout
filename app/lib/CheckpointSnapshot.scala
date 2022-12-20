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

import java.time.Instant
import java.time.Instant.now
import scala.jdk.CollectionConverters._
import com.madgag.okhttpscala._
import lib.Config.Checkpoint
import lib.SSL.InsecureSocketFactory
import okhttp3.OkHttpClient
import okhttp3.Request.Builder
import org.eclipse.jgit.lib.{AbbreviatedObjectId, ObjectId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.util.Try
import scala.util.matching.Regex

trait CheckpointSnapshoter {
  def snapshot(checkpoint: Checkpoint): Future[Iterator[AbbreviatedObjectId]]
}

object CheckpointSnapshoter extends CheckpointSnapshoter {

  val client = new OkHttpClient()
  val insecureClient: OkHttpClient = new OkHttpClient.Builder()
    .sslSocketFactory(InsecureSocketFactory, SSL.TrustEveryoneTrustManager)
    .hostnameVerifier((_, _) => true).build()

  val hexRegex: Regex = """\b\p{XDigit}{40}\b""".r

  def snapshot(checkpoint: Checkpoint): Future[Iterator[AbbreviatedObjectId]] = {

    val clientForCheckpoint = if (checkpoint.sslVerification) client else insecureClient

    clientForCheckpoint.execute(new Builder().url(checkpoint.url.toString).build()) {
      resp =>
        val responseHeaderValues = resp.headers().toMultimap.entrySet().asScala.toSeq.flatMap(_.getValue.asScala.toSeq)
        val responseValues: Set[String] = responseHeaderValues.toSet + resp.body().string
        for {
          responseValue <- responseValues.iterator
          commitText <- hexRegex.findAllIn(responseValue)
        } yield AbbreviatedObjectId.fromString(commitText)
    }
  }
}

case class CheckpointSnapshot(
  checkpoint: Checkpoint,
  commitIdTry: Try[Option[ObjectId]],
  time: Instant = now
)
