package lib

import java.time.Duration.ofMinutes

import com.google.common.io.Files.createTempDir
import com.madgag.git._
import com.madgag.scalagithub.GitHub._
import com.madgag.scalagithub.commands.CreateRepo
import com.madgag.scalagithub.model.Repo
import com.madgag.time.Implicits._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.scalatest.BeforeAndAfterAll

import scala.collection.convert.wrapAll._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait TestRepoCreation extends Helpers with BeforeAndAfterAll {

  val testRepoNamePrefix: String = s"prout-test-${getClass.getSimpleName}-"

  def isTestRepo(repo: Repo) =
    repo.name.startsWith(testRepoNamePrefix) && repo.created_at.toInstant.age() > ofMinutes(10)

  override def beforeAll {
    val oldRepos = github.listRepos("updated", "desc").all().futureValue.filter(isTestRepo)
    Future.traverse(oldRepos)(_.delete())
  }

  def createTestRepo(fileName: String): Repo = {
    val cr = CreateRepo(
      name = testRepoNamePrefix + System.currentTimeMillis().toString,
      `private` = false
    )
    val testRepoId = github.createRepo(cr).futureValue.repoId

    val localGitRepo = test.unpackRepo(fileName)

    val testGithubRepo = eventually { github.getRepo(testRepoId).futureValue }

    val config = localGitRepo.getConfig
    config.setString("remote", "origin", "url", testGithubRepo.clone_url)
    config.save()

    val pushResults = localGitRepo.git.push.setCredentialsProvider(Bot.githubCredentials.git).setPushTags().setPushAll().call()

    forAll (pushResults.toSeq) { pushResult =>
      all (pushResult.getRemoteUpdates.map(_.getStatus)) must be(RemoteRefUpdate.Status.OK)
    }

    eventually {
      whenReady(testGithubRepo.refs.list().all()) { _ must not be empty }
    }

    eventually {
      Git.cloneRepository().setBare(true).setURI(testGithubRepo.clone_url).setDirectory(createTempDir()).call()
    }

    testGithubRepo
  }
}