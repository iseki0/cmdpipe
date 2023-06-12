package space.iseki.cmdpipe

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executor

internal class CmdlineImpl<SO, SE>(
    private val d: Data,
    private val stdoutHandler: ((InputStream) -> SO)?,
    private val stderrHandler: ((InputStream) -> SE)?,
) : Cmdline<SO, SE> {
    data class Data(
        val cmdline: List<String>,
        val wd: File?,
        val env: List<Pair<String, String?>>,
        val stdinHandler: ((OutputStream) -> Unit)?,
        val timeout: Int,
        val executor: Executor,
    )

    private fun copyData(d: Data) = CmdlineImpl(d, stdoutHandler, stderrHandler)

    override fun withEnvironment(vararg variables: Pair<String, String?>): Cmdline<SO, SE> =
        copyData(d.copy(env = d.env + variables))

    override fun withWorkingDirectory(dir: File): Cmdline<SO, SE> =
        copyData(d.copy(wd = dir))

    override fun withTimeout(millisecond: Int): Cmdline<SO, SE> =
        copyData(d.copy(timeout = millisecond))


    override fun <SO> handleStdout(handler: (InputStream) -> SO): Cmdline<SO, SE> =
        CmdlineImpl(d, handler, stderrHandler)

    override fun <SE> handleStderr(handler: (InputStream) -> SE): Cmdline<SO, SE> =
        CmdlineImpl(d, stdoutHandler, handler)

    override fun handleStdin(handler: (OutputStream) -> Unit): Cmdline<SO, SE> =
        copyData(d.copy(stdinHandler = handler))

    override fun execute(): ExecutionResult<SO, SE> {
        TODO("Not yet implemented")
    }
}
