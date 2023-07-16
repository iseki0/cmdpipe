@file:JvmName("-Util")

package space.iseki.cmdpipe

internal fun Throwable.lazyMessage() = object {
    override fun toString(): String = this@lazyMessage.message ?: "null"
}
