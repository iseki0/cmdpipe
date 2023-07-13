package space.iseki.cmdpipe

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test


class EscapeTest {

    @Test
    fun test() {
        val t = simpleEscapeString("abcdefg\r\n啊あ😀😁😂🤣😃", true).also(::println)
        assertEquals("abc", simpleEscapeString("abc", true))
        assertEquals("\\u554A", simpleEscapeString("啊", true))
        assertEquals("啊", simpleEscapeString("啊", false))
        assertEquals("😀", simpleEscapeString("😀", false))
        assertEquals("\\uD83D\\uDE00", simpleEscapeString("😀", true))
        assertEquals("\\r\\n\\t", simpleEscapeString("\r\n\t", true))
        assertEquals("\\u0000", simpleEscapeString("\u0000", true))

    }
}