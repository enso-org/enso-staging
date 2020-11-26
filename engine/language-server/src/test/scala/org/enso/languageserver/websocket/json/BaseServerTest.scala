package org.enso.languageserver.websocket.json

import java.nio.file.Files
import java.util.UUID

import akka.testkit.TestProbe
import io.circe.literal._
import org.apache.commons.io.FileUtils
import org.enso.jsonrpc.test.JsonRpcServerTestKit
import org.enso.jsonrpc.{ClientControllerFactory, Protocol}
import org.enso.languageserver.capability.CapabilityRouter
import org.enso.languageserver.data._
import org.enso.languageserver.effect.ZioExec
import org.enso.languageserver.event.InitializedEvent
import org.enso.languageserver.filemanager.{
  FileManager,
  FileSystem,
  ReceivesTreeUpdatesHandler
}
import org.enso.languageserver.io._
import org.enso.languageserver.protocol.json.{
  JsonConnectionControllerFactory,
  JsonRpc
}
import org.enso.languageserver.runtime.ContextRegistry
import org.enso.languageserver.search.SuggestionsHandler
import org.enso.languageserver.session.SessionRouter
import org.enso.languageserver.text.BufferRegistry
import org.enso.searcher.sql.{SqlDatabase, SqlSuggestionsRepo, SqlVersionsRepo}
import org.enso.text.Sha3_224VersionCalculator

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class BaseServerTest extends JsonRpcServerTestKit {

  val timeout: FiniteDuration = 10.seconds

  val testContentRoot       = Files.createTempDirectory(null).toRealPath()
  val testContentRootId     = UUID.randomUUID()
  val config                = mkConfig
  val runtimeConnectorProbe = TestProbe()
  val versionCalculator     = Sha3_224VersionCalculator

  sys.addShutdownHook(FileUtils.deleteQuietly(testContentRoot.toFile))

  def mkConfig: Config =
    Config(
      Map(testContentRootId -> testContentRoot.toFile),
      FileManagerConfig(timeout = 3.seconds),
      PathWatcherConfig(),
      ExecutionContextConfig(requestTimeout = 3.seconds),
      DirectoriesConfig(testContentRoot.toFile)
    )

  override def protocol: Protocol = JsonRpc.protocol

  val stdOut    = new ObservableOutputStream
  val stdErr    = new ObservableOutputStream
  val stdInSink = new ObservableOutputStream
  val stdIn     = new ObservablePipedInputStream(stdInSink)

  val sessionRouter =
    system.actorOf(SessionRouter.props())

  val stdOutController =
    system.actorOf(
      OutputRedirectionController
        .props(stdOut, OutputKind.StandardOutput, sessionRouter)
    )

  val stdErrController =
    system.actorOf(
      OutputRedirectionController
        .props(stdErr, OutputKind.StandardError, sessionRouter)
    )

  val stdInController =
    system.actorOf(
      InputRedirectionController.props(stdIn, stdInSink, sessionRouter)
    )

  override def clientControllerFactory: ClientControllerFactory = {

    val zioExec         = ZioExec(zio.Runtime.default)
    val sqlDatabase     = SqlDatabase(config.directories.suggestionsDatabaseFile)
    val suggestionsRepo = new SqlSuggestionsRepo(sqlDatabase)(system.dispatcher)
    val versionsRepo    = new SqlVersionsRepo(sqlDatabase)(system.dispatcher)

    val fileManager =
      system.actorOf(FileManager.props(config, new FileSystem, zioExec))
    val bufferRegistry =
      system.actorOf(
        BufferRegistry.props(
          versionsRepo,
          fileManager,
          runtimeConnectorProbe.ref
        )(
          Sha3_224VersionCalculator
        )
      )
    val fileEventRegistry =
      system.actorOf(
        ReceivesTreeUpdatesHandler.props(config, new FileSystem, zioExec)
      )

    val contextRegistry =
      system.actorOf(
        ContextRegistry.props(
          suggestionsRepo,
          config,
          runtimeConnectorProbe.ref,
          sessionRouter
        )
      )

    val suggestionsHandler =
      system.actorOf(
        SuggestionsHandler.props(
          config,
          suggestionsRepo,
          versionsRepo,
          sessionRouter,
          runtimeConnectorProbe.ref
        )
      )

    val capabilityRouter =
      system.actorOf(
        CapabilityRouter.props(
          bufferRegistry,
          fileEventRegistry,
          suggestionsHandler
        )
      )

    // initialize
    val suggestionsRepoInit = suggestionsRepo.init
    suggestionsRepoInit.onComplete {
      case Success(()) =>
        system.eventStream.publish(InitializedEvent.SuggestionsRepoInitialized)
      case Failure(ex) =>
        system.log.error(ex, "Failed to initialize Suggestions repo")
    }(system.dispatcher)

    val versionsRepoInit = versionsRepo.init
    versionsRepoInit.onComplete {
      case Success(()) =>
        system.eventStream.publish(InitializedEvent.FileVersionsRepoInitialized)
      case Failure(ex) =>
        system.log.error(ex, "Failed to initialize FileVersions repo")
    }(system.dispatcher)

    Await.ready(suggestionsRepoInit, timeout)
    Await.ready(versionsRepoInit, timeout)

    new JsonConnectionControllerFactory(
      bufferRegistry,
      capabilityRouter,
      fileManager,
      contextRegistry,
      suggestionsHandler,
      stdOutController,
      stdErrController,
      stdInController,
      runtimeConnectorProbe.ref,
      Future.successful(())
    )
  }

  def getInitialisedWsClient(): WsTestClient = {
    val client = new WsTestClient(address)
    initSession(client)
    client
  }

  private def initSession(client: WsTestClient): UUID = {
    val clientId = UUID.randomUUID()
    client.send(json"""
          { "jsonrpc": "2.0",
            "method": "session/initProtocolConnection",
            "id": 1,
            "params": {
              "clientId": $clientId
            }
          }
          """)
    client.expectJson(json"""
            { "jsonrpc":"2.0",
              "id":1,
              "result":{
                "contentRoots":[ $testContentRootId ]
              }
            }
              """)
    clientId
  }

}
