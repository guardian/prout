package configuration

import cats.effect.std.Dispatcher
import cats.effect.unsafe.IORuntime
import cats.effect.{IO, Resource}
import play.api.BuiltInComponents

trait CatsEffectComponents { self: BuiltInComponents =>
  /**
   * Allocates a cats-effect Resource and registers its release
   * action with Play’s ApplicationLifecycle.
   */
  def allocateResource[A](res: Resource[IO, A])(using IORuntime): A = {
    // Run the resource allocation synchronously on startup
    val (alloc, release) = res.allocated.unsafeRunSync()

    // Register cleanup with Play's lifecycle
    applicationLifecycle.addStopHook { () => release.unsafeToFuture() }

    alloc
  }
}
