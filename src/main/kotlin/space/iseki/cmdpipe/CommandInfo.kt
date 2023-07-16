package space.iseki.cmdpipe

import java.io.File
import java.nio.charset.Charset

data class CommandInfo internal constructor(
    /**
     * commandline
     */
    val commandLine: List<String> = emptyList(),
    /**
     * working directory. If the property is null, Java default value (current working directory) will be used
     */
    val workingDirectory: File? = null,
    /**
     * environment variables, only containing specified
     */
    val additionalEnvVars: List<EnvVar> = emptyList(),
    /**
     * zero will be interpreted as no timeout, milliseconds
     */
    val timeout: Long = 0,
    val inheritIO: Boolean = false,
    val killSubprocess: Boolean = true,
    val enableDefaultErrorRecorder: Boolean = true,
    val ioCharset: Charset = defaultCharset,
) {
    private companion object {
        val defaultCharset: Charset = Charset.defaultCharset()
    }
}

