package space.iseki.cmdpipe.logging

internal object MdcManager {
    private val Empty = Value(object : MdcProvider.V {})

    class Value(val v: MdcProvider.V)

    private val provider by lazy {
        try {
            Class.forName("space.iseki.cmdpipe.logging.Slf4jMdcProvider").getDeclaredConstructor().newInstance() as MdcProvider
        } catch (_: Throwable) {
            null
        }
    }

    // TODO: 这样子会导致所有为 null 的都被设置为 Empty，restore 时被忽略
    fun dump() = runCatching { provider?.dump() }.getOrNull()?.let { Value(it) } ?: Empty
    fun restore(v: Value) {
        if (v == Empty) return
        try {
            provider?.restore(v.v)
        } catch (_: Throwable) {
        }
    }
}


