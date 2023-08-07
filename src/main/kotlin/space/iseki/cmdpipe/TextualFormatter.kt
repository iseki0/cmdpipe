package space.iseki.cmdpipe

import java.text.MessageFormat
import java.time.Duration
import java.util.*

internal object TextualFormatter {
    private val baseName = TextualFormatter::class.java.name
    private val defaultResource = ResourceBundle.getBundle(baseName)

    private fun getResourceBundle(locale: Locale? = null) =
        locale?.let { ResourceBundle.getBundle(baseName, it) } ?: defaultResource

    fun format(data: CommandInfo, locale: Locale? = null) = buildString {
        with(getResourceBundle(locale)) {
            appendLine(t("cmdinfo.headerLine"))
            append(t("id.commandline"))
            appendLine(data.commandLine.toString())
            if (data.workingDirectory == null) {
                appendLine(t("cmdinfo.workingDir.unset"))
            } else {
                appendLine(t("cmdinfo.workingDir").messageFormat(data.workingDirectory!!))
            }
            if (data.timeout!=0L){
                appendLine(t("cmdinfo.timeout").messageFormat(data.timeout))
            }
            appendLine(t("cmdinfo.inheritIO").messageFormat(data.inheritIO.toString()))
            appendLine(t("cmdinfo.charset").messageFormat(data.ioCharset.name()))
            appendLine(t("cmdinfo.envtitle").messageFormat(data.additionalEnvVars.size))
            for (it in data.additionalEnvVars) {
                append("  ")
                appendLine(it.toString())
            }
        }
    }

    fun format(data: ExecutionInfo, locale: Locale? = null) = buildString {
        with(getResourceBundle(locale)) {
            appendLine(t("exeinfo.headerLine"))
            appendLine(t("exeinfo.pid").messageFormat(data.pid))
            run {
                val t = data.exitCode?.toString() ?: "-"
                appendLine(t("exeinfo.exitcode").messageFormat(t))
            }
            if (data.endAt != null) {
                val dur = runCatching { Duration.between(data.startAt, data.endAt).toString() }.getOrNull() ?: "-"
                appendLine(t("exeinfo.timing").messageFormat(data.startAt, data.endAt!!, dur))
            } else {
                appendLine(t("exeinfo.timing.inProgress").messageFormat(data.startAt))
            }
            appendLine(t("exeinfo.timeoutToKill").messageFormat(data.timeoutToKilled))
        }
    }

    private fun ResourceBundle.t(tag: String) = getString(tag)
    private fun String.messageFormat(vararg args: Any) = MessageFormat.format(this, *args)
}
