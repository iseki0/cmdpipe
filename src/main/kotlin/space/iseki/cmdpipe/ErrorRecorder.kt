package space.iseki.cmdpipe

import java.io.Writer

internal class ErrorRecorder(
    private val lineWidth: Int = 80,
    private val topLineCount: Int = 4,
    private val bottomLineCount: Int = 4,
) {
    val writer by lazy {
        object: Writer(){
            private val lb = StringBuilder()
            override fun close() {
                flush()
            }

            override fun flush() {
                if (lb.isNotEmpty()){
                    commitLine(lb.toString())
                    lb.clear()
                }
            }

            override fun write(cbuf: CharArray, off: Int, len: Int) {
                for (i in (off until off + len)) {
                    when(val ch = cbuf[i]){
                        '\n' -> flush()
                        '\r' -> Unit
                        else -> if (lb.length<lineWidth) lb.append(ch) else Unit
                    }
                }
            }
        }
    }
    private val topLines = ArrayList<String>()
    private val bottomLines = ArrayDeque<String>()
    private fun commitLine(line: String) {
        if (line.isBlank()) return
        if (topLines.size < topLineCount) {
            topLines += line
            return
        }
        if (bottomLines.size >= bottomLineCount) {
            bottomLines.removeFirst()
        }
        bottomLines.addLast(line)
    }

    override fun toString(): String {
        if (bottomLines.isEmpty()) {
            return topLines.joinToString(separator = "\n")
        }
        return buildList { addAll(topLines); add("...");addAll(bottomLines) }.joinToString(separator = "\n")
    }
}
