package space.iseki.cmdpipe

import java.io.File

/**
 * The class is internal use only, no compatibility guaranteed.
 */
internal data class CmdlineExecutionInfo(
    val cmdline: List<String>,
    val environments: List<Pair<String, String?>>,
    val workingDirectory: File?,
    val startAt: Long,
    val endAt: Long,
    val pid: Long? = null,
    val exitCode: Int? = null
)
