package org.enso.runtimeversionmanager.test

import org.enso.cli.OS
import org.enso.testkit.process.{RunResult, WrappedProcess}

import java.lang.{ProcessBuilder => JProcessBuilder}
import scala.jdk.CollectionConverters._

/** A mix-in providing helper functions for running native commands in tests.
  *
  * It provides multiple ways to start a command, inspect its exit code and
  * interact with its streams.
  */
trait NativeTestHelper {

  /** Starts the provided `command`.
    *
    * `extraEnv` may be provided to extend the environment. Care must be taken
    * on Windows where environment variables are (mostly) case-insensitive.
    *
    * If `waitForDescendants` is set, tries to wait for descendants of the
    * launched process to finish too. Especially important on Windows where
    * child processes may run after the launcher parent has been terminated.
    */
  def start(
    command: Seq[String],
    extraEnv: Seq[(String, String)]
  ): WrappedProcess = {
    val builder = new JProcessBuilder(command: _*)
    val newKeys = extraEnv.map(_._1.toLowerCase)
    if (newKeys.distinct.size < newKeys.size) {
      throw new IllegalArgumentException(
        "The extra environment keys have to be unique"
      )
    }

    lazy val existingKeys =
      builder.environment().keySet().asScala
    for ((key, value) <- extraEnv) {
      val keyName =
        if (OS.isWindows)
          // On Windows, environment variables are case insensitive.
          existingKeys.find(_.equalsIgnoreCase(key)).getOrElse(key)
        else key
      builder.environment().put(keyName, value)
    }

    try {
      val process = builder.start()
      new WrappedProcess(command, process)
    } catch {
      case e: Exception =>
        throw new RuntimeException("Cannot run the Native Image binary", e)
    }
  }

  /** Runs the provided `command`.
    *
    * `extraEnv` may be provided to extend the environment. Care must be taken
    * on Windows where environment variables are (mostly) case-insensitive.
    */
  def runCommand(
    command: Seq[String],
    extraEnv: Seq[(String, String)],
    waitForDescendants: Boolean = true
  ): RunResult = start(command, extraEnv).join(waitForDescendants)

}
