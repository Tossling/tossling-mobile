package com.kopylovis.tossling.protocol

import com.kopylovis.tossling.protocol.crypto.Primitives
import com.kopylovis.tossling.protocol.crypto.hex

fun deviceIdFrom(androidId: String): String = Primitives.sha256("tossy-device:$androidId".encodeToByteArray()).hex().take(16)
