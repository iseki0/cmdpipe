package space.iseki.cmdpipe

import org.junit.jupiter.api.Test

import java.time.Instant
import java.util.*

class TextualFormatterTest {

    @Test
    fun format() {
        val a = commandInfoOf(
            commandLine = listOf("foo", "bar"),
            workingDirectory = null,
            additionalEnvVars = listOf(
                EnvVar("a", "b"),
                EnvVar("b", null),
            ),
            timeout = 10,
            inheritIO = false,
            killSubprocess = true,
        )
        TextualFormatter.format(a).also(::println)
        TextualFormatter.format(a, Locale.CHINESE).also(::println)
    }

    @Test
    fun format0(){
        val a = executionInfoOf(
            pid = 1,
            startAt = Instant.now(),
            endAt = null,
            exitCode = 99,
        )
        TextualFormatter.format(a).also(::println)
        TextualFormatter.format(a, Locale.CHINESE).also(::println)
    }

    @Test
    fun format1(){
        val n = Instant.now()
        val a = executionInfoOf(
            pid = 1,
            startAt = n,
            endAt = n,
            exitCode = 99,
        )
        TextualFormatter.format(a).also(::println)
        TextualFormatter.format(a, Locale.CHINESE).also(::println)
    }

}