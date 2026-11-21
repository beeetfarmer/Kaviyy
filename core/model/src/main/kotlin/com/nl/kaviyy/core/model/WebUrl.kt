package com.nl.kaviyy.core.model

/** Only http(s) links leave the app (no intent:, javascript:, file: …). */
fun webUrl(raw: String?): String? {
    val url = raw?.trim().orEmpty()
    val scheme = url.substringBefore(':', "").lowercase()
    return url.takeIf { scheme == "https" || scheme == "http" }
}
