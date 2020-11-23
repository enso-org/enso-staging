package org.enso.projectmanager.infrastructure.languageserver

import java.util.UUID

import akka.actor.{
  Actor,
  ActorLogging,
  ActorRef,
  Cancellable,
  Props,
  Scheduler,
  Terminated
}
import org.enso.projectmanager.boot.configuration.SupervisionConfig
import org.enso.projectmanager.data.Socket
import org.enso.projectmanager.infrastructure.http.WebSocketConnectionFactory
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerController.ServerDied
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerSupervisor.{
  SendHeartbeat,
  ServerUnresponsive,
  StartSupervision
}
import org.enso.projectmanager.util.UnhandledLogging

/** A supervisor process responsible for monitoring language server and
  * restarting it when the server is unresponsive. It delegates server
  * monitoring to the [[HeartbeatSession]] actor.
  *
  * @param config a server config
  * @param serverProcess a server handle
  * @param supervisionConfig a supervision config
  * @param connectionFactory a web socket connection factory
  * @param scheduler a scheduler
  */
class LanguageServerSupervisor(
  config: LanguageServerConnectionInfo,
  serverProcess: ActorRef,
  supervisionConfig: SupervisionConfig,
  connectionFactory: WebSocketConnectionFactory,
  scheduler: Scheduler
) extends Actor
    with ActorLogging
    with UnhandledLogging {

  import context.dispatcher

  override def preStart(): Unit = { self ! StartSupervision }

  override def receive: Receive = uninitialized

  private def uninitialized: Receive = {
    case GracefulStop =>
      context.stop(self)

    case StartSupervision =>
      val cancellable =
        scheduler.scheduleAtFixedRate(
          supervisionConfig.initialDelay,
          supervisionConfig.heartbeatInterval,
          self,
          SendHeartbeat
        )
      context.become(supervising(cancellable))
  }

  private def supervising(cancellable: Cancellable): Receive = {
    case SendHeartbeat =>
      val socket = Socket(config.interface, config.rpcPort)
      context.actorOf(
        HeartbeatSession.props(
          socket,
          supervisionConfig.heartbeatTimeout,
          connectionFactory,
          scheduler
        ),
        s"heartbeat-${UUID.randomUUID()}"
      )

    case ServerUnresponsive =>
      log.info(s"Server is unresponsive [$config]. Restarting it...")
      cancellable.cancel()
      log.info(s"Restarting the server")
      serverProcess ! Restart
      context.become(restarting)

    case GracefulStop =>
      cancellable.cancel()
      stop()
  }

  private def restarting: Receive = {
    case LanguageServerProcess.ServerTerminated(_) =>
      log.error("Cannot restart language server")
      context.parent ! ServerDied
      context.stop(self)

    case LanguageServerProcess.ServerConfirmedFinishedBooting =>
      log.info(s"Language server restarted [$config]")
      val cancellable =
        scheduler.scheduleAtFixedRate(
          supervisionConfig.initialDelay,
          supervisionConfig.heartbeatInterval,
          self,
          SendHeartbeat
        )
      context.become(supervising(cancellable))

    case GracefulStop =>
      stop()
  }

  private def waitingForChildren(): Receive = { case Terminated(_) =>
    if (context.children.isEmpty) {
      context.stop(self)
    }
  }

  private def stop(): Unit = {
    if (context.children.isEmpty) {
      context.stop(self)
    } else {
      context.children.foreach(_ ! GracefulStop)
      context.children.foreach(context.watch)
      context.become(waitingForChildren())
    }
  }

}

object LanguageServerSupervisor {

  private case object StartSupervision

  /** A command responsible for initiating heartbeat session.
    */
  case object SendHeartbeat

  /** Signals that server is unresponsive.
    */
  case object ServerUnresponsive

  /** Signals that the heartbeat has been received (only sent if demanded). */
  case object HeartbeatReceived

  /** Creates a configuration object used to create a [[LanguageServerSupervisor]].
    *
    * @param connectionInfo a server config
    * @param serverProcess a server handle
    * @param supervisionConfig a supervision config
    * @param connectionFactory a web socket connection factory
    * @param scheduler a scheduler
    * @return a configuration object
    */
  def props(
    connectionInfo: LanguageServerConnectionInfo,
    serverProcess: ActorRef,
    supervisionConfig: SupervisionConfig,
    connectionFactory: WebSocketConnectionFactory,
    scheduler: Scheduler
  ): Props =
    Props(
      new LanguageServerSupervisor(
        connectionInfo,
        serverProcess,
        supervisionConfig,
        connectionFactory,
        scheduler
      )
    )

}