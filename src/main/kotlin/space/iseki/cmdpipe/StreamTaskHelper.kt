package space.iseki.cmdpipe

import space.iseki.cmdpipe.logging.MdcManager
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask

internal class StreamTaskHelper(
    private val executor: Executor,
    private val onError: () -> Unit,
) {
    private val tasks = ArrayList<FutureTask<*>>(4)
    private val mainThreadMdc = MdcManager.dump()

    @Volatile
    var rootException: Throwable? = null
        private set

    companion object {
        private val ROOT_EXCEPTION: VarHandle =
            MethodHandles.lookup().findVarHandle(StreamTaskHelper::class.java, "rootException", Throwable::class.java)
    }


    @Synchronized
    fun <T> submitTask(task: () -> T): FutureTask<T>? {
        if (rootException != null) return null
        val taskFuture = FutureTask {
            val old = MdcManager.dump()
            try {
                MdcManager.restore(mainThreadMdc)
                return@FutureTask task()
            } catch (th: Throwable) {
                handleError(th)
                throw th
            } finally {
                MdcManager.restore(old)
            }
        }
        try {
            executor.execute(taskFuture)
        } catch (th: Throwable) {
            handleError(th)
            throw th
        }
        tasks.add(taskFuture)
        return taskFuture
    }

    fun handleError(th: Throwable) {
        Thread.interrupted() // clear interrupt signal
        if (!ROOT_EXCEPTION.compareAndSet(this, null, th)) {
            rootException?.addSuppressed(th)
            return
        }
        handleError0()
        Thread.interrupted()
        onError()
    }

    fun waitAll() {
        for (task in synchronized(this) { tasks.toList() }) {
            runCatching { task.get() }
        }
    }

    @Synchronized
    private fun handleError0() {
        for (task in tasks) {
            Thread.interrupted() // clear interrupt signal
            runCatching { task.cancel(true) }
        }
        tasks.clear() // release
    }
}

