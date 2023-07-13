package space.iseki.cmdpipe

import java.lang.invoke.MethodHandles

internal class ExceptionManager {
    companion object {
        private val ROOT =
            MethodHandles.lookup().findVarHandle(ExceptionManager::class.java, "root", Throwable::class.java)
    }

    @Volatile
    private var root: Throwable? = null

    fun add(th: Throwable) {
        if (!ROOT.compareAndSet(this, null, th)) {
            root!!.addSuppressed(th)
        }
    }

    val exception: Throwable?
        get() = root
}