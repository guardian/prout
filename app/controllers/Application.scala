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

import com.netaporter.uri.Uri
import lib._
import lib.actions.Parsers
import org.kohsuke.github.GHRepository
import play.api.Logger
import play.api.cache.Cache
import play.api.mvc._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object Application extends Controller {

  def githubHook(siteUrl: String, siteLabel: Option[String]) = Action(Parsers.githubHookJson("monkey")) { request =>
    val site = Site.from(Uri.parse(siteUrl), siteLabel)
    for (repoFullName <- (request.body \ "repository" \ "full_name").validate[String]) {
      Scanner.updateFor(site, RepoFullName(repoFullName))
    }
    NoContent
  }

  def updateRepo(repoOwner: String, repoName: String, siteUrl: String, siteLabel: Option[String]) = Action { implicit req =>
    val site = Site.from(Uri.parse(siteUrl), siteLabel)
    val repoFullName = RepoFullName(repoOwner, repoName)
    Scanner.updateFor(site, repoFullName)
    NoContent
  }

  def index = Action { implicit req =>
    Ok(views.html.userPages.index())
  }

}
