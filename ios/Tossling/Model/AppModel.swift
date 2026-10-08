import Foundation
import Observation
import SwiftUI
import TosslingKit
import UniformTypeIdentifiers

@MainActor
@Observable
final class AppModel {

    enum Connection {
        case offline, connecting, online
    }

    private(set) var isPaired = false
    private(set) var connection = Connection.offline
    private(set) var clips: [Clip] = []
    private(set) var devices: [Member] = []
    private(set) var busy = false
    var notice: String?

    private let store = Store.shared
    private let server = Server.shared
    private let history = HistoryStore()
    private var core: RoomCore?
    private var listener: Task<Void, Never>?
    private var lastPing = Date.distantPast

    init() {
        clips = history.load()
        if let config = store.config {
            core = RoomCore(config: config, members: store.members)
            isPaired = true
            refreshDevices()
        }
    }

    var host: String { core.map { URL(string: $0.config.server)?.host ?? $0.config.server } ?? "" }

    var deviceName: String {
        get { store.deviceName }
        set {
            store.deviceName = newValue
            guard let core else { return }
            core.rename(name: newValue)
            store.config = core.config
            Task { try? await server.publish(core.config, core.hello(to: nil, renewed: false, invite: nil)) }
        }
    }

    var autoCopy: Bool {
        get { store.autoCopy }
        set { store.autoCopy = newValue }
    }

    func isOnline(_ member: Member) -> Bool { core?.isOnline(member: member, nowMs: Self.nowMs) ?? false }

    func isComputer(_ member: Member) -> Bool { member.isComputer }

    func becameActive() {
        let sent = Outbox.take()
        if !sent.isEmpty {
            clips = (sent + clips).sorted { $0.date > $1.date }
            history.save(clips)
        }
        guard isPaired else { return }
        Push.shared.follow(room: core?.config.room)
        connect()
    }

    func wentToBackground() {
        listener?.cancel()
        listener = nil
        connection = .offline
    }

    func pair(raw: String) async throws {
        guard let paired = PairingCode.shared.read(raw: raw, deviceId: store.deviceId, deviceName: store.deviceName, identity: store.identity, source: "ios", nowMs: Self.nowMs) else {
            throw PairingError.notACode
        }
        let works: Bool
        do {
            works = try await server.accountWorks(server: paired.config.server, token: paired.config.token)
        } catch {
            throw PairingError.network(error.localizedDescription)
        }
        guard works else { throw PairingError.token }
        let core = RoomCore(config: paired.config, members: paired.members)
        try await server.publish(core.config, core.hello(to: nil, renewed: true, invite: nil))
        store.config = core.config
        store.members = core.members
        store.lastEventId = ""
        self.core = core
        isPaired = true
        refreshDevices()
        Push.shared.follow(room: core.config.room)
        connect()
        notice = String(localized: "Connected to \(paired.computer.isEmpty ? host : paired.computer)")
    }

    func leave() async {
        if let core { try? await server.publish(core.config, core.bye()) }
        listener?.cancel()
        listener = nil
        store.forgetRoom()
        Push.shared.follow(room: nil)
        core = nil
        isPaired = false
        connection = .offline
        devices = []
    }

    func probe() {
        guard let core, Date().timeIntervalSince(lastPing) > 60 else { return }
        lastPing = Date()
        Task { try? await server.publish(core.config, core.ping()) }
    }

    func sendText(_ text: String) async {
        guard let core, !text.isEmpty else { return }
        await perform(String(localized: "Sent the text")) { try await Sender(core: core).text(text) }
    }

    func sendImage(_ data: Data, type: UTType) async {
        guard let core else { return }
        await perform(String(localized: "Sent the image")) { try await Sender(core: core).image(data, type: type) }
    }

    func sendFile(_ url: URL) async {
        guard let core else { return }
        await perform(String(localized: "Sent \(url.lastPathComponent)")) { try await Sender(core: core).file(url) }
    }

    func copy(_ clip: Clip) {
        switch clip.kind {
        case .text:
            UIPasteboard.general.string = clip.text
        case .image:
            if let url = Paths.url(of: clip), let image = UIImage(contentsOfFile: url.path) { UIPasteboard.general.image = image }
        case .file:
            if let url = Paths.url(of: clip) { UIPasteboard.general.url = url }
        }
        notice = String(localized: "Copied")
    }

    func delete(_ clip: Clip) {
        if clip.kind == .image, let url = Paths.url(of: clip) { try? FileManager.default.removeItem(at: url) }
        clips.removeAll { $0.id == clip.id }
        history.save(clips)
    }

    private func connect() {
        listener?.cancel()
        guard let core else { return }
        listener = Task { [weak self] in await self?.listen(core) }
    }

    private func listen(_ core: RoomCore) async {
        connection = .connecting
        try? await server.publish(core.config, core.knowsOthers ? core.hello(to: nil, renewed: false, invite: nil) : core.ping())
        var retry: Double = 1
        while !Task.isCancelled {
            do {
                let since = store.lastEventId.isEmpty ? "15m" : store.lastEventId
                for try await event in server.events(core.config, since: since, onOpen: { Task { @MainActor in self.connection = .online } }) {
                    retry = 1
                    await handle(event, core: core)
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
            if joined.isNew { notice = String(localized: "\(joined.from) is connected") }
        case let rekeyed as IncomingRekeyed:
            store.config = rekeyed.config
            store.lastEventId = ""
            Push.shared.follow(room: rekeyed.config.room)
            notice = String(localized: "\(rekeyed.from) changed the room key")
            store.members = core.members
            connect()
        case let kicked as IncomingKicked:
            if !kicked.ignored {
                notice = String(localized: "\(kicked.from) disconnected this phone from the room")
                await leaveSilently()
                return
            }
        case let gone as IncomingFileGone:
            notice = String(localized: "Did not receive \(gone.name) from \(gone.from): the server has already deleted it")
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
                add(Clip(incoming: true, kind: .text, text: text, device: content.from, event: event))
                if content.fresh { place(text: text, from: content.from) }
            case "image":
                guard let url = content.attachmentUrl else { return }
                let data = core.openAttachment(data: try await server.download(core.config, url: url))
                let type = UTType(mimeType: meta.mime) ?? .png
                let file = Paths.unique("\(UUID().uuidString).\(type.preferredFilenameExtension ?? "png")", in: Paths.images)
                try data.write(to: file)
                add(Clip(incoming: true, kind: .image, file: file.lastPathComponent, size: Int64(data.count), device: content.from, event: event))
                if content.fresh, let image = UIImage(data: data) { place(image: image, from: content.from) }
            case "file":
                guard let url = content.attachmentUrl else { return }
                let name = meta.fileName ?? "file"
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
                add(Clip(incoming: true, kind: .file, file: target.lastPathComponent, name: target.lastPathComponent, size: size, device: content.from, event: event))
                notice = String(localized: "\(target.lastPathComponent) from \(content.from) is in Files, On My iPhone, Tossling")
            default:
                break
            }
        } catch let error as ServerError where error.isGone {
            notice = String(localized: "The server has already deleted what \(content.from) sent")
        } catch {
            notice = error.localizedDescription
        }
    }

    private func place(text: String, from: String) {
        guard autoCopy, UIApplication.shared.applicationState == .active else { return }
        UIPasteboard.general.string = text
        notice = String(localized: "From \(from): ready to paste")
    }

    private func place(image: UIImage, from: String) {
        guard autoCopy, UIApplication.shared.applicationState == .active else { return }
        UIPasteboard.general.image = image
        notice = String(localized: "From \(from): ready to paste")
    }

    private func leaveSilently() async {
        listener?.cancel()
        listener = nil
        store.forgetRoom()
        Push.shared.follow(room: nil)
        core = nil
        isPaired = false
        connection = .offline
        devices = []
    }

    private func perform(_ done: String, _ work: @escaping () async throws -> Clip) async {
        busy = true
        defer { busy = false }
        do {
            add(try await work())
            notice = done
        } catch {
            notice = String(localized: "Did not send: \(error.localizedDescription)")
        }
    }

    private func add(_ clip: Clip) {
        clips.insert(clip, at: 0)
        history.save(clips)
    }

    private func refreshDevices() {
        devices = core?.others(nowMs: Self.nowMs) ?? []
    }

    private static var nowMs: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}

enum PairingError: LocalizedError {
    case notACode, token, network(String)

    var errorDescription: String? {
        switch self {
        case .notACode: String(localized: "This is not a Tossling code. Open Devices, then Connect a Phone on a computer.")
        case .token: String(localized: "The server did not accept the token. Show a new code on the computer.")
        case let .network(text): String(localized: "Could not reach the server: \(text)")
        }
    }
}
