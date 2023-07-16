package space.iseki.cmdpipe

class CmdlineHandlerException internal constructor(
    override val cause: Throwable,
    val commandInfo: CommandInfo,
    val executionInfo: ExecutionInfo,
    val stdinHandlerThrows: Throwable?,
    val stdoutHandlerThrows: Throwable?,
    val stderrHandlerThrows: Throwable?,
) : RuntimeException() {
    override val message: String = "caught exception in handles, the first exception: $cause"
}
