package space.iseki.cmdpipe

data class EnvVar(
    val name: String,
    /**
     * if is null, the variable which matches the name will be removed
     */
    val value: String?,
    val confidential: Boolean = false,
) {
    override fun toString(): String {
        val key = simpleEscapeString(name, true)
        val value = value?.let { simpleEscapeString(it, false) }
        return when {
            value == null -> "$key (cleared)"
            confidential -> "$key ***"
            else -> "$key=\"$value\""
        }
    }

    constructor(pair: Pair<String, String?>) : this(pair.first, pair.second)
}

