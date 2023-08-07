package space.iseki.cmdpipe

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.assertThrows
import java.nio.charset.Charset
import kotlin.test.Test

class CmdlineTest {
    private val isWindows = System.getProperty("os.name").startsWith("Windows")
    private val cmd3 = if (isWindows) listOf("cmd", "/c", "dir /?a") else listOf("ls", "-aa")
    private val cmd = if (isWindows) listOf("cmd", "/c", "dir") else listOf("ls")
    private val cmd2 = if (isWindows) listOf("cmd", "/c", "ping", "127.0.0.1") else listOf("sleep", "5")
    private val cs = when {
        isWindows -> Charset.forName("gbk")
        else -> Charset.defaultCharset()
    }

    @Test
    fun test() {
        val a = cmdline(cmd).handleStdout { it.reader(cs).readText() }.execute()
        println(a.cmdline)
        println(a.stdoutValue)
        println(a.stderrSnapshot)
    }

    @Test
    fun test2() {
        val a = cmdline(cmd3).handleStdout { it.reader(cs).readText() }.execute()
        println(a.cmdline)
        println("====stdout====")
        println(a.stdoutValue)
        println("====stderr====")
        println(a.stderrSnapshot)
        println("exit status: ${a.exitCode}")
    }

    @Test
    fun test3() {
        assertThrows<CmdlineHandlerException> {
            cmdline(cmd).handleStdout { error("1") }.withEnvironment("a" to "b").execute()
        }.printStackTrace(System.out)
    }


    @Test
    fun testCancel(){
        assertThrows<kotlinx.coroutines.TimeoutCancellationException> {
            runBlocking {
                withTimeout(1000){
                    runInterruptible {
                        val t = assertThrows<InterruptedException> {
                            cmdline(cmd2).execute()
                        }
                        println(t)
                        t.printStackTrace(System.out)
                    }
                }
            }
        }
    }

    @Test
    fun testTimeout(){
        assertThrows<CmdlineTimeoutException> {
            println(cmdline(cmd2).withTimeout(1000).execute())
        }.printStackTrace(System.out)
    }
}
