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

import com.madgag.git._
import lib.TextCommitIdExtractor.extractCommit
import org.eclipse.jgit.lib.ObjectId
import org.joda.time.{DateTime, ReadableInstant}
import play.api.Logger
import play.api.libs.ws.WS

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._

object SiteSnapshot {


  def apply(site: Site): Future[SiteSnapshot] = {
    import play.api.Play.current

    val siteCommitIdF =
      WS.url(site.url.toString).get().map(resp => extractCommit(resp.body)()).andThen {
        case ci => Logger.info(s"Site '${site.label}' commit id: ${ci.map(_.map(_.name()))}")
      }

    for {
      siteCommitId <- siteCommitIdF
    } yield SiteSnapshot(site, siteCommitId, DateTime.now)
  }
}

case class SiteSnapshot(site: Site, commitId: Option[ObjectId], time: ReadableInstant)
