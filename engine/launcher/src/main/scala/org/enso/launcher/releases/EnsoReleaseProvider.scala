package org.enso.launcher.releases
import nl.gn0s1s.bump.SemVer
import org.enso.launcher.OS

import scala.util.{Failure, Success, Try}

/**
  * A helper class that implements the shared logic of engine and launcher
  * releases which handles listing releases excluding broken ones.
  *
  * @param simpleReleaseProvider a base release provided that provides the raw
  *                              releases
  * @tparam ReleaseType type of the specific component's release, containing any
  *                     necessary metadata
  */
abstract class EnsoReleaseProvider[ReleaseType](
  simpleReleaseProvider: SimpleReleaseProvider
) extends ReleaseProvider[ReleaseType] {
  protected val tagPrefix = "enso-"

  /**
    * @inheritdoc
    */
  override def findLatestVersion(): Try[SemVer] =
    fetchAllValidVersions().flatMap { versions =>
      versions.sorted.lastOption.map(Success(_)).getOrElse {
        Failure(ReleaseProviderException("No valid engine versions were found"))
      }
    }

  /**
    * @inheritdoc
    */
  override def fetchAllValidVersions(): Try[Seq[SemVer]] =
    simpleReleaseProvider.listReleases().map { releases =>
      releases
        .filter(!_.isMarkedBroken)
        .map(_.tag.stripPrefix(tagPrefix))
        .flatMap(SemVer(_))
    }
}

object EnsoReleaseProvider {

  /**
    * Returns a full system-dependent package name for a component with a given
    * name and version.
    *
    * The package name can depend on the current OS and its architecture.
    */
  def packageNameForComponent(
    componentName: String,
    version: SemVer
  ): String = {
    val os = OS.operatingSystem match {
      case OS.Linux   => "linux"
      case OS.MacOS   => "macos"
      case OS.Windows => "windows"
    }
    val arch = OS.architecture
    val extension = OS.operatingSystem match {
      case OS.Linux   => ".tar.gz"
      case OS.MacOS   => ".tar.gz"
      case OS.Windows => ".zip"
    }
    s"enso-$componentName-$version-$os-$arch$extension"
  }
}
