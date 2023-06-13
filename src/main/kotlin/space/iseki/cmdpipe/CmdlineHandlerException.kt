package space.iseki.cmdpipe

class CmdlineHandlerException internal constructor(
    private val info: CmdlineExecutionInfo,
    override val cause: Throwable,
    private val stdinHandlerThrows: Throwable?,
    private val stdoutHandlerThrows: Throwable?,
    private val stderrHandlerThrows: Throwable?,
) : RuntimeException() {
    override val message: String by lazy(LazyThreadSafetyMode.NONE) {
        buildString {
            val firstCauseLine = cause.toString().substringBefore('\n').trim()
            appendLine("handler throws exception during command execution, the first exception: $firstCauseLine")
            if (stdinHandlerThrows != null) {
                appendLine("StdinHandler: ${"$stdinHandlerThrows".substringBefore('\n').trim()}")
            }
            if (stdoutHandlerThrows != null) {
                appendLine("StdoutHandler: ${"$stdoutHandlerThrows".substringBefore('\n').trim()}")
            }
            if (stderrHandlerThrows != null) {
                appendLine("StderrHandler: ${"$stderrHandlerThrows".substringBefore('\n').trim()}")
            }
            CmdlineExecutionInfoTextualFormatter.format(info)
        }
    }
}


