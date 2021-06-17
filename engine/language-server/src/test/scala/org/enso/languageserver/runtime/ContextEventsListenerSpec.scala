package org.enso.languageserver.runtime

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.apache.commons.io.FileUtils
import org.enso.languageserver.data._
import org.enso.languageserver.event.InitializedEvent
import org.enso.languageserver.filemanager.{
  ContentRootType,
  ContentRootWithFile
}
import org.enso.languageserver.runtime.ContextRegistryProtocol._
import org.enso.languageserver.search.Suggestions
import org.enso.languageserver.session.JsonSession
import org.enso.languageserver.session.SessionRouter.{
  DeliverToBinaryController,
  DeliverToJsonController
}
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.searcher.SuggestionsRepo
import org.enso.searcher.sql.{SqlDatabase, SqlSuggestionsRepo}
import org.enso.testkit.RetrySpec
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.nio.file.Files
import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

class ContextEventsListenerSpec
    extends TestKit(ActorSystem("TestSystem"))
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with RetrySpec {

  import system.dispatcher

  val Timeout: FiniteDuration = 10.seconds

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "ContextEventsListener" should {

    "not send empty updates" taggedAs Retry in withDb {
      (_, _, _, router, _, _) =>
        router.expectNoMessage()
    }

    "send expression updates" taggedAs Retry in withDb {
      (clientId, contextId, repo, router, _, listener) =>
        val (_, suggestionIds) = Await.result(
          repo.insertAll(
            Seq(
              Suggestions.atom,
              Suggestions.method,
              Suggestions.function,
              Suggestions.local
            )
          ),
          Timeout
        )

        listener ! Api.ExpressionUpdates(
          contextId,
          Set(
            Api.ExpressionUpdate(
              Suggestions.method.externalId.get,
              Some(Suggestions.method.returnType),
              Some(
                Api.MethodPointer(
                  Suggestions.method.module,
                  Suggestions.method.selfType,
                  Suggestions.method.name
                )
              ),
              Vector(),
              false,
              Api.ExpressionUpdate.Payload.Value()
            )
          )
        )

        router.expectMsg(
          DeliverToJsonController(
            clientId,
            ContextRegistryProtocol.ExpressionUpdatesNotification(
              contextId,
              Vector(
                ContextRegistryProtocol.ExpressionUpdate(
                  Suggestions.method.externalId.get,
                  Some(Suggestions.method.returnType),
                  Some(suggestionIds(1).get),
                  Vector(),
                  false,
                  ContextRegistryProtocol.ExpressionUpdate.Payload.Value
                )
              )
            )
          )
        )
    }

    "send dataflow error updates" taggedAs Retry in withDb {
      (clientId, contextId, _, router, _, listener) =>
        listener ! Api.ExpressionUpdates(
          contextId,
          Set(
            Api.ExpressionUpdate(
              Suggestions.method.externalId.get,
              None,
              None,
              Vector(),
              false,
              Api.ExpressionUpdate.Payload.DataflowError(
                Seq(Suggestions.function.externalId.get)
              )
            )
          )
        )

        router.expectMsg(
          DeliverToJsonController(
            clientId,
            ContextRegistryProtocol.ExpressionUpdatesNotification(
              contextId,
              Vector(
                ContextRegistryProtocol.ExpressionUpdate(
                  Suggestions.method.externalId.get,
                  None,
                  None,
                  Vector(),
                  false,
                  ContextRegistryProtocol.ExpressionUpdate.Payload
                    .DataflowError(Seq(Suggestions.function.externalId.get))
                )
              )
            )
          )
        )
    }

    "send runtime error updates" taggedAs Retry in withDb {
      (clientId, contextId, _, router, _, listener) =>
        listener ! Api.ExpressionUpdates(
          contextId,
          Set(
            Api.ExpressionUpdate(
              Suggestions.method.externalId.get,
              None,
              None,
              Vector(),
              false,
              Api.ExpressionUpdate.Payload.Panic("Method failure", Seq())
            )
          )
        )

        router.expectMsg(
          DeliverToJsonController(
            clientId,
            ContextRegistryProtocol.ExpressionUpdatesNotification(
              contextId,
              Vector(
                ContextRegistryProtocol.ExpressionUpdate(
                  Suggestions.method.externalId.get,
                  None,
                  None,
                  Vector(),
                  false,
                  ContextRegistryProtocol.ExpressionUpdate.Payload
                    .Panic("Method failure", Seq())
                )
              )
            )
          )
        )
    }

    "send expression updates grouped" taggedAs Retry in withDb(0.seconds) {
      (clientId, contextId, repo, router, _, listener) =>
        Await.result(
          repo.insertAll(
            Seq(
              Suggestions.atom,
              Suggestions.method,
              Suggestions.function,
              Suggestions.local
            )
          ),
          Timeout
        )

        listener ! Api.ExpressionUpdates(
          contextId,
          Set(
            Api.ExpressionUpdate(
              Suggestions.method.externalId.get,
              None,
              None,
              Vector(),
              false,
              Api.ExpressionUpdate.Payload.Value()
            )
          )
        )

        listener ! Api.ExpressionUpdates(
          contextId,
          Set(
            Api.ExpressionUpdate(
              Suggestions.local.externalId.get,
              None,
              None,
              Vector(),
              false,
              Api.ExpressionUpdate.Payload.Value()
            )
          )
        )

        listener ! ContextEventsListener.RunExpressionUpdates

        router.expectMsg(
          DeliverToJsonController(
            clientId,
            ExpressionUpdatesNotification(
              contextId,
              Vector(
                ContextRegistryProtocol.ExpressionUpdate(
                  Suggestions.method.externalId.get,
                  None,
                  None,
                  Vector(),
                  false,
                  ContextRegistryProtocol.ExpressionUpdate.Payload.Value
                ),
                ContextRegistryProtocol.ExpressionUpdate(
                  Suggestions.local.externalId.get,
                  None,
                  None,
                  Vector(),
                  false,
                  ContextRegistryProtocol.ExpressionUpdate.Payload.Value
                )
              )
            )
          )
        )
    }

    "register oneshot visualization" taggedAs Retry in withDb {
      (clientId, contextId, _, router, registry, listener) =>
        val ctx = Api.VisualisationContext(
          UUID.randomUUID(),
          contextId,
          UUID.randomUUID()
        )

        listener ! RegisterOneshotVisualisation(
          ctx.contextId,
          ctx.visualisationId,
          ctx.expressionId
        )

        val data1 = Array[Byte](1, 2, 3)
        listener ! Api.VisualisationUpdate(ctx, data1)
        router.expectMsg(
          DeliverToBinaryController(
            clientId,
            VisualisationUpdate(
              VisualisationContext(
                ctx.visualisationId,
                ctx.contextId,
                ctx.expressionId
              ),
              data1
            )
          )
        )
        registry.expectMsg(
          DetachVisualisation(
            clientId,
            ctx.contextId,
            ctx.visualisationId,
            ctx.expressionId
          )
        )

        val data2 = Array[Byte](2, 3, 4)
        listener ! Api.VisualisationUpdate(ctx, data2)
        router.expectMsg(
          DeliverToBinaryController(
            clientId,
            VisualisationUpdate(
              VisualisationContext(
                ctx.visualisationId,
                ctx.contextId,
                ctx.expressionId
              ),
              data2
            )
          )
        )
        registry.expectNoMessage()
    }

    "send visualization updates" taggedAs Retry in withDb {
      (clientId, contextId, _, router, registry, listener) =>
        val ctx = Api.VisualisationContext(
          UUID.randomUUID(),
          contextId,
          UUID.randomUUID()
        )

        val data1 = Array[Byte](1, 2, 3)
        listener ! Api.VisualisationUpdate(ctx, data1)
        router.expectMsg(
          DeliverToBinaryController(
            clientId,
            VisualisationUpdate(
              VisualisationContext(
                ctx.visualisationId,
                ctx.contextId,
                ctx.expressionId
              ),
              data1
            )
          )
        )
        registry.expectNoMessage()

        val data2 = Array[Byte](2, 3, 4)
        listener ! Api.VisualisationUpdate(ctx, data2)
        router.expectMsg(
          DeliverToBinaryController(
            clientId,
            VisualisationUpdate(
              VisualisationContext(
                ctx.visualisationId,
                ctx.contextId,
                ctx.expressionId
              ),
              data2
            )
          )
        )
        registry.expectNoMessage()
    }

    "send execution failed notification" taggedAs Retry in withDb {
      (clientId, contextId, _, router, _, listener) =>
        val message = "Test execution failed"
        listener ! Api.ExecutionFailed(
          contextId,
          Api.ExecutionResult.Failure(message, None)
        )

        router.expectMsg(
          DeliverToJsonController(
            clientId,
            ExecutionFailedNotification(
              contextId,
              ExecutionFailure(
                message,
                None
              )
            )
          )
        )
    }

    "send execution update notification" taggedAs Retry in withDb {
      (clientId, contextId, _, router, _, listener) =>
        val message = "Test execution failed"
        listener ! Api.ExecutionUpdate(
          contextId,
          Seq(Api.ExecutionResult.Diagnostic.error(message))
        )

        router.expectMsg(
          DeliverToJsonController(
            clientId,
            ExecutionDiagnosticNotification(
              contextId,
              Seq(
                ExecutionDiagnostic(
                  ExecutionDiagnosticKind.Error,
                  message,
                  None,
                  None,
                  None,
                  Vector()
                )
              )
            )
          )
        )
    }

    "send visualisation evaluation failed notification" taggedAs Retry in withDb {
      (clientId, contextId, _, router, _, listener) =>
        val message         = "Test visualisation evaluation failed"
        val visualisationId = UUID.randomUUID()
        val expressionId    = UUID.randomUUID()
        listener ! Api.VisualisationEvaluationFailed(
          contextId,
          visualisationId,
          expressionId,
          message,
          None
        )

        router.expectMsg(
          DeliverToJsonController(
            clientId,
            VisualisationEvaluationFailed(
              contextId,
              visualisationId,
              expressionId,
              message,
              None
            )
          )
        )
    }
  }

  def newConfig(root: ContentRootWithFile): Config = {
    Config(
      Map(root.id -> root),
      FileManagerConfig(timeout = 3.seconds),
      PathWatcherConfig(),
      ExecutionContextConfig(requestTimeout = 3.seconds),
      ProjectDirectoriesConfig.initialize(root.file)
    )
  }

  def newJsonSession(clientId: UUID): JsonSession =
    JsonSession(clientId, TestProbe().ref)

  def withDb(
    test: (
      UUID,
      UUID,
      SuggestionsRepo[Future],
      TestProbe,
      TestProbe,
      ActorRef
    ) => Any
  ): Unit =
    withDb(100.millis)(test)

  def withDb(updatesSendRate: FiniteDuration)(
    test: (
      UUID,
      UUID,
      SuggestionsRepo[Future],
      TestProbe,
      TestProbe,
      ActorRef
    ) => Any
  ): Unit = {
    val testContentRoot = Files.createTempDirectory(null).toRealPath()
    sys.addShutdownHook(FileUtils.deleteQuietly(testContentRoot.toFile))
    val config = newConfig(
      ContentRootWithFile(
        UUID.randomUUID(),
        ContentRootType.Project,
        "Project",
        testContentRoot.toFile
      )
    )
    val clientId        = UUID.randomUUID()
    val contextId       = UUID.randomUUID()
    val router          = TestProbe("session-router")
    val contextRegistry = TestProbe("context-registry")
    val db              = SqlDatabase(config.directories.suggestionsDatabaseFile)
    val repo            = new SqlSuggestionsRepo(db)
    val listener = contextRegistry.childActorOf(
      ContextEventsListener.props(
        config,
        repo,
        newJsonSession(clientId),
        contextId,
        router.ref,
        updatesSendRate
      )
    )
    val repoInit = repo.init
    repoInit.onComplete {
      case Success(()) =>
        system.eventStream.publish(InitializedEvent.SuggestionsRepoInitialized)
      case Failure(ex) =>
        system.log.error(
          s"ContextEventsListenerSpec failed to initialize Suggestions repo. $ex"
        )
    }
    Await.ready(repoInit, Timeout)

    try test(clientId, contextId, repo, router, contextRegistry, listener)
    finally {
      system.stop(listener)
      repo.close()
    }
  }

}
