package lib

import com.madgag.git._
import lib.ConfigFinder.configTreeWalk
import org.eclipse.jgit.lib.Repository
import org.scalatestplus.play._

class ConfigFinderSpec extends PlaySpec {

  def configFilesIn(repoPath: String): Set[String] = {
    val localGitRepo: Repository = test.unpackRepo(repoPath)

    implicit val (revWalk, reader) = localGitRepo.singleThreadedReaderTuple

    val master = localGitRepo.resolve("master").asRevCommit

    configTreeWalk(master).map(_.getPathString).toSet
  }

   "Config finder" must {
     "find config in the root directory" in {
       configFilesIn("/simple.git.zip") mustEqual(Set(".prout.json"))
     }

     "find config in sub folders" in {
       configFilesIn("/multi-folder.git.zip") mustEqual(Set("foo/.prout.json","bar/.prout.json","baz/.prout.json"))
     }
   }
 }