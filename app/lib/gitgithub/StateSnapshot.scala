package lib.gitgithub

trait StateSnapshot[PersistableState] {
  val newPersistableState: PersistableState
}
