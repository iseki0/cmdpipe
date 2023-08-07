@file:JvmName("-CommandInfo")

package space.iseki.cmdpipe

import java.io.File
import java.nio.charset.Charset

private val defaultCharset: Charset = Charset.defaultCharset()

@JvmName("-commandInfoOf")
internal fun commandInfoOf(
    commandLine: List<String> = emptyList(),
    workingDirectory: File? = null,
    additionalEnvVars: List<EnvVar> = emptyList(),
    timeout: Long = 0,
    inheritIO: Boolean = false,
    killSubprocess: Boolean = true,
    enableDefaultErrorRecorder: Boolean = true,
    ioCharset: Charset = defaultCharset,
): CommandInfo = CommandInfo(
    commandLine,
    workingDirectory,
    additionalEnvVars,
    timeout,
    inheritIO,
    killSubprocess,
    enableDefaultErrorRecorder,
    ioCharset,
)

@JvmName("-copy")
internal fun CommandInfo.copy(
    commandLine: List<String> = commandLine(),
    workingDirectory: File? = workingDirectory(),
    additionalEnvVars: List<EnvVar> = additionalEnvVars(),
    timeout: Long = timeout(),
    inheritIO: Boolean = inheritIO(),
    killSubprocess: Boolean = killSubprocess(),
    enableDefaultErrorRecorder: Boolean = enableDefaultErrorRecorder(),
    ioCharset: Charset = ioCharset(),
) = CommandInfo(
    commandLine,
    workingDirectory,
    additionalEnvVars,
    timeout,
    inheritIO,
    killSubprocess,
    enableDefaultErrorRecorder,
    ioCharset
)
