package lib

import com.madgag.git._
import org.eclipse.jgit.lib.ObjectReader
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.{AndTreeFilter, PathFilterGroup, TreeFilter}

import scala.collection.convert.wrapAll._

object GitChanges {

  val treeDiffFilter: TreeFilter = (w: TreeWalk) => w.isSubtree && (1 until w.getTreeCount).exists(!w.idEqual(_, 0))

  /**
   *
   * @return interestingPaths which were affected by the changes base..head
   */
  def affectedFolders(base: RevCommit, head: RevCommit, interestingPaths: Set[String])(implicit reader: ObjectReader): Set[String] = {
    val pathsWhichDoNoStartAndEndWithSlashes = interestingPaths.filterNot(p => p.startsWith("/") && p.endsWith("/"))
    require(pathsWhichDoNoStartAndEndWithSlashes.isEmpty,
      s"Interesting paths should start and end with a slash: $pathsWhichDoNoStartAndEndWithSlashes")

    val (rootPaths, subFolderPaths) = interestingPaths.partition(_ == "/")

    val affectedRootPaths = rootPaths.filter(_ => base.getTree != head.getTree)
    val affectedSubFolderPaths = if (subFolderPaths.isEmpty) Set.empty else {
      val treeFilter = AndTreeFilter.create(PathFilterGroup.createFromStrings(subFolderPaths.map(_.stripPrefix("/"))), treeDiffFilter)
      walk(base.getTree, head.getTree)(treeFilter, postOrderTraversal = true).map(_.slashPrefixedPath + "/").toSet.filter(subFolderPaths)
    }

    affectedRootPaths ++ affectedSubFolderPaths
  }
}
