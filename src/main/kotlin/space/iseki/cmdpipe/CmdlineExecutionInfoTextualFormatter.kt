package space.iseki.cmdpipe

import java.time.Duration
import java.time.Instant
import java.util.*

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
        append(t("title.cmdline"))
        append(' ')
        append(cmdline)
        if (workingDirectory != null) {
            append(' ')
            append(t("main.at"))
            append(' ')
            append(workingDirectory)
        }
    }

    context (CmdlineExecutionInfo)
    private fun StringBuilder.appendCmdStatus() {
        if (pid == null && exitCode == null) return
        append(' ')
        if (pid != null) {
            append(t("main.pid"))
            append('=')
            append(pid)
            if (exitCode != null) append(' ')
        }
        if (exitCode != null) {
            append(t("main.exit"))
            append('=')
            append(exitCode)
        }
    }

    context (CmdlineExecutionInfo)
    private fun StringBuilder.appendEnvironments() {
        appendLine(t("title.env"))
        environments.forEach { (k, v) ->
            append("  ")
            append(k)
            if (v != null) {
                append('=')
                appendLine(v.multilineFormatted())
            } else {
                append(' ')
                appendLine(t("env.cleared"))
            }
        }
    }

    context (CmdlineExecutionInfo)
    private fun StringBuilder.appendTiming() {
        append(t("title.timing"))
        append(' ')
        if (startAt != 0L) {
            append(Instant.ofEpochMilli(startAt).toString())
        }
        append("..")
        if (endAt != 0L) {
            append(Instant.ofEpochMilli(endAt).toString())
        }
        if (startAt != 0L && endAt != 0L) {
            append(' ')
            append(t("timing.duration"))
            append(' ')
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

    private val bundle by lazy { ResourceBundle.getBundle(CmdlineExecutionInfoTextualFormatter::class.java.name) }
    private fun t(key: String): String = bundle.getString(key)
}

