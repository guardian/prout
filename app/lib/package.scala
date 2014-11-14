import lib.Config.Checkpoint
import org.eclipse.jgit.lib.{AbbreviatedObjectId, ObjectId}

import scala.concurrent.Future

package object lib {

  type CheckpointSnapshoter = Checkpoint => Future[Iterator[AbbreviatedObjectId]]

}
