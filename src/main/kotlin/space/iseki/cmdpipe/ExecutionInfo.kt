package space.iseki.cmdpipe

import java.time.Instant

@JvmName("-executionInfoOf")
internal fun executionInfoOf(
    pid: Long,
    startAt: Instant,
    endAt: Instant? = null,
    exitCode: Int? = null,
    timeoutToKilled: Boolean = false,
    stderrSnapshot: String = "",
) = ExecutionInfo(pid, startAt, endAt, exitCode, timeoutToKilled, stderrSnapshot)
