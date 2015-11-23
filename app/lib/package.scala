import lib.Config.Checkpoint
import org.eclipse.jgit.lib.AbbreviatedObjectId

import scala.concurrent.Future

package object lib {

  type CheckpointSnapshoter = Checkpoint => Future[Iterator[AbbreviatedObjectId]]

}
