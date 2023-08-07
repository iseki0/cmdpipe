package space.iseki.cmdpipe

class CmdlineHandlerException internal constructor(
    cause: Throwable,
    val commandInfo: CommandInfo,
    val executionInfo: ExecutionInfo,
    val stdinHandlerThrows: Throwable?,
    val stdoutHandlerThrows: Throwable?,
    val stderrHandlerThrows: Throwable?,
) : CmdlineException("caught exception in handles, the first exception: $cause")
