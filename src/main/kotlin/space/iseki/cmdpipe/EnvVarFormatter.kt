package space.iseki.cmdpipe

internal object EnvVarFormatter {
    fun format(envVar: EnvVar): String {
        val key = simpleEscapeString(envVar.name, true)
        val value = envVar.value?.let { simpleEscapeString(it, false) }
        return when {
            value == null -> "$key (cleared)"
            envVar.confidential -> "$key ***"
            else -> "$key=\"$value\""
        }
    }
}
