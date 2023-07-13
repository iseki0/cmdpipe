package space.iseki.cmdpipe.logging

internal interface MdcProvider {
    interface V

    fun dump(): V
    fun restore(v: V)
}

