package org.enso.launcher.releases.github

import org.enso.launcher.releases.{Release, SimpleReleaseProvider}

import scala.util.Try

/**
  * Implements [[SimpleReleaseProvider]] providing releases from a specified GitHub
  * repository using the GitHub Release API.
  *
  * @param owner owner of the repository
  * @param repositoryName name of the repository
  */
class GithubReleaseProvider(
  owner: String,
  repositoryName: String
) extends SimpleReleaseProvider {
  private val repo = GithubAPI.Repository(owner, repositoryName)

  /**
    * @inheritdoc
    */
  override def releaseForTag(tag: String): Try[Release] =
    GithubAPI.getRelease(repo, tag).waitForResult().map(GithubRelease)

  /**
    * @inheritdoc
    */
  override def listReleases(): Try[Seq[Release]] =
    GithubAPI.listReleases(repo).map(_.map(GithubRelease))
}
