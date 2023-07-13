package space.iseki.cmdpipe

data class EnvVar(
    val name: String,
    /**
     * if is null, the variable which matches the name will be removed
     */
    val value: String?
) {
    override fun toString(): String {
        val key = simpleEscapeString(name, true)
        val value = value?.let { simpleEscapeString(it, false) }
        return if (value != null) {
            "$key=\"$value\""
        } else {
            "$key (cleared)"
        }
    }

    constructor(pair: Pair<String, String?>) : this(pair.first, pair.second)
}

