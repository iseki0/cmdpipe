package space.iseki.cmdpipe

import org.junit.jupiter.api.Test
import java.nio.charset.Charset

class CmdTest {
    private val isWindows = System.getProperty("os.name").startsWith("Windows")
    private val cmd3 = if (isWindows) listOf("cmd", "/c", "dir /?a") else listOf("ls", "-aa")
    private val cmd = if (isWindows) listOf("cmd", "/c", "dir") else listOf("ls")

    @Test
    fun test1() {
        val stdout = Cmd.read { ctx ->
            ctx.stream.bufferedReader(Charset.defaultCharset()).readText().prependIndent("> ")
        }
        val stderr = Cmd.read { ctx ->
            ctx.stream.bufferedReader(Charset.defaultCharset()).readText().prependIndent("stderr> ")
        }
        Cmd.Builder().cmdline(cmd3).handleStdout(stdout).handleStderr(stderr).start().use {
            println(stdout.future().get())
            println(stderr.future().get())
        }
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