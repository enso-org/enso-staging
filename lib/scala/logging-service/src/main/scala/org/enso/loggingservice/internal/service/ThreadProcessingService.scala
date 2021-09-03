package org.enso.loggingservice.internal.service

import org.enso.loggingservice.LogLevel
import org.enso.loggingservice.internal.protocol.WSLogMessage
import org.enso.loggingservice.internal.{
  BlockingConsumerMessageQueue,
  DefaultLogMessageRenderer,
  InternalLogger
}

import scala.util.control.NonFatal

/** A mix-in for implementing services that process messages from a
  * [[BlockingConsumerMessageQueue]] in a separate thread.
  */
trait ThreadProcessingService extends Service {

  /** The queue that is the source of messages.
    */
  protected def queue: BlockingConsumerMessageQueue

  /** Log level used for filtering messages from the queue.
    * @return
    */
  protected def logLevel: LogLevel

  /** Logic responsible for processing each message from [[queue]].
    *
    * This function is guaranteed to be called synchronously from a single
    * thread.
    */
  protected def processMessage(message: WSLogMessage): Unit

  /** Called after the message processing thread has been stopped, can be used
    * to finish termination.
    */
  protected def afterShutdown(): Unit

  private var queueThread: Option[Thread] = None

  /** Starts the thread processing messages from [[queue]].
    */
  protected def startQueueProcessor(): Unit = {
    if (queueThread.isDefined) {
      throw new IllegalStateException(
        "The processing thread has already been started."
      )
    }

    val thread = new Thread(() => runQueue())
    thread.setName("logging-service-processing-thread")
    thread.setDaemon(true)
    queueThread = Some(thread)
    thread.start()
  }

  private lazy val renderer = new DefaultLogMessageRenderer(
    printExceptions = false
  )

  /** The runner filters out internal messages that have disabled log levels,
    * but passes through all external messages (as their log level is set
    * independently and can be lower).
    */
  private def runQueue(): Unit = {
    try {
      while (!Thread.currentThread().isInterrupted) {
        val message = queue.nextMessage(logLevel)
        try {
          processMessage(message)
        } catch {
          case NonFatal(e) =>
            InternalLogger.error(
              s"One of the printers failed to write a message: $e"
            )
            InternalLogger.error(
              s"The dropped message was: ${renderer.render(message)}"
            )
        }
      }
    } catch {
      case _: InterruptedException =>
    }
  }

  /** @inheritdoc */
  abstract override def terminate(): Unit = {
    super.terminate()
    queueThread match {
      case Some(thread) =>
        thread.interrupt()
        thread.join(100)
        queueThread = None
        afterShutdown()
      case None =>
    }
  }
}
