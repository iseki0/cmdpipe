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
    fun <SO> handleStdout(handler: InputHandler<SO>): Cmdline<SO, SE>
    fun <SE> handleStderr(handler: InputHandler<SE>): Cmdline<SO, SE>
    fun handleStdin(handler: OutputHandler<Unit>): Cmdline<SO, SE>

    /**
     *
     * @see ProcessBuilder.environment
     * @see ProcessBuilder.start
     * @throws CmdlineHandlerException
     * @throws UnsupportedOperationException
     * @throws IllegalArgumentException
     * @throws InterruptedException
     */
    @Throws(InterruptedException::class)
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

