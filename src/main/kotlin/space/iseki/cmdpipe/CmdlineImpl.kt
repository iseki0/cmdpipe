package space.iseki.cmdpipe

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.concurrent.*


internal class CmdlineImpl<SO, SE> private constructor(
    private val d: Data,
    private val stdoutHandler: ((InputStream) -> SO)?,
    private val stderrHandler: ((InputStream) -> SE)?,
) : Cmdline<SO, SE> {

    private companion object {
        private val logger = runCatching { LoggerFactory.getLogger(Cmdline::class.java.name) }.getOrNull()

        private inline fun logging(block: Logger.() -> Unit) {
            logger?.block()
        }

        private val defaultExecutor by lazy {
            val factory = Executors.defaultThreadFactory()
            val delegatedThreadFactory = ThreadFactory { r -> factory.newThread(r).also { it.isDaemon = true } }
            Executors.newCachedThreadPool(delegatedThreadFactory)
                .also { logging { debug("builtin thread pool created, {}", it) } }
        }
    }

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

    override fun withWorkingDirectory(dir: File): Cmdline<SO, SE> = copyData(d.copy(wd = dir))

    override fun withTimeout(millisecond: Long): Cmdline<SO, SE> = copyData(d.copy(timeout = millisecond))

    override fun withExecutor(executor: Executor) = copyData(d.copy(executor = executor))

    override fun <SO> handleStdout(handler: (InputStream) -> SO): Cmdline<SO, SE> =
        CmdlineImpl(d, handler, stderrHandler)

    override fun <SE> handleStderr(handler: (InputStream) -> SE): Cmdline<SO, SE> =
        CmdlineImpl(d, stdoutHandler, handler)

    override fun handleStdin(handler: (OutputStream) -> Unit): Cmdline<SO, SE> =
        copyData(d.copy(stdinHandler = handler))

    override fun execute(): ExecutionResult<SO, SE> {
        val mainMdc = safeDumpMDC()
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

        var process: Process? = null


        fun <T : AutoCloseable, R> maybeHandleFor(handlerName: String, autoCloseable: T, handler: ((T) -> R)?) =
            when (handler) {
                null -> null.apply { logging { trace("{} handler not set", handlerName) } }
                else -> FutureTask {
                    val oldMdc = safeDumpMDC()
                    try {
                        safeSetMDC(mainMdc)
                        logging { trace("{} handler begin", handlerName) }
                        autoCloseable.use(handler).also { logging { trace("{} handler end", handlerName) } }
                    } catch (th: Throwable) {
                        logging { debug("handler $handlerName throws, process will be killed forcibly", th) }
                        runCatching { process?.destroyForcibly() }.onFailure {
                            logging {
                                debug(
                                    "process killing failed",
                                    it
                                )
                            }
                        }
                        exceptionManager.add(th)
                        throw th
                    } finally {
                        safeSetMDC(oldMdc)
                    }
                }.also {
                    d.executor.execute(it)
                    cleanupHandlers.add { runCatching { it.cancel(true) } }
                }
            }
        try {
            // create process
            logging { debug("running command: {}", d.cmdline) }
            val processBuilder = ProcessBuilder(d.cmdline)
            if (d.wd != null) processBuilder.directory(d.wd)
            processBuilder.configureEnvironments(d.env)
            if (stdoutHandler == null) {
                logging { trace("discard stdout") }
                processBuilder.discardStdout()
            }
            process = processBuilder.start()
            executionInfo = executionInfo.copy(
                pid = process.safePID(),
                startAt = System.currentTimeMillis(),
            )
            logging { debug("process started, pid: {}", executionInfo.pid) }
            if (d.stdinHandler == null) {
                logging { trace("close stdin because the handler not set") }
                process.outputStream.close()
            }
            val stdinHandlerFuture = maybeHandleFor("stdin", process.outputStream, d.stdinHandler)
            val stdoutHandlerFuture = maybeHandleFor("stdout", process.inputStream, stdoutHandler)
            val stderrHandlerFuture = maybeHandleFor("stderr", process.errorStream, stderrHandler)
            if (stderrHandlerFuture == null) {
                logging { trace("no stderr handler set, builtin error recorder will be used") }
                maybeHandleFor("stderr-recorder", process.errorStream) {
                    it.reader(Charset.defaultCharset()).copyTo(stderrRecorder.writer)
                }
            }
            if (d.timeout > 0) {
                logging { debug("wait the process terminate in {}ms", d.timeout) }
                if (!process.waitFor(d.timeout, TimeUnit.MILLISECONDS)) {
                    logging { debug("timeout, process will be killed") }
                    process.destroyForcibly()
                }
            }
            logging { trace("wait the process terminate") }
            executionInfo = executionInfo.copy(exitCode = process.waitFor(), endAt = System.currentTimeMillis())
            logging { debug("process terminated, exit code: {}", executionInfo.exitCode) }
            listOfNotNull(stdinHandlerFuture, stdoutHandlerFuture, stderrHandlerFuture).waitAll()
            logging { trace("all handlers terminated") }
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
            runCatching { process?.destroyForcibly() }.onFailure {
                logging { debug("process killing failed", it) }
                e.addSuppressed(e)
            }
            when (e) {
                is InterruptedException, is CmdlineHandlerException, is TimeoutException -> throw e
                else -> throw CmdlineException(info = executionInfo, cause = e)
            }
        } finally {
            cleanupHandlers.forEach { it() }
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

}

