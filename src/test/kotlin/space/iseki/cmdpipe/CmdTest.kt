package space.iseki.cmdpipe

import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import kotlin.test.assertTrue

class CmdTest {
    private val isWindows = System.getProperty("os.name").startsWith("Windows")
    private val cmdError = if (isWindows) listOf("cmd", "/c", "dir /xxxx") else listOf("ls", "--xxxx")
    private val cmd = if (isWindows) listOf("cmd", "/c", "dir") else listOf("ls")

    @Test
    fun testErrorCmd() {
        val stdout = Cmd.read { ctx ->
            ctx.stream.bufferedReader(Charset.defaultCharset()).readText().prependIndent("> ")
        }
        val stderr = Cmd.read { ctx ->
            ctx.stream.bufferedReader(Charset.defaultCharset()).readText().prependIndent("stderr> ")
        }
        Cmd.Builder().cmdline(cmdError).handleStdout(stdout).handleStderr(stderr).start().use {
            println(stdout.future().get())
            println(stderr.future().get())
        }
        assertTrue { stderr.future().get().contains("xxxx") }
    }

    @Test
    fun test2() {
        val stdout = Cmd.read { ctx ->
            ctx.stream.bufferedReader(Charset.defaultCharset()).readText().prependIndent("> ")
        }
        val stderr = Cmd.read { ctx ->
            ctx.stream.bufferedReader(Charset.defaultCharset()).readText().prependIndent("stderr> ")
        }
        Cmd.Builder().cmdline(cmd).handleStdout(stdout).handleStderr(stderr).start().use {
            println(stdout.future().get())
            println(stderr.future().get())
        }
    }

}