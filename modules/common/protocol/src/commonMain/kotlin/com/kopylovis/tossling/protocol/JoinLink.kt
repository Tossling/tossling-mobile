package com.kopylovis.tossling.protocol

import kotlin.io.encoding.Base64

const val JOIN_LINK_PREFIX = "tossling://join"

fun joinLinkCode(link: String): String? {
    val trimmed = link.trim()
    if (!trimmed.startsWith(JOIN_LINK_PREFIX)) return null
    val code = trimmed.substringAfter('?', "").split('&').firstOrNull { it.startsWith("code=") }?.substringAfter("code=") ?: return null
    val raw = runCatching { Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(code).decodeToString() }.getOrNull() ?: return null
    return raw.takeIf { pairingHost(raw = it).isNotEmpty() }
}

fun joinLink(code: String): String =
    "$JOIN_LINK_PREFIX?code=" + Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(code.encodeToByteArray())

fun pairingName(raw: String): String =
    runCatching { SyncJson.decodeFromString(Pairing.serializer(), raw.trim()).macName }.getOrDefault("")
