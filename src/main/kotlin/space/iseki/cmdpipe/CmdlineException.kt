package space.iseki.cmdpipe

class CmdlineException internal constructor(
    private val info: CmdlineExecutionInfo,
    override val cause: Throwable,
) : RuntimeException() {
    override val message: String by lazy(LazyThreadSafetyMode.NONE) {
        buildString {
            val firstCauseLine = cause.toString().substringBefore('\n').trim()
            appendLine("exception happened during command execution: $firstCauseLine")
            CmdlineExecutionInfoTextualFormatter.format(info)
        }
    }
}

