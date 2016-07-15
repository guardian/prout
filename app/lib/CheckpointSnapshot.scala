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
import javax.net.ssl.{HostnameVerifier, SSLSession}

import com.madgag.okhttpscala._
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request.Builder
import lib.Config.Checkpoint
import lib.SSL.InsecureSocketFactory
import org.eclipse.jgit.lib.{AbbreviatedObjectId, ObjectId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.util.Try

object CheckpointSnapshot {

  val client = new OkHttpClient()
  val insecureClient = new OkHttpClient().setSslSocketFactory(InsecureSocketFactory).setHostnameVerifier(new HostnameVerifier {
    override def verify(hostname: String, sslSession: SSLSession): Boolean = true
  })

  val hexRegex = """\b\p{XDigit}{40}\b""".r

  def apply(checkpoint: Checkpoint): Future[Iterator[AbbreviatedObjectId]] = {

    val clientForCheckpoint = if (checkpoint.sslVerification) client else insecureClient

    clientForCheckpoint.execute(new Builder().url(checkpoint.url.toString).build()) {
      resp => hexRegex.findAllIn(resp.body().string).map(AbbreviatedObjectId.fromString)
    }
  }
}

case class CheckpointSnapshot(
  checkpoint: Checkpoint,
  commitIdTry: Try[Option[ObjectId]],
  time: Instant = now
)
