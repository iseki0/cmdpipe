package space.iseki.cmdpipe

import java.io.File

data class CommandInfo internal constructor(
    /**
     * commandline
     */
    val commandLine: List<String>,
    /**
     * working directory. If the property is null, Java default value (current working directory) will be used
     */
    val workingDirectory: File?,
    /**
     * environment variables, only containing specified
     */
    val additionalEnvVars: List<EnvVar>,
    /**
     * zero will be interpreted as no timeout, milliseconds
     */
    val timeout: Long,
    val inheritIO: Boolean,
    val killSubprocess: Boolean,
)

