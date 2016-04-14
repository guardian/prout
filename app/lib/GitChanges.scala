package lib

import com.madgag.git._
import com.madgag.scalagithub.model.PullRequest
import org.eclipse.jgit.lib.ObjectReader
import org.eclipse.jgit.revwalk.filter.RevFilter.MERGE_BASE
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.{AndTreeFilter, PathFilterGroup, TreeFilter}

import scala.collection.convert.wrapAll._

object GitChanges {

  val treeDiffFilter: TreeFilter = (w: TreeWalk) => w.isSubtree && (1 until w.getTreeCount).exists(!w.idEqual(_, 0))

  def affects(pullRequest: PullRequest, interestingPaths: Set[String])(implicit revWalk: RevWalk): Set[String] = for {
    tipCommit <- pullRequest.availableTipCommits
    affectedPath <- affectedFolders(pullRequest.base.asRevCommit, tipCommit, interestingPaths)
  } yield affectedPath

  /**
   *
   * @return interestingPaths which were affected by the changes base..head
   */
  def affectedFolders(base: RevCommit, head: RevCommit, interestingPaths: Set[String])(implicit revWalk: RevWalk): Set[String] = {
    implicit val reader = revWalk.getObjectReader

    val pathsWhichDoNotStartAndEndWithSlashes = interestingPaths.filterNot(p => p.startsWith("/") && p.endsWith("/"))
    require(pathsWhichDoNotStartAndEndWithSlashes.isEmpty,
      s"Interesting paths should start and end with a slash: $pathsWhichDoNotStartAndEndWithSlashes")

    revWalk.reset()
    revWalk.setRevFilter(MERGE_BASE)
    revWalk.markStart(base)
    revWalk.markStart(head)
    val mergeBase = revWalk.next()

    differentFolders(head, mergeBase, interestingPaths)
  }

  private def differentFolders(head: RevCommit, mergeBase: RevCommit, interestingPaths: Set[String])(implicit reader: ObjectReader): Set[String] = {
    val (rootPaths, subFolderPaths) = interestingPaths.partition(_ == "/")

    val affectedRootPaths = rootPaths.filter(_ => mergeBase.getTree != head.getTree)
    val affectedSubFolderPaths = if (subFolderPaths.isEmpty) Set.empty
    else {
      val treeFilter = AndTreeFilter.create(PathFilterGroup.createFromStrings(subFolderPaths.map(_.stripPrefix("/"))), treeDiffFilter)
      walk(mergeBase.getTree, head.getTree)(treeFilter, postOrderTraversal = true).map(_.slashPrefixedPath + "/").toSet.filter(subFolderPaths)
    }

    affectedRootPaths ++ affectedSubFolderPaths
  }
}
