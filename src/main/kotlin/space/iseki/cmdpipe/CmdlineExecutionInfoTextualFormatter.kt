package space.iseki.cmdpipe

import java.time.Duration
import java.time.Instant

internal object CmdlineExecutionInfoTextualFormatter {
    fun format(sb: StringBuilder, info: CmdlineExecutionInfo) {
        with(info) {
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
    }

    fun format(info: CmdlineExecutionInfo) = buildString { format(this, info) }

    context (CmdlineExecutionInfo)
    private fun StringBuilder.appendCmdline() {
        append("Cmdline: ")
        append(cmdline)
        if (workingDirectory != null) {
            append(" at ")
            append(workingDirectory)
        }
    }

    context (CmdlineExecutionInfo)
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

    context (CmdlineExecutionInfo)
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

    context (CmdlineExecutionInfo)
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