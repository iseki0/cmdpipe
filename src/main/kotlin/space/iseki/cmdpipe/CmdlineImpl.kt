package space.iseki.cmdpipe

import org.slf4j.MDC
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.*

private val defaultExecutor by lazy {
    val factory = Executors.defaultThreadFactory()
    val delegatedThreadFactory = ThreadFactory { r -> factory.newThread(r).also { it.isDaemon = true } }
    Executors.newCachedThreadPool(delegatedThreadFactory)
}

internal class CmdlineImpl<SO, SE> private constructor(
    private val d: Data,
    private val stdoutHandler: ((InputStream) -> SO)?,
    private val stderrHandler: ((InputStream) -> SE)?,
) : Cmdline<SO, SE> {
    constructor(cmdArray: Collection<String>) : this(Data(cmdArray.toList()), null, null)

    data class Data(
        val cmdline: List<String>,
        val wd: File? = null,
        val env: List<Pair<String, String?>> = emptyList(),
        val stdinHandler: ((OutputStream) -> Unit)? = null,
        val timeout: Long = 0,
        val executor: Executor = defaultExecutor,
    )

    private fun copyData(d: Data) = CmdlineImpl(d, stdoutHandler, stderrHandler)

    override fun withCmdline(cmdArray: Collection<String>): Cmdline<SO, SE> =
        copyData(d.copy(cmdline = cmdArray.toList()))

    override fun withEnvironment(vararg variables: Pair<String, String?>): Cmdline<SO, SE> =
        copyData(d.copy(env = d.env + variables))

    override fun withWorkingDirectory(dir: File): Cmdline<SO, SE> =
        copyData(d.copy(wd = dir))

    override fun withTimeout(millisecond: Long): Cmdline<SO, SE> =
        copyData(d.copy(timeout = millisecond))

    override fun withExecutor(executor: Executor) = copyData(d.copy(executor = executor))

    override fun <SO> handleStdout(handler: (InputStream) -> SO): Cmdline<SO, SE> =
        CmdlineImpl(d, handler, stderrHandler)

    override fun <SE> handleStderr(handler: (InputStream) -> SE): Cmdline<SO, SE> =
        CmdlineImpl(d, stdoutHandler, handler)

    override fun handleStdin(handler: (OutputStream) -> Unit): Cmdline<SO, SE> =
        copyData(d.copy(stdinHandler = handler))

    override fun execute(): ExecutionResult<SO, SE> {
        val mdc = safeDumpMDC()
        val cleanupHandlers = mutableListOf<() -> Unit>()
        val exceptionManager = ExceptionManager()
        var executionInfo = CmdlineExecutionInfo(
            cmdline = d.cmdline,
            environments = d.env,
            workingDirectory = d.wd,
            startAt = 0,
            endAt = 0,
            pid = null,
            exitCode = null,
        )

        val stderrRecorder = ErrorRecorder()
        fun <R> task(process: Process, block: () -> R): FutureTask<R> {
            val task = FutureTask {
                val oldMdc = safeDumpMDC()
                try {
                    safeSetMDC(mdc)
                    block()
                } catch (th: Throwable) {
                    runCatching { process.destroyForcibly() }.onFailure { th.addSuppressed(it) }
                    exceptionManager.add(th)
                    throw th
                } finally {
                    safeSetMDC(oldMdc)
                }
            }
            d.executor.execute(task)
            cleanupHandlers.add { runCatching { task.cancel(true) } }
            return task
        }

        var process: Process? = null
        try {
            // create process
            val processBuilder = ProcessBuilder(d.cmdline)
            if (d.wd != null) processBuilder.directory(d.wd)
            processBuilder.configureEnvironments(d.env)
            if (stdoutHandler == null) processBuilder.discardStdout()
            process = processBuilder.start()
            executionInfo = executionInfo.copy(
                pid = process.safePID(),
                startAt = System.currentTimeMillis(),
            )
            val stdinHandlerFuture = d.stdinHandler?.let { task(process) { process.outputStream.use(it) } }
            val stdoutHandlerFuture = stdoutHandler?.let { task(process) { process.inputStream.use(it) } }
            val stderrHandlerFuture = stderrHandler?.let { task(process) { process.errorStream.use(it) } }
            if (stderrHandlerFuture == null) {
                task(process) { process.errorStream.use { it.reader().copyTo(stderrRecorder.writer) } }
            }
            if (d.timeout > 0) {
                if (!process.waitFor(d.timeout, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                }
            }
            executionInfo = executionInfo.copy(exitCode = process.waitFor(), endAt = System.currentTimeMillis())
            listOfNotNull(stdinHandlerFuture, stdoutHandlerFuture, stderrHandlerFuture).waitAll()
            exceptionManager.exception?.let {
                throw CmdlineHandlerException(
                    info = executionInfo,
                    cause = it,
                    stdinHandlerThrows = stdinHandlerFuture?.throwableOrNull(),
                    stdoutHandlerThrows = stdoutHandlerFuture?.throwableOrNull(),
                    stderrHandlerThrows = stderrHandlerFuture?.throwableOrNull(),
                )
            }
            return ExecutionResult(
                cmdline = d.cmdline,
                _stdoutValue = stdoutHandlerFuture?.get(),
                _stderrValue = stderrHandlerFuture?.get(),
                exitCode = executionInfo.exitCode!!,
                startAt = executionInfo.startAt,
                endAt = executionInfo.endAt,
                errorRecorder = stderrRecorder,
            )
        } catch (e: Exception) {
            runCatching { process?.destroyForcibly() }.onFailure { e.addSuppressed(e) }
            when (e) {
                is InterruptedException, is CmdlineHandlerException, is TimeoutException -> throw e
                else -> throw CmdlineException(info = executionInfo, cause = e)
            }
        } finally {
            cleanupHandlers.forEach { it() }
        }
    }
}

private fun Collection<Future<*>>.waitAll() {
    forEach { runCatching { it.get() } }
}


private fun <T> Future<T>.throwableOrNull() = runCatching { get() }.exceptionOrNull()?.let {
    when (it) {
        is ExecutionException -> it.cause
        else -> it
    }
}

private fun ProcessBuilder.configureEnvironments(m: List<Pair<String, String?>>) {
    if (m.isEmpty()) return
    val env = environment()
    for ((k, v) in m) {
        when (v) {
            null -> env.remove(k)
            else -> env[k] = v
        }
    }
}

private fun ProcessBuilder.discardStdout() {
    redirectOutput(ProcessBuilder.Redirect.DISCARD)
}

private fun Process.safePID() = runCatching { pid() }.getOrDefault(-1)

private fun safeDumpMDC() = runCatching { MDC.getCopyOfContextMap() }.getOrNull()
private fun safeSetMDC(m: Map<String, String>?) {
    runCatching { MDC.setContextMap(m) }
}
