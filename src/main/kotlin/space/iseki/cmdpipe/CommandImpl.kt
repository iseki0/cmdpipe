@file:JvmName("-CommandImplKt")

package space.iseki.cmdpipe

import space.iseki.cmdpipe.logging.Logger
import space.iseki.cmdpipe.logging.MdcManager
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.ThreadFactory

class CommandImpl<SO, SE>(
    private val info: CommandInfo,
    private val executor: Executor,
    private val stdinHandler: OutputHandler<Unit>?,
    private val stdoutHandler: InputHandler<SO>?,
    private val stderrHandler: InputHandler<SE>?,
) : Cmdline<SO, SE> {
    companion object {
        private val logger = Logger.getLogger(CommandImpl::class.java)
        private val defaultExecutor by lazy {
            val factory = Executors.defaultThreadFactory()
            val delegatedThreadFactory = ThreadFactory { r -> factory.newThread(r).also { it.isDaemon = true } }
            Executors.newCachedThreadPool(delegatedThreadFactory)
                .also { logger.debug("builtin thread pool created, {}", it) }
        }

    }

    private fun copy(info: CommandInfo) =
        CommandImpl(info, executor, stdinHandler, stdoutHandler, stderrHandler)

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

    /**
     *
     * @see ProcessBuilder.environment
     * @see ProcessBuilder.start
     * @throws UnsupportedOperationException
     * @throws IllegalArgumentException
     * @throws java.io.IOException
     */
    override fun execute(): ExecutionResult<SO, SE> {
        if (info.commandLine.isEmpty()) {
            throw IllegalArgumentException("command line is empty")
        }
        logger.debug("executing command: {} at {}", info.commandLine, info.workingDirectory)
        val pb = ProcessBuilder()
        pb.command(info.commandLine)
        if (info.workingDirectory != null) configureDir(pb, info.workingDirectory)
        configureEnviron(pb, info.additionalEnvVars)
        configureRedirect(pb)
        val process = pb.start()
        logger.debug("process started, pid: {}", process.pid())
        try {
        } catch (th: Throwable) {

        }


        TODO("Not yet implemented")
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
        if (stderrHandler == null) {
            logger.trace("stderr handler is not set, default error recorder will be used")
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
        for ((k, v) in env) {
            if (v == null) {
                logger.trace("del envvar {}", k)
                map.remove(k)
            } else {
                logger.trace("add envvar {}", k)
                map[k] = v
            }
        }
    }

    internal inner class Running(val process: Process) {
        private val exceptionManager = ExceptionManager()
        private val asyncTasks = mutableListOf<FutureTask<*>>()

        fun doWait() {
            val pid = safePid()

            val stdoutTask = when {
                info.inheritIO || stdoutHandler == null -> null
                else -> setupHandler("stdout", stdoutHandler, process.inputStream)
            }
            val stderrTask = when {
                info.inheritIO || stderrHandler == null -> null
                else -> setupHandler("stderr", stderrHandler, process.errorStream)
            }
            val errorRecorderTask = when {
                info.inheritIO || stderrHandler != null -> null
                else -> setupHandler("err-recorder", createErrorRecorderHandler(), process.errorStream)
            }
            val stdinTask = when {
                stdinHandler != null -> setupHandler("stdin", stdinHandler, process.outputStream)
                else -> {
                    logger.trace("stdin handler not set, close stdin of the process")
                    process.outputStream.close()
                    null
                }
            }
            TODO()
        }

        private fun createErrorRecorderHandler(): InputHandler<ErrorRecorder> = { input ->
            ErrorRecorder().also { recorder -> input.reader(Charset.defaultCharset()).copyTo(recorder.writer) }
        }

        private fun safePid() = try {
            process.pid()
        } catch (e: UnsupportedOperationException) {
            logger.debug("get PID failed, the operation is not supported: {}", e.lazyMessage())
            -1
        }


        private val mainThreadMdc = MdcManager.dump()

        private fun <T, R> setupHandler(
            handlerName: String,
            handler: (T) -> R,
            stream: T
        ): FutureTask<R> where T : AutoCloseable {
            logger.trace("setup handler {}", handlerName)
            val task = FutureTask {
                val old = MdcManager.dump()
                try {
                    MdcManager.restore(mainThreadMdc)
                    return@FutureTask stream.use(handler)
                } catch (th: Throwable) {
                    logger.debug("handler {} throws exception: {}", handlerName, th.lazyToString())
                    exceptionManager.add(th)
                    kill()
                    throw th
                } finally {
                    MdcManager.restore(old)
                }
            }
            asyncTasks.add(task)
            executor.execute(task)
            return task
        }

        private fun kill() {
            try {
                killProcessAndAllDescendants()
            } finally {
                logger.trace("killing main process")
                process.destroyForcibly()
            }
        }

        private fun killProcessAndAllDescendants() {
            try {
                for (it in process.descendants()) {
                    try {
                        val pid = runCatching { it.pid() }.getOrElse { -1 }
                        val successful = it.destroyForcibly()
                        logger.trace("kill sub-process pid {}, {}", pid, if (successful) "successful" else "failed")
                    } catch (e: Exception) {
                        logger.trace("exception caught during sub-process killing", e)
                    }
                }
            } catch (e: UnsupportedOperationException) {
                logger.debug("cannot kill descendants process, the operation is not supported: {}", e.lazyMessage())
            }
        }

    }
}
