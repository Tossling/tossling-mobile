import Foundation
import Observation
import SwiftUI
import TosslingKit
import UniformTypeIdentifiers
import UserNotifications

struct DeviceItem: Identifiable, Hashable {
    let id: String
    let name: String
    let ownName: String
    let computer: Bool
    let online: Bool
    let seen: Date?
    let since: Date?
    let isSelf: Bool
    let isOwner: Bool
    let hasAlias: Bool
}

enum Payload {
    case text(String)
    case image(Data, UTType)
    case file(URL)

    var preview: String {
        switch self {
        case let .text(text): String(text.prefix(200).split(separator: "\n").first ?? "")
        case .image: String(localized: "image")
        case let .file(url): url.lastPathComponent
        }
    }
}

@MainActor
@Observable
final class AppModel {

    enum Connection {
        case offline, connecting, online
    }

    private(set) var isPaired = false
    private(set) var savedRoom: RoomConfig?
    private(set) var connection = Connection.offline
    private(set) var clips: [Clip] = []
    private(set) var devices: [DeviceItem] = []
    private(set) var projects: [Project] = []
    private(set) var alerts: [ProjectAlert] = []
    private(set) var island: IslandMessage?
    var pending: Payload?

    private let store = Store.shared
    private let server = Server.shared
    private let history = HistoryStore()
    private var core: RoomCore?
    private var listener: Task<Void, Never>?
    private var lastPing = Date.distantPast
    private var lastProjectSync = Date.distantPast
    private var islandTask: Task<Void, Never>?

    init() {
        clips = history.load()
        projects = FeedStore.projects()
        alerts = FeedStore.alerts()
        if let config = store.config {
            if store.hasStarted {
                core = RoomCore(config: config, members: store.members)
                isPaired = true
                refreshDevices()
            } else {
                savedRoom = config
            }
        }
    }

    var host: String { (core?.config.server ?? savedRoom?.server).map { URL(string: $0)?.host ?? $0 } ?? "" }
    var unread: Int { alerts.filter { !$0.isRead }.count }
    var others: [DeviceItem] { devices.filter { !$0.isSelf } }

    var deviceName: String {
        get { store.deviceName }
        set {
            let name = newValue.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !name.isEmpty, name != store.deviceName else { return }
            store.deviceName = name
            guard let core else { return }
            core.rename(name: name)
            store.config = core.config
            refreshDevices()
            Task { try? await server.publish(core.config, core.hello(to: nil, renewed: false, invite: nil)) }
        }
    }

    var paused: Bool {
        get { store.paused }
        set { store.paused = newValue; say(newValue ? String(localized: "Sending is paused") : String(localized: "Sending is on"), .info) }
    }

    var sendsImages: Bool {
        get { store.sendsImages }
        set { store.sendsImages = newValue }
    }

    var quietHours: Bool {
        get { store.quietHours }
        set { store.quietHours = newValue }
    }

    var autoCopy: Bool {
        get { store.autoCopy }
        set { store.autoCopy = newValue }
    }

    func say(_ text: String, _ kind: IslandMessage.Kind) {
        let message = IslandMessage(text: text, kind: kind)
        withAnimation(.spring(response: 0.31, dampingFraction: 0.55)) { island = message }
        islandTask?.cancel()
        islandTask = Task {
            try? await Task.sleep(for: .seconds(kind.duration))
            guard !Task.isCancelled, island == message else { return }
            withAnimation(.easeOut(duration: 0.2)) { island = nil }
        }
    }

    func becameActive() {
        UNUserNotificationCenter.current().setBadgeCount(unread)
        let sent = Outbox.take()
        if !sent.isEmpty {
            clips = (sent + clips).sorted { $0.date > $1.date }
            history.save(clips)
        }
        mergeInbox()
        guard isPaired else { return }
        followPush()
        connect()
        Task {
            await syncProjects(force: false)
            await retireTokens()
        }
    }

    func wentToBackground() {
        listener?.cancel()
        listener = nil
        connection = .offline
    }

    // MARK: Pairing

    func pairingTarget(raw: String) throws -> PairedRoom {
        guard let paired = PairingCode.shared.read(raw: raw, deviceId: store.deviceId, deviceName: store.deviceName, identity: store.identity, source: "ios", nowMs: Self.nowMs) else {
            throw PairingError.notACode
        }
        return paired
    }

    func pair(_ paired: PairedRoom) async throws {
        let works: Bool
        do {
            works = try await server.accountWorks(server: paired.config.server, token: paired.config.token)
        } catch {
            throw PairingError.network(URL(string: paired.config.server)?.host ?? paired.config.server)
        }
        guard works else { throw PairingError.token }
        if let old = core { try? await server.publish(old.config, old.bye()) }
        let core = RoomCore(config: paired.config, members: paired.members)
        try await server.publish(core.config, core.hello(to: nil, renewed: true, invite: nil))
        listener?.cancel()
        store.forgetRoom()
        store.config = core.config
        store.members = core.members
        store.lastEventId = ""
        store.hasStarted = true
        savedRoom = nil
        self.core = core
        refreshDevices()
    }

    func finishPairing() {
        isPaired = true
        followPush()
        connect()
        Task { await syncProjects(force: true) }
    }

    func restore() async throws {
        guard let saved = savedRoom else { return }
        let works: Bool
        do {
            works = try await server.accountWorks(server: saved.server, token: saved.token)
        } catch {
            throw PairingError.network(URL(string: saved.server)?.host ?? saved.server)
        }
        guard works else { throw PairingError.roomMoved }
        let core = RoomCore(config: saved, members: store.members)
        try? await server.publish(core.config, core.hello(to: nil, renewed: true, invite: nil))
        store.hasStarted = true
        savedRoom = nil
        self.core = core
        refreshDevices()
        finishPairing()
    }

    func leave() async throws {
        if let core { try await server.publish(core.config, core.bye()) }
        forget()
    }

    func revoke(_ device: DeviceItem) async throws {
        guard let core else { return }
        guard device.id != core.config.owner else { throw RoomError.creator }
        let prefix = core.config.room.hasPrefix("tossy-") ? "tossy-" : "tossling-"
        let room = RoomCore.companion.createRoom(prefix: prefix)
        let key = ClipCipher.companion.randomKey()
        let legacy = core.members.values.contains { $0.id != device.id && $0.id != core.config.deviceId && $0.pk.isEmpty }
        let token = legacy ? nil : try? await server.createToken(core.config)
        let messages = core.revoke(id: device.id, room: room, key: key, token: token)
        for message in messages { try await server.publish(core.config, message) }
        let oldToken = core.config.token
        core.switchRoom(room: room, key: key, token: token, keep: Set(core.members.keys.filter { $0 != device.id }))
        if token != nil { store.retiredTokens[oldToken] = Date().addingTimeInterval(86400) }
        store.config = core.config
        store.members = core.members
        store.lastEventId = ""
        refreshDevices()
        followPush()
        connect()
    }

    func setAlias(_ device: DeviceItem, _ alias: String) {
        let trimmed = alias.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed == device.ownName {
            store.aliases.removeValue(forKey: device.id)
        } else {
            store.aliases[device.id] = trimmed
        }
        refreshDevices()
    }

    func probe() {
        guard let core, Date().timeIntervalSince(lastPing) > 60 else { return }
        lastPing = Date()
        Task { try? await server.publish(core.config, core.ping()) }
    }

    func refresh() async -> Bool {
        guard let core else { return false }
        do {
            let events = try await server.poll(core.config, since: store.lastEventId.isEmpty ? "15m" : store.lastEventId)
            for event in events { await handle(event, core: core) }
            await syncProjects(force: false)
            await pollProjects()
            return true
        } catch {
            return false
        }
    }

    // MARK: Sending

    func readClipboard() -> Result<Payload, SendRefusal> {
        guard isPaired else { return .failure(.notPaired) }
        guard !paused else { return .failure(.paused) }
        let board = UIPasteboard.general
        if board.hasImages {
            guard sendsImages else { return .failure(.imagesOff) }
            for type in [UTType.png, .jpeg, .heic, .gif] {
                if let data = board.data(forPasteboardType: type.identifier) {
                    return data.count <= 15_000_000 ? .success(.image(data, type)) : .failure(.tooLarge)
                }
            }
            if let data = board.image?.pngData() { return data.count <= 15_000_000 ? .success(.image(data, .png)) : .failure(.tooLarge) }
        }
        if board.hasURLs, let url = board.url, !url.isFileURL { return .success(.text(url.absoluteString)) }
        if let text = board.string, !text.isEmpty {
            return text.utf8.count <= 1_000_000 ? .success(.text(text)) : .failure(.tooLarge)
        }
        return .failure(.empty)
    }

    func send(_ outgoing: Payload, to target: DeviceItem? = nil) async {
        guard let core else { return }
        let sender = Sender(core: core)
        let destination = target.map { Target(id: $0.id, name: $0.name) }
        let where_ = target?.name ?? (others.count > 1 ? String(localized: "all devices") : (others.first?.name ?? String(localized: "the computer")))
        say(String(localized: "Sending to \(where_)"), .busy)
        do {
            let clip: Clip = switch outgoing {
            case let .text(text): try await sender.text(text, to: destination)
            case let .image(data, type): try await sender.image(data, type: type, to: destination)
            case let .file(url): try await sender.file(url, to: destination)
            }
            add(clip)
            say(String(localized: "Sent to \(where_)"), .done)
        } catch {
            say(error.localizedDescription, .error)
        }
    }

    func copy(_ clip: Clip) {
        switch clip.kind {
        case .text: UIPasteboard.general.string = clip.text
        case .image: if let url = Paths.url(of: clip), let image = UIImage(contentsOfFile: url.path) { UIPasteboard.general.image = image }
        case .file: if let url = Paths.url(of: clip) { UIPasteboard.general.url = url }
        }
        say(String(localized: "Copied"), .done)
    }

    func togglePin(_ clip: Clip) {
        guard let index = clips.firstIndex(where: { $0.id == clip.id }) else { return }
        clips[index].isPinned.toggle()
        history.save(clips)
        say(clips[index].isPinned ? String(localized: "Pinned: it stays until you remove it") : String(localized: "Unpinned"), .done)
    }

    func delete(_ clip: Clip) {
        if clip.kind == .image, let url = Paths.url(of: clip) { try? FileManager.default.removeItem(at: url) }
        clips.removeAll { $0.id == clip.id }
        history.save(clips)
    }

    // MARK: Projects

    func syncProjects(force: Bool) async {
        guard let core, force || Date().timeIntervalSince(lastProjectSync) > 300 else { return }
        lastProjectSync = Date()
        guard let subscriptions = try? await server.subscriptions(core.config) else { return }
        let server = core.config.server
        let remote = subscriptions.filter { sub in
            (sub.baseUrl.isEmpty || sub.baseUrl == server) && sub.topic.range(of: "^[A-Za-z0-9_-]{1,64}$", options: .regularExpression) != nil
                && !sub.topic.hasPrefix("tossling-") && !sub.topic.hasPrefix("tossy-")
        }
        var updated = projects.filter { project in remote.contains { $0.topic == project.topic } }
        for sub in remote {
            let name = sub.displayName?.isEmpty == false ? sub.displayName! : sub.topic
            if let index = updated.firstIndex(where: { $0.topic == sub.topic }) {
                updated[index].name = name
            } else {
                updated.append(Project(topic: sub.topic, name: name, color: updated.count % 6))
            }
        }
        let added = updated.filter { project in !projects.contains { $0.topic == project.topic } }
        setProjects(updated)
        for project in added { await pollBacklog(project.topic, markRead: true) }
        followPush()
        if !added.isEmpty { connect() }
    }

    func addProject(name: String, topic: String, color: Int) async throws -> TosslingProject? {
        guard let core else { throw ProjectError.noServer }
        guard topic.range(of: "^[a-z0-9_-]{1,64}$", options: .regularExpression) != nil else { throw ProjectError.invalidTopic }
        guard !projects.contains(where: { $0.topic == topic }) else { throw ProjectError.duplicate }
        let created: TosslingProject?
        do {
            created = try await server.createProject(core.config, topic: topic, name: name)
        } catch let error as ServerError where error.isAuth {
            throw ProjectError.token
        } catch {
            throw ProjectError.network
        }
        setProjects(projects + [Project(topic: topic, name: name, color: color)])
        await pollBacklog(topic, markRead: true)
        followPush()
        connect()
        return created
    }

    func updateProject(_ project: Project) {
        setProjects(projects.map { $0.topic == project.topic ? project : $0 })
    }

    func removeProject(_ project: Project) async throws {
        guard let core else { return }
        try await server.deleteProject(core.config, topic: project.topic)
        setProjects(projects.filter { $0.topic != project.topic })
        setAlerts(alerts.filter { $0.topic != project.topic })
        followPush()
        connect()
    }

    func toggleMute(_ project: Project) {
        var changed = project
        changed.isMuted.toggle()
        updateProject(changed)
        say(changed.isMuted ? String(localized: "Muted") : String(localized: "Sound on"), .info)
    }

    func project(_ topic: String) -> Project? { projects.first { $0.topic == topic } }

    func markRead(_ alert: ProjectAlert, _ read: Bool = true) {
        setAlerts(alerts.map { $0.id == alert.id ? { var a = $0; a.isRead = read; return a }($0) : $0 })
        if read { UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: [alert.id]) }
    }

    func markAllRead() {
        setAlerts(alerts.map { var a = $0; a.isRead = true; return a })
        UNUserNotificationCenter.current().removeAllDeliveredNotifications()
        say(String(localized: "All read"), .done)
    }

    func delete(_ alert: ProjectAlert) {
        setAlerts(alerts.filter { $0.id != alert.id })
        say(String(localized: "Deleted"), .done)
    }

    func clearFeed() {
        setAlerts([])
        say(String(localized: "Feed cleared"), .done)
    }

    // MARK: Private

    private func forget() {
        listener?.cancel()
        listener = nil
        store.forgetRoom()
        store.hasStarted = false
        Push.shared.follow(topics: [])
        core = nil
        isPaired = false
        connection = .offline
        devices = []
    }

    private func followPush() {
        guard let core else { return }
        Push.shared.follow(topics: [core.config.room] + projects.map(\.topic))
    }

    private func connect() {
        listener?.cancel()
        guard let core else { return }
        listener = Task { [weak self] in await self?.listen(core) }
    }

    private func listen(_ core: RoomCore) async {
        connection = .connecting
        try? await server.publish(core.config, core.knowsOthers ? core.hello(to: nil, renewed: false, invite: nil) : core.ping())
        await pollProjects()
        var retry: Double = 1
        while !Task.isCancelled {
            do {
                let since = store.lastEventId.isEmpty ? "15m" : store.lastEventId
                let topics = [core.config.room] + projects.map(\.topic)
                for try await event in server.events(core.config, topics: topics, since: since, onOpen: { Task { @MainActor in self.connection = .online } }) {
                    retry = 1
                    if event.topic == core.config.room || event.topic.isEmpty {
                        await handle(event, core: core)
                    } else if event.event == "message" {
                        receiveAlert(event)
                    }
                    if Task.isCancelled || self.core !== core { return }
                }
            } catch let error as ServerError where error.isAuth {
                retry = max(retry, 30)
            } catch {}
            if Task.isCancelled { return }
            connection = .connecting
            try? await Task.sleep(for: .seconds(retry))
            retry = min(retry * 2, 60)
        }
    }

    private func handle(_ event: NtfyEvent, core: RoomCore) async {
        let incoming = core.open(event: event, nowMs: Self.nowMs)
        store.lastEventId = incoming.eventId
        switch incoming {
        case let content as IncomingContent:
            await receive(content, core: core)
        case let pinged as IncomingPinged:
            if let reply = pinged.reply { try? await server.publish(core.config, reply) }
        case let joined as IncomingJoined:
            if joined.isNew { say(String(localized: "New device in the room: \(name(of: joined.memberId, fallback: joined.from))"), .device) }
        case let rekeyed as IncomingRekeyed:
            store.config = rekeyed.config
            store.lastEventId = ""
            store.members = core.members
            refreshDevices()
            followPush()
            connect()
            return
        case let kicked as IncomingKicked:
            if !kicked.ignored {
                say(String(localized: "\(kicked.from) disconnected this phone from the room"), .error)
                forget()
                return
            }
        case let gone as IncomingFileGone:
            say(String(localized: "Did not receive \(gone.name) from \(gone.from): the server has already deleted it"), .error)
        default:
            break
        }
        store.members = core.members
        refreshDevices()
    }

    private func receive(_ content: IncomingContent, core: RoomCore) async {
        let meta = content.meta
        let event = content.eventId
        guard !clips.contains(where: { $0.event == event }) else { return }
        let from = name(of: content.memberId, fallback: content.from)
        do {
            switch meta.kind {
            case "text":
                let text: String
                if let inline = meta.text {
                    text = inline
                } else if let url = content.attachmentUrl {
                    text = String(decoding: core.openAttachment(data: try await server.download(core.config, url: url)), as: UTF8.self)
                } else {
                    return
                }
                add(Clip(incoming: true, kind: .text, text: text, device: from, event: event))
                if content.fresh, autoCopy, UIApplication.shared.applicationState == .active {
                    UIPasteboard.general.string = text
                    say(String(localized: "From \(from): ready to paste"), .done)
                }
            case "image":
                guard sendsImages, let url = content.attachmentUrl else { return }
                let data = core.openAttachment(data: try await server.download(core.config, url: url))
                let type = UTType(mimeType: meta.mime) ?? .png
                let file = Paths.unique("\(UUID().uuidString).\(type.preferredFilenameExtension ?? "png")", in: Paths.images)
                try data.write(to: file)
                add(Clip(incoming: true, kind: .image, file: file.lastPathComponent, size: Int64(data.count), device: from, event: event))
                if content.fresh, autoCopy, UIApplication.shared.applicationState == .active, let image = UIImage(data: data) {
                    UIPasteboard.general.image = image
                    say(String(localized: "From \(from): ready to paste"), .done)
                }
            case "file":
                guard let url = content.attachmentUrl else { return }
                let name = meta.fileName ?? "file"
                say(String(localized: "Receiving \(name)"), .busy)
                let sealed = try await server.downloadFile(core.config, url: url)
                defer { try? FileManager.default.removeItem(at: sealed) }
                let target = Paths.unique(name, in: Paths.documents)
                let stream = meta.format?.intValue == 2
                let size = try await Task.detached { () throws -> Int64 in
                    do {
                        return FileCrypto.shared.open(cipher: core.fileCipher(), from: sealed.path, to: target.path, stream: stream)
                    } catch {
                        try? FileManager.default.removeItem(at: target)
                        throw error
                    }
                }.value
                add(Clip(incoming: true, kind: .file, file: target.lastPathComponent, name: target.lastPathComponent, size: size, device: from, event: event))
                say(String(localized: "\(target.lastPathComponent) is in Files, Tossling"), .done)
            default:
                break
            }
        } catch let error as ServerError where error.isGone {
            say(String(localized: "The server has already deleted what \(from) sent"), .error)
        } catch {
            say(error.localizedDescription, .error)
        }
    }

    private func receiveAlert(_ event: NtfyEvent) {
        guard let alert = Self.alert(from: event), projects.contains(where: { $0.topic == alert.topic }) else { return }
        var ids = FeedStore.lastIds()
        ids[alert.topic] = alert.id
        FeedStore.save(lastIds: ids)
        guard !alerts.contains(where: { $0.id == alert.id }) else { return }
        setAlerts([alert] + alerts)
    }

    private func pollProjects() async {
        for project in projects { await pollBacklog(project.topic, markRead: false) }
    }

    private func pollBacklog(_ topic: String, markRead: Bool) async {
        guard let core else { return }
        let ids = FeedStore.lastIds()
        guard let events = try? await server.poll(core.config, topic: topic, since: ids[topic] ?? "24h") else { return }
        var fresh: [ProjectAlert] = []
        for event in events {
            guard var alert = Self.alert(from: event), !alerts.contains(where: { $0.id == alert.id }) else { continue }
            alert.isRead = markRead
            fresh.append(alert)
        }
        if let last = events.last {
            var updated = FeedStore.lastIds()
            updated[topic] = last.id
            FeedStore.save(lastIds: updated)
        }
        if !fresh.isEmpty { setAlerts(fresh + alerts) }
    }

    func openAlert(_ id: String) -> Bool {
        mergeInbox()
        guard let alert = alerts.first(where: { $0.id == id }) else { return false }
        markRead(alert)
        return true
    }

    private func mergeInbox() {
        let inbox = FeedStore.takeInbox().filter { alert in !alerts.contains { $0.id == alert.id } }
        if !inbox.isEmpty { setAlerts(inbox + alerts) }
    }

    private func retireTokens() async {
        guard let core else { return }
        for (token, due) in store.retiredTokens where due <= Date() && token != core.config.token {
            do {
                try await server.deleteToken(core.config, token: token)
                store.retiredTokens.removeValue(forKey: token)
            } catch let error as ServerError where (400..<500).contains(error.code) {
                store.retiredTokens.removeValue(forKey: token)
            } catch {}
        }
    }

    private func setProjects(_ value: [Project]) {
        projects = value
        FeedStore.save(projects: value)
    }

    private func setAlerts(_ value: [ProjectAlert]) {
        alerts = Array(value.sorted { $0.time > $1.time }.prefix(FeedStore.limit))
        FeedStore.save(alerts: alerts)
        UNUserNotificationCenter.current().setBadgeCount(unread)
    }

    private func add(_ clip: Clip) {
        clips.insert(clip, at: 0)
        history.save(clips)
    }

    private func name(of id: String, fallback: String) -> String {
        store.aliases[id] ?? fallback
    }

    private func refreshDevices() {
        guard let core else {
            devices = []
            return
        }
        let now = Self.nowMs
        let aliases = store.aliases
        let me = DeviceItem(id: core.config.deviceId, name: store.deviceName, ownName: store.deviceName, computer: false, online: connection == .online, seen: Date(), since: nil, isSelf: true, isOwner: core.config.isOwner, hasAlias: false)
        let members = core.members.values.filter { $0.id != core.config.deviceId }.map { member in
            DeviceItem(
                id: member.id,
                name: aliases[member.id] ?? member.name,
                ownName: member.name,
                computer: member.isComputer,
                online: core.isOnline(member: member, nowMs: now),
                seen: member.seen > 0 ? Date(timeIntervalSince1970: TimeInterval(member.seen) / 1000) : nil,
                since: member.since > 0 ? Date(timeIntervalSince1970: TimeInterval(member.since) / 1000) : nil,
                isSelf: false,
                isOwner: member.id == core.config.owner,
                hasAlias: aliases[member.id] != nil
            )
        }
        devices = (members + [me]).sorted { lhs, rhs in
            if lhs.computer != rhs.computer { return lhs.computer }
            return (lhs.since ?? .distantFuture) < (rhs.since ?? .distantFuture)
        }
    }

    static func alert(from event: NtfyEvent) -> ProjectAlert? {
        guard event.event == "message" else { return nil }
        return ProjectAlert(
            id: event.id,
            topic: event.topic,
            title: event.title ?? "",
            message: event.message ?? "",
            priority: event.priority?.intValue ?? 3,
            click: event.click.flatMap { $0.isEmpty ? nil : $0 },
            isMarkdown: event.contentType == "text/markdown",
            time: Date(timeIntervalSince1970: TimeInterval(event.time))
        )
    }

    private static var nowMs: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}

enum SendRefusal: LocalizedError {
    case notPaired, paused, tooLarge, empty, imagesOff

    var errorDescription: String? {
        switch self {
        case .notPaired: String(localized: "Tossling is not paired with a computer yet")
        case .paused: String(localized: "Sending is paused")
        case .tooLarge: String(localized: "Too large: up to 1 MB of text, 15 MB per image or 500 MB per file")
        case .empty: String(localized: "The clipboard is empty")
        case .imagesOff: String(localized: "Images are turned off in settings")
        }
    }
}

enum PairingError: LocalizedError {
    case notACode, token, network(String), roomMoved

    var title: String {
        switch self {
        case .notACode: String(localized: "This is not a Tossling code")
        case .token, .roomMoved: String(localized: "The server did not accept the token")
        case .network: String(localized: "No connection to the server")
        }
    }

    var errorDescription: String? {
        switch self {
        case .notACode: String(localized: "The QR code does not look like a pairing code. On the computer open Devices, then Connect a Phone, and scan that code.")
        case .token: String(localized: "The code is outdated or already used. Get a new one on the computer and scan it again.")
        case let .network(host): String(localized: "\(host) does not respond. Check the internet on the phone and that the server is running.")
        case .roomMoved: String(localized: "The room has moved on without this phone. Pair again with the QR code.")
        }
    }
}

enum RoomError: LocalizedError {
    case creator

    var errorDescription: String? { String(localized: "The room creator can't be removed") }
}

enum ProjectError: LocalizedError {
    case invalidTopic, duplicate, noServer, token, network

    var errorDescription: String? {
        switch self {
        case .invalidTopic: String(localized: "Channel: latin letters, digits, - and _")
        case .duplicate: String(localized: "This channel is already added")
        case .noServer: String(localized: "Pair with a computer first: it brings the server")
        case .token: String(localized: "No access to the channel: ntfy access")
        case .network: String(localized: "No connection to the server")
        }
    }
}
