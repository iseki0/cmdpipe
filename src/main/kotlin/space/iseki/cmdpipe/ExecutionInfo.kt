package space.iseki.cmdpipe

import java.time.Instant

data class ExecutionInfo internal constructor(
    val pid: Int,
    val startAt: Instant,
    val endAt: Instant? = null,
    val exitCode: Int? = null,
    val timeoutToKilled: Boolean = false,
)
