package org.enso.languageserver.search

import java.util.UUID
import akka.actor.{Actor, ActorRef, Props, Stash}
import akka.pattern.{ask, pipe}
import com.typesafe.scalalogging.LazyLogging
import org.enso.languageserver.capability.CapabilityProtocol.{
  AcquireCapability,
  CapabilityAcquired,
  CapabilityReleased,
  ReleaseCapability
}
import org.enso.languageserver.data.{
  CapabilityRegistration,
  ClientId,
  Config,
  ReceivesSuggestionsDatabaseUpdates
}
import org.enso.languageserver.event.InitializedEvent
import org.enso.languageserver.filemanager.{
  ContentRootType,
  FileDeletedEvent,
  Path
}
import org.enso.languageserver.refactoring.ProjectNameChangedEvent
import org.enso.languageserver.search.SearchProtocol._
import org.enso.languageserver.search.handler.{
  ImportModuleHandler,
  InvalidateModulesIndexHandler
}
import org.enso.languageserver.session.SessionRouter.DeliverToJsonController
import org.enso.languageserver.util.UnhandledLogging
import org.enso.logger.masking.MaskedPath
import org.enso.pkg.PackageManager
import org.enso.polyglot.Suggestion
import org.enso.polyglot.data.TypeGraph
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.searcher.data.QueryResult
import org.enso.searcher.{FileVersionsRepo, SuggestionsRepo}
import org.enso.text.ContentVersion
import org.enso.text.editing.model.Position

import scala.concurrent.Future
import scala.util.{Failure, Success}

/** The handler of search requests.
  *
  * Handler initializes the database and responds to the search requests.
  *
  * == Implementation ==
  *
  * {{
  *
  *                               +--------------------+
  *                               | SuggestionsRepo    |
  *                               +---------+----------+
  *                                         ^
  *              Capability,Search          |
  *              Request/Response           v
  *  +---------+<---------------->+---------+----------+
  *  | Clients |                  | SuggestionsHandler |
  *  +---------+<-----------------+---------+----------+
  *              Database Update            ^
  *              Notifications              |
  *                                         |
  *                               +---------+----------+
  *                               | RuntimeConnector   |
  *                               +----+----------+----+
  *                                    ^          ^
  *          SuggestionsDatabaseUpdate |          | ExpressionValuesComputed
  *                                    |          |
  *                   +----------------+--+    +--+--------------------+
  *                   | EnsureCompiledJob |    | IdExecutionInstrument |
  *                   +-------------------+    +-----------------------+
  *
  * }}
  *
  * @param config the server configuration
  * @param suggestionsRepo the suggestions repo
  * @param sessionRouter the session router
  * @param runtimeConnector the runtime connector
  */
final class SuggestionsHandler(
  config: Config,
  suggestionsRepo: SuggestionsRepo[Future],
  fileVersionsRepo: FileVersionsRepo[Future],
  sessionRouter: ActorRef,
  runtimeConnector: ActorRef
) extends Actor
    with Stash
    with LazyLogging
    with UnhandledLogging {

  import SuggestionsHandler.ProjectNameUpdated
  import context.dispatcher

  private val timeout = config.executionContext.requestTimeout

  override def preStart(): Unit = {
    logger.info(
      "Starting suggestions handler from [{}, {}, {}].",
      config,
      suggestionsRepo,
      fileVersionsRepo
    )
    context.system.eventStream
      .subscribe(self, classOf[Api.ExpressionUpdates])
    context.system.eventStream
      .subscribe(self, classOf[Api.SuggestionsDatabaseModuleUpdateNotification])
    context.system.eventStream.subscribe(self, classOf[ProjectNameChangedEvent])
    context.system.eventStream.subscribe(self, classOf[FileDeletedEvent])
    context.system.eventStream
      .subscribe(self, InitializedEvent.SuggestionsRepoInitialized.getClass)
    context.system.eventStream
      .subscribe(self, InitializedEvent.TruffleContextInitialized.getClass)

    config.contentRoots.foreach {
      case (_, root) if root.`type` == ContentRootType.Project =>
        PackageManager.Default
          .loadPackage(root.file)
          .fold(
            t => {
              logger.error(
                "Failed to read the package definition from [{}]. {} {}",
                MaskedPath(root.file.toPath),
                t.getClass.getName,
                t.getMessage
              )
            },
            pkg => self ! ProjectNameUpdated(pkg.config.name)
          )
      case _ =>
    }
  }

  override def receive: Receive =
    initializing(SuggestionsHandler.Initialization())

  def initializing(init: SuggestionsHandler.Initialization): Receive = {
    case ProjectNameChangedEvent(oldName, newName) =>
      logger.info(
        "Initializing: project name changed from [{}] to [{}].",
        oldName,
        newName
      )
      suggestionsRepo
        .renameProject(oldName, newName)
        .map(_ => ProjectNameUpdated(newName))
        .pipeTo(self)

    case ProjectNameUpdated(name, updates) =>
      logger.info("Initializing: project name is updated to [{}].", name)
      updates.foreach(sessionRouter ! _)
      tryInitialize(init.copy(project = Some(name)))

    case InitializedEvent.SuggestionsRepoInitialized =>
      logger.info("Initializing: suggestions repo initialized.")
      tryInitialize(
        init.copy(suggestions =
          Some(InitializedEvent.SuggestionsRepoInitialized)
        )
      )

    case InitializedEvent.TruffleContextInitialized =>
      logger.info("Initializing: Truffle context initialized.")
      val requestId = UUID.randomUUID()
      runtimeConnector
        .ask(Api.Request(requestId, Api.GetTypeGraphRequest()))(timeout, self)
        .pipeTo(self)

    case Api.Response(_, Api.GetTypeGraphResponse(g)) =>
      logger.info("Initializing: got type graph response.")
      tryInitialize(init.copy(typeGraph = Some(g)))

    case _ => stash()
  }

  def verifying(
    projectName: String,
    graph: TypeGraph
  ): Receive = {
    case Api.Response(_, Api.VerifyModulesIndexResponse(toRemove)) =>
      logger.info("Verifying: got verification response.")
      suggestionsRepo
        .removeModules(toRemove)
        .map(_ => SuggestionsHandler.Verified)
        .pipeTo(self)

    case SuggestionsHandler.Verified =>
      logger.info("Verified.")
      context.become(initialized(projectName, graph, Set()))
      unstashAll()

    case _ =>
      stash()
  }

  def initialized(
    projectName: String,
    graph: TypeGraph,
    clients: Set[ClientId]
  ): Receive = {
    case AcquireCapability(
          client,
          CapabilityRegistration(ReceivesSuggestionsDatabaseUpdates())
        ) =>
      sender() ! CapabilityAcquired
      context.become(initialized(projectName, graph, clients + client.clientId))

    case ReleaseCapability(
          client,
          CapabilityRegistration(ReceivesSuggestionsDatabaseUpdates())
        ) =>
      sender() ! CapabilityReleased
      context.become(initialized(projectName, graph, clients - client.clientId))

    case msg: Api.SuggestionsDatabaseModuleUpdateNotification =>
      logger.debug("Got module update [{}].", MaskedPath(msg.file.toPath))
      val isVersionChanged =
        fileVersionsRepo.getVersion(msg.file).map { digestOpt =>
          !digestOpt.map(ContentVersion(_)).contains(msg.version)
        }
      val applyUpdatesIfVersionChanged =
        isVersionChanged.flatMap { isChanged =>
          if (isChanged) applyDatabaseUpdates(msg).map(Some(_))
          else Future.successful(None)
        }
      applyUpdatesIfVersionChanged
        .onComplete {
          case Success(Some(notification)) =>
            logger.debug(
              "Complete module update [{}].",
              MaskedPath(msg.file.toPath)
            )
            if (notification.updates.nonEmpty) {
              clients.foreach { clientId =>
                sessionRouter ! DeliverToJsonController(clientId, notification)
              }
            }
          case Success(None) =>
            logger.debug(
              "Skip module update, version not changed [{}].",
              MaskedPath(msg.file.toPath)
            )
          case Failure(ex) =>
            logger.error(
              "Error applying suggestion database updates [{}, {}]. {}",
              MaskedPath(msg.file.toPath),
              msg.version,
              ex.getMessage
            )
        }

    case Api.ExpressionUpdates(_, updates) =>
      logger.debug(
        "Received expression updates [{}].",
        updates.map(u => (u.expressionId, u.expressionType))
      )
      val types = updates.toSeq
        .flatMap(update => update.expressionType.map(update.expressionId -> _))
      suggestionsRepo
        .updateAll(types)
        .map { case (version, updatedIds) =>
          val updates = types.zip(updatedIds).collect {
            case ((_, typeValue), Some(suggestionId)) =>
              SuggestionsDatabaseUpdate.Modify(
                id         = suggestionId,
                returnType = Some(fieldUpdate(typeValue))
              )
          }
          SuggestionsDatabaseUpdateNotification(version, updates)
        }
        .onComplete {
          case Success(notification) =>
            if (notification.updates.nonEmpty) {
              clients.foreach { clientId =>
                sessionRouter ! DeliverToJsonController(clientId, notification)
              }
            }
          case Failure(ex) =>
            logger.error(
              "Error applying changes from computed values [{}]. {}",
              updates.map(_.expressionId),
              ex.getMessage
            )
        }

    case GetSuggestionsDatabaseVersion =>
      suggestionsRepo.currentVersion
        .map(GetSuggestionsDatabaseVersionResult)
        .pipeTo(sender())

    case GetSuggestionsDatabase =>
      suggestionsRepo.getAll
        .map { case (version, entries) =>
          GetSuggestionsDatabaseResult(
            version,
            entries.map(SuggestionDatabaseEntry(_))
          )
        }
        .pipeTo(sender())

    case Completion(path, pos, selfType, returnType, tags) =>
      val selfTypes = selfType.toList.flatMap(ty => ty :: graph.getParents(ty))
      getModuleName(projectName, path)
        .fold(
          Future.successful,
          module =>
            suggestionsRepo
              .search(
                Some(module),
                selfTypes,
                returnType,
                tags.map(_.map(SuggestionKind.toSuggestion)),
                Some(toPosition(pos))
              )
              .map(CompletionResult.tupled)
        )
        .pipeTo(sender())

    case Import(suggestionId) =>
      val action = for {
        result <- suggestionsRepo.select(suggestionId)
      } yield result
        .map(SearchProtocol.ImportSuggestion)
        .getOrElse(SearchProtocol.SuggestionNotFoundError)

      val handler = context.system
        .actorOf(ImportModuleHandler.props(config, timeout, runtimeConnector))
      action.pipeTo(handler)(sender())

    case FileDeletedEvent(path) =>
      getModuleName(projectName, path)
        .fold(
          err => Future.successful(Left(err)),
          module =>
            suggestionsRepo
              .removeModules(Seq(module))
              .map { case (version, ids) =>
                Right(
                  SuggestionsDatabaseUpdateNotification(
                    version,
                    ids.map(SuggestionsDatabaseUpdate.Remove)
                  )
                )
              }
        )
        .onComplete {
          case Success(Right(notification)) =>
            if (notification.updates.nonEmpty) {
              clients.foreach { clientId =>
                sessionRouter ! DeliverToJsonController(clientId, notification)
              }
            }
          case Success(Left(err)) =>
            logger.error(
              s"Error cleaning the index after file delete event [{}].",
              err
            )
          case Failure(ex) =>
            logger.error(
              "Error cleaning the index after file delete event. {}",
              ex.getMessage
            )
        }

    case InvalidateSuggestionsDatabase =>
      val action = for {
        _ <- suggestionsRepo.clean
        _ <- fileVersionsRepo.clean
      } yield SearchProtocol.InvalidateModulesIndex

      val handler = context.system
        .actorOf(
          InvalidateModulesIndexHandler.props(config, timeout, runtimeConnector)
        )
      action.pipeTo(handler)(sender())

    case ProjectNameChangedEvent(oldName, newName) =>
      suggestionsRepo
        .renameProject(oldName, newName)
        .map {
          case (version, moduleIds, selfTypeIds, returnTypeIds, argumentIds) =>
            val suggestionModuleUpdates = moduleIds.map {
              case (suggestionId, moduleName) =>
                SuggestionsDatabaseUpdate.Modify(
                  id     = suggestionId,
                  module = Some(fieldUpdate(moduleName))
                )
            }
            val selfTypeUpdates = selfTypeIds.map {
              case (suggestionId, selfType) =>
                SuggestionsDatabaseUpdate.Modify(
                  id       = suggestionId,
                  selfType = Some(fieldUpdate(selfType))
                )
            }
            val returnTypeUpdates = returnTypeIds.map {
              case (suggestionId, returnType) =>
                SuggestionsDatabaseUpdate.Modify(
                  id         = suggestionId,
                  returnType = Some(fieldUpdate(returnType))
                )
            }
            val argumentUpdates =
              argumentIds.groupBy(_._1).map { case (suggestionId, grouped) =>
                val argumentUpdates = grouped.map { case (_, index, typeName) =>
                  SuggestionArgumentUpdate.Modify(
                    index    = index,
                    reprType = Some(fieldUpdate(typeName))
                  )
                }
                SuggestionsDatabaseUpdate.Modify(
                  id        = suggestionId,
                  arguments = Some(argumentUpdates)
                )
              }
            val notification =
              SuggestionsDatabaseUpdateNotification(
                version,
                suggestionModuleUpdates ++ selfTypeUpdates ++ returnTypeUpdates ++ argumentUpdates
              )
            val updates = clients.map(DeliverToJsonController(_, notification))
            ProjectNameUpdated(newName, updates)
        }
        .pipeTo(self)

    case ProjectNameUpdated(name, updates) =>
      updates.foreach(sessionRouter ! _)
      context.become(initialized(name, graph, clients))
  }

  /** Transition the initialization process.
    *
    * @param state current initialization state
    */
  private def tryInitialize(state: SuggestionsHandler.Initialization): Unit = {
    state.initialized.fold(context.become(initializing(state))) {
      case (name, graph) =>
        logger.debug("Initialized with state [{}].", state)
        val requestId = UUID.randomUUID()
        suggestionsRepo.getAllModules
          .flatMap { modules =>
            runtimeConnector.ask(
              Api.Request(requestId, Api.VerifyModulesIndexRequest(modules))
            )(timeout, self)
          }
          .pipeTo(self)
        context.become(verifying(name, graph))
        unstashAll()
    }
  }

  /** Handle the suggestions database update.
    *
    * Function applies notification updates on the suggestions database and
    * builds the notification to the user
    *
    * @param msg the suggestions database update notification from runtime
    * @return the API suggestions database update notification
    */
  private def applyDatabaseUpdates(
    msg: Api.SuggestionsDatabaseModuleUpdateNotification
  ): Future[SuggestionsDatabaseUpdateNotification] =
    for {
      actionResults      <- suggestionsRepo.applyActions(msg.actions)
      (version, results) <- suggestionsRepo.applyTree(msg.updates)
      _                  <- fileVersionsRepo.setVersion(msg.file, msg.version.toDigest)
    } yield {
      val actionUpdates = actionResults.flatMap {
        case QueryResult(ids, Api.SuggestionsDatabaseAction.Clean(_)) =>
          ids.map(SuggestionsDatabaseUpdate.Remove)
      }
      val treeUpdates = results.flatMap {
        case QueryResult(ids, Api.SuggestionUpdate(suggestion, action)) =>
          val verb = action.getClass.getSimpleName
          action match {
            case Api.SuggestionAction.Add() =>
              if (ids.isEmpty)
                logger.error("Failed to {} [{}].", verb, suggestion)
              ids.map(id => SuggestionsDatabaseUpdate.Add(id, suggestion))
            case Api.SuggestionAction.Remove() =>
              if (ids.isEmpty)
                logger.error(s"Failed to {} [{}].", verb, suggestion)
              ids.map(id => SuggestionsDatabaseUpdate.Remove(id))
            case m: Api.SuggestionAction.Modify =>
              ids.map { id =>
                SuggestionsDatabaseUpdate.Modify(
                  id            = id,
                  externalId    = m.externalId.map(fieldUpdateOption),
                  arguments     = m.arguments.map(_.map(toApiArgumentAction)),
                  returnType    = m.returnType.map(fieldUpdate),
                  documentation = m.documentation.map(fieldUpdateOption),
                  scope         = m.scope.map(fieldUpdate)
                )
              }
          }
      }
      SuggestionsDatabaseUpdateNotification(
        version,
        actionUpdates ++ treeUpdates
      )
    }

  /** Construct the field update object from an optional value.
    *
    * @param value the optional value
    * @return the field update object representint the value update
    */
  private def fieldUpdateOption[A](value: Option[A]): FieldUpdate[A] =
    value match {
      case Some(value) => FieldUpdate(FieldAction.Set, Some(value))
      case None        => FieldUpdate(FieldAction.Remove, None)
    }

  /** Construct the field update object from and update value.
    *
    * @param value the update value
    * @return the field update object representing the value update
    */
  private def fieldUpdate[A](value: A): FieldUpdate[A] =
    FieldUpdate(FieldAction.Set, Some(value))

  /** Construct [[SuggestionArgumentUpdate]] from the runtime message.
    *
    * @param action the runtime message
    * @return the [[SuggestionArgumentUpdate]] message
    */
  private def toApiArgumentAction(
    action: Api.SuggestionArgumentAction
  ): SuggestionArgumentUpdate =
    action match {
      case Api.SuggestionArgumentAction.Add(index, argument) =>
        SuggestionArgumentUpdate.Add(index, argument)
      case Api.SuggestionArgumentAction.Remove(index) =>
        SuggestionArgumentUpdate.Remove(index)
      case Api.SuggestionArgumentAction.Modify(
            index,
            name,
            reprType,
            isSuspended,
            hasDefault,
            defaultValue
          ) =>
        SuggestionArgumentUpdate.Modify(
          index,
          name.map(fieldUpdate),
          reprType.map(fieldUpdate),
          isSuspended.map(fieldUpdate),
          hasDefault.map(fieldUpdate),
          defaultValue.map(fieldUpdateOption)
        )
    }

  /** Build the module name from the requested file path.
    *
    * @param projectName the project name
    * @param path the requested file path
    * @return the module name
    */
  private def getModuleName(
    projectName: String,
    path: Path
  ): Either[SearchFailure, String] =
    for {
      root <- config.findContentRoot(path.rootId).left.map(FileSystemError)
      module <-
        ModuleNameBuilder
          .build(projectName, root.file.toPath, path.toFile(root.file).toPath)
          .toRight(ModuleNameNotResolvedError(path))
    } yield module

  private def toPosition(pos: Position): Suggestion.Position =
    Suggestion.Position(pos.line, pos.character)
}

object SuggestionsHandler {

  /** The notification about the project name update.
    *
    * @param projectName the new project name
    * @param updates the list of updates to send
    */
  case class ProjectNameUpdated(
    projectName: String,
    updates: Iterable[DeliverToJsonController[_]]
  )
  object ProjectNameUpdated {

    /** Create the notification about the project name update.
      *
      * @param projectName the new project name
      * @return the notification about the project name update.
      */
    def apply(projectName: String): ProjectNameUpdated =
      new ProjectNameUpdated(projectName, Seq())
  }

  /** The notification that the suggestions database has been verified. */
  case object Verified

  /** The initialization state of the handler.
    *
    * @param project the project name
    * @param suggestions the initialization event of the suggestions repo
    * @param typeGraph the Enso type hierarchy
    */
  private case class Initialization(
    project: Option[String] = None,
    suggestions: Option[InitializedEvent.SuggestionsRepoInitialized.type] =
      None,
    typeGraph: Option[TypeGraph] = None
  ) {

    /** Check if all the components are initialized.
      *
      * @return the project name
      */
    def initialized: Option[(String, TypeGraph)] =
      for {
        _     <- suggestions
        name  <- project
        graph <- typeGraph
      } yield (name, graph)
  }

  /** Creates a configuration object used to create a [[SuggestionsHandler]].
    *
    * @param config the server configuration
    * @param suggestionsRepo the suggestions repo
    * @param fileVersionsRepo the file versions repo
    * @param sessionRouter the session router
    * @param runtimeConnector the runtime connector
    */
  def props(
    config: Config,
    suggestionsRepo: SuggestionsRepo[Future],
    fileVersionsRepo: FileVersionsRepo[Future],
    sessionRouter: ActorRef,
    runtimeConnector: ActorRef
  ): Props =
    Props(
      new SuggestionsHandler(
        config,
        suggestionsRepo,
        fileVersionsRepo,
        sessionRouter,
        runtimeConnector
      )
    )
}
