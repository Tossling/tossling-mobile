package com.kopylovis.tossling.protocol.network

import com.kopylovis.tossling.protocol.SyncJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NtfyEvent(
    val id: String = "",
    val time: Long = 0,
    val event: String = "",
    val topic: String = "",
    val title: String? = null,
    val message: String? = null,
    val priority: Int? = null,
    val tags: List<String>? = null,
    val click: String? = null,
    val icon: String? = null,
    @SerialName("content_type") val contentType: String? = null,
    val attachment: NtfyAttachment? = null,
)

@Serializable
data class NtfyAttachment(
    val url: String = "",
    val type: String? = null,
    val size: Long? = null,
)

@Serializable
data class NtfyToken(
    val token: String = "",
)

@Serializable
data class NtfySubscription(
    @SerialName("base_url") val baseUrl: String = "",
    val topic: String = "",
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class NtfyAccount(
    val subscriptions: List<NtfySubscription> = emptyList(),
)

@Serializable
data class NtfyTokenRequest(
    val label: String,
)

@Serializable
data class TosslingHealth(
    val server: String = "",
    val push: Boolean? = null,
    val url: String = "",
) {
    val isOurs: Boolean get() = server == TOSSLING_SERVER || server == TOSSY_SERVER
    val isTossling: Boolean get() = server == TOSSLING_SERVER

    companion object {
        const val TOSSLING_SERVER = "tossling-server"
        const val TOSSY_SERVER = "tossy-server"
    }
}

@Serializable
data class TosslingProjectRequest(
    val topic: String,
    val name: String,
)

@Serializable
data class TosslingProject(
    val topic: String = "",
    val name: String = "",
    val publisher: String? = null,
    val token: String? = null,
    val example: String? = null,
)

fun parseEvent(line: String): NtfyEvent? =
    line.takeIf { it.isNotBlank() }?.let { runCatching { SyncJson.decodeFromString(NtfyEvent.serializer(), it) }.getOrNull() }

fun parseHealth(text: String): TosslingHealth? =
    runCatching { SyncJson.decodeFromString(TosslingHealth.serializer(), text) }.getOrNull()?.takeIf { it.isOurs }
