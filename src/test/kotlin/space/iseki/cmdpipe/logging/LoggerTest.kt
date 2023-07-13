package space.iseki.cmdpipe.logging

import org.junit.jupiter.api.Test

class LoggerTest {

    @Test
    fun test(){
        val logger = Logger.getLogger(LoggerTest::class.java)
        logger.debug("{}", 123)
    }
}