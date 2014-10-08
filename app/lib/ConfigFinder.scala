package lib

import com.madgag.git._
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
}
