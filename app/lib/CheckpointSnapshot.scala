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

import com.ning.http.util.AllowAllHostnameVerifier
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request.Builder
import lib.Config.Checkpoint
import lib.SSL.InsecureSocketFactory
import lib.okhttpscala._
import org.eclipse.jgit.lib.{AbbreviatedObjectId, ObjectId}
import org.joda.time.{DateTime, ReadableInstant}
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._

object CheckpointSnapshot {

  val client = new OkHttpClient()
  val insecureClient = new OkHttpClient().setSslSocketFactory(InsecureSocketFactory).setHostnameVerifier(new AllowAllHostnameVerifier)

  val hexRegex = """\b\p{XDigit}{40}\b""".r

  def apply(checkpoint: Checkpoint): Future[Iterator[AbbreviatedObjectId]] = {

    val clientForCheckpoint = if (checkpoint.sslVerification) client else insecureClient

    val responseF = clientForCheckpoint.execute(new Builder().url(checkpoint.url.toString).build())

    responseF.onComplete { r =>
      Logger.info(s"${checkpoint.name} : $r")
    }

    responseF.map {
      resp => hexRegex.findAllIn(resp.body().string).map(AbbreviatedObjectId.fromString)
    }
  }
}

case class CheckpointSnapshot(
  checkpoint: Checkpoint,
  commitId: Option[ObjectId],
  time: ReadableInstant = DateTime.now
)
