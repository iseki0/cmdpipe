@file:JvmName("-Cmdline")

package space.iseki.cmdpipe

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executor

typealias OutputHandler<T> = (OutputStream) -> T
typealias InputHandler<T> = (InputStream) -> T

interface Cmdline<SO, SE> {
    fun withCmdline(vararg cmdArray: String) = withCmdline(listOf(*cmdArray))
    fun withCmdline(cmdArray: Collection<String>): Cmdline<SO, SE>
    fun withEnvironment(vararg variables: Pair<String, String?>): Cmdline<SO, SE>
    fun withWorkingDirectory(dir: File): Cmdline<SO, SE>
    fun withExecutor(executor: Executor): Cmdline<SO, SE>
    fun withTimeout(millisecond: Long): Cmdline<SO, SE>

    /**
     * Handle the stdout of the commandline
     *
     * If the handler not set, the stdout will be discarded.
     *
     * The handler will be wrapped as a job and executed by the executor.
     * When the commandline execution terminated, the job will be cancelled with an interruption.
     *
     * If the handler throws anything, the commandline execution will be terminated and the [execute] function might throw a [CmdlineHandlerException].
     *
     */
    fun <SO> handleStdout(handler: InputHandler<SO>): Cmdline<SO, SE>

    /**
     * Handle the stderr of the commandline.
     *
     * If the handler not set, the stderr will be recorded by an internal text recorder.
     * At that case, the caller can retrieve the text from execution result or exceptions.
     *
     * @see handleStdout
     */
    fun <SE> handleStderr(handler: InputHandler<SE>): Cmdline<SO, SE>

    /**
     * Provide a stdin for the commandline
     *
     * If not presents, the stdin will be closed when the process started.
     *
     * @see handleStdout
     */
    fun handleStdin(handler: OutputHandler<Unit>): Cmdline<SO, SE>

    /**
     * Execute the given commandline
     *
     * During the commandline execution, if the current thread is interrupted, a [CmdlineInterruptedException] will be thrown, and the *interrupted status* will __not__ be cleared.
     * The caller might use `Thread.interrupted()` to check the interruption.
     *
     * @see ProcessBuilder.environment
     * @see ProcessBuilder.start
     * @throws CmdlineHandlerException when any of the handlers(stdin, stdout, stderr) throws
     * @throws CmdlineInterruptedException when the command execution was interrupted. In that case, the *interrupted status* will not be cleared. See also: [Thread.interrupted]
     * @throws CmdlineTimeoutException when the command execution timeout
     * @throws UnsupportedOperationException depends on the underlying implementations
     * @throws CmdlineException
     * @throws IllegalArgumentException when the commandline is empty, or any other suitable reasons
     * @throws
     */
    fun execute(): ExecutionResult<SO, SE>
    fun inheritIO(f: Boolean): Cmdline<SO, SE> = TODO()
    fun inheritIO() = inheritIO(true)

    companion object {
        @JvmStatic
        fun of(cmdArray: Collection<String>): Cmdline<Nothing, Nothing> = CommandImpl(cmdArray.toList())

        @JvmStatic
        fun of(vararg cmdArray: String): Cmdline<Nothing, Nothing> = CommandImpl(cmdArray.toList())
    }
}

fun cmdline(cmdArray: Collection<String>) = Cmdline.of(cmdArray)
fun cmdline(vararg cmdArray: String) = Cmdline.of(listOf(*cmdArray))
fun Collection<String>.asCmdline() = Cmdline.of(this)

