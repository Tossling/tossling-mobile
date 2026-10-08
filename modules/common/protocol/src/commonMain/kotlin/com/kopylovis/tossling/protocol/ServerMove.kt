package com.kopylovis.tossling.protocol

private val SERVER_URL = Regex("^([A-Za-z][A-Za-z0-9+.-]*)://([A-Za-z0-9.-]+|\\[[0-9A-Fa-f:.]+])(?::([0-9]{1,5}))?(?:[/?#].*)?$")

fun normalizedServer(raw: String): String? {
    val match = SERVER_URL.matchEntire(raw.trim()) ?: return null
    val scheme = match.groupValues[1].lowercase()
    val host = match.groupValues[2].lowercase()
    if (scheme != "https" && scheme != "http") return null
    val port = match.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull()
        ?.takeUnless { (scheme == "https" && it == 443) || (scheme == "http" && it == 80) }
    return "$scheme://$host" + (port?.let { ":$it" } ?: "")
}

fun serverMoveTarget(current: String, url: String?): String? {
    if (url.isNullOrBlank()) return null
    val target = normalizedServer(url) ?: return null
    val now = normalizedServer(current) ?: return null
    if (target == now) return null
    val scheme = target.substringBefore("://")
    if (scheme != "https" && scheme != now.substringBefore("://")) return null
    return target
}

fun acceptsServerMove(isTossling: Boolean, accountOk: Boolean): Boolean = isTossling && accountOk
