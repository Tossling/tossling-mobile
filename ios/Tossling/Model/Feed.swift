import Foundation

extension Project {
    static func colorIndex(topic: String, color: Int) -> Int {
        if (0..<6).contains(color) { return color }
        var hash: Int32 = 0
        for unit in topic.utf16 { hash = hash &* 31 &+ Int32(unit) }
        return Int(hash & Int32.max) % 6
    }
}

struct Project: Codable, Identifiable, Hashable {
    var topic: String
    var name: String
    var color: Int = -1
    var isMuted = false
    var icon: String?

    var id: String { topic }
    var colorIndex: Int { Self.colorIndex(topic: topic, color: color) }

    var initials: String {
        let clean = name.trimmingCharacters(in: .whitespaces)
        guard let first = clean.first else { return "?" }
        let rest = clean.dropFirst()
        if first.isLowercase, let second = rest.first, second.isLetter { return String(clean.prefix(2)) }
        return first.uppercased()
    }
}

struct ProjectAlert: Codable, Identifiable, Hashable {
    var id: String
    var topic: String
    var title: String
    var message: String
    var priority: Int = 3
    var click: String?
    var isMarkdown = false
    var time: Date
    var isRead = false

    var isUrgent: Bool { priority >= 5 }
    var isImportant: Bool { priority == 4 }
    var isQuiet: Bool { priority <= 2 }

    func title(for project: String) -> String {
        var kept: [String] = []
        for part in title.components(separatedBy: " · ").map({ $0.trimmingCharacters(in: .whitespaces) }) where !part.isEmpty {
            if part.caseInsensitiveCompare(project.trimmingCharacters(in: .whitespaces)) == .orderedSame { continue }
            if kept.contains(where: { $0.caseInsensitiveCompare(part) == .orderedSame }) { continue }
            kept.append(part)
        }
        return kept.joined(separator: " · ")
    }

    var plainMessage: String { Markdown.plain(message) }
}

enum Markdown {

    static func plain(_ text: String) -> String {
        var s = text
        s = s.replacingOccurrences(of: "```[a-zA-Z0-9]*\\n", with: "", options: .regularExpression)
        s = s.replacingOccurrences(of: "\\[([^\\]]*)\\]\\([^)]*\\)", with: "$1", options: .regularExpression)
        for mark in ["**", "__", "`"] { s = s.replacingOccurrences(of: mark, with: "") }
        s = s.replacingOccurrences(of: "(?<![A-Za-z0-9])[*_]|[*_](?![A-Za-z0-9])", with: "", options: .regularExpression)
        s = s.replacingOccurrences(of: "(?m)^#+\\s*", with: "", options: .regularExpression)
        s = s.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
        return s.trimmingCharacters(in: .whitespaces)
    }

    static func looksLikeMarkdown(_ text: String) -> Bool {
        text.contains("**") || text.contains("```") || text.contains("](") || text.range(of: "`[^`]+`", options: .regularExpression) != nil
    }
}

enum FeedStore {

    static let limit = 500

    private static var dir: URL? { Paths.shared }

    static func projects() -> [Project] { read("projects.json") ?? [] }

    static func save(projects: [Project]) { write(projects, "projects.json") }

    static func alerts() -> [ProjectAlert] { read("alerts.json") ?? [] }

    static func save(alerts: [ProjectAlert]) { write(Array(alerts.sorted { $0.time > $1.time }.prefix(limit)), "alerts.json") }

    static func lastIds() -> [String: String] { read("last-ids.json") ?? [:] }

    static func save(lastIds: [String: String]) { write(lastIds, "last-ids.json") }

    static func addToInbox(_ alert: ProjectAlert) {
        var inbox: [ProjectAlert] = read("alerts-inbox.json") ?? []
        guard !inbox.contains(where: { $0.id == alert.id }) else { return }
        inbox.append(alert)
        write(inbox, "alerts-inbox.json")
    }

    static func inboxCount() -> Int { (read("alerts-inbox.json") as [ProjectAlert]?)?.count ?? 0 }

    static func takeInbox() -> [ProjectAlert] {
        let inbox: [ProjectAlert] = read("alerts-inbox.json") ?? []
        if let url = dir?.appendingPathComponent("alerts-inbox.json") { try? FileManager.default.removeItem(at: url) }
        return inbox
    }

    private static func read<T: Decodable>(_ name: String) -> T? {
        guard let url = dir?.appendingPathComponent(name), let data = try? Data(contentsOf: url) else { return nil }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .millisecondsSince1970
        return try? decoder.decode(T.self, from: data)
    }

    private static func write<T: Encodable>(_ value: T, _ name: String) {
        guard let url = dir?.appendingPathComponent(name) else { return }
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .millisecondsSince1970
        if let data = try? encoder.encode(value) { try? data.write(to: url, options: .atomic) }
    }
}
