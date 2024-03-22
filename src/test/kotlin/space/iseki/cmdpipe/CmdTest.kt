package space.iseki.cmdpipe

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.io.IOException
import java.nio.charset.Charset
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
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
        Cmd.Builder().cmdline(cmdError).handleStdout(stdout).handleStderr(stderr).start()
        println(stdout.future().get())
        println(stderr.future().get())
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
        Cmd.Builder().cmdline(cmd).handleStdout(stdout).handleStderr(stderr).start()
        println(stdout.future().get())
        println(stderr.future().get())
    }

    @Test
    fun testThrows1() {
        val e = assertThrows<IOException> { Cmd.Builder().cmdline("something_does_not_exist").start() }
        println(e.message)
        assertTrue { e.message!!.contains("error=2") }
    }

    @Test
    fun testRunNodeKill() {
        val stdout = Cmd.read {
            it.stream.bufferedReader(Charset.defaultCharset()).readText()
        }
        val node = try {
            Cmd.Builder().cmdline("node").handleStdout(stdout).start()
        } catch (e: IOException) {
            if (e.message!!.contains("error=2")) {
                Assumptions.assumeTrue(false, "node not found")
            }
            throw e
        }
        val p = node.process
        assertTrue { p.isAlive }
        node.stopAll(true)
        assertTrue(p.waitFor(1, TimeUnit.SECONDS))
        assertFalse(p.isAlive)
        assertTimeoutPreemptively(Duration.ofSeconds(1)) {
            try {
                println(stdout.future().get().prependIndent("> "))
            } catch (e: ExecutionException) {
                if (e.cause is IOException && e.cause!!.message!!.contains("Stream closed")) {
                    println("Stream closed")
                } else {
                    throw e
                }
            }
            println("node pid: ${node.process.pid()}")
            println("node exit: ${node.process.exitValue()}")
        }
    }

}
