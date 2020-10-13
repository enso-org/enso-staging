import com.typesafe.sbt.SbtLicenseReport.autoImportImpl.{
  licenseOverrides,
  licenseSelection
}
import com.typesafe.sbt.license
import sbt.Keys.{ivyModule, streams, update, updateClassifiers}
import sbt.{File, Project}
import src.main.scala.licenses.{
  DistributionDescription,
  SBTDistributionComponent
}

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object Distribution {

  /**
    * Creates a [[DistributionDescription]].
    */
  def apply(
    name: String,
    packageDestination: File,
    sbtComponents: Seq[SBTDistributionComponent]
  ): DistributionDescription =
    DistributionDescription(name, packageDestination, sbtComponents)

  /**
    * A macro that creates [[SBTDistributionComponent]] descriptions from a list
    * of project references.
    */
  def sbtProjects(projects: Project*): Seq[SBTDistributionComponent] =
    macro sbtProjectsImpl

  /**
    * Implementation of the [[sbtProjects]] macro.
    *
    * It triggers execution of the tasks that are used to get information from
    * SBT on each project.
    */
  def sbtProjectsImpl(c: blackbox.Context)(
    projects: c.Expr[Project]*
  ): c.Expr[Seq[SBTDistributionComponent]] = {
    import c.universe._
    val gathered = {
      projects.map(p =>
        reify {
          val deliberatelyTriggerAndIgnore = (p.splice / update).value

          val configs   = GatherLicenses.licenseConfigurations.value
          val ivyMod    = (p.splice / ivyModule).value
          val overrides = (p.splice / licenseOverrides).value.lift
          val report = license.LicenseReport.makeReport(
            ivyMod,
            configs,
            (p.splice / licenseSelection).value,
            overrides,
            (p.splice / streams).value.log
          )
          SBTDistributionComponent(
            p.splice.id,
            report,
            (p.splice / updateClassifiers).value
          )
        }
      )
    }
    c.Expr[Seq[SBTDistributionComponent]](
      Apply(reify(Seq).tree, gathered.map(_.tree).toList)
    )
  }
}
