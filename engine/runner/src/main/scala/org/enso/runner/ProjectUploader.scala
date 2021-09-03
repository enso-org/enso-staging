package org.enso.runner

import com.typesafe.scalalogging.Logger
import org.enso.cli.ProgressBar
import org.enso.cli.task.{ProgressReporter, TaskProgress}
import org.enso.languageserver.libraries.CompilerBasedDependencyExtractor
import org.enso.libraryupload.{auth, LibraryUploader}
import org.enso.loggingservice.LogLevel
import org.enso.pkg.PackageManager

import java.nio.file.Path

/** Gathers helper functions for uploading a library project. */
object ProjectUploader {

  private lazy val logger = Logger[ProjectUploader.type]

  /** Uploads a project to a library repository.
    *
    * @param projectRoot path to the root of the project
    * @param uploadUrl URL of upload endpoint of the repository to upload to
    * @param authToken an optional token used for authentication in the
    *                  repository
    * @param showProgress specifies if CLI progress bars should be displayed
    *                     showing progress of compression and upload
    * @param logLevel the log level to use for the context gathering
    *                 dependencies
    */
  def uploadProject(
    projectRoot: Path,
    uploadUrl: String,
    authToken: Option[String],
    showProgress: Boolean,
    logLevel: LogLevel
  ): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val progressReporter = new ProgressReporter {
      override def trackProgress(
        message: String,
        task: TaskProgress[_]
      ): Unit = {
        logger.info(message)
        if (showProgress) {
          ProgressBar.waitWithProgress(task)
        }
      }
    }

    val token = authToken match {
      case Some(value) => auth.SimpleHeaderToken(value)
      case None        => auth.NoAuthorization
    }

    val dependencyExtractor = new CompilerBasedDependencyExtractor(logLevel)

    LibraryUploader(dependencyExtractor)
      .uploadLibrary(
        projectRoot,
        uploadUrl,
        token,
        progressReporter
      )
      .get
  }

  /** Updates manifest of the project.
    *
    * @param projectRoot path to the root of the project
    * @param logLevel the log level to use for the context gathering
    *                 dependencies
    */
  def updateManifest(projectRoot: Path, logLevel: LogLevel): Unit = {
    val pkg = PackageManager.Default.loadPackage(projectRoot.toFile).get

    val dependencyExtractor = new CompilerBasedDependencyExtractor(logLevel)
    LibraryUploader(dependencyExtractor).updateManifest(pkg).get
  }
}
