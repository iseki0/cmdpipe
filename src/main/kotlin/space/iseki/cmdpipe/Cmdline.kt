@file:JvmName("-Cmdline")

package space.iseki.cmdpipe

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executor

interface Cmdline<SO, SE> {
    fun withEnvironment(vararg variables: Pair<String, String?>): Cmdline<SO, SE>
    fun withWorkingDirectory(dir: File): Cmdline<SO, SE>
    fun withExecutor(executor: Executor): Cmdline<SO, SE>
    fun withTimeout(millisecond: Long): Cmdline<SO, SE>
    fun <SO> handleStdout(handler: (InputStream) -> SO): Cmdline<SO, SE>
    fun <SE> handleStderr(handler: (InputStream) -> SE): Cmdline<SO, SE>
    fun handleStdin(handler: (OutputStream) -> Unit): Cmdline<SO, SE>
    fun execute(): ExecutionResult<SO, SE>

    companion object {
        @JvmStatic
        fun of(cmdArray: Collection<String>): Cmdline<Nothing, Nothing> = CmdlineImpl(cmdArray)
    }
}

fun cmdline(cmdArray: Collection<String>) = Cmdline.of(cmdArray)
