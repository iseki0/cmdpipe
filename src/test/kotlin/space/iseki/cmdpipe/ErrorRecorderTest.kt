package space.iseki.cmdpipe

import kotlin.test.Test

class ErrorRecorderTest{
    @Test
    fun test(){
        val recorder = ErrorRecorder()
        val text = """foo
            |bar
            |The line is very looooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong.
            |Something else.
            |We have some blank lines.
            |
            |
            |
            |For some non-ASCII characters:
            |果てなく続いていく
            |生きとし生けるもの
            |抗うことのできない
            |散りゆく輪廻抱いて
        """.trimMargin()
        text.encodeToByteArray().inputStream().reader().copyTo(recorder.writer)
        recorder.writer.close()
        println(recorder)
    }
}
