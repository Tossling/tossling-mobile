import CryptoKit
import Foundation

enum ServerRefusal: String, Codable {
    case token, busy

    init?(code: Int, account: Bool) {
        switch code {
        case 401: self = .token
        case 403 where account: self = .token
        case 429: self = .busy
        default: return nil
        }
    }

    var message: String {
        switch self {
        case .token: String(localized: "The server no longer accepts this device's token. Connect it to the room again.")
        case .busy: String(localized: "Too many requests from this network, the server asks to wait. Tossling will try again later.")
        }
    }
}

struct ServerRefused: LocalizedError {
    let kind: ServerRefusal

    var errorDescription: String? { kind.message }
}

struct Refusal: Codable, Equatable {
    let kind: ServerRefusal
    let failures: Int
    let until: Date

    static func pause(failures: Int) -> TimeInterval {
        min(60 * pow(2, Double(max(failures, 1) - 1)), 30 * 60)
    }
}

struct RefusalBook: Codable, Equatable {

    static let kept: TimeInterval = 7 * 86400

    private(set) var tokens: [String: Refusal] = [:]
    private(set) var servers: [String: Refusal] = [:]

    func refusal(server: String, token: String) -> Refusal? {
        tokens[Self.key(server: server, token: token)] ?? servers[Self.key(server: server)]
    }

    func held(server: String, token: String, at now: Date) -> Refusal? {
        [tokens[Self.key(server: server, token: token)], servers[Self.key(server: server)]]
            .compactMap { $0 }
            .filter { now < $0.until }
            .max { $0.until < $1.until }
    }

    mutating func refused(_ kind: ServerRefusal, server: String, token: String, at now: Date) {
        switch kind {
        case .token:
            let key = Self.key(server: server, token: token)
            tokens[key] = Self.next(after: tokens[key], kind: kind, at: now)
        case .busy:
            let key = Self.key(server: server)
            servers[key] = Self.next(after: servers[key], kind: kind, at: now)
        }
    }

    mutating func passed(server: String, token: String) {
        tokens.removeValue(forKey: Self.key(server: server, token: token))
        servers.removeValue(forKey: Self.key(server: server))
    }

    mutating func prune(at now: Date) {
        tokens = tokens.filter { now.timeIntervalSince($0.value.until) < Self.kept }
        servers = servers.filter { now.timeIntervalSince($0.value.until) < Self.kept }
    }

    private static func next(after previous: Refusal?, kind: ServerRefusal, at now: Date) -> Refusal {
        if let previous, now < previous.until { return previous }
        let failures = (previous?.failures ?? 0) + 1
        return Refusal(kind: kind, failures: failures, until: now.addingTimeInterval(Refusal.pause(failures: failures)))
    }

    static func key(server: String) -> String {
        server.hasSuffix("/") ? String(server.dropLast()) : server
    }

    static func key(server: String, token: String) -> String {
        let digest = SHA256.hash(data: Data(token.utf8)).prefix(16).map { String(format: "%02x", $0) }.joined()
        return "\(key(server: server)) \(digest)"
    }
}

final class RefusalGate: @unchecked Sendable {

    static let shared = RefusalGate(defaults: UserDefaults(suiteName: Paths.groupID) ?? .standard)

    private static let storageKey = "server-refusals"

    private let defaults: UserDefaults
    private let now: () -> Date
    private let lock = NSLock()

    init(defaults: UserDefaults, now: @escaping () -> Date = Date.init) {
        self.defaults = defaults
        self.now = now
    }

    func check(server: String, token: String) throws {
        if let held = book.held(server: server, token: token, at: now()) { throw ServerRefused(kind: held.kind) }
    }

    func pauseLeft(server: String, token: String) -> TimeInterval {
        let time = now()
        return book.held(server: server, token: token, at: time).map { $0.until.timeIntervalSince(time) } ?? 0
    }

    func refusal(server: String, token: String) -> ServerRefusal? {
        book.refusal(server: server, token: token)?.kind
    }

    func record(code: Int, account: Bool, server: String, token: String) {
        let changed: Bool
        if (200..<300).contains(code) {
            changed = update { $0.passed(server: server, token: token) }
        } else if let kind = ServerRefusal(code: code, account: account) {
            changed = update { $0.refused(kind, server: server, token: token, at: now()) }
        } else {
            changed = false
        }
        if changed { NotificationCenter.default.post(name: .serverRefusal, object: nil) }
    }

    private var book: RefusalBook {
        lock.withLock { load() }
    }

    private func update(_ change: (inout RefusalBook) -> Void) -> Bool {
        lock.withLock {
            let before = load()
            var book = before
            change(&book)
            book.prune(at: now())
            guard book != before else { return false }
            if book == RefusalBook() {
                defaults.removeObject(forKey: Self.storageKey)
            } else {
                defaults.set(try? JSONEncoder().encode(book), forKey: Self.storageKey)
            }
            return true
        }
    }

    private func load() -> RefusalBook {
        defaults.data(forKey: Self.storageKey).flatMap { try? JSONDecoder().decode(RefusalBook.self, from: $0) } ?? RefusalBook()
    }
}

extension Notification.Name {
    static let serverRefusal = Notification.Name("com.kopylovis.tossling.server-refusal")
}
