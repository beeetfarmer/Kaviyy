package com.nl.kaviyy.core.model.log

/** Strips credentials from text before it is logged (AGENTS.md logging rule, MASVS N7). */
object Redactor {
    const val MASK = "<redacted>"

    // key (optionally quoted) + ':' or '=' + value (optionally quoted / "Bearer "-prefixed).
    // Keys: Authorization, x-api-key, apiKey/api_key, *token, *password, *secret (case-insensitive).
    private val keyValue =
        Regex(
            """("?)([\w-]*(?:authorization|api[-_]?key|token|password|secret))\1""" +
                """(\s*[:=]\s*)("?)(?:bearer\s+)?[^"&\s,;}]+""",
            RegexOption.IGNORE_CASE,
        )
    private val urlUserInfo = Regex("""://[^/\s:@]+:[^/\s@]+@""")

    fun redact(text: String): String =
        text
            .replace(keyValue, "\$1\$2\$1\$3\$4$MASK")
            .replace(urlUserInfo, "://$MASK@")
}
