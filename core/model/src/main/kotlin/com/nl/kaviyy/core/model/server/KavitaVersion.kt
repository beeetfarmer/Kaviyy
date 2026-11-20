package com.nl.kaviyy.core.model.server

/** Kavita's 4-part version (`0.9.0.2`). Missing parts count as 0; a suffix like `-beta` is ignored. */
@ConsistentCopyVisibility
data class KavitaVersion private constructor(
    val parts: List<Int>,
) : Comparable<KavitaVersion> {
    override fun compareTo(other: KavitaVersion): Int =
        parts.zip(other.parts).map { (a, b) -> a.compareTo(b) }.firstOrNull { it != 0 } ?: 0

    val isSupported: Boolean get() = this >= MIN_SUPPORTED

    override fun toString() = parts.joinToString(".")

    companion object {
        private const val PARTS = 4
        private val PATTERN = Regex("""^v?(\d+(?:\.\d+){0,3})""")

        fun parse(raw: String?): KavitaVersion? =
            raw
                ?.trim()
                ?.let { PATTERN.find(it) }
                ?.groupValues
                ?.get(1)
                ?.split('.')
                ?.mapNotNull { it.toIntOrNull() }
                ?.takeIf { it.isNotEmpty() }
                ?.let { KavitaVersion(it + List(PARTS - it.size) { 0 }) } // pad so equals agrees with compareTo

        /** Security hotfix release (AGENTS.md). Below it: warn, still allow use. */
        val MIN_SUPPORTED: KavitaVersion = checkNotNull(parse("0.9.0.2"))
    }
}
