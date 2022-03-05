package lib

import com.madgag.git._
import com.madgag.scala.collection.decorators._
import lib.Config.RepoConfig
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.treewalk.TreeWalk

object ConfigFinder {
  
  val ProutConfigFileName = ".prout.json"
  
  private val configFilter: TreeWalk => Boolean = w => {
    w.isSubtree || w.getNameString == ProutConfigFileName
  }

  def configIdMapFrom(c: RevCommit)(implicit repoThreadLocal: ThreadLocalObjectDatabaseResources): Map[String, ObjectId] = {
    implicit val reader = repoThreadLocal.reader()
    walk(c.getTree)(configFilter).map { tw =>
      val configPath = tw.slashPrefixedPath
      configPath.reverse.dropWhile(_ != '/').reverse -> tw.getObjectId(0)
    }.toMap
  }

  def config(c: RevCommit)(implicit repoThreadLocal: ThreadLocalObjectDatabaseResources): RepoConfig = {
    val checkpointsByNameByFolder = configIdMapFrom(c).mapV(Config.readConfigFrom)
    RepoConfig(checkpointsByNameByFolder)
  }
}
