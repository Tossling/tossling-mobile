package com.kopylovis.tossling.protocol.room

import com.kopylovis.tossling.protocol.crypto.toByteArray
import com.kopylovis.tossling.protocol.crypto.toData
import platform.Foundation.NSData

val Outgoing.bodyData: NSData? get() = body?.toData()

fun RoomCore.image(data: NSData, mime: String): Outgoing = image(bytes = data.toByteArray(), mime = mime, to = null)

fun RoomCore.openAttachment(data: NSData): NSData = openAttachment(sealed = data.toByteArray()).toData()
