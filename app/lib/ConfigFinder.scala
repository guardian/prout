package lib

import com.madgag.git._
import lib.Config.Checkpoint
import org.eclipse.jgit.lib.ObjectReader
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.treewalk.TreeWalk

object ConfigFinder {

  private val configFilter: TreeWalk => Boolean = w => w.isSubtree || w.getNameString == ".prout.json"

  /**
   *
   * @return treewalk that only returns prout config files
   */
  def configTreeWalk(c: RevCommit)(implicit reader: ObjectReader): TreeWalk = walk(c.getTree)(configFilter)

  def configIdMapFrom(c: RevCommit)(implicit reader: ObjectReader) = configTreeWalk(c).map { tw =>
    val configPath = tw.slashPrefixedPath
    configPath.reverse.dropWhile(_ != '/').reverse -> tw.getObjectId(0)
  }.toMap

  def config(c: RevCommit)(implicit reader: ObjectReader): Map[String, Set[Checkpoint]] = {
    val checkpointsByNameByFolder: Map[String, Set[Checkpoint]] = configIdMapFrom(c).mapValues(Config.readConfigFrom)

    val foldersByCheckpointName: Map[String, Seq[String]] = (for {
      (folder, checkpointNames) <- checkpointsByNameByFolder.mapValues(_.map(_.name)).toSeq
      checkpointName <- checkpointNames
    } yield checkpointName -> folder).groupBy(_._1).mapValues(_.map(_._2))

    val checkpointsNamedInMultipleFolders: Map[String, Seq[String]] = foldersByCheckpointName.filter(_._2.size > 1)

    require(checkpointsNamedInMultipleFolders.isEmpty, s"Duplicate checkpoints defined in multiple config files: $checkpointsNamedInMultipleFolders")

    checkpointsByNameByFolder
  }
}
