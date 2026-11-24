import Foundation
import TosslingKit
import UniformTypeIdentifiers

/// A file on its way to the room. The sealed copy and the record live in the app group, so the share sheet, the app
/// and the system's background session all see the same file.
struct Upload: Codable, Identifiable, Equatable {

    enum State: String, Codable {
        case sealing, uploading, paused, failed
    }

    let id: String
    let name: String
    let size: Int64
    let targetName: String
    let toAll: Bool
    let created: Date
    var message = ""
    var state = State.sealing
    var total: Int64 = 0
    var error: String?
    /// Bytes on the wire so far; only in memory, the delegate reports it.
    var sent: Int64 = 0

    var fraction: Double { total > 0 ? min(1, Double(sent) / Double(total)) : 0 }
    var percent: Int { Int(fraction * 100) }

    private enum CodingKeys: String, CodingKey {
        case id, name, size, targetName, toAll, created, message, state, total, error
    }
}

private enum UploadStore {

    static var dir: URL {
        let url = (Paths.shared ?? Paths.support).appendingPathComponent("uploads", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    private static var list: URL { dir.appendingPathComponent("uploads.json") }

    static func sealed(_ id: String) -> URL { dir.appendingPathComponent(id) }

    static func load() -> [Upload] {
        (try? Data(contentsOf: list)).flatMap { try? JSONDecoder().decode([Upload].self, from: $0) } ?? []
    }

    /// Reads the file again before every change: the share sheet and the app write it from two processes.
    static func change(_ apply: (inout [Upload]) -> Void) -> [Upload] {
        var uploads = load()
        apply(&uploads)
        if let data = try? JSONEncoder().encode(uploads) { try? data.write(to: list, options: .atomic) }
        return uploads
    }
}

/// Sends files through a background URLSession: the upload goes on after the share sheet closes or the app leaves the
/// screen, and iOS wakes the app when it ends. ntfy takes an attachment in one request and cannot append to it, so
/// Continue after a pause sends the sealed file again from the first byte.
///
/// Main thread only: the sessions deliver their events on the main queue.
final class Uploader: NSObject, URLSessionDataDelegate {

    static let shared = Uploader()

    static let appSession = "com.kopylovis.tossling.uploads"
    static let shareSession = "com.kopylovis.tossling.uploads.share"

    /// The list changed or an upload moved on.
    var onChange: (([Upload]) -> Void)?
    /// A file reached the server. Without a handler the clip goes to the Outbox for the app to pick up.
    var onSent: ((Clip) -> Void)?

    private(set) var uploads: [Upload] = []
    private var sessions: [String: URLSession] = [:]
    private var finishers: [String: () -> Void] = [:]
    private var progress: [String: (sent: Int64, total: Int64)] = [:]
    private var sealing: Set<String> = []
    private var lastNotice = Date.distantPast

    private var ownSession: String { Bundle.main.bundleURL.pathExtension == "appex" ? Self.shareSession : Self.appSession }

    private override init() {
        super.init()
        uploads = UploadStore.load()
    }

    // MARK: Sessions

    /// Connects to this process's session, and in the app to the share sheet's too, then drops records whose upload
    /// died with a process that is gone.
    func activate() {
        connect(ownSession)
        reload()
        // The share sheet sees only its own session, so it cannot tell a lost upload from one the app is sending.
        guard ownSession == Self.appSession else { return notify(force: true) }
        connect(Self.shareSession)
        allTasks { [weak self] tasks in
            guard let self else { return }
            for task in tasks {
                guard let id = task.taskDescription, task.state == .running else { continue }
                if task.countOfBytesExpectedToSend > 0 { progress[id] = (task.countOfBytesSent, task.countOfBytesExpectedToSend) }
            }
            let live = Set(tasks.filter { $0.state == .running || $0.state == .suspended }.compactMap(\.taskDescription))
            let lost = uploads.filter { upload in
                (upload.state == .uploading && !live.contains(upload.id) && Date().timeIntervalSince(upload.created) > 10)
                    || (upload.state == .sealing && !self.sealing.contains(upload.id))
            }
            guard !lost.isEmpty else { return notify(force: true) }
            let interrupted = String(localized: "The upload was interrupted")
            update { list in
                for upload in lost {
                    guard let index = list.firstIndex(where: { $0.id == upload.id }) else { continue }
                    if list[index].state == .sealing {
                        try? FileManager.default.removeItem(at: UploadStore.sealed(upload.id))
                        list.remove(at: index)
                    } else {
                        list[index].state = .failed
                        list[index].error = interrupted
                    }
                }
            }
        }
    }

    /// iOS launched the app for a session's events: connect to it and call [completion] once they are delivered.
    func handleEvents(for identifier: String, completion: @escaping () -> Void) {
        finishers[identifier] = completion
        connect(identifier)
    }

    private func connect(_ identifier: String) {
        guard sessions[identifier] == nil else { return }
        let configuration = URLSessionConfiguration.background(withIdentifier: identifier)
        configuration.sharedContainerIdentifier = Paths.groupID
        configuration.isDiscretionary = false
        configuration.sessionSendsLaunchEvents = true
        configuration.timeoutIntervalForResource = 24 * 3600
        sessions[identifier] = URLSession(configuration: configuration, delegate: self, delegateQueue: .main)
    }

    private func allTasks(_ done: @escaping ([URLSessionTask]) -> Void) {
        let group = DispatchGroup()
        var tasks: [URLSessionTask] = []
        for session in sessions.values {
            group.enter()
            session.getAllTasks { found in
                DispatchQueue.main.async {
                    tasks += found
                    group.leave()
                }
            }
        }
        group.notify(queue: .main) { done(tasks) }
    }

    // MARK: Actions

    /// Seals [file] into the app group and starts the upload. Returns the upload's id.
    @MainActor
    @discardableResult
    func start(file: URL, core: RoomCore, target: Target?, id given: String? = nil) async throws -> String {
        connect(ownSession)
        let access = file.startAccessingSecurityScopedResource()
        defer { if access { file.stopAccessingSecurityScopedResource() } }
        let name = file.lastPathComponent
        let size = (try? file.resourceValues(forKeys: [.fileSizeKey]).fileSize).map { Int64($0) } ?? 0
        guard size <= Sender.maxFile else { throw SendError.tooLarge(name) }
        let mime = UTType(filenameExtension: file.pathExtension)?.preferredMIMEType ?? "application/octet-stream"
        let id = given ?? UUID().uuidString
        let sealed = UploadStore.sealed(id)
        let record = Upload(id: id, name: name, size: size, targetName: target?.name ?? "", toAll: target == nil, created: Date())
        sealing.insert(id)
        update { $0.append(record) }
        do {
            _ = try await Task.detached { try FileCrypto.shared.seal(cipher: core.fileCipher(), from: file.path, to: sealed.path) }.value
        } catch {
            sealing.remove(id)
            try? FileManager.default.removeItem(at: sealed)
            update { $0.removeAll { $0.id == id } }
            throw error
        }
        sealing.remove(id)
        guard uploads.contains(where: { $0.id == id }) else {
            // Cancelled while sealing.
            try? FileManager.default.removeItem(at: sealed)
            return id
        }
        let message = core.file(name: name, size: size, mime: mime, to: target.map { [$0.id] }).message
        let total = ((try? sealed.resourceValues(forKeys: [.fileSizeKey]).fileSize).map { Int64($0) }) ?? 0
        update { list in
            guard let index = list.firstIndex(where: { $0.id == id }) else { return }
            list[index].message = message
            list[index].total = total
            list[index].state = .uploading
        }
        if let upload = uploads.first(where: { $0.id == id }) { send(upload, config: core.config) }
        return id
    }

    func pause(_ id: String) {
        update { list in
            guard let index = list.firstIndex(where: { $0.id == id }), list[index].state == .uploading else { return }
            list[index].state = .paused
        }
        progress[id] = nil
        cancelTasks(id)
        notify(force: true)
    }

    /// Sends a paused or failed file again, from the first byte.
    func resume(_ id: String, config: RoomConfig) {
        guard FileManager.default.fileExists(atPath: UploadStore.sealed(id).path) else {
            update { $0.removeAll { $0.id == id } }
            return
        }
        update { list in
            guard let index = list.firstIndex(where: { $0.id == id }) else { return }
            list[index].state = .uploading
            list[index].error = nil
        }
        progress[id] = nil
        if let upload = uploads.first(where: { $0.id == id }) { send(upload, config: config) }
    }

    func cancel(_ id: String) {
        update { $0.removeAll { $0.id == id } }
        progress[id] = nil
        cancelTasks(id)
        if !sealing.contains(id) { try? FileManager.default.removeItem(at: UploadStore.sealed(id)) }
    }

    private func send(_ upload: Upload, config: RoomConfig) {
        connect(ownSession)
        guard let session = sessions[ownSession] else { return }
        let task = session.uploadTask(with: Server.shared.uploadRequest(config, message: upload.message), fromFile: UploadStore.sealed(upload.id))
        task.taskDescription = upload.id
        task.resume()
    }

    private func cancelTasks(_ id: String) {
        allTasks { tasks in tasks.filter { $0.taskDescription == id }.forEach { $0.cancel() } }
    }

    private func reload() {
        uploads = merged(UploadStore.load())
    }

    private func update(_ apply: (inout [Upload]) -> Void) {
        uploads = merged(UploadStore.change(apply))
        notify(force: true)
    }

    private func merged(_ list: [Upload]) -> [Upload] {
        list.map { upload in
            var upload = upload
            if upload.state == .uploading, let done = progress[upload.id] {
                upload.sent = done.sent
                if done.total > 0 { upload.total = done.total }
            }
            return upload
        }
    }

    private func notify(force: Bool = false) {
        let now = Date()
        guard force || now.timeIntervalSince(lastNotice) > 0.25 else { return }
        lastNotice = now
        onChange?(uploads)
    }

    // MARK: URLSessionDelegate

    func urlSession(_ session: URLSession, task: URLSessionTask, didSendBodyData bytesSent: Int64, totalBytesSent: Int64, totalBytesExpectedToSend: Int64) {
        guard let id = task.taskDescription else { return }
        progress[id] = (totalBytesSent, totalBytesExpectedToSend)
        uploads = merged(uploads)
        notify()
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let id = task.taskDescription else { return }
        progress[id] = nil
        reload()
        guard let upload = uploads.first(where: { $0.id == id }) else { return notify(force: true) }
        let code = (task.response as? HTTPURLResponse)?.statusCode ?? 0
        if let error = error as? URLError, error.code == .cancelled {
            // Pause and Cancel come here with the record already changed; anything else stopped it behind our back.
            if upload.state == .uploading { fail(id, String(localized: "The upload was interrupted")) }
            return notify(force: true)
        }
        if let error {
            return fail(id, error.localizedDescription)
        }
        guard (200..<300).contains(code) else {
            return fail(id, "HTTP \(code)")
        }
        try? FileManager.default.removeItem(at: UploadStore.sealed(id))
        update { $0.removeAll { $0.id == id } }
        let clip = Clip(incoming: false, kind: .file, name: upload.name, size: upload.size, device: upload.targetName, toAll: upload.toAll)
        if let onSent { onSent(clip) } else { Outbox.add(clip) }
    }

    func urlSessionDidFinishEvents(forBackgroundURLSession session: URLSession) {
        guard let identifier = session.configuration.identifier, let finish = finishers.removeValue(forKey: identifier) else { return }
        finish()
    }

    private func fail(_ id: String, _ reason: String) {
        update { list in
            guard let index = list.firstIndex(where: { $0.id == id }) else { return }
            list[index].state = .failed
            list[index].error = reason
        }
    }
}
