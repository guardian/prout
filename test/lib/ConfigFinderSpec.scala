package lib

import com.madgag.git._
import lib.ConfigFinder._
import org.eclipse.jgit.lib.Repository
import org.scalatestplus.play._

class ConfigFinderSpec extends PlaySpec {

  def configFilesIn(repoPath: String): Set[String] = {
    val localGitRepo: Repository = test.unpackRepo(repoPath)

    implicit val (revWalk, reader) = localGitRepo.singleThreadedReaderTuple

    val master = localGitRepo.resolve("master").asRevCommit

    configIdMapFrom(master).keySet
  }

   "Config finder" must {
     "find config in the root directory" in {
       configFilesIn("/simple.git.zip") mustEqual(Set("/"))
     }

     "find config in sub folders" in {
       configFilesIn("/multi-folder.git.zip") mustEqual(Set("/foo/","/bar/","/baz/"))
     }
   }
 }