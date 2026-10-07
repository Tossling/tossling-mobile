package com.kopylovis.tossling.sync.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.kopylovis.tossling.core.presentation.tr
import com.kopylovis.tossling.protocol.ClipItem
import com.kopylovis.tossling.protocol.ClipKind
import com.kopylovis.tossling.protocol.ClipMeta
import com.kopylovis.tossling.protocol.DeviceKind
import com.kopylovis.tossling.protocol.Endpoint
import com.kopylovis.tossling.protocol.LinkStatus
import com.kopylovis.tossling.protocol.MacDevice
import com.kopylovis.tossling.protocol.Member
import com.kopylovis.tossling.protocol.OLD_ROOM_PREFIX
import com.kopylovis.tossling.protocol.Pairing
import com.kopylovis.tossling.protocol.PairingException
import com.kopylovis.tossling.protocol.PairingProblem
import com.kopylovis.tossling.protocol.ROOM_PREFIX
import com.kopylovis.tossling.protocol.RoomDevice
import com.kopylovis.tossling.protocol.RoomSwitch
import com.kopylovis.tossling.protocol.SyncJson
import com.kopylovis.tossling.protocol.Transfer
import com.kopylovis.tossling.protocol.crypto.ClipCipher
import com.kopylovis.tossling.protocol.crypto.DeviceIdentity
import com.kopylovis.tossling.protocol.crypto.RoomKeys
import com.kopylovis.tossling.protocol.crypto.RoomSecret
import com.kopylovis.tossling.protocol.network.NtfyClient
import com.kopylovis.tossling.protocol.network.NtfyEvent
import com.kopylovis.tossling.protocol.network.NtfyException
import com.kopylovis.tossling.sync.clipboard.ClipboardBridge
import com.kopylovis.tossling.sync.clipboard.Outgoing
import com.kopylovis.tossling.sync.entry.ClipWidget
import com.kopylovis.tossling.sync.notifications.SyncNotifications
import com.kopylovis.tossling.sync.push.PushRegistrar
import com.kopylovis.tossling.sync.work.RetireTokenWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

class ClipRepository internal constructor(
    private val context: Context,
    private val store: PairingStore,
    private val client: NtfyClient,
    private val bridge: ClipboardBridge,
    private val notifications: SyncNotifications,
    private val push: PushRegistrar,
    private val settings: SyncSettings,
    private val backup: RoomBackup,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val links = MutableStateFlow<Map<String, LinkStatus>>(emptyMap())

    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(CLOCK_MS)
        }
    }

    private val _joined = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val joined: SharedFlow<String> = _joined.asSharedFlow()

    val pairings: StateFlow<List<Pairing>> = store.pairings

    val isPaired: StateFlow<Boolean> = store.pairings
        .map { it.isNotEmpty() }
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = store.pairings.value.isNotEmpty())

    val macs: StateFlow<List<MacDevice>> = combine(store.pairings, links) { pairings, status ->
        pairings.flatMap { pairing -> macsOf(pairing = pairing, link = status[pairing.id] ?: LinkStatus()) }
    }.stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = emptyList())

    val devices: StateFlow<List<RoomDevice>> = combine(store.pairings, links, settings.deviceName, clock) { pairings, status, name, now ->
        pairings.flatMap { pairing -> devicesOf(pairing = pairing, link = status[pairing.id] ?: LinkStatus(), selfName = name, now = now) }
    }.stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = emptyList())

    val history: StateFlow<List<ClipItem>> = store.history

    val isPushAvailable: Boolean get() = push.isAvailable

    private val _transfer = MutableStateFlow<Transfer?>(null)
    val transfer: StateFlow<Transfer?> = _transfer

    private val mutex = Mutex()

    private val random = SecureRandom()

    @Volatile private var lastSentHash = ""

    @Volatile private var lastSentAt = 0L

    @Volatile private var lastPingAt = 0L

    init {
        scope.launch {
            combine(store.pairings, settings.deviceName) { pairings, name ->
                val rooms = pairings.filter { it.isRoom }.map { it.copy(members = emptyList(), since = 0) }
                if (rooms.isEmpty()) null else SavedRooms(deviceId = settings.deviceId, identity = Base64.getEncoder().encodeToString(settings.identity.privateKey), name = name, rooms = rooms)
            }.filterNotNull().distinctUntilChanged().collect { backup.save(saved = it) }
        }
    }

    internal fun moveServer(from: String, to: String) = store.moveServer(from = from, to = to)

    suspend fun savedRooms(): SavedRooms? = if (store.pairings.value.isNotEmpty()) null else backup.load()

    suspend fun restore(saved: SavedRooms): Pairing {
        val identity = runCatching { DeviceIdentity(privateKey = Base64.getDecoder().decode(saved.identity)) }.getOrNull()
            ?: throw PairingException(problem = PairingProblem.NOT_TOSSLING)
        val rooms = saved.rooms.filter { it.isRoom }
        if (rooms.isEmpty()) throw PairingException(problem = PairingProblem.NOT_TOSSLING)
        rooms.forEach { room ->
            try {
                client.check(endpoint = room.endpoint, topic = room.inTopic)
            } catch (error: NtfyException) {
                val problem = if (error.code == 401 || error.code == 403) PairingProblem.TOKEN else PairingProblem.NETWORK
                throw PairingException(problem = problem, server = room.host, cause = error)
            } catch (error: Exception) {
                throw PairingException(problem = PairingProblem.NETWORK, server = room.host, cause = error)
            }
        }
        settings.restoreDevice(id = saved.deviceId, identity = identity, name = saved.name)
        rooms.forEach { room ->
            val pairing = room.copy(since = System.currentTimeMillis())
            store.savePairing(pairing = pairing)
            if (pairing.macId.isNotEmpty()) {
                store.saveMember(pairingId = pairing.id, member = Member(id = pairing.macId, name = pairing.macName.ifEmpty { "Mac" }, source = SOURCE_MAC, seen = 0, pk = pairing.macKey))
            }
            if (settings.isInstant.value) push.subscribe(topic = pairing.inTopic)
            publishControl(pairing = pairing, meta = control(kind = ClipMeta.HELLO))
            markLink(id = pairing.id, isOnline = true)
        }
        return rooms.first()
    }

    suspend fun forgetSavedRooms() = backup.clear()

    fun endpoint(): Endpoint? = store.pairings.value.firstOrNull()?.endpoint

    fun owns(topic: String): Boolean = store.pairings.value.any { it.inTopic == topic }

    fun roomSwitch(raw: String): RoomSwitch? {
        val pairing = runCatching { parse(raw = raw) }.getOrNull() ?: return null
        val others = store.pairings.value.filter { it.id != pairing.id }
        if (others.isEmpty()) return null
        val current = others.flatMap { other -> devicesOf(pairing = other, link = LinkStatus(), selfName = "", now = System.currentTimeMillis()).filter { !it.isSelf } }
        return RoomSwitch(from = current.joinToString(separator = ", ") { it.name }, to = pairing.macName.ifEmpty { "Mac" })
    }

    suspend fun pair(raw: String): Pairing {
        val pairing = parse(raw = raw)
        try {
            client.check(endpoint = pairing.endpoint, topic = pairing.inTopic)
        } catch (error: NtfyException) {
            val problem = if (error.code == 401 || error.code == 403) PairingProblem.TOKEN else PairingProblem.NETWORK
            throw PairingException(problem = problem, server = pairing.host, cause = error)
        } catch (error: Exception) {
            throw PairingException(problem = PairingProblem.NETWORK, server = pairing.host, cause = error)
        }
        store.savePairing(pairing = pairing.copy(since = System.currentTimeMillis()))
        if (pairing.isRoom && pairing.macId.isNotEmpty()) {
            store.saveMember(pairingId = pairing.id, member = Member(id = pairing.macId, name = pairing.macName.ifEmpty { "Mac" }, source = SOURCE_MAC, seen = System.currentTimeMillis(), pk = pairing.macKey), renewed = true)
        }
        if (settings.isInstant.value) push.subscribe(topic = pairing.inTopic)
        publishControl(pairing = pairing, meta = control(kind = ClipMeta.HELLO).copy(renewed = true))
        markLink(id = pairing.id, isOnline = true)
        store.pairings.value.filter { it.id != pairing.id }.forEach { other -> leave(pairingId = other.id) }
        return pairing
    }

    suspend fun unpair(id: String) {
        val pairing = store.pairings.value.firstOrNull { it.id == id } ?: return
        push.unsubscribe(topic = pairing.inTopic)
        store.removePairing(id = id)
        links.update { it - id }
        if (store.pairings.value.isEmpty()) {
            bridge.clipsDir.deleteRecursively()
            store.clear()
            backup.clear()
        }
    }

    suspend fun unpairAll() {
        store.pairings.value.forEach { pairing -> push.unsubscribe(topic = pairing.inTopic) }
        bridge.clipsDir.deleteRecursively()
        store.clear()
        links.value = emptyMap()
        backup.clear()
    }

    suspend fun leave(pairingId: String) {
        val pairing = store.pairings.value.firstOrNull { it.id == pairingId } ?: return
        if (pairing.isRoom) runCatching { publishControl(pairing = pairing, meta = control(kind = ClipMeta.BYE)) }
        unpair(id = pairingId)
    }

    suspend fun revoke(pairingId: String, deviceId: String) {
        val pairing = store.pairings.value.firstOrNull { it.id == pairingId } ?: return
        if (!pairing.isRoom) {
            unpair(id = pairingId)
            return
        }
        if (deviceId == pairing.ownerId) throw IllegalStateException(tr("The room creator can't be removed", "Создателя комнаты нельзя отключить"))
        val remaining = pairing.members.filter { it.id != deviceId && it.id != settings.deviceId && !it.id.startsWith(LEGACY_PREFIX) }
        val room = "${roomPrefix(endpoint = pairing.endpoint)}${hex(bytes = ROOM_BYTES)}"
        val key = Base64.getEncoder().encodeToString(ByteArray(ClipCipher.KEY_SIZE).also(random::nextBytes))
        val isLegacy = remaining.any { RoomKeys.decodeKey(it.pk) == null }
        if (isLegacy) Log.w(TAG, "some devices have no key yet, the new room key goes out under the old one")
        val token = if (isLegacy || pairing.token.isEmpty()) null else runCatching { client.createToken(endpoint = pairing.endpoint, label = TOKEN_LABEL) }
            .onFailure { Log.w(TAG, "kept the old token", it) }
            .getOrNull()
        publishControl(pairing = pairing, meta = control(kind = ClipMeta.KICK).copy(to = listOf(deviceId)))
        if (remaining.isNotEmpty()) {
            val sealed = RoomKeys.seal(secret = RoomSecret(room = room, key = key, token = token), recipients = remaining.associate { it.id to it.pk })
            val rekey = control(kind = ClipMeta.REKEY).copy(
                to = remaining.map { it.id },
                ephemeral = sealed.ephemeral,
                keys = sealed.keys,
                room = room.takeIf { isLegacy },
                key = key.takeIf { isLegacy },
            )
            publishControl(pairing = pairing, meta = rekey)
        }
        switchRoom(pairing = pairing, room = room, key = key, token = token, keep = null, without = deviceId)
        if (token != null) RetireTokenWorker.enqueue(context = context, server = pairing.server, token = token, old = pairing.token)
    }

    private suspend fun roomPrefix(endpoint: Endpoint): String =
        if (runCatching { client.tosslingHealth(endpoint = endpoint) }.getOrNull()?.isTossling == true) ROOM_PREFIX else OLD_ROOM_PREFIX

    fun renameLater(name: String) {
        scope.launch { rename(name = name) }
    }

    suspend fun rename(name: String) {
        val clean = name.trim()
        if (clean.isEmpty() || clean == settings.deviceName.value) return
        settings.setDeviceName(value = clean)
        store.pairings.value.filter { it.isRoom }.forEach { pairing ->
            runCatching { publishControl(pairing = pairing, meta = control(kind = ClipMeta.HELLO)) }
        }
    }

    fun setAlias(pairingId: String, deviceId: String, alias: String) {
        store.setAlias(pairingId = pairingId, memberId = deviceId, alias = alias.trim())
    }

    suspend fun ping() {
        val now = System.currentTimeMillis()
        if (now - lastPingAt < PING_INTERVAL_MS) return
        lastPingAt = now
        store.pairings.value.filter { it.isRoom }.forEach { pairing ->
            runCatching { publishControl(pairing = pairing, meta = control(kind = ClipMeta.PING)) }
                .onSuccess { markLink(id = pairing.id, isOnline = true) }
                .onFailure { markLink(id = pairing.id, isOnline = false) }
        }
    }

    internal suspend fun subscribeAll(subscribe: Boolean) {
        store.pairings.value.forEach { pairing ->
            if (subscribe) push.subscribe(topic = pairing.inTopic) else push.unsubscribe(topic = pairing.inTopic)
        }
    }

    suspend fun fetch(topic: String? = null, onTransfer: (Transfer) -> Unit = { notifications.showTransfer(transfer = it) }): Int = mutex.withLock {
        val targets = store.pairings.value.filter { topic == null || it.inTopic == topic }
        var applied = 0
        var failure: Exception? = null
        var succeeded = false
        targets.forEach { pairing ->
            try {
                val events = client.poll(endpoint = pairing.endpoint, topic = pairing.inTopic, since = store.lastId(pairing).ifEmpty { FIRST_SINCE })
                markLink(id = pairing.id, isOnline = true)
                succeeded = true
                for (event in events.filter { it.event == MESSAGE_EVENT }) {
                    val outcome = try {
                        receive(pairing = pairing, event = event, onTransfer = onTransfer)
                    } catch (error: InterruptedTransfer) {
                        throw error
                    } catch (error: Exception) {
                        Log.e(TAG, "message ${event.id} failed", error)
                        Outcome.SKIPPED
                    }
                    if (outcome == Outcome.LEFT) break
                    store.saveLastId(pairing = pairing, id = event.id)
                    if (outcome == Outcome.APPLIED) applied++
                }
            } catch (error: Exception) {
                markLink(id = pairing.id, isOnline = false)
                failure = error
            }
        }
        if (!succeeded) failure?.let { throw it }
        applied
    }

    suspend fun send(outgoing: Outgoing, targets: Set<String>? = null, onTransfer: (Transfer) -> Unit = { notifications.showTransfer(transfer = it) }): ClipItem {
        val routes = store.pairings.value.mapNotNull { pairing ->
            val ids = recipientsOf(pairing = pairing)
            val chosen = if (targets == null) null else ids.filter { it in targets }
            when {
                chosen != null && chosen.isEmpty() -> null
                pairing.isRoom -> pairing to chosen?.takeIf { it.size < ids.size }
                else -> pairing to null
            }
        }
        if (routes.isEmpty()) throw IllegalStateException(tr("Tossling is not paired", "Tossling не подключён"))
        val isStream = outgoing.kind == ClipKind.FILE
        val size = outgoing.file.length()
        val bytes = if (isStream) null else outgoing.file.readBytes()
        val isText = outgoing.kind == ClipKind.TEXT
        val inline = isText && bytes != null && bytes.size <= INLINE_LIMIT
        val base = ClipMeta(
            kind = when (outgoing.kind) {
                ClipKind.TEXT -> ClipMeta.TEXT
                ClipKind.IMAGE -> ClipMeta.IMAGE
                ClipKind.FILE -> ClipMeta.FILE
            },
            mime = outgoing.mime,
            fileName = outgoing.name,
            device = settings.deviceName.value,
            text = if (inline) bytes?.decodeToString() else null,
            sender = settings.deviceId,
            format = if (isStream) ClipCipher.STREAM_FORMAT else null,
            size = if (isStream) size else null,
        )
        val context = currentCoroutineContext()
        val reporter = TransferReporter(onTransfer = { _transfer.value = it; onTransfer(it) }, base = Transfer(name = outgoing.name ?: "file", incoming = false, device = targetNames(targets), done = 0, total = size * routes.size))
        val hash = MessageDigest.getInstance("SHA-256")
        val delivered = mutableListOf<Pairing>()
        var failure: Exception? = null
        try {
            routes.forEachIndexed { index, (pairing, to) ->
                val meta = base.copy(to = to)
                val cipher = cipherFor(pairing)
                try {
                    val message = cipher.sealToText(plain = encode(meta))
                    if (isStream) {
                        client.publishStream(endpoint = pairing.endpoint, topic = pairing.outTopic, message = message, length = ClipCipher.sealedSize(size)) { output ->
                            val file = outgoing.file.inputStream().buffered(STREAM_BUFFER)
                            val input = if (index == 0) DigestInputStream(file, hash) else file
                            input.use {
                                cipher.sealStream(input = it, output = output, size = size) { done ->
                                    context.ensureActive()
                                    reporter.report(done = size * index + done)
                                }
                            }
                        }
                    } else {
                        client.publish(endpoint = pairing.endpoint, topic = pairing.outTopic, message = message, body = if (inline) null else bytes?.let(cipher::seal))
                    }
                    markLink(id = pairing.id, isOnline = true)
                    delivered += pairing
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    markLink(id = pairing.id, isOnline = false)
                    failure = error
                }
            }
        } finally {
            if (isStream) finishTransfer()
        }
        if (delivered.isNotEmpty()) {
            lastSentHash = if (isStream) hex(hash.digest()) else digest(bytes = bytes ?: ByteArray(0))
            lastSentAt = System.currentTimeMillis()
        }
        if (delivered.isEmpty()) throw failure ?: IllegalStateException("not sent")
        val reached = devices.value.filter { device -> !device.isSelf && device.pairingId in delivered.map { it.id } && (targets == null || device.id in targets) }
        val others = devices.value.count { !it.isSelf }
        val id = UUID.randomUUID().toString()
        val keptName = when (outgoing.kind) {
            ClipKind.FILE -> "$id-${safeName(outgoing.name ?: "file")}"
            ClipKind.TEXT -> "$id.txt"
            ClipKind.IMAGE -> "$id.${bridge.extensionFor(outgoing.mime)}"
        }
        val fullText = if (isText) bytes?.decodeToString() else null
        val keeps = if (isText) (fullText?.length ?: 0) > PREVIEW_LENGTH else size <= KEEP_LIMIT
        val kept = if (keeps) File(bridge.clipsDir, keptName).also { outgoing.file.copyTo(it, overwrite = true) } else null
        outgoing.file.delete()
        val item = ClipItem(
            id = id,
            incoming = false,
            kind = outgoing.kind,
            text = fullText?.take(PREVIEW_LENGTH),
            file = kept?.absolutePath,
            mime = outgoing.mime,
            name = outgoing.name,
            size = size,
            macId = targets?.singleOrNull(),
            device = reached.joinToString(separator = ", ") { it.name },
            toAll = others > 1 && reached.size >= others,
            time = System.currentTimeMillis(),
        )
        remember(item = item)
        return item
    }

    private fun targetNames(targets: Set<String>?): String {
        val chosen = devices.value.filter { !it.isSelf && (targets == null || it.id in targets) }
        return if (targets == null && chosen.size > 1) "" else chosen.joinToString(separator = ", ") { it.name }
    }

    fun copyText(text: String) = bridge.writeText(text = text)

    fun copyAgain(item: ClipItem) {
        when (item.kind) {
            ClipKind.TEXT -> (item.file?.let(::File)?.takeIf { it.exists() }?.readText() ?: item.text)?.let(bridge::writeText)
            ClipKind.IMAGE -> item.file?.let(::File)?.takeIf { it.exists() }?.let { file ->
                bridge.writeImage(file = file, mime = item.mime ?: "image/png")
            }
            ClipKind.FILE -> Unit
        }
    }

    fun setPinned(item: ClipItem, isPinned: Boolean) = store.setPinned(id = item.id, isPinned = isPinned)

    fun delete(item: ClipItem) {
        store.removeHistory(id = item.id)?.let(::dropFiles)
        ClipWidget.refresh(context = context, last = store.history.value.firstOrNull())
    }

    fun fileUri(item: ClipItem): Uri? {
        val path = item.file ?: return null
        if (path.startsWith("content://")) return Uri.parse(path)
        return File(path).takeIf { it.exists() }?.let(bridge::shareUri)
    }

    private suspend fun receive(pairing: Pairing, event: NtfyEvent, onTransfer: (Transfer) -> Unit): Outcome {
        val age = System.currentTimeMillis() / 1000 - event.time
        val isStale = age > STALE_SECONDS
        val cipher = cipherFor(pairing)
        val sealedMeta = event.message ?: return Outcome.SKIPPED
        val meta = SyncJson.decodeFromString(ClipMeta.serializer(), cipher.openText(text = sealedMeta).decodeToString())
        if (meta.sender.isNotEmpty() && meta.sender == settings.deviceId) return Outcome.SKIPPED
        if (meta.to?.contains(settings.deviceId) == false) return Outcome.SKIPPED
        if (pairing.isRoom) {
            val memberId = meta.sender.ifEmpty { "$LEGACY_PREFIX${meta.source}-${meta.device}" }
            when (meta.kind) {
                ClipMeta.BYE -> {
                    store.removeMember(pairingId = pairing.id, memberId = memberId)
                    return Outcome.SKIPPED
                }

                ClipMeta.KICK -> {
                    unpair(id = pairing.id)
                    notifications.showRemoved(by = meta.device)
                    return Outcome.LEFT
                }

                ClipMeta.REKEY -> {
                    val box = meta.keys?.get(settings.deviceId)
                    val ephemeral = meta.ephemeral
                    val opened = if (box != null && ephemeral != null) RoomKeys.open(identity = settings.identity, id = settings.deviceId, ephemeral = ephemeral, box = box) else null
                    val room = opened?.room ?: meta.room ?: return Outcome.SKIPPED
                    val key = opened?.key ?: meta.key ?: return Outcome.SKIPPED
                    switchRoom(pairing = pairing, room = room, key = key, token = opened?.token, keep = meta.to.orEmpty() + memberId, without = null)
                    return Outcome.LEFT
                }
            }
            val now = System.currentTimeMillis()
            val isNew = store.saveMember(pairingId = pairing.id, member = Member(id = memberId, name = meta.device.ifEmpty { "?" }, source = meta.source, seen = minOf(now, event.time * 1000), pk = meta.publicKey?.takeIf { RoomKeys.decodeKey(it) != null }.orEmpty()), renewed = meta.renewed == true && meta.kind == ClipMeta.HELLO)
            if (isNew && !isStale && meta.kind == ClipMeta.HELLO && now - pairing.since > SETTLE_MS) {
                val name = meta.device.ifEmpty { "?" }
                notifications.showJoined(name = name)
                _joined.tryEmit(name)
            }
            if (meta.kind == ClipMeta.PING && meta.sender.isNotEmpty() && !isStale) {
                runCatching { publishControl(pairing = pairing, meta = control(kind = ClipMeta.HELLO).copy(to = listOf(meta.sender))) }
                return Outcome.SKIPPED
            }
        } else if (meta.source == SOURCE_MAC) {
            store.saveMacName(id = pairing.id, name = meta.device)
        }
        if (meta.kind == ClipMeta.HELLO || meta.kind == ClipMeta.PING) return Outcome.SKIPPED
        if (meta.kind == ClipMeta.MOVE) {
            val room = meta.room ?: return Outcome.SKIPPED
            if (pairing.isRoom) return Outcome.SKIPPED
            moveToRoom(pairing = pairing, room = room, macId = meta.sender, macName = meta.device)
            return Outcome.LEFT
        }
        val device = meta.device.ifEmpty { pairing.macName }
        val url = event.attachment?.url?.takeIf { it.isNotEmpty() }
        if (meta.kind == ClipMeta.FILE && age > FILE_STALE_SECONDS) {
            notifications.showMissed(name = safeName(meta.fileName ?: "file"), device = device)
            return Outcome.SKIPPED
        }
        if (meta.kind == ClipMeta.FILE && meta.format == ClipCipher.STREAM_FORMAT) {
            return receiveStream(pairing = pairing, event = event, meta = meta, url = url ?: return Outcome.SKIPPED, device = device, onTransfer = onTransfer)
        }
        val data = try {
            url?.let { cipher.open(sealed = client.download(endpoint = pairing.endpoint, url = it)) }
        } catch (error: NtfyException) {
            if (error.code != HTTP_NOT_FOUND && error.code != HTTP_GONE) throw error
            if (meta.kind == ClipMeta.FILE) notifications.showMissed(name = safeName(meta.fileName ?: "file"), device = device)
            return Outcome.SKIPPED
        }
        val item = when (meta.kind) {
            ClipMeta.TEXT -> {
                val text = meta.text ?: data?.decodeToString() ?: return Outcome.SKIPPED
                if (isEcho(bytes = text.toByteArray())) return Outcome.SKIPPED
                if (!isStale) bridge.writeText(text = text)
                val full = if (text.length > PREVIEW_LENGTH) File(bridge.clipsDir, "${event.id}.txt").apply { writeText(text) } else null
                ClipItem(id = event.id, incoming = true, kind = ClipKind.TEXT, text = text.take(PREVIEW_LENGTH), file = full?.absolutePath, macId = pairing.id, device = device, time = event.time * 1000)
            }

            ClipMeta.FILE -> {
                val bytes = data ?: return Outcome.SKIPPED
                val name = safeName(meta.fileName ?: "file")
                val saved = bridge.saveDownload(name = name, mime = meta.mime, bytes = bytes) ?: return Outcome.SKIPPED
                ClipItem(id = event.id, incoming = true, kind = ClipKind.FILE, file = saved.toString(), mime = meta.mime, name = name, size = bytes.size.toLong(), macId = pairing.id, device = device, time = event.time * 1000)
            }

            ClipMeta.IMAGE -> {
                if (!settings.sendsImages.value) return Outcome.SKIPPED
                val bytes = data ?: return Outcome.SKIPPED
                if (isEcho(bytes = bytes)) return Outcome.SKIPPED
                val file = File(bridge.clipsDir, "${event.id}.${bridge.extensionFor(meta.mime)}").apply { writeBytes(bytes) }
                if (!isStale) bridge.writeImage(file = file, mime = meta.mime)
                ClipItem(id = event.id, incoming = true, kind = ClipKind.IMAGE, file = file.absolutePath, mime = meta.mime, macId = pairing.id, device = device, time = event.time * 1000)
            }

            else -> return Outcome.SKIPPED
        }
        remember(item = item)
        if (isStale && item.kind != ClipKind.FILE) return Outcome.SKIPPED
        notifications.showReceived(item = item)
        return Outcome.APPLIED
    }

    private suspend fun receiveStream(pairing: Pairing, event: NtfyEvent, meta: ClipMeta, url: String, device: String, onTransfer: (Transfer) -> Unit): Outcome {
        val name = safeName(meta.fileName ?: "file")
        val total = meta.size ?: event.attachment?.size ?: 0L
        val cipher = cipherFor(pairing)
        val context = currentCoroutineContext()
        val reporter = TransferReporter(onTransfer = { _transfer.value = it; onTransfer(it) }, base = Transfer(name = name, incoming = true, device = device, done = 0, total = total))
        var saved: Uri? = null
        var size = 0L
        try {
            client.downloadTo(endpoint = pairing.endpoint, url = url) { input ->
                saved = bridge.saveDownload(name = name, mime = meta.mime) { output ->
                    size = cipher.openStream(input = input, output = output) { done ->
                        context.ensureActive()
                        reporter.report(done = done)
                    }
                }
            }
        } catch (error: NtfyException) {
            if (error.code == HTTP_NOT_FOUND || error.code == HTTP_GONE) {
                notifications.showMissed(name = name, device = device)
                return Outcome.SKIPPED
            }
            throw InterruptedTransfer(cause = error)
        } catch (error: IOException) {
            throw InterruptedTransfer(cause = error)
        } finally {
            finishTransfer()
        }
        val uri = saved ?: return Outcome.SKIPPED
        val item = ClipItem(id = event.id, incoming = true, kind = ClipKind.FILE, file = uri.toString(), mime = meta.mime, name = name, size = size, macId = pairing.id, device = device, time = event.time * 1000)
        remember(item = item)
        notifications.showReceived(item = item)
        return Outcome.APPLIED
    }

    private fun finishTransfer() {
        _transfer.value = null
        notifications.clearTransfer()
    }

    private suspend fun switchRoom(pairing: Pairing, room: String, key: String, token: String?, keep: List<String>?, without: String?) {
        val moved = store.rekey(pairingId = pairing.id, room = room, key = key, token = token, without = without) ?: return
        keep?.let { ids -> moved.members.filterNot { it.id in ids }.forEach { store.removeMember(pairingId = moved.id, memberId = it.id) } }
        push.unsubscribe(topic = pairing.inTopic)
        if (settings.isInstant.value) push.subscribe(topic = room)
        links.update { map -> map[pairing.id]?.let { map - pairing.id + (moved.id to it) } ?: map }
        runCatching { publishControl(pairing = moved, meta = control(kind = ClipMeta.HELLO)) }
        Log.i(TAG, "switched ${pairing.id} to a new room")
    }

    private suspend fun moveToRoom(pairing: Pairing, room: String, macId: String, macName: String) {
        val moved = store.moveToRoom(pairingId = pairing.id, room = room) ?: return
        push.unsubscribe(topic = pairing.inTopic)
        if (settings.isInstant.value) push.subscribe(topic = room)
        if (macId.isNotEmpty()) {
            store.saveMember(pairingId = moved.id, member = Member(id = macId, name = macName.ifEmpty { pairing.macName }, source = SOURCE_MAC, seen = System.currentTimeMillis()))
        }
        links.update { map -> map[pairing.id]?.let { map - pairing.id + (moved.id to it) } ?: map }
        runCatching { publishControl(pairing = moved, meta = control(kind = ClipMeta.HELLO)) }
        Log.i(TAG, "moved ${pairing.id} to room")
    }

    private suspend fun publishControl(pairing: Pairing, meta: ClipMeta) {
        client.publish(endpoint = pairing.endpoint, topic = pairing.outTopic, message = cipherFor(pairing).sealToText(plain = encode(meta)), body = null)
    }

    private fun control(kind: String): ClipMeta = ClipMeta(kind = kind, device = settings.deviceName.value, sender = settings.deviceId, publicKey = settings.identity.publicText)

    private fun recipientsOf(pairing: Pairing): List<String> = when {
        !pairing.isRoom -> listOf(pairing.id)
        pairing.members.any { it.id != settings.deviceId } -> pairing.members.map { it.id }.filter { it != settings.deviceId }
        else -> listOf(pairing.macId.ifEmpty { pairing.id })
    }

    private fun macsOf(pairing: Pairing, link: LinkStatus): List<MacDevice> {
        val macMembers = pairing.members.filter { it.isComputer }
        return when {
            !pairing.isRoom -> listOf(MacDevice(id = pairing.id, pairingId = pairing.id, name = pairing.macName.ifEmpty { "Mac" }, host = pairing.host, link = link))
            macMembers.isNotEmpty() -> macMembers.map { MacDevice(id = it.id, pairingId = pairing.id, name = it.title, host = pairing.host, link = link) }
            pairing.macId.isEmpty() && pairing.macName.isEmpty() -> emptyList()
            else -> listOf(MacDevice(id = pairing.macId.ifEmpty { pairing.id }, pairingId = pairing.id, name = pairing.macName.ifEmpty { "Mac" }, host = pairing.host, link = link))
        }
    }

    private fun devicesOf(pairing: Pairing, link: LinkStatus, selfName: String, now: Long): List<RoomDevice> {
        val self = RoomDevice(
            id = settings.deviceId,
            pairingId = pairing.id,
            name = selfName,
            kind = DeviceKind.PHONE,
            isSelf = true,
            isOnline = link.isOnline,
            seen = link.lastContact.takeIf { it > 0 } ?: now,
            since = pairing.since,
            host = pairing.host,
            isRoom = pairing.isRoom,
            ownName = deviceName(context = context),
        )
        val others = if (pairing.isRoom && pairing.members.any { it.id != settings.deviceId }) {
            pairing.members.filter { it.id != settings.deviceId }.map { member ->
                RoomDevice(
                    id = member.id,
                    pairingId = pairing.id,
                    name = member.title,
                    kind = if (member.isComputer) DeviceKind.COMPUTER else DeviceKind.PHONE,
                    isSelf = false,
                    isOnline = now - member.seen < ONLINE_MS,
                    seen = member.seen,
                    since = member.since,
                    host = pairing.host,
                    isRoom = true,
                    isOwner = member.id == pairing.ownerId,
                    ownName = member.name,
                    hasAlias = member.alias.isNotEmpty() && member.alias != member.name,
                )
            }
        } else {
            macsOf(pairing = pairing, link = link).map { mac ->
                RoomDevice(
                    id = mac.id,
                    pairingId = pairing.id,
                    name = mac.name,
                    kind = DeviceKind.COMPUTER,
                    isSelf = false,
                    isOnline = link.isOnline,
                    seen = link.lastContact,
                    since = pairing.since,
                    host = pairing.host,
                    isRoom = pairing.isRoom,
                    isOwner = pairing.isRoom && mac.id == pairing.ownerId,
                )
            }
        }
        return (others + self).sortedWith(compareBy<RoomDevice> { it.kind }.thenBy { it.since })
    }

    private fun isEcho(bytes: ByteArray): Boolean =
        System.currentTimeMillis() - lastSentAt < ECHO_WINDOW_MS && digest(bytes = bytes) == lastSentHash

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(separator = "") { "%02x".format(it) }

    private fun hex(bytes: Int): String = hex(ByteArray(bytes).also(random::nextBytes))

    private fun hex(bytes: ByteArray): String = bytes.joinToString(separator = "") { "%02x".format(it) }

    private fun markLink(id: String, isOnline: Boolean) {
        links.update { map ->
            val previous = map[id] ?: LinkStatus()
            map + (id to if (isOnline) LinkStatus(isOnline = true, lastContact = System.currentTimeMillis()) else previous.copy(isOnline = false))
        }
    }

    private fun remember(item: ClipItem) {
        store.addHistory(item = item).forEach(::dropFiles)
        ClipWidget.refresh(context = context, last = store.history.value.firstOrNull())
    }

    private fun dropFiles(item: ClipItem) {
        item.file?.takeIf { !it.startsWith("content://") && it.startsWith(bridge.clipsDir.absolutePath) }?.let(::File)?.delete()
    }

    private fun safeName(name: String): String =
        name.substringAfterLast('/').replace(Regex("[\\u0000-\\u001f\\\\:*?\"<>|]"), "_").take(MAX_NAME).ifBlank { "file" }

    private fun parse(raw: String): Pairing {
        val pairing = runCatching { SyncJson.decodeFromString(Pairing.serializer(), raw.trim()) }.getOrNull()
            ?: throw PairingException(problem = PairingProblem.NOT_TOSSLING)
        val server = pairing.server.trim().trimEnd('/')
        val valid = server.startsWith("https://") || server.startsWith("http://")
        val normalized = if (pairing.room.isNotBlank()) pairing.copy(inTopic = pairing.room, outTopic = pairing.room) else pairing
        if (!valid || normalized.inTopic.isBlank() || normalized.outTopic.isBlank() || runCatching { ClipCipher.fromBase64(pairing.key) }.isFailure) {
            throw PairingException(problem = PairingProblem.NOT_TOSSLING)
        }
        return normalized.copy(server = server, members = emptyList())
    }

    private fun cipherFor(pairing: Pairing): ClipCipher = ClipCipher.fromBase64(key = pairing.key)

    private fun encode(meta: ClipMeta): ByteArray = SyncJson.encodeToString(ClipMeta.serializer(), meta).toByteArray()

    private enum class Outcome { APPLIED, SKIPPED, LEFT }

    private class InterruptedTransfer(cause: Throwable) : Exception(cause)

    private class TransferReporter(private val onTransfer: (Transfer) -> Unit, private val base: Transfer) {

        private var lastAt = 0L
        private var lastPercent = -1

        fun report(done: Long) {
            val transfer = base.copy(done = done)
            val now = System.currentTimeMillis()
            val finished = done >= base.total
            if (transfer.percent == lastPercent && !finished) return
            if (now - lastAt < REPORT_MS && !finished) return
            lastAt = now
            lastPercent = transfer.percent
            onTransfer(transfer)
        }
    }

    private companion object {
        private const val TAG = "ClipRepository"
        private const val MESSAGE_EVENT = "message"
        private const val SOURCE_MAC = "mac"
        private const val LEGACY_PREFIX = "legacy-"
        private const val TOKEN_LABEL = "tossling"
        private const val ROOM_BYTES = 12
        private const val FIRST_SINCE = "15m"
        private const val STALE_SECONDS = 15 * 60
        private const val FILE_STALE_SECONDS = 3 * 60 * 60
        private const val INLINE_LIMIT = 2_400
        private const val PREVIEW_LENGTH = 2_000
        private const val ECHO_WINDOW_MS = 60_000L
        private const val ONLINE_MS = 5 * 60_000L
        private const val PING_INTERVAL_MS = 60_000L
        private const val SETTLE_MS = 60_000L
        private const val CLOCK_MS = 30_000L
        private const val MAX_NAME = 120
        private const val KEEP_LIMIT = 25_000_000L
        private const val STREAM_BUFFER = 1 shl 16
        private const val REPORT_MS = 500L
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_GONE = 410
    }
}
