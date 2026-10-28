package com.personalrecords.app.core.common

private val entityRegex = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")

private val namedEntities = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to " "
)

/**
 * Decodes the XML/HTML entities RSS feeds leave in titles (`&apos;`, `&amp;`, `&#39;`).
 * Unknown named entities are kept as-is so real text is never lost.
 */
fun String.decodeHtmlEntities(): String {
    if ('&' !in this) return this
    return entityRegex.replace(this) { match ->
        val body = match.groupValues[1]
        when {
            body.startsWith("#x") || body.startsWith("#X") ->
                body.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: match.value
            body.startsWith("#") ->
                body.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: match.value
            else -> namedEntities[body.lowercase()] ?: match.value
        }
    }
}
