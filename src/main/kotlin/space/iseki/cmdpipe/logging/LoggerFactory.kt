package space.iseki.cmdpipe.logging

internal interface LoggerFactory {
    fun get(name: String): Logger
}