@file:JvmName("-CommandImplKt")

package space.iseki.cmdpipe

import space.iseki.cmdpipe.logging.Logger
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.concurrent.*
import kotlin.jvm.optionals.getOrNull

internal class CommandImpl<SO, SE>(
    private val info: CommandInfo,
    private val executor: Executor = defaultExecutor,
    private val stdinHandler: OutputHandler<Unit>? = null,
    private val stdoutHandler: InputHandler<SO>? = null,
    private val stderrHandler: InputHandler<SE>? = null,
) : Cmdline<SO, SE> {

    companion object {
        operator fun invoke(cmdline: List<String>) =
            CommandImpl<Nothing, Nothing>(info = commandInfoOf(commandLine = cmdline))

        @JvmStatic
        private val logger = Logger.getLogger(CommandImpl::class.java)

        @JvmStatic
        private val defaultExecutor by lazy {
            val factory = Executors.defaultThreadFactory()
            val delegatedThreadFactory = ThreadFactory { r -> factory.newThread(r).also { it.isDaemon = true } }
            Executors.newCachedThreadPool(delegatedThreadFactory)
                .also { logger.debug("builtin thread pool created, {}", it) }
        }

    }

    private fun copy(info: CommandInfo) = CommandImpl(info, executor, stdinHandler, stdoutHandler, stderrHandler)

    override fun withCmdline(cmdArray: Collection<String>): Cmdline<SO, SE> =
        info.copy(commandLine = cmdArray.toList()).let(::copy)

    override fun withEnvironment(vararg variables: Pair<String, String?>): Cmdline<SO, SE> {
        val nenv = info.additionalEnvVars + variables.map(::EnvVar)
        return info.copy(additionalEnvVars = nenv).let(::copy)
    }

    override fun inheritIO(f: Boolean): Cmdline<SO, SE> = info.copy(inheritIO = f).let(::copy)

    override fun withWorkingDirectory(dir: File): Cmdline<SO, SE> = info.copy(workingDirectory = dir).let(::copy)

    override fun withExecutor(executor: Executor): Cmdline<SO, SE> =
        CommandImpl(info, executor, stdinHandler, stdoutHandler, stderrHandler)

    override fun withTimeout(millisecond: Long): Cmdline<SO, SE> = info.copy(timeout = millisecond).let(::copy)

    override fun <SO> handleStdout(handler: InputHandler<SO>): Cmdline<SO, SE> =
        CommandImpl(info, executor, stdinHandler, handler, stderrHandler)

    override fun <SE> handleStderr(handler: InputHandler<SE>): Cmdline<SO, SE> =
        CommandImpl(info, executor, stdinHandler, stdoutHandler, handler)

    override fun handleStdin(handler: OutputHandler<Unit>): Cmdline<SO, SE> =
        CommandImpl(info, executor, handler, stdoutHandler, stderrHandler)

    override fun execute(): ExecutionResult<SO, SE> {
        if (info.commandLine.isEmpty()) {
            throw IllegalArgumentException("command line is empty")
        }
        logger.debug("executing command: {} at {}", info.commandLine, info.workingDirectory)
        try {
            val pb = ProcessBuilder()
            logger.trace("configuring process builder {}", pb)
            configureProcessBuilder(pb)
            logger.trace("configuring completed")
            val process = pb.start()
            logger.debug("process started, pid: {}", process.pid())
            return Running(process).doHandle()
        } catch (e: IOException) {
            throw CmdlineException("unhandled I/O exception during the commandline execution", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt() // recover the state
            throw CmdlineInterruptedException()
        }
    }

    private fun configureProcessBuilder(pb: ProcessBuilder) {
        pb.command(info.commandLine)
        info.workingDirectory?.let { configureDir(pb, it) }
        configureEnviron(pb, info.additionalEnvVars)
        configureRedirect(pb)
    }


    private fun configureRedirect(pb: ProcessBuilder) {
        if (info.inheritIO) {
            logger.trace("inheritIO, so no handler and recorder will be used")
            pb.inheritIO()
            return
        }
        if (stdoutHandler == null) {
            logger.trace("stdout handler is not set, discard")
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        }
        if (stderrHandler == null && !info.enableDefaultErrorRecorder) {
            logger.trace("stderr handler is not set, and the default error recorder is disabled. Discard stderr")
            pb.redirectError(ProcessBuilder.Redirect.DISCARD)
        }
    }

    private fun configureDir(pb: ProcessBuilder, dir: File) {
        logger.trace("configuring working directory")
        pb.directory(dir)
    }

    private fun configureEnviron(pb: ProcessBuilder, env: List<EnvVar>) {
        if (env.isEmpty()) return
        logger.trace("configuring process environment variables")
        val map = pb.environment()
        for (env in env) {
            val k = env.name
            val v = env.value
            if (v == null) {
                logger.trace("del envvar {}", k)
                map.remove(k)
            } else {
                logger.trace("add envvar {}", k)
                map[k] = v
            }
        }
    }


    internal inner class Running(private val process: Process) {

        private val pid = runCatching { process.pid() }
            .onFailure { logger.debug("get pid failed", it) }.getOrElse { -1 }


        private val startAt = Instant.now()
        private val stdinHolder: Holder<OutputStream, Unit>?
        private val stdoutHolder: Holder<InputStream, SO>?
        private val stderrHolder: Holder<InputStream, SE>?
        private val errorRecorderHolder: Holder<InputStream, String>?


        init {
            try {
                stdinHolder = when {
                    info.inheritIO -> null
                    stdinHandler != null -> Holder("stdin", process.outputStream, stdinHandler)
                    else -> {
                        runCatching { process.outputStream.close() }
                            .onSuccess { logger.trace("stdin closed") }
                            .onFailure { logger.trace("stdin cannot close", it) }
                        null
                    }
                }
                stdoutHolder = when {
                    info.inheritIO -> null
                    stdoutHandler != null -> Holder("stdout", process.inputStream, stdoutHandler)
                    else -> null
                }
                stderrHolder = when {
                    info.inheritIO -> null
                    stderrHandler != null -> Holder("stderr", process.errorStream, stderrHandler)
                    else -> null
                }
                errorRecorderHolder = when {
                    info.inheritIO || stderrHolder != null -> null
                    info.enableDefaultErrorRecorder ->
                        Holder("stderr-recorder", process.errorStream, createErrorRecorderHandler())

                    else -> null
                }
            } catch (th: Throwable) {
                logger.debug("exception caught during get stdio, killing")
                closeAllStream() // streams may not be initialized, not a problem
                kill()
                throw th
            }
        }

        private val helper = StreamTaskHelper(executor) {
            logger.trace("handler exception caught, async task helper shutdown")
            closeAllStream()
            kill()
        }

        fun doHandle(): ExecutionResult<SO, SE> {
            var allPipeClosed = false
            try {
                val stdinJob = stdinHolder?.let { helper.submitTask { it.execute() } }
                val stdoutJob = stdoutHolder?.let { helper.submitTask { it.execute() } }
                val stderrJob = stderrHolder?.let { helper.submitTask { it.execute() } }
                val errorRecorderJob = errorRecorderHolder?.let { helper.submitTask { it.execute() } }
                var timeoutToKilled = false
                if (info.timeout != 0L) {
                    logger.trace("waiting process with timeout, {}ms", info.timeout)
                    if (!process.waitFor(info.timeout, TimeUnit.MILLISECONDS)) {
                        logger.debug("process timeout, killing")
                        timeoutToKilled = true
                        helper.handleError(CancellationException("process execution timeout"))
                        kill()
                    }
                }
                logger.trace("waiting process terminate")
                val exitCode = process.waitFor()
                val endAt = Instant.now()
                logger.trace("process terminated, exit code: {}", exitCode)
                logger.trace("wait all handler terminate")
                stdinJob?.getOrNull()
                stdoutJob?.getOrNull()
                stderrJob?.getOrNull()
                errorRecorderJob?.getOrNull()
                stdinJob?.getOrNull()
                logger.trace("all handler terminated")
                val rootException = helper.rootException
                val executionInfo = executionInfoOf(
                    pid = pid,
                    startAt = startAt,
                    endAt = endAt,
                    exitCode = exitCode,
                    timeoutToKilled = timeoutToKilled,
                )
                if (timeoutToKilled) {
                    throw CmdlineTimeoutException(
                        commandInfo = info,
                        executionInfo = executionInfo,
                        stdinHandlerThrows = stdinJob?.exceptionOrNull(),
                        stdoutHandlerThrows = stdoutJob?.exceptionOrNull(),
                        stderrHandlerThrows = stderrJob?.exceptionOrNull(),
                    )
                }
                if (rootException != null) {
                    logger.trace("exception caught in handlers")
                    throw CmdlineHandlerException(
                        rootException,
                        commandInfo = info,
                        executionInfo = executionInfo,
                        stdinHandlerThrows = stdinJob?.exceptionOrNull(),
                        stdoutHandlerThrows = stdoutJob?.exceptionOrNull(),
                        stderrHandlerThrows = stderrJob?.exceptionOrNull(),
                    )
                }
                allPipeClosed = true
                return ExecutionResult<SO, SE>(
                    cmdline = info.commandLine,
                    exitCode = exitCode,
                    _stdoutValue = stdoutJob?.getOrNull(),
                    _stderrValue = stderrJob?.getOrNull(),
                    startAt = startAt.epochSecond,
                    endAt = endAt.epochSecond,
                    commandInfo = info,
                    executionInfo = executionInfo,
                )
            }catch (e: RejectedExecutionException){
                throw CmdlineException("internal executor has rejected to execute handler task", e)
            } finally {
                if (!allPipeClosed) {
                    logger.trace("cleaning...")
                    runCatching {
                        kill()
                        closeAllStream()
                    }.onFailure { logger.debug("cleanup failed", it) }
                }
            }
        }

        private fun closeAllStream() {
            logger.trace("close all opened stream")
            runCatching { stdinHolder?.stream?.close() }.onFailure { logger.debug("fail to close stdin", it) }
            runCatching { stdoutHolder?.stream?.close() }.onFailure { logger.debug("fail to close stdout", it) }
            runCatching { stderrHolder?.stream?.close() }.onFailure { logger.debug("fail to close stderr", it) }
            runCatching { errorRecorderHolder?.stream?.close() }.onFailure { logger.debug("fail to close stderr", it) }
        }

        private fun kill() {
            if (info.killSubprocess) runCatching { killAllDescendants() }
            logger.trace("kill main process")
            process.destroyForcibly()
        }

        private fun killAllDescendants() {
            logger.trace("kill all descendants")
            val children = runCatching { process.descendants() }
                .onFailure { logger.debug("list descendant process is unsupported: {}", it.lazyMessage()) }
                .getOrNull() ?: return
            for (cp in children) {
                val cpPid = runCatching { cp.pid() }.getOrElse { -1 }
                val cpCommand = cp.info().commandLine().getOrNull()
                    ?: cp.info().command().getOrNull().orEmpty()
                runCatching {
                    val rs = cp.destroyForcibly()
                    logger.trace("kill sub-process pid {}, command: {}, successful: {}", cpPid, cpCommand, rs)
                }
            }
        }

        private fun createErrorRecorderHandler(): InputHandler<String> = { input ->
            ErrorRecorder().also { recorder -> input.reader(info.ioCharset).copyTo(recorder.writer) }.toString()
        }

    }

    private class Holder<T : AutoCloseable, R>(val name: String, val stream: T, val handler: (T) -> R) {
        fun execute() = try {
            logger.trace("handler {} begin", name)
            stream.use(handler)
        } catch (th: Throwable) {
            logger.trace("handler {} caught", name, th)
            throw th
        } finally {
            logger.trace("handler {} end", name)
        }
    }

}

@Suppress("NOTHING_TO_INLINE")
private inline fun <T> FutureTask<T>.getOrNull() = runCatching { get() }.getOrNull()

@Suppress("NOTHING_TO_INLINE")
private inline fun <T> FutureTask<T>.exceptionOrNull() = runCatching { get() }.exceptionOrNull()
