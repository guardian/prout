package lib

import com.google.common.io.Files
import com.madgag.git._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.kohsuke.github._
import org.scalatest.BeforeAndAfterAll

import scala.collection.convert.wrapAll._


trait TestRepoCreation extends Helpers with BeforeAndAfterAll {

  val testRepoNamePrefix: String = s"prout-test-${getClass.getSimpleName}-"

  override def beforeAll {
    conn().getMyself.getAllRepositories.values.filter(_.getName.startsWith(testRepoNamePrefix)).foreach(_.delete())
  }

  def createTestRepo(fileName: String): GHRepository = {
    val gitHub = conn()
    val testRepoId = gitHub.createRepository(testRepoNamePrefix + System.currentTimeMillis().toString, fileName, "", true).getFullName

    val localGitRepo = test.unpackRepo(fileName)

    val testGithubRepo = eventually { gitHub.getRepository(testRepoId) }

    val config = localGitRepo.getConfig
    config.setString("remote", "origin", "url", testGithubRepo.gitHttpTransportUrl)
    config.save()

    val pushResults = localGitRepo.git.push.setCredentialsProvider(Bot.githubCredentials.git).setPushTags().setPushAll().call()

    forAll (pushResults.toSeq) { pushResult =>
      all (pushResult.getRemoteUpdates.map(_.getStatus)) must be(RemoteRefUpdate.Status.OK)
    }

    eventually {
      testGithubRepo.getBranches must not be empty
    }

    eventually {
      Git.cloneRepository().setBare(true).setURI(testGithubRepo.gitHttpTransportUrl).setDirectory(Files.createTempDir()).call()
    }

    testGithubRepo
  }
}