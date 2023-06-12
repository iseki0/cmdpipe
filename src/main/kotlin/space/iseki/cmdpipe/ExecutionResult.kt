package space.iseki.cmdpipe

import java.time.Duration

class ExecutionResult<SO, SE> private constructor(
    val cmdline: List<String>,
    val stdoutValue: SO,
    val stderrValue: SE,
    val exitCode: Int,
    private val startAt: Long,
    private val endAt: Long,
) {
    val usedTimeDuration: Duration
        get() = Duration.ofMillis(startAt - endAt)
}

