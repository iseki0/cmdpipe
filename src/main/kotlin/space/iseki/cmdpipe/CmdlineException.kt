package space.iseki.cmdpipe

open class CmdlineException internal constructor(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
