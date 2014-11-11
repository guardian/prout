import lib.Config.Checkpoint
import org.eclipse.jgit.lib.ObjectId

import scala.concurrent.Future

package object lib {

  type CheckpointSnapshoter = Checkpoint => Future[Option[ObjectId]]

}
