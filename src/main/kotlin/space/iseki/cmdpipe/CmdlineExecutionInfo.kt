package space.iseki.cmdpipe

import java.io.File
import java.time.Duration
import java.time.Instant

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
) {
    internal fun generateMessage(sb: StringBuilder) {
        with(sb) {
            appendCmdline()
            appendCmdStatus()
            appendLine()
            if (startAt != 0L || endAt != 0L) {
                appendTiming()
                appendLine()
            }
            if (environments.isNotEmpty()) appendEnvironments()
        }
    }

    fun generateMessage() = buildString { generateMessage(this) }

    private fun StringBuilder.appendCmdline() {
        append("Cmdline: ")
        append(cmdline)
        if (workingDirectory != null) {
            append(" at ")
            append(workingDirectory)
        }
    }

    private fun StringBuilder.appendCmdStatus() {
        if (pid == null && exitCode == null) return
        append(" [")
        if (pid != null) {
            append("Pid=")
            append(pid)
            if (exitCode != null) append(' ')
        }
        if (exitCode != null) {
            append("Exit=")
            append(exitCode)
        }
        append(']')
    }

    private fun StringBuilder.appendEnvironments() {
        appendLine("Environment variables:")
        environments.forEach { (k, v) ->
            append("  ")
            append(k)
            if (v != null) {
                append('=')
                appendLine(v.multilineFormatted())
            } else {
                appendLine(" (cleared)")
            }
        }
    }

    private fun StringBuilder.appendTiming() {
        append("Timing: ")
        if (startAt != 0L) {
            append(Instant.ofEpochMilli(startAt).toString())
        }
        append("..")
        if (endAt != 0L) {
            append(Instant.ofEpochMilli(endAt).toString())
        }
        if (startAt != 0L && endAt != 0L) {
            append(" duration ")
            append(Duration.ofMillis(endAt - startAt).toString())
        }
    }

    private fun String.multilineFormatted(linePrefix: String = " ".repeat(4)) = buildString {
        val lines = this@multilineFormatted.lines()
        append(lines.first())
        for (it in lines.subList(1, lines.size)) {
            appendLine()
            append(linePrefix)
            append(it)
        }
    }
}
