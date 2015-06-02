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

import lib._
import lib.actions.Parsers
import play.api.Logger
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.json.{JsArray, JsNumber}
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Api extends Controller {

  val logger = Logger(getClass)

  val checkpointSnapshoter: CheckpointSnapshoter = CheckpointSnapshot(_)

  def githubHook() = Action.async(parse.json) { request =>
    updateFor(Parsers.parseGitHubHookJson(request.body))
  }

  def updateRepo(repoOwner: String, repoName: String) = Action.async { request =>
    updateFor(RepoFullName(repoOwner, repoName))
  }

  def updateFor(repoFullName: RepoFullName): Future[Result] = {
    Logger.debug(s"update requested for $repoFullName")
    for {
      whiteList <- RepoWhitelistService.whitelist()
      update <- updateFor(repoFullName, whiteList)
    } yield update
  }

  def updateFor(repoFullName: RepoFullName, whiteList: RepoWhitelist): Future[Result] = {
    val scanGuardF = Future { // wrapped in a future to avoid timing attacks
      val knownRepo = whiteList.allKnownRepos(repoFullName)
      Logger.debug(s"$repoFullName known=$knownRepo")
      require(knownRepo, s"${repoFullName.text} not on known-repo whitelist")

      val scanScheduler = Cache.getOrElse(repoFullName.text) {
        val scheduler = new ScanScheduler(repoFullName, checkpointSnapshoter, Bot.githubCredentials.conn())
        logger.info(s"Creating $scheduler for $repoFullName")
        scheduler
      }
      Logger.debug(s"$repoFullName scanScheduler=$scanScheduler")

      val firstScanF = scanScheduler.scan()

      firstScanF.onComplete { _ => Delayer.delayTheFuture {
        /* Do a *second* scan shortly after the first one ends, to cope with:
         * 1. Latency in GH API
         * 2. Checkpoint site stabilising on the new version after deploy
         */
          scanScheduler.scan()
        }
      }

      firstScanF
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
