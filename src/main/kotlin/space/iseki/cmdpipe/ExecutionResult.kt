package space.iseki.cmdpipe

import java.time.Duration

class ExecutionResult<SO, SE> internal constructor(
    val cmdline: List<String>,
    val exitCode: Int,
    private val _stdoutValue: Any?,
    private val _stderrValue: Any?,
    private val startAt: Long,
    private val endAt: Long,
    val commandInfo: CommandInfo,
    val executionInfo: ExecutionInfo,
) {
    val usedTimeDuration: Duration
        get() = Duration.ofMillis(startAt - endAt)

    val stdoutValue: SO
        @Suppress("UNCHECKED_CAST")
        get() = _stdoutValue as SO

    val stderrValue: SE
        @Suppress("UNCHECKED_CAST")
        get() = _stderrValue as SE

    val stderrSnapshot: String
        get() = executionInfo.stderrSnapshot
}

