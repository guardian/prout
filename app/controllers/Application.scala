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
import org.kohsuke.github.GHRepository
import play.api.Logger
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

import com.netaporter.uri.Uri._
import com.netaporter.uri.Uri

import scala.concurrent.Future

object Application extends Controller {

  def githubHook(siteUrl: String, siteLabel: Option[String]) = Action(parse.json) { request =>
    val site = Site.from(Uri.parse(siteUrl), siteLabel)
    for (repoFullName <- (request.body \ "repository" \ "full_name").validate[String]) {
      scan(site, Bot.conn().getRepository(repoFullName))
    }
    NoContent
  }

  def measureLag(orgName: String, repoName: String, siteUrl: String, siteLabel: Option[String]) = Action { implicit req =>
    val site = Site.from(Uri.parse(siteUrl), siteLabel)
    val githubRepo = Bot.conn().getOrganization(orgName).getRepository(repoName)

    scan(site, githubRepo)

    NoContent
  }

  def scan(site: Site, githubRepo: GHRepository) = Future {
    Logger.info(s"Asked to audit ${githubRepo.getFullName}")

    for {
      siteSnapshot <- SiteSnapshot(site)
      repoSnapshot <- RepoSnapshot(githubRepo)
    } yield {
      Logger.info(s"about to get status...")
      val status = DeploymentProgressSnapshot(repoSnapshot, siteSnapshot)
      Logger.info(s"got status...")

      status.goCrazy()
    }
  }

  def index = Action { implicit req =>
    Ok(views.html.userPages.index())
  }

}
