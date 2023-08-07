package space.iseki.cmdpipe

class CmdlineTimeoutException internal constructor(
    val commandInfo: CommandInfo,
    val executionInfo: ExecutionInfo,
    val stdinHandlerThrows: Throwable?,
    val stdoutHandlerThrows: Throwable?,
    val stderrHandlerThrows: Throwable?,
) : RuntimeException("command execution timeout") {
}