package space.iseki.cmdpipe

import java.io.File
import kotlin.test.Test

class CmdlineExecutionInfoTest{

    @Test
    fun test(){
        val info = CmdlineExecutionInfo(
            cmdline = listOf("foo", "bar"),
            environments = mapOf("a" to "1", "b" to null, "c" to "这是个\n多行文本"),
            workingDirectory = File("/test"),
            startAt = 1,
            endAt = 1000,
            pid = 1,
            exitCode = 2,
        )
        println(info.generateMessage())
    }
}
