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

import lib.Config.Checkpoint
import lib._
import lib.actions.Parsers
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object Api extends Controller {

  implicit val checkpointSnapshoter: Checkpoint => Future[CheckpointSnapshot] = CheckpointSnapshot(_)

  def githubHook() = Action(parse.json) { request =>
    val repoFullName = Parsers.parseGitHubHookJson(request.body)
    Future {
      Scanner.updateFor(repoFullName)
    }
    NoContent
  }

  def updateRepo(repoOwner: String, repoName: String) = Action {
    val repoFullName = RepoFullName(repoOwner, repoName)
    Future {
      Scanner.updateFor(repoFullName)
    }
    NoContent
  }
}
