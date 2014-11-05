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

import java.util.concurrent.TimeUnit._

import lib.Config.Checkpoint
import lib._
import lib.actions.Parsers
import play.api.Logger
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.json.{JsNumber, JsArray}
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object Api extends Controller {

  val droid: Droid = new Droid

  implicit val checkpointSnapshoter: Checkpoint => Future[CheckpointSnapshot] = CheckpointSnapshot(_)

  def githubHook() = Action.async(parse.json) { request =>
    updateFor(Parsers.parseGitHubHookJson(request.body))
  }

  def updateRepo(repoOwner: String, repoName: String) = Action.async { request =>
    updateFor(RepoFullName(repoOwner, repoName))
  }

  def updateFor(repoFullName: RepoFullName)(implicit checkpointSnapshoter: Checkpoint => Future[CheckpointSnapshot]): Future[Result] = {
    Logger.debug(s"update requested for $repoFullName")
    for {
      whiteList <- RepoWhitelistService.whitelist()
      update <- updateFor(repoFullName, whiteList)
    } yield update
  }

  def updateFor(repoFullName: RepoFullName, whiteList: RepoWhitelist): Future[Result] = {
    val scanGuardF = Future { // wrapped in a future to avoid timing attacks
      require(whiteList.allKnownRepos(repoFullName), s"${repoFullName.text} not on known-repo whitelist")

      Cache.getOrElse(repoFullName.text) {
        new Dogpile(droid.scan(Bot.githubCredentials.conn().getRepository(repoFullName.text)))
      }.doAtLeastOneMore()
    }
    val mightBePrivate = !whiteList.publicRepos(repoFullName)
    if (mightBePrivate) {
      // Response must be immediate, with no private information (e.g. even acknowledging that repo exists)
      Future.successful(NoContent)
    } else {
      // we can delay the response to return information about the repo config, and the updates generated
      for {
        scanGuard <- scanGuardF
        scan <- scanGuard
      } yield Ok(JsArray(scan.map(summary => JsNumber(summary.pr.getNumber))))
    }
  }
}
