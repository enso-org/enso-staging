package org.enso.launcher.releases

import java.nio.file.Path

import org.enso.launcher.Logger
import org.enso.launcher.http.URIBuilder
import org.enso.launcher.releases.engine.{EngineRelease, EngineReleaseProvider}
import org.enso.launcher.releases.fallback.SimpleReleaseProviderWithFallback
import org.enso.launcher.releases.fallback.staticwebsite.StaticWebsiteFallbackReleaseProvider
import org.enso.launcher.releases.github.GithubReleaseProvider
import org.enso.launcher.releases.launcher.{
  LauncherRelease,
  LauncherReleaseProvider
}
import org.enso.launcher.releases.testing.FakeReleaseProvider

/**
  * Represents the default Enso repository providing releases for the engine and
  * the launcher.
  *
  * In test mode, the default GitHub repository can be overridden with a local
  * filesystem-backed repository.
  */
object EnsoRepository {
  // TODO [RW] The release provider will be moved from staging to the main
  //  repository, when the first official Enso release is released.
  private val githubRepository = new GithubReleaseProvider(
    "enso-org",
    "enso-staging"
  )

  private val launcherS3Fallback = new StaticWebsiteFallbackReleaseProvider(
    URIBuilder.fromHost("launcherfallback.release.enso.org") / "bucket",
    "launcher"
  )

  /**
    * Default provider of engine releases.
    */
  def defaultEngineReleaseProvider: ReleaseProvider[EngineRelease] =
    new EngineReleaseProvider(defaultEngineRepository)

  /**
    * Default provider of launcher releases.
    */
  def defaultLauncherReleaseProvider: ReleaseProvider[LauncherRelease] =
    new LauncherReleaseProvider(launcherRepository)

  /**
    * Overrides the default repository with a local filesystem based fake
    * repository.
    *
    * Currently only the launcher repository is overridden.
    *
    * Internal method used for testing.
    */
  def internalUseFakeRepository(fakeRepositoryRoot: Path): Unit =
    if (buildinfo.Info.isRelease)
      throw new IllegalStateException(
        "Internal testing function internalUseFakeRepository used in a " +
        "release build."
      )
    else {
      Logger.debug(s"[TEST] Using a fake repository at $fakeRepositoryRoot.")
      launcherRepository = makeFakeRepository(fakeRepositoryRoot)
    }

  private val defaultEngineRepository = githubRepository
  private val defaultLauncherRepository = new SimpleReleaseProviderWithFallback(
    baseProvider     = githubRepository,
    fallbackProvider = launcherS3Fallback
  )
  private var launcherRepository: SimpleReleaseProvider =
    defaultLauncherRepository

  private def makeFakeRepository(
    fakeRepositoryRoot: Path
  ): SimpleReleaseProvider =
    FakeReleaseProvider(fakeRepositoryRoot)
}
