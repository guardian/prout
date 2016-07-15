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

import com.madgag.scalagithub.model.RepoId
import lib.actions.Actions
import lib.{Bot, RepoSnapshot}
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

object Application extends Controller {

  def index = Action { implicit req =>
    Ok(views.html.userPages.index())
  }

  def zoomba(repoId: RepoId) = Actions.repoAuthenticated(repoId).async { implicit req =>
    implicit val checkpointSnapshoter = Api.checkpointSnapshoter
    for {
      wl <- RepoWhitelistService.repoWhitelist.get()
      repoFetchedByProut <- Bot.github.getRepo(repoId)
      proutPresenceQuickCheck <- RepoWhitelistService.hasProutConfigFile(repoFetchedByProut)
      repoSnapshot <- RepoSnapshot(repoFetchedByProut)
      diagnostic <- repoSnapshot.diagnostic()
    } yield {
      val known = wl.allKnownRepos(repoId)
      Ok(views.html.userPages.repo(proutPresenceQuickCheck, repoSnapshot, diagnostic))
    }
  }

}
