package org.enso.launcher

import nl.gn0s1s.bump.SemVer

object Constants {

  /** The engine version in which the uploads command has been introduced.
    *
    * It is used to check by the launcher if the engine can handle this command
    * and provide better error messages if it cannot.
    */
  val uploadIntroducedVersion: SemVer =
    SemVer(0, 2, 17, Some("SNAPSHOT"))

  /** The engine version in which the dependency preinstall command has been
    * introduced.
    *
    * It is used to check by the launcher if the engine can handle this command
    * and provide better error messages if it cannot.
    */
  val preinstallDependenciesIntroducedVersion: SemVer =
    SemVer(0, 2, 28, Some("SNAPSHOT"))

  /** The upload URL associated with the main Enso library repository. */
  val defaultUploadUrl = "https://publish.libraries.release.enso.org/"
}
