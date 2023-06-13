package space.iseki.cmdpipe

import java.util.concurrent.TimeoutException

internal class CmdlineTimeoutException internal constructor(private val info: CmdlineExecutionInfo) :
    TimeoutException() {
    override val message: String by lazy(LazyThreadSafetyMode.NONE) {
        buildString {
            append("command execution timeout. ")
            CmdlineExecutionInfoTextualFormatter.format(info)
        }
    }
}
