package space.iseki.cmdpipe

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.charset.Charset
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteExisting
import kotlin.io.path.pathString
import kotlin.system.measureTimeMillis
import kotlin.test.*

class CmdTest {
    private val isWindows = !OSNameUtils.IS_UNIX_LIKE
    private val cmdError = if (isWindows) listOf("cmd", "/c", "dir /xxxx") else listOf("ls", "--xxxx")
    private val cmd = if (isWindows) listOf("cmd", "/c", "dir") else listOf("ls")

    @Test
    fun testRunErrorCmd() {
        val stdout = Cmd.input { ctx ->
            ctx.stream.bufferedReader(Charset.defaultCharset()).readText().prependIndent("> ")
        }
        val stderr = Cmd.input { ctx ->
            ctx.stream.bufferedReader(Charset.defaultCharset()).readText().prependIndent("stderr> ")
        }
        Cmd.Builder().cmdline(cmdError).handleStdout(stdout).handleStderr(stderr).start()
        println(stdout.future().get())
        println(stderr.future().get())
        assertTrue { stderr.future().get().contains("xxxx") }
    }

    @Test
    fun testRunCmd() {
        val stdout = Cmd.input { ctx ->
            ctx.stream.bufferedReader(Charset.defaultCharset()).readText().prependIndent("> ")
        }.lastInit()
        val stderr = Cmd.input { ctx ->
            ctx.stream.bufferedReader(Charset.defaultCharset()).readText().prependIndent("stderr> ")
        }
        Cmd.Builder().cmdline(cmd).handleStdout(stdout).handleStderr(stderr).start()
        println(stdout.future().get())
        println(stderr.future().get())
    }

    @Test
    fun testThrowCmdNotFound() {
        val e = assertThrows<IOException> { Cmd.Builder().cmdline("something_does_not_exist").start().stopAll(true) }
        println(e.message)
        assertTrue { e.message!!.contains("error=2") }
    }

    @Test
    fun testNotExecutable() {
        Assumptions.assumeTrue(OSNameUtils.IS_UNIX_LIKE)
        val f = kotlin.io.path.createTempFile(suffix = ".sh")
        try {
            val e = assertThrows<IOException> { Cmd.Builder().cmdline(f.pathString).start().stopAll(true) }
            assertContains(e.message!!, "error=13")
            val cmd = Cmd.Builder().cmdline(f.pathString).autoGrantExecutableOnFailure().start()
            assertTrue(cmd.waitFor(1, TimeUnit.SECONDS))
            assertEquals(0, cmd.process.exitValue())
        } finally {
            f.deleteExisting()
        }
    }

    @Test
    fun testReUseProcessor() {
        val p = Cmd.input { it.stream.bufferedReader(Charset.defaultCharset()).readText() }
        Cmd.Builder().cmdline(cmd).handleStdout(p).start().stopAll(true)
        val e =
            assertThrows<IllegalStateException> { Cmd.Builder().cmdline(cmd).handleStdout(p).start() }.also(::println)
        assertContains(e.message!!, "STDOUT")
        assertContains(e.message!!, "used")
    }

    @Test
    fun testRunNodeTimeoutKill() {
        val stdin = Cmd.output { Thread.sleep(8) }
        val node = startSkipTestIfNotFound(Cmd.Builder().cmdline("node").handleStdin(stdin))
        try {
            val f = node.backgroundWaitTimeoutKill(100, TimeUnit.MILLISECONDS)
            assertSame(f, node.backgroundWaitTimeoutKill(100, TimeUnit.MILLISECONDS))
            val t = measureTimeMillis {
                assertTimeoutPreemptively(Duration.ofSeconds(1)) {
                    assertFalse(f.get())
                }
                assertTimeout(Duration.ofSeconds(1)) {
                    try {
                        stdin.future().get()
                    } catch (ignored: InterruptedException) {
                    }
                }
            }
            println(t)
            assertTrue(t > 100)
        } finally {
            if (node.process.isAlive) node.stopAll(true)
        }

    }

    @Test
    fun testRunNodeKill() {
        val stdout = Cmd.input {
            it.stream.bufferedReader(Charset.defaultCharset()).readText()
        }
        val node = startSkipTestIfNotFound(Cmd.Builder().cmdline("node").handleStdout(stdout))
        val p = node.process
        assertTrue(p.isAlive)
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

    @Test
    fun testNodeInteractive() {
        val stdin = Cmd.output { (_, _, stream) ->
            stream.use { it.write("console.log(12345+54321);".encodeToByteArray()) }
        }
        val stdout = Cmd.input { (_, _, s) ->
            s.readAllBytes().decodeToString()
        }
        val stderr = Cmd.input { (_, _, s) ->
            s.readAllBytes().decodeToString()
        }
        val cmd = Cmd.Builder().cmdline("node").handleStdin(stdin).handleStdout(stdout).handleStderr(stderr)
        val f = startSkipTestIfNotFound(cmd).backgroundWaitTimeoutKill(3, TimeUnit.SECONDS)
        assertTrue(f.get(), "node not exit")
        assertContains(stdout.future().get(), "66666")
        assertTrue(stderr.future().get().isEmpty())
    }


    private fun startSkipTestIfNotFound(cmd: Cmd.Builder) = try {
        cmd.start()
    } catch (e: IOException) {
        if (e.message!!.contains("error=2")) {
            Assumptions.assumeTrue(false, "executable file not found")
        }
        throw e
    }
}
