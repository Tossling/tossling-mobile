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
    var toAll = false
    var date = Date()
    var event: String?
    var isPinned = false

    init(incoming: Bool, kind: Kind, text: String? = nil, file: String? = nil, name: String? = nil, size: Int64 = 0, device: String, toAll: Bool = false, event: String? = nil) {
        self.incoming = incoming
        self.kind = kind
        self.text = text
        self.file = file
        self.name = name
        self.size = size
        self.device = device
        self.toAll = toAll
        self.event = event
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(UUID.self, forKey: .id)
        incoming = try c.decode(Bool.self, forKey: .incoming)
        kind = try c.decode(Kind.self, forKey: .kind)
        text = try c.decodeIfPresent(String.self, forKey: .text)
        file = try c.decodeIfPresent(String.self, forKey: .file)
        name = try c.decodeIfPresent(String.self, forKey: .name)
        size = try c.decodeIfPresent(Int64.self, forKey: .size) ?? 0
        device = try c.decodeIfPresent(String.self, forKey: .device) ?? ""
        toAll = try c.decodeIfPresent(Bool.self, forKey: .toAll) ?? false
        date = try c.decodeIfPresent(Date.self, forKey: .date) ?? Date()
        event = try c.decodeIfPresent(String.self, forKey: .event)
        isPinned = try c.decodeIfPresent(Bool.self, forKey: .isPinned) ?? false
    }

    func matches(_ query: String) -> Bool {
        let words = query.lowercased().split(whereSeparator: \.isWhitespace)
        guard !words.isEmpty else { return true }
        let haystack = [text ?? "", name ?? "", device, kind.rawValue].joined(separator: " ").lowercased()
        return words.allSatisfy { haystack.contains($0) }
    }
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
        var unpinned = 0
        var kept: [Clip] = []
        var dropped: [Clip] = []
        for clip in clips {
            if clip.isPinned {
                kept.append(clip)
            } else if unpinned < Self.limit {
                kept.append(clip)
                unpinned += 1
            } else {
                dropped.append(clip)
            }
        }
        for dropped in dropped where dropped.kind == .image {
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
