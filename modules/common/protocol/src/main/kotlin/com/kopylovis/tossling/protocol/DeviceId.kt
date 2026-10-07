package com.kopylovis.tossling.protocol

import java.security.MessageDigest

fun deviceIdFrom(androidId: String): String =
    MessageDigest.getInstance("SHA-256").digest("tossy-device:$androidId".toByteArray()).joinToString(separator = "") { "%02x".format(it) }.take(16)
