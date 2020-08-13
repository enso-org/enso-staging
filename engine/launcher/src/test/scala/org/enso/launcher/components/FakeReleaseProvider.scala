package org.enso.launcher.components

import java.nio.file.{Files, Path, StandardCopyOption}

import org.enso.cli.{ProgressListener, TaskProgress}
import org.enso.launcher.releases.{
  Asset,
  Release,
  ReleaseProvider,
  ReleaseProviderException
}
import org.enso.launcher.{FileSystem, OS}

import scala.io.Source
import scala.sys.process._
import scala.util.{Success, Try, Using}

case class FakeReleaseProvider(
  releasesRoot: Path,
  copyIntoArchiveRoot: Seq[String] = Seq.empty
) extends ReleaseProvider {
  private val releases =
    FileSystem
      .listDirectory(releasesRoot)
      .map(FakeRelease(_, copyIntoArchiveRoot))

  /**
    * @inheritdoc
    */
  override def releaseForTag(tag: String): Try[Release] =
    releases
      .find(_.tag == tag)
      .toRight(ReleaseProviderException(s"Release $tag does not exist."))
      .toTry

  /**
    * @inheritdoc
    */
  override def listReleases(): Try[Seq[Release]] = Success(releases)
}

case class FakeRelease(path: Path, copyIntoArchiveRoot: Seq[String] = Seq.empty)
    extends Release {

  /**
    * @inheritdoc
    */
  override def tag: String = path.getFileName.toString

  /**
    * @inheritdoc
    */
  override def assets: Seq[Asset] = {
    val pathsToCopy = copyIntoArchiveRoot.map(path.resolve)
    FileSystem.listDirectory(path).map(FakeAsset(_, pathsToCopy))
  }
}

case class FakeAsset(source: Path, copyIntoArchiveRoot: Seq[Path] = Seq.empty)
    extends Asset {

  /**
    * @inheritdoc
    */
  override def fileName: String = source.getFileName.toString

  /**
    * @inheritdoc
    */
  override def downloadTo(path: Path): TaskProgress[Unit] = {
    val result = Try(copyFakeAsset(path))
    new TaskProgress[Unit] {
      override def addProgressListener(
        listener: ProgressListener[Unit]
      ): Unit = {
        listener.done(result)
      }
    }
  }

  private def copyFakeAsset(destination: Path): Unit =
    if (Files.isDirectory(source))
      copyArchive(destination)
    else
      copyNormalFile(destination)

  private def copyArchive(destination: Path): Unit = {
    val directoryName = source.getFileName.toString
    lazy val innerRoot = {
      val roots = FileSystem.listDirectory(source).filter(Files.isDirectory(_))
      if (roots.length > 1) {
        throw new IllegalStateException(
          "Cannot copy files into the root if there are more than one root."
        )
      }

      roots.headOption.getOrElse(source)
    }

    for (sourceToCopy <- copyIntoArchiveRoot) {
      Files.copy(
        sourceToCopy,
        innerRoot.resolve(sourceToCopy.getFileName),
        StandardCopyOption.REPLACE_EXISTING
      )
    }
    if (directoryName.endsWith(".tar.gz") && OS.isUNIX)
      packTarGz(source, destination)
    else if (directoryName.endsWith(".zip") && OS.isWindows)
      packZip(source, destination)
    else {
      throw new IllegalArgumentException(
        s"Fake-archive format $directoryName is not supported on " +
        s"${OS.operatingSystem}."
      )
    }
  }

  private def copyNormalFile(destination: Path): Unit =
    FileSystem.copyFile(source, destination)

  /**
    * @inheritdoc
    */
  override def fetchAsText(): TaskProgress[String] =
    if (Files.isDirectory(source))
      throw new IllegalStateException(
        "Cannot fetch a fake archive (a directory) as text."
      )
    else {
      val txt = Using(Source.fromFile(source.toFile)) { src =>
        src.getLines().mkString("\n")
      }
      (listener: ProgressListener[String]) => {
        listener.done(txt)
      }
    }

  private def packTarGz(source: Path, destination: Path): Unit = {
    val files = FileSystem.listDirectory(source)
    val exitCode = Process(
      Seq(
        "tar",
        "-czf",
        destination.toAbsolutePath.toString
      ) ++ files.map(_.getFileName.toString),
      source.toFile
    ).!
    if (exitCode != 0) {
      throw new RuntimeException(
        s"tar failed. Cannot create fake-archive for $source"
      )
    }
  }

  private def packZip(source: Path, destination: Path): Unit = {
    val files = FileSystem.listDirectory(source)
    val exitCode = Process(
      Seq(
        "powershell",
        "Compress-Archive",
        "-Path",
        files.map(_.getFileName.toString).mkString(","),
        "-DestinationPath",
        destination.toAbsolutePath.toString
      ),
      source.toFile
    ).!
    if (exitCode != 0) {
      throw new RuntimeException(
        s"tar failed. Cannot create fake-archive for $source"
      )
    }
  }
}
