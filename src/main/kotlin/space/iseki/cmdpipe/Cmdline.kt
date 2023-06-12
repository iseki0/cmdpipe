package space.iseki.cmdpipe

import java.io.File
import java.io.InputStream
import java.io.OutputStream

interface Cmdline<SO, SE> {
    fun withEnvironment(vararg variables: Pair<String, String?>): Cmdline<SO, SE>
    fun withWorkingDirectory(dir: File): Cmdline<SO, SE>
    fun withTimeout(millisecond: Int): Cmdline<SO, SE>
    fun <SO> handleStdout(handler: (InputStream) -> SO): Cmdline<SO, SE>
    fun <SE> handleStderr(handler: (InputStream) -> SE): Cmdline<SO, SE>
    fun handleStdin(handler: (OutputStream) -> Unit): Cmdline<SO, SE>
    fun execute(): ExecutionResult<SO, SE>
}

