package space.iseki.cmdpipe.logging

import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.spi.LocationAwareLogger


@Suppress("unused")
internal class Slf4JLoggerFactory : LoggerFactory {
    override fun get(name: String): Logger = org.slf4j.LoggerFactory.getLogger(name).let {
        when (it) {
            is LocationAwareLogger -> Slf4jLocationAwareLoggerWrapper1(it)
            else -> Slf4jLoggerWrapper(it)
        }
    }


    private class Slf4jLoggerWrapper(private val logger: org.slf4j.Logger) : org.slf4j.Logger by logger, Logger
    private class Slf4jLocationAwareLoggerWrapper1(logger: LocationAwareLogger) :
        Logger, org.slf4j.Logger by Slf4jLocationAwareLoggerWrapper(logger, FQCN) {
        companion object {
            @JvmField
            val FQCN: String = Slf4jLocationAwareLoggerWrapper1::class.java.name
        }

    }

    private open class Slf4jLocationAwareLoggerWrapper(
        private val logger: LocationAwareLogger,
        private val fqcn: String,
    ) : org.slf4j.helpers.AbstractLogger(), LocationAwareLogger {
        override fun isTraceEnabled(): Boolean = logger.isTraceEnabled
        override fun isTraceEnabled(marker: Marker?): Boolean = logger.isTraceEnabled(marker)
        override fun isDebugEnabled(): Boolean = logger.isDebugEnabled
        override fun isDebugEnabled(marker: Marker?): Boolean = logger.isDebugEnabled(marker)
        override fun isInfoEnabled(): Boolean = logger.isInfoEnabled
        override fun isInfoEnabled(marker: Marker?): Boolean = logger.isInfoEnabled(marker)
        override fun isWarnEnabled(): Boolean = logger.isWarnEnabled
        override fun isWarnEnabled(marker: Marker?): Boolean = logger.isWarnEnabled(marker)
        override fun isErrorEnabled(): Boolean = logger.isErrorEnabled
        override fun isErrorEnabled(marker: Marker?): Boolean = logger.isErrorEnabled(marker)
        override fun log(
            marker: Marker?,
            fqcn: String?,
            level: Int,
            message: String?,
            argArray: Array<out Any>?,
            t: Throwable?
        ) {
            logger.log(marker, fqcn, level, message, argArray, t)
        }

        override fun getFullyQualifiedCallerName(): String = logger.name

        override fun handleNormalizedLoggingCall(
            level: Level,
            marker: Marker?,
            messagePattern: String?,
            arguments: Array<out Any>?,
            throwable: Throwable?
        ) {
            log(marker, fqcn, level.toInt(), messagePattern, arguments, throwable)
        }

    }
}

