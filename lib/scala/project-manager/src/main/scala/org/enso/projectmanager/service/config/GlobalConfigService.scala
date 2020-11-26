package org.enso.projectmanager.service.config

import io.circe.Json
import nl.gn0s1s.bump.SemVer
import org.enso.pkg.{DefaultEnsoVersion, EnsoVersion, SemVerEnsoVersion}
import org.enso.projectmanager.control.core.CovariantFlatMap
import org.enso.projectmanager.control.effect.{ErrorChannel, Sync}
import org.enso.projectmanager.service.config.GlobalConfigServiceFailure.ConfigurationFileAccessFailure
import org.enso.projectmanager.service.versionmanagement.NoOpInterface
import org.enso.projectmanager.versionmanagement.DistributionConfiguration
import org.enso.runtimeversionmanager.config.GlobalConfigurationManager

/** Implementation of global configuration management logic.
  *
  * @param distributionConfiguration a distribution configuration
  */
class GlobalConfigService[F[+_, +_]: Sync: ErrorChannel: CovariantFlatMap](
  distributionConfiguration: DistributionConfiguration
) extends GlobalConfigServiceApi[F] {

  val configurationManager = new GlobalConfigurationManager(
    distributionConfiguration.makeRuntimeVersionManager(new NoOpInterface),
    distributionConfiguration.distributionManager
  )

  override def getKey(
    key: String
  ): F[GlobalConfigServiceFailure, Option[String]] =
    Sync[F].blockingOp {
      val valueOption = configurationManager.getConfig.original.apply(key)
      valueOption.map(json => json.asString.getOrElse(json.toString()))
    }.recoverAccessErrors

  override def setKey(
    key: String,
    value: String
  ): F[GlobalConfigServiceFailure, Unit] = Sync[F].blockingOp {
    configurationManager.updateConfigRaw(key, Json.fromString(value))
  }.recoverAccessErrors

  override def deleteKey(key: String): F[GlobalConfigServiceFailure, Unit] =
    Sync[F].blockingOp {
      configurationManager.removeFromConfig(key)
    }.recoverAccessErrors

  override def getDefaultEnsoVersion: F[GlobalConfigServiceFailure, SemVer] =
    Sync[F].blockingOp {
      configurationManager.defaultVersion
    }.recoverAccessErrors

  implicit class AccessErrorRecovery[A](fa: F[Throwable, A]) {
    def recoverAccessErrors: F[GlobalConfigServiceFailure, A] = {
      ErrorChannel[F].mapError(fa) { throwable =>
        ConfigurationFileAccessFailure(throwable.getMessage)
      }
    }
  }

  override def resolveEnsoVersion(
    ensoVersion: EnsoVersion
  ): F[GlobalConfigServiceFailure, SemVer] = ensoVersion match {
    case DefaultEnsoVersion         => getDefaultEnsoVersion
    case SemVerEnsoVersion(version) => CovariantFlatMap[F].pure(version)
  }
}
