import Foundation
import TosslingKit
import UniformTypeIdentifiers

enum SendError: LocalizedError {
    case notPaired, tooLarge(String)

    var errorDescription: String? {
        switch self {
        case .notPaired: String(localized: "Tossling is not in a room yet. Open it and scan the code on a computer.")
        case let .tooLarge(name): String(localized: "\(name) is larger than 500 MB")
        }
    }
}

struct Sender {

    static let maxFile: Int64 = 500_000_000

    let core: RoomCore
    private let server = Server.shared

    init(core: RoomCore) {
        self.core = core
    }

    init() throws {
        guard let raw = Keychain.read("room"), let config = RoomCore.companion.decodeConfig(text: raw) else { throw SendError.notPaired }
        core = RoomCore(config: config, members: [:])
    }

    func text(_ text: String) async throws -> Clip {
        try await server.publish(core.config, core.text(text: text, to: nil))
        return Clip(incoming: false, kind: .text, text: text, device: "")
    }

    func image(_ data: Data, type: UTType) async throws -> Clip {
        try await server.publish(core.config, core.image(data: data, mime: type.preferredMIMEType ?? "image/png"))
        let file = Paths.unique("\(UUID().uuidString).\(type.preferredFilenameExtension ?? "png")", in: Paths.images)
        try? data.write(to: file)
        return Clip(incoming: false, kind: .image, file: file.lastPathComponent, size: Int64(data.count), device: "")
    }

    func file(_ url: URL) async throws -> Clip {
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        let name = url.lastPathComponent
        let size = (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize).map { Int64($0) } ?? 0
        guard size <= Self.maxFile else { throw SendError.tooLarge(name) }
        let sealed = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: sealed) }
        let mime = UTType(filenameExtension: url.pathExtension)?.preferredMIMEType ?? "application/octet-stream"
        let core = core
        _ = try await Task.detached { FileCrypto.shared.seal(cipher: core.fileCipher(), from: url.path, to: sealed.path) }.value
        try await server.publishFile(core.config, message: core.file(name: name, size: size, mime: mime, to: nil).message, file: sealed)
        return Clip(incoming: false, kind: .file, name: name, size: size, device: "")
    }
}
