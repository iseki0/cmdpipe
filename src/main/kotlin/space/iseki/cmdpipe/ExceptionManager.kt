package space.iseki.cmdpipe

import java.util.concurrent.atomic.AtomicReference

internal class ExceptionManager {
    private var root = AtomicReference<Throwable?>(null)

    fun add(th: Throwable) {
        if (!root.compareAndSet(null, th)) {
            root.get()!!.addSuppressed(th)
        }
    }

    val exception: Throwable?
        get() = root.get()
}