package com.kopylovis.tossling.sync.data

import java.net.URI

internal fun normalizedServer(raw: String): String? {
    val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    if (scheme != "https" && scheme != "http") return null
    val port = uri.port.takeUnless { it == -1 || (scheme == "https" && it == 443) || (scheme == "http" && it == 80) }
    return "$scheme://$host" + (port?.let { ":$it" } ?: "")
}

internal fun serverMoveTarget(current: String, url: String?): String? {
    if (url.isNullOrBlank()) return null
    val target = normalizedServer(url) ?: return null
    val now = normalizedServer(current) ?: return null
    if (target == now) return null
    val scheme = target.substringBefore("://")
    if (scheme != "https" && scheme != now.substringBefore("://")) return null
    return target
}

internal fun acceptsServerMove(isTossling: Boolean, accountOk: Boolean): Boolean = isTossling && accountOk
