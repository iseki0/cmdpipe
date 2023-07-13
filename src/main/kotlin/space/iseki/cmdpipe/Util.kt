@file:JvmName("-Util")

package space.iseki.cmdpipe

internal fun Any?.lazyToString() = object {
    override fun toString(): String = this@lazyToString.toString()
}

internal fun Throwable.lazyMessage() = object {
    override fun toString(): String = this@lazyMessage.message ?: "null"
}
