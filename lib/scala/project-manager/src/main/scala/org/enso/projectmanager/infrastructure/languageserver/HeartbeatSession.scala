package org.enso.projectmanager.infrastructure.languageserver

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Cancellable, Props, Scheduler, Status}
import akka.stream.SubscriptionWithCancelException.StageWasCompleted
import io.circe.parser._
import org.enso.projectmanager.data.Socket
import org.enso.projectmanager.infrastructure.http.WebSocketConnection.{
  WebSocketConnected,
  WebSocketMessage,
  WebSocketStreamClosed,
  WebSocketStreamFailure
}
import org.enso.projectmanager.infrastructure.http.WebSocketConnectionFactory
import org.enso.projectmanager.infrastructure.languageserver.HeartbeatSession.{
  HeartbeatTimeout,
  SocketClosureTimeout
}
import org.enso.projectmanager.infrastructure.languageserver.LanguageServerSupervisor.{
  HeartbeatReceived,
  ServerUnresponsive
}
import org.enso.projectmanager.util.UnhandledLogging

import scala.concurrent.duration.FiniteDuration

/** Implements one ping-pong session.
  *
  * @param socket a server socket
  * @param timeout a session timeout
  * @param connectionFactory a web socket connection factory
  * @param scheduler a scheduler
  * @param method api method to use for the heartbeat message
  * @param sendConfirmations whether to send [[HeartbeatReceived]] to confirm
  *                          that a response has been received
  * @param quietErrors if set, reports errors in debug level instead of error
  *                    level, can be used when errors are expected (i.e. on
  *                    startup)
  */
class HeartbeatSession(
  socket: Socket,
  timeout: FiniteDuration,
  connectionFactory: WebSocketConnectionFactory,
  scheduler: Scheduler,
  method: String,
  sendConfirmations: Boolean,
  quietErrors: Boolean
) extends Actor
    with ActorLogging
    with UnhandledLogging {

  import context.dispatcher

  private val requestId = UUID.randomUUID()

  private val connection = connectionFactory.createConnection(socket)

  override def preStart(): Unit = {
    connection.attachListener(self)
    connection.connect()
  }

  override def receive: Receive = pingStage

  private def pingStage: Receive = {
    case WebSocketConnected =>
      log.debug(s"Sending ping message to $socket")
      connection.send(s"""
                         |{ 
                         |   "jsonrpc": "2.0",
                         |   "method": "$method",
                         |   "id": "$requestId",
                         |   "params": null
                         |}
                         |""".stripMargin)
      val cancellable = scheduler.scheduleOnce(timeout, self, HeartbeatTimeout)
      context.become(pongStage(cancellable))

    case WebSocketStreamFailure(th) =>
      logError(th, s"An error occurred during connecting to websocket $socket")
      context.parent ! ServerUnresponsive
      stop()

    // TODO [RW] or maybe make WebSocket connection always reply with WebSocketStreamFailure instead of this? but there were some problems with that
    case Status.Failure(th: Throwable) =>
      logError(th, s"An error occurred during connecting to websocket $socket")
      context.parent ! ServerUnresponsive
      stop()

    case GracefulStop =>
      stop()
  }

  private def pongStage(cancellable: Cancellable): Receive = {
    case WebSocketMessage(payload) =>
      val maybeJson =
        parse(payload).flatMap(_.hcursor.downField("id").as[String])

      maybeJson match {
        case Left(error) =>
          logError(error, "An error occurred during parsing pong reply")

        case Right(id) =>
          if (id == requestId.toString) {
            log.debug(s"Received correct pong message from $socket")

            if (sendConfirmations) {
              context.parent ! HeartbeatReceived
            }

            cancellable.cancel()
            stop()
          } else {
            log.warning(s"Received unknown response $payload")
          }
      }

    case HeartbeatTimeout =>
      log.debug(s"Heartbeat timeout detected for $requestId")
      context.parent ! ServerUnresponsive
      stop()

    case WebSocketStreamClosed =>
      context.parent ! ServerUnresponsive
      context.stop(self)

    case WebSocketStreamFailure(th) =>
      logError(th, s"An error occurred during waiting for Pong message")
      context.parent ! ServerUnresponsive
      cancellable.cancel()
      stop()

    case GracefulStop =>
      cancellable.cancel()
      stop()
  }

  private def socketClosureStage(cancellable: Cancellable): Receive = {
    case WebSocketStreamClosed | WebSocketStreamFailure(StageWasCompleted) =>
      context.stop(self)
      cancellable.cancel()

    case WebSocketStreamFailure(th) =>
      logError(th, s"An error occurred during closing web socket")
      context.stop(self)
      cancellable.cancel()

    case SocketClosureTimeout =>
      logError(s"Socket closure timed out")
      context.stop(self)
      connection.detachListener(self)

    case GracefulStop => // ignoring it, because the actor is already closing
  }

  private def stop(): Unit = {
    connection.disconnect()
    val closureTimeout =
      scheduler.scheduleOnce(timeout, self, SocketClosureTimeout)
    context.become(socketClosureStage(closureTimeout))
  }

  private def logError(throwable: Throwable, message: String): Unit = {
    if (quietErrors) {
      log.debug(s"$message ($throwable)")
    } else {
      log.error(throwable, message)
    }
  }

  private def logError(message: String): Unit = {
    if (quietErrors) {
      log.debug(message)
    } else {
      log.error(message)
    }
  }

}

object HeartbeatSession {

  /** Signals heartbeat timeout.
    */
  case object HeartbeatTimeout

  /** Signals socket closure timeout.
    */
  case object SocketClosureTimeout

  /** Creates a configuration object used to create an ordinary
    * [[HeartbeatSession]] for monitoring server's status.
    *
    * @param socket a server socket
    * @param timeout a session timeout
    * @param connectionFactory a web socket connection factory
    * @param scheduler a scheduler
    * @return a configuration object
    */
  def props(
    socket: Socket,
    timeout: FiniteDuration,
    connectionFactory: WebSocketConnectionFactory,
    scheduler: Scheduler
  ): Props =
    Props(
      new HeartbeatSession(
        socket,
        timeout,
        connectionFactory,
        scheduler,
        "heartbeat/ping",
        sendConfirmations = false,
        quietErrors       = false
      )
    )

  /** Creates a configuration object used to create an initial
    * [[HeartbeatSession]] for checking if the server has finished booting.
    *
    * @param socket a server socket
    * @param timeout a session timeout
    * @param connectionFactory a web socket connection factory
    * @param scheduler a scheduler
    * @return a configuration object
    */
  def initialProps(
    socket: Socket,
    timeout: FiniteDuration,
    connectionFactory: WebSocketConnectionFactory,
    scheduler: Scheduler
  ): Props =
    Props(
      new HeartbeatSession(
        socket,
        timeout,
        connectionFactory,
        scheduler,
        "heartbeat/init",
        sendConfirmations = true,
        quietErrors       = true
      )
    )

}
