package space.iseki.cmdpipe.logging

internal object NoOpLogger: Logger {
    override fun trace(format: String, vararg args: Any?) {}

    override fun debug(format: String, vararg args: Any?) {}

}