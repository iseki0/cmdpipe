package space.iseki.cmdpipe.logging

import org.slf4j.MDC

internal class Slf4jMdcProvider : MdcProvider {
    private class Wrapper(val v: Map<String, String>) : MdcProvider.V

    override fun dump(): MdcProvider.V = Wrapper(MDC.getCopyOfContextMap())

    override fun restore(v: MdcProvider.V) {
        MDC.setContextMap((v as Wrapper).v)
    }
}