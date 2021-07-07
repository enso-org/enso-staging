package org.enso.compiler

import com.oracle.truffle.api.TruffleFile
import com.typesafe.scalalogging.Logger
import org.enso.distribution.{DistributionManager, EditionManager, LanguageHome}
import org.enso.editions.{DefaultEdition, LibraryName, LibraryVersion}
import org.enso.interpreter.instrument.NotificationHandler
import org.enso.interpreter.runtime.builtin.Builtins
import org.enso.interpreter.runtime.util.TruffleFileSystem
import org.enso.interpreter.runtime.{Context, Module}
import org.enso.librarymanager.{
  DefaultLibraryProvider,
  ResolvedLibrary,
  ResolvingLibraryProvider
}
import org.enso.librarymanager.local.DefaultLocalLibraryProvider
import org.enso.logger.masking.MaskedPath
import org.enso.pkg.{Package, PackageManager, QualifiedName}

import java.nio.file.Path
import scala.util.Try

/** Manages loaded packages and modules. */
trait PackageRepository {

  /** Informs the repository that it should populate the top scope with modules
    * belonging to a given package.
    *
    * @param libraryName the name of the library that should be loaded
    * @return `Right(())` if the package was already loaded or successfully
    *         downloaded. A `Left` containing an error otherwise.
    */
  def ensurePackageIsLoaded(
    libraryName: LibraryName
  ): Either[PackageRepository.Error, Unit]

  /** Get a sequence of currently loaded packages. */
  def getLoadedPackages(): Seq[Package[TruffleFile]]

  /** Get a sequence of currently loaded modules. */
  def getLoadedModules(): Seq[Module]

  /** Get a loaded module by its qualified name. */
  def getLoadedModule(qualifiedName: String): Option[Module]

  /** Register the main project package. */
  def registerMainProjectPackage(
    libraryName: LibraryName,
    pkg: Package[TruffleFile]
  ): Unit

  /** Register a single module, outside of any packages or part of an already
    * loaded package, that has been created manually during runtime.
    */
  def registerModuleCreatedInRuntime(module: Module): Unit

  /** Removes a module with the given name from the list of loaded modules. */
  def deregisterModule(qualifiedName: String): Unit

  /** Modifies package and module names to reflect the project name change. */
  def renameProject(namespace: String, oldName: String, newName: String): Unit

  /** This is a temporary workaround that should be removed once we get
    * integrated with the editions.
    */
  def registerForPreload(packages: Seq[Package[TruffleFile]]): Unit
}

object PackageRepository {

  /** A trait representing errors reported by this system */
  sealed trait Error

  object Error {

    /** Indicates that a resolution error has happened, for example the package
      *  was not defined in the selected edition.
      */
    case class PackageCouldNotBeResolved(cause: Throwable) extends Error {
      override def toString: String =
        s"The package could not be resolved: ${cause.getMessage}"
    }

    /** Indicates that the package was missing and a download was attempted, but
      * it failed - for example due to connectivity problems or just because the
      * package did not exist in the repository.
      */
    case class PackageDownloadFailed(cause: Throwable) extends Error {
      override def toString: String =
        s"The package download has failed: ${cause.getMessage}"
    }

    /** Indicates that the package was already present in the cache (or within
      * local packages), but it could not be loaded, possibly to a filesystem
      * error or insufficient permissions.
      */
    case class PackageLoadingError(cause: String) extends Error {
      override def toString: String =
        s"The package could not be loaded: $cause"
    }
  }

  /** The default [[PackageRepository]] implementation.
    *
    * @param libraryProvider the [[ResolvingLibraryProvider]] which resolves
    *                        which library version should be imported and
    *                        locates them (or downloads if they are missing)
    * @param context the language context
    * @param builtins the builtins module
    * @param notificationHandler a notification handler
    */
  class Default(
    libraryProvider: ResolvingLibraryProvider,
    context: Context,
    builtins: Builtins,
    notificationHandler: NotificationHandler
  ) extends PackageRepository {

    private val logger = Logger[Default]

    implicit private val fs: TruffleFileSystem = new TruffleFileSystem
    private val packageManager                 = new PackageManager[TruffleFile]

    /** The mapping containing all loaded packages.
      *
      * It should be modified only from within synchronized sections, but it may
      * be always read. Thus elements should be added to this mapping only after
      * all library loading bookkeeping has been finished - so that if other,
      * unsynchronized threads read this map, every element it contains is
      * already fully processed.
      */
    val loadedPackages
      : collection.concurrent.Map[LibraryName, Option[Package[TruffleFile]]] = {
      val builtinsName = LibraryName(Builtins.NAMESPACE, Builtins.PACKAGE_NAME)
      collection.concurrent.TrieMap(builtinsName -> None)
    }

    /** The mapping containing loaded modules. */
    val loadedModules: collection.concurrent.Map[String, Module] =
      collection.concurrent.TrieMap(Builtins.MODULE_NAME -> builtins.getModule)

    /** @inheritdoc */
    override def registerMainProjectPackage(
      libraryName: LibraryName,
      pkg: Package[TruffleFile]
    ): Unit = registerPackageInternal(
      libraryName    = libraryName,
      pkg            = pkg,
      libraryVersion = LibraryVersion.Local,
      isLibrary      = false
    )

    private def registerPackageInternal(
      libraryName: LibraryName,
      libraryVersion: LibraryVersion,
      pkg: Package[TruffleFile],
      isLibrary: Boolean
    ): Unit = {
      val extensions = pkg.listPolyglotExtensions("java")
      extensions.foreach(context.getEnvironment.addToHostClassPath)

      pkg.listSources
        .map { srcFile =>
          new Module(srcFile.qualifiedName, pkg, srcFile.file)
        }
        .foreach(registerModule)

      if (isLibrary) {
        val root = Path.of(pkg.root.toString)
        notificationHandler.addedLibrary(libraryName, libraryVersion, root)
      }

      loadedPackages.put(libraryName, Some(pkg))
    }

    /** This package modifies the [[loadedPackages]], so it should be only
      * called from within synchronized sections.
      */
    private def loadPackage(
      libraryName: LibraryName,
      libraryVersion: LibraryVersion,
      root: Path
    ): Either[Error, Unit] = Try {
      logger.debug(
        s"Loading library $libraryName from " +
        s"[${MaskedPath(root).applyMasking()}]."
      )
      val rootFile = context.getEnvironment.getInternalTruffleFile(
        root.toAbsolutePath.normalize.toString
      )
      val pkg = packageManager.loadPackage(rootFile).get
      registerPackageInternal(
        libraryName    = libraryName,
        libraryVersion = libraryVersion,
        pkg            = pkg,
        isLibrary      = true
      )
    }.toEither.left.map { error => Error.PackageLoadingError(error.getMessage) }

    /** @inheritdoc */
    override def ensurePackageIsLoaded(
      libraryName: LibraryName
    ): Either[Error, Unit] = {
      runPreload()
      if (loadedPackages.contains(libraryName)) Right(())
      else {
        logger.trace(s"Resolving library $libraryName.")
        val resolvedLibrary = libraryProvider.findLibrary(libraryName)
        logger.whenTraceEnabled {
          resolvedLibrary match {
            case Left(error) =>
              logger.trace(s"Resolution failed with [$error].")
            case Right(resolved) =>
              logger.trace(
                s"Found library ${resolved.name} @ ${resolved.version} " +
                s"at [${MaskedPath(resolved.location).applyMasking()}]."
              )
          }
        }

        this.synchronized {
          // We check again inside of the monitor, in case that some other
          // thread has just added this library.
          if (loadedPackages.contains(libraryName)) Right(())
          else
            resolvedLibrary
              .flatMap { library =>
                loadPackage(library.name, library.version, library.location)
              }
              .left
              .map {
                case ResolvingLibraryProvider.Error.NotResolved(details) =>
                  Error.PackageCouldNotBeResolved(details)
                case ResolvingLibraryProvider.Error.DownloadFailed(reason) =>
                  Error.PackageDownloadFailed(reason)
                case ResolvingLibraryProvider.Error.RequestedLocalLibraryDoesNotExist =>
                  Error.PackageLoadingError(
                    "The local library has not been found on the local " +
                    "libraries search paths."
                  )
              }
        }
      }
    }

    /** @inheritdoc */
    override def getLoadedModules(): Seq[Module] = {
      runPreload()
      loadedModules.values.toSeq
    }

    /** @inheritdoc */
    override def getLoadedPackages(): Seq[Package[TruffleFile]] = {
      runPreload()
      loadedPackages.values.toSeq.flatten
    }

    /** @inheritdoc */
    override def getLoadedModule(qualifiedName: String): Option[Module] = {
      runPreload()
      loadedModules.get(qualifiedName)
    }

    /** @inheritdoc */
    override def registerModuleCreatedInRuntime(module: Module): Unit =
      registerModule(module)

    private def registerModule(module: Module): Unit =
      loadedModules.put(module.getName.toString, module)

    override def deregisterModule(qualifiedName: String): Unit =
      loadedModules.remove(qualifiedName)

    /** @inheritdoc */
    override def renameProject(
      namespace: String,
      oldName: String,
      newName: String
    ): Unit = this.synchronized {
      renamePackages(namespace, oldName, newName)
      renameModules(namespace, oldName, newName)
    }

    private def renamePackages(
      namespace: String,
      oldName: String,
      newName: String
    ): Unit = {
      val toChange = loadedPackages.toSeq.filter { case (name, _) =>
        name.namespace == namespace && name.name == oldName
      }

      for ((key, _) <- toChange) {
        loadedPackages.remove(key)
      }

      for ((key, pkgOption) <- toChange) {
        val newPkg = pkgOption.map(_.setPackageName(newName))
        val newKey = key.copy(name = newName)
        loadedPackages.put(newKey, newPkg)
      }
    }

    private def renameModules(
      namespace: String,
      oldName: String,
      newName: String
    ): Unit = {
      val separator: String = QualifiedName.separator
      val keys = loadedModules.keySet.filter(name =>
        name.startsWith(namespace + separator + oldName + separator)
      )

      for {
        key    <- keys
        module <- loadedModules.remove(key)
      } {
        module.renameProject(newName)
        loadedModules.put(module.getName.toString, module)
      }
    }

    /** Temporary workaround, will be removed once editions are integrated. */
    private var toPreload: List[Package[TruffleFile]] = Nil
    private def runPreload(): Unit = {
      for (pkg <- toPreload) {
        registerPackageInternal(
          libraryName    = pkg.libraryName,
          pkg            = pkg,
          libraryVersion = LibraryVersion.Local,
          isLibrary      = true
        )
      }
      toPreload = Nil
    }

    /** @inheritdoc */
    override def registerForPreload(packages: Seq[Package[TruffleFile]]): Unit =
      toPreload ++= packages
  }

  /** A temporary [[ResolvingLibraryProvider]] that ignores the edition and just
    * provides the local libraries.
    *
    * TODO [RW] it should be removed once the editions are integrated
    */
  private class TemporaryLocalProvider(searchPaths: List[Path])
      extends ResolvingLibraryProvider {

    private val localRepo = new DefaultLocalLibraryProvider(searchPaths)

    override def findLibrary(
      name: LibraryName
    ): Either[ResolvingLibraryProvider.Error, ResolvedLibrary] =
      localRepo
        .findLibrary(name)
        .map(ResolvedLibrary(name, LibraryVersion.Local, _))
        .toRight {
          ResolvingLibraryProvider.Error.RequestedLocalLibraryDoesNotExist
        }
  }

  def initializeRepository(
    projectPackage: Option[Package[TruffleFile]],
    languageHome: Option[String],
    distributionManager: DistributionManager,
    context: Context,
    builtins: Builtins,
    notificationHandler: NotificationHandler
  ): PackageRepository = {
    val rawEdition = projectPackage
      .flatMap(_.config.edition)
      .getOrElse(DefaultEdition.getDefaultEdition)

    val homeManager = languageHome.map { home => LanguageHome(Path.of(home)) }
    val editionSearchPaths =
      homeManager.map(_.editions).toList ++
      distributionManager.paths.editionSearchPaths
    val editionManager = new EditionManager(editionSearchPaths)
    val edition        = editionManager.resolveEdition(rawEdition).get

    val resolvingLibraryProvider =
      new DefaultLibraryProvider(
        distributionManager = distributionManager,
        languageHome        = homeManager,
        edition             = edition,
        preferLocalLibraries =
          projectPackage.map(_.config.preferLocalLibraries).getOrElse(false)
      )
    new Default(
      resolvingLibraryProvider,
      context,
      builtins,
      notificationHandler
    )
  }

  /** A temporary helper constructor for [[PackageRepository]] that does not
    * need any edition configuration.
    *
    * TODO [RW] it should be removed once the editions are integrated
    */
  def makeLegacyRepository(
    distributionManager: DistributionManager,
    context: Context,
    builtins: Builtins,
    notificationHandler: NotificationHandler
  ): PackageRepository = {
    val searchPaths = distributionManager.paths.localLibrariesSearchPaths.toList
    new Default(
      new TemporaryLocalProvider(searchPaths),
      context,
      builtins,
      notificationHandler
    )
  }
}
