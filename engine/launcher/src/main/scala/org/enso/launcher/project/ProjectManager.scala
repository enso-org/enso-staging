package org.enso.launcher.project

import java.nio.file.Path

import nl.gn0s1s.bump.SemVer
import org.enso.launcher.{GlobalConfigurationManager, Logger}
import org.enso.pkg.{PackageManager, SemVerEnsoVersion}

import scala.util.{Failure, Try}

/**
  * A helper class for project management.
  *
  * It allows to create new project, open existing ones or traverse the
  * directory tree to find a project based on a path inside it.
  */
class ProjectManager(globalConfigurationManager: GlobalConfigurationManager) {

  private val packageManager = PackageManager.Default

  /**
    * Creates a new project at the specified path with the given name.
    *
    * If the version is not provided, the default Enso engine version is used.
    *
    * @param name specifies the name of the project
    * @param path specifies where the project should be created
    * @param ensoVersion if provided, specifies an exact Enso version that the
    *                    project should be associated with
    */
  def newProject(
    name: String,
    path: Path,
    ensoVersion: Option[SemVer] = None
  ): Unit = {
    packageManager.create(
      root = path.toFile,
      name = name,
      ensoVersion = SemVerEnsoVersion(
        ensoVersion.getOrElse(globalConfigurationManager.defaultVersion)
      )
    )
    Logger.info(s"Project created in `$path`.")
  }

  /**
    * Tries to load the project at the provided `path`.
    */
  def loadProject(path: Path): Try[Project] =
    packageManager
      .loadPackage(path.toFile)
      .map(new Project(_, globalConfigurationManager))
      .recoverWith(error => Failure(ProjectLoadingError(path, error)))

  /**
    * Traverses the directory tree looking for a project in one of the ancestors
    * of the provided `path`.
    *
    * If a package file is missing in a directory, its ancestors are searched
    * recursively. However if a package file exists in some directory, but there
    * are errors preventing from loading it, that error is reported.
    */
  def findProject(path: Path): Try[Option[Project]] =
    tryFindingProject(path.toAbsolutePath.normalize).map(Some(_)).recover {
      case PackageManager.PackageNotFound() => None
    }

  private def tryFindingProject(root: Path): Try[Project] =
    packageManager
      .loadPackage(root.toFile)
      .map(new Project(_, globalConfigurationManager))
      .recoverWith {
        case PackageManager.PackageNotFound() if root.getParent != null =>
          tryFindingProject(root.getParent)
        case otherError => Failure(otherError)
      }
}
