@file:JvmName("-Escape")

package space.iseki.cmdpipe

import java.util.*

private val hex = HexFormat.of().withUpperCase()
internal fun simpleEscapeString(input: String, escapeUnicode: Boolean): String = buildString(capacity = input.length) {
    for (ch in input) {
        val code = ch.code
        when {
            escapeUnicode && code > 0x7f ->{
                append('\\')
                append('u')
                append(hex.toHexDigits(ch))
            }

            code < 32 -> when (ch) {
                '\b' -> append("\\b")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                '\u000C' -> append("\\u000C")
                '\r' -> append("\\r")
                else ->{
                    append('\\')
                    append('u')
                    append(hex.toHexDigits(ch))
                }
            }

            else -> when (ch) {
                '\'' -> append("\\'")
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                else -> append(ch)
            }
        }
    }
}


