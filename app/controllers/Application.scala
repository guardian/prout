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

package controllers

import com.netaporter.uri._
import lib.TextCommitIdExtractor._
import lib._
import lib.actions.Parsers
import org.eclipse.jgit.lib.Repository
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.Logger
import play.api.libs.ws.WS
import play.api.mvc._

import scala.concurrent.Future

object Application extends Controller {

  def updateRepo(repoOwner: String, repoName: String, checkpoint: String) = Action { implicit req =>
    Scanner.updateFor(checkpoint, RepoFullName(repoOwner, repoName))
    NoContent
  }

  def snsHook(repoOwner: String, repoName: String, checkpoint: String) = Action(parse.json) { request =>
    for {
      message <- (request.body \ "Message").validate[String]
      timestamp <- (request.body \ "Timestamp").validate[String].map(ISODateTimeFormat.basicDateTime.parseDateTime)
    } {
      def siteSnapshotForRepo(repoSnapshot: RepoSnapshot) = Future {
        val commitId = TextCommitIdExtractor.extractCommit(message)(repoSnapshot.gitRepo)
        SiteSnapshot(Site(Uri.parse("http://example.com"), checkpoint), commitId, timestamp)
      }
      Scanner.updateFor(checkpoint, RepoFullName(repoOwner, repoName), siteSnapshotForRepo)
    }
    NoContent
  }

  def githubHook(checkpoint: String) = Action(Parsers.githubHookJson("monkey")) { request =>
    for (repoFullName <- (request.body \ "repository" \ "full_name").validate[String]) {
      Scanner.updateFor(checkpoint, RepoFullName(repoFullName), siteSnapshot())
    }
    NoContent
  }

  def siteSnapshot(site: Site)(repoSnapshot: RepoSnapshot) = Future {
    import play.api.Play.current
    implicit val gitRepo = repoSnapshot.gitRepo

    val siteCommitIdF =
      WS.url(site.url.toString).get().map(resp => extractCommit(resp.body)).andThen {
        case ci => Logger.info(s"Site '${site.label}' commit id: ${ci.map(_.map(_.name()))}")
      }

    for {
      siteCommitId <- siteCommitIdF
    } yield SiteSnapshot(site, siteCommitId, DateTime.now)
  }


  def index = Action { implicit req =>
    Ok(views.html.userPages.index())
  }

}
