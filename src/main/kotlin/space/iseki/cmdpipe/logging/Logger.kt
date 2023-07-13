package space.iseki.cmdpipe.logging

internal interface Logger {
    fun trace(format: String, vararg args: Any?)
    fun debug(format: String, vararg args: Any?)

    companion object {
        private val factory: LoggerFactory? by lazy {
            runCatching {
                Class.forName("space.iseki.cmdpipe.logging.Slf4JLoggerFactory").getDeclaredConstructor()
                    .newInstance() as LoggerFactory
            }.getOrNull()
        }

        fun getLogger(klass: Class<*>) = runCatching { factory?.get(klass.name) }.getOrNull() ?: NoOpLogger
    }
}