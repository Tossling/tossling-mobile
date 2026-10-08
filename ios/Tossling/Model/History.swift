import Foundation

struct Clip: Codable, Identifiable, Equatable {

    enum Kind: String, Codable {
        case text, image, file
    }

    var id = UUID()
    var incoming: Bool
    var kind: Kind
    var text: String?
    var file: String?
    var name: String?
    var size: Int64 = 0
    var device: String
    var date = Date()
    var event: String?
}

enum Paths {

    static var documents: URL { FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0] }

    static var support: URL {
        let url = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    static let groupID = "group.com.kopylovis.tossling"

    static var shared: URL? { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupID) }

    static var images: URL {
        let url = (shared ?? support).appendingPathComponent("images", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    static func url(of clip: Clip) -> URL? {
        guard let file = clip.file else { return nil }
        guard clip.kind == .image else { return documents.appendingPathComponent(file) }
        let current = images.appendingPathComponent(file)
        if FileManager.default.fileExists(atPath: current.path) { return current }
        return support.appendingPathComponent("images", isDirectory: true).appendingPathComponent(file)
    }

    static func unique(_ name: String, in dir: URL) -> URL {
        let safe = name.replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: ":", with: "_")
        let base = (safe as NSString).deletingPathExtension
        let ext = (safe as NSString).pathExtension
        var candidate = dir.appendingPathComponent(safe.isEmpty ? "file" : safe)
        var index = 2
        while FileManager.default.fileExists(atPath: candidate.path) {
            candidate = dir.appendingPathComponent(ext.isEmpty ? "\(base) \(index)" : "\(base) \(index).\(ext)")
            index += 1
        }
        return candidate
    }
}

final class HistoryStore {

    static let limit = 100

    private let url = Paths.support.appendingPathComponent("history.json")

    func load() -> [Clip] {
        guard let data = try? Data(contentsOf: url) else { return [] }
        return (try? JSONDecoder().decode([Clip].self, from: data)) ?? []
    }

    func save(_ clips: [Clip]) {
        let kept = Array(clips.prefix(Self.limit))
        for dropped in clips.dropFirst(Self.limit) where dropped.kind == .image {
            if let file = Paths.url(of: dropped) { try? FileManager.default.removeItem(at: file) }
        }
        if let data = try? JSONEncoder().encode(kept) { try? data.write(to: url, options: .atomic) }
    }
}

enum Outbox {

    private static var url: URL? { Paths.shared?.appendingPathComponent("outbox.json") }

    static func add(_ clip: Clip) {
        guard let url else { return }
        var clips = (try? Data(contentsOf: url)).flatMap { try? JSONDecoder().decode([Clip].self, from: $0) } ?? []
        clips.append(clip)
        if let data = try? JSONEncoder().encode(clips) { try? data.write(to: url, options: .atomic) }
    }

    static func take() -> [Clip] {
        guard let url, let data = try? Data(contentsOf: url) else { return [] }
        try? FileManager.default.removeItem(at: url)
        return (try? JSONDecoder().decode([Clip].self, from: data)) ?? []
    }
}
