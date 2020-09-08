package org.enso.launcher.upgrade

import java.nio.file.{Files, Path, StandardCopyOption}

import io.circe.parser
import nl.gn0s1s.bump.SemVer
import org.enso.launcher.FileSystem.PathSyntax
import org.enso.launcher._
import org.scalatest.exceptions.TestFailedException
import org.scalatest.{BeforeAndAfterAll, OptionValues}

class UpgradeSpec
    extends NativeTest
    with WithTemporaryDirectory
    with BeforeAndAfterAll
    with OptionValues {

  /**
    * Location of the fake releases root.
    */
  private val fakeReleaseRoot = Path
      .of(
        getClass
          .getResource("/org/enso/launcher/components/fake-releases")
          .toURI
      ) / "launcher"

  /**
    * Location of built Rust artifacts.
    */
  private val rustBuildRoot = Path.of("./target/rust/debug/")

  /**
    * Path to a launcher shim that pretends to be `version`.
    */
  private def builtLauncherBinary(version: SemVer): Path = {
    val simplifiedVersion = version.toString.replaceAll("[.-]", "")
    rustBuildRoot / OS.executableName(s"launcher_$simplifiedVersion")
  }

  /**
    * Copies a launcher shim into the fake release directory.
    */
  private def prepareLauncherBinary(version: SemVer): Unit = {
    val os          = OS.operatingSystem.configName
    val arch        = OS.architecture
    val ext         = if (OS.isWindows) "zip" else "tar.gz"
    val packageName = s"enso-launcher-$version-$os-$arch.$ext"
    val destinationDirectory =
      fakeReleaseRoot / s"enso-$version" / packageName / "enso" / "bin"
    Files.createDirectories(destinationDirectory)
    Files.copy(
      builtLauncherBinary(version),
      destinationDirectory / OS.executableName("enso"),
      StandardCopyOption.REPLACE_EXISTING
    )
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    prepareLauncherBinary(SemVer(0, 0, 0))
    prepareLauncherBinary(SemVer(0, 0, 1))
    prepareLauncherBinary(SemVer(0, 0, 2))
    prepareLauncherBinary(SemVer(0, 0, 3))
  }

  /**
    * Prepares a launcher distribution in the temporary test location.
    *
    * If `launcherVersion` is not provided, the default one is used.
    *
    * It waits a 100ms delay after creating the launcher copy to ensure that the
    * copy can be called right away after calling this function. It is not
    * absolutely certain that this is helpful, but from time to time, the tests
    * fail because the filesystem does not allow to access the executable as
    * 'not-ready'. This delay is an attempt to make the tests more stable.
    */
  private def prepareDistribution(
    portable: Boolean,
    launcherVersion: Option[SemVer] = None
  ): Unit = {
    val sourceLauncherLocation =
      launcherVersion.map(builtLauncherBinary).getOrElse(baseLauncherLocation)
    Files.createDirectories(launcherPath.getParent)
    Files.copy(sourceLauncherLocation, launcherPath)
    if (portable) {
      val root = launcherPath.getParent.getParent
      FileSystem.writeTextFile(root / ".enso.portable", "mark")
    }
    Thread.sleep(100)
  }

  /**
    * Path to the launcher executable in the temporary distribution.
    */
  private def launcherPath =
    getTestDirectory / "enso" / "bin" / OS.executableName("enso")

  /**
    * Runs `enso version` to inspect the version reported by the launcher.
    * @return the reported version
    */
  private def checkVersion(): SemVer = {
    val run = runLauncherAt(
      launcherPath,
      Seq("version", "--json", "--only-launcher")
    )
    run should returnSuccess
    val version = parser.parse(run.stdout).getOrElse {
      throw new TestFailedException(
        s"Version should be a valid JSON string, got '${run.stdout}' instead.",
        1
      )
    }
    SemVer(version.asObject.value.apply("version").value.asString.value).value
  }

  /**
    * Runs the launcher in the temporary distribution.
    *
    * @param args arguments for the launcher
    * @param extraEnv environment variable overrides
    * @return result of the run
    */
  private def run(
    args: Seq[String],
    extraEnv: Map[String, String] = Map.empty
  ): RunResult = {
    val testArgs = Seq(
      "--internal-emulate-repository",
      fakeReleaseRoot.toAbsolutePath.toString,
      "--auto-confirm",
      "--hide-progress"
    )
    runLauncherAt(launcherPath, testArgs ++ args, extraEnv)
  }

  "upgrade" should {
    "upgrade/downgrade to latest version (excluding broken)" in {
      // precondition for the test to make sense
      SemVer(buildinfo.Info.ensoVersion).value should be > SemVer(0, 0, 3)

      prepareDistribution(portable = true)
      run(Seq("upgrade")) should returnSuccess

      checkVersion() shouldEqual SemVer(0, 0, 3)
    }

    "upgrade or downgrade to a specific version " +
    "(and update necessary files)" in {
      prepareDistribution(
        portable        = true,
        launcherVersion = Some(SemVer(0, 0, 0))
      )
      val root = launcherPath.getParent.getParent
      FileSystem.writeTextFile(root / "README.md", "Old readme")
      checkVersion() shouldEqual SemVer(0, 0, 0)
      run(Seq("upgrade", "0.0.1")) should returnSuccess
      checkVersion() shouldEqual SemVer(0, 0, 1)
      TestHelpers.readFileContent(root / "README.md").trim shouldEqual "Content"
      TestHelpers
        .readFileContent(root / "components-licences" / "test-license.txt")
        .trim shouldEqual "Test license"
    }

    "upgrade also in installed mode" in {
      prepareDistribution(
        portable        = false,
        launcherVersion = Some(SemVer(0, 0, 0))
      )
      val dataRoot   = getTestDirectory / "data"
      val configRoot = getTestDirectory / "config"
      checkVersion() shouldEqual SemVer(0, 0, 0)
      val env = Map(
        "ENSO_DATA_DIRECTORY"   -> dataRoot.toString,
        "ENSO_CONFIG_DIRECTORY" -> configRoot.toString
      )
      run(
        Seq("upgrade", "0.0.1"),
        extraEnv = env
      ) should returnSuccess
      checkVersion() shouldEqual SemVer(0, 0, 1)
      TestHelpers
        .readFileContent(dataRoot / "README.md")
        .trim shouldEqual "Content"
      TestHelpers
        .readFileContent(dataRoot / "components-licences" / "test-license.txt")
        .trim shouldEqual "Test license"
    }

    "perform a multi-step upgrade if necessary" in {
      // 0.0.3 can only be upgraded from 0.0.2 which can only be upgraded from
      // 0.0.1, so the upgrade path should be following:
      // 0.0.0 -> 0.0.1 -> 0.0.2 -> 0.0.3
      prepareDistribution(
        portable        = true,
        launcherVersion = Some(SemVer(0, 0, 0))
      )

      checkVersion() shouldEqual SemVer(0, 0, 0)
      run(Seq("upgrade", "0.0.3")) should returnSuccess
      checkVersion() shouldEqual SemVer(0, 0, 3)

      val launchedVersions = Seq(
        "0.0.0",
        "0.0.0",
        "0.0.1",
        "0.0.2",
        "0.0.3"
      )

      val reportedLaunchLog = TestHelpers
        .readFileContent(launcherPath.getParent / ".launcher_version_log")
        .trim
        .linesIterator
        .toSeq

      reportedLaunchLog shouldEqual launchedVersions
    }

    "automatically trigger if an action requires a newer version and re-run " +
    "that action with the upgraded launcher" ignore {
      prepareDistribution(
        portable        = true,
        launcherVersion = Some(SemVer(0, 0, 2))
      )
      val enginesPath = getTestDirectory / "enso" / "dist"
      Files.createDirectories(enginesPath)

      // TODO [RW] re-enable this test when #1046 is done and the engine
      //  distribution can be used in the test
//      FileSystem.copyDirectory(
//        Path.of("target/distribution/"),
//        enginesPath / "0.1.0"
//      )
      val script  = getTestDirectory / "script.enso"
      val message = "Hello from test"
      val content =
        s"""from Builtins import all
           |main = IO.println "$message"
           |""".stripMargin
      FileSystem.writeTextFile(script, content)

      // TODO [RW] make sure the right `java` is used to run the engine
      //  (this should be dealt with in #1046)
      val result = run(
        Seq(
          "run",
          script.toAbsolutePath.toString,
          "--use-system-jvm",
          "--use-enso-version",
          "0.1.0"
        )
      )
      result should returnSuccess
      result.stdout should include(message)
    }
  }
}
