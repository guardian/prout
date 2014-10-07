package lib

import com.madgag.git._
import org.eclipse.jgit.lib.ObjectReader
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.{AndTreeFilter, PathFilterGroup}

import scala.collection.convert.wrapAll._

object GitChanges {

  val treeDiffFilter: TreeWalk => Boolean = w => w.isSubtree && (1 until w.getTreeCount).exists(!w.idEqual(_, 0))

  /**
   *
   * @return interestingPaths which were affected by the changes base..head
   */
  def affectedFolders(base: RevCommit, head: RevCommit, interestingPaths: Set[String])(implicit reader: ObjectReader): Set[String] = {
    val treeFilter = AndTreeFilter.create(PathFilterGroup.createFromStrings(interestingPaths), treeDiffFilter)

    walk(base.getTree, head.getTree)(treeFilter, postOrderTraversal = true).map(_.getPathString).filter(interestingPaths).toSet
  }
}
