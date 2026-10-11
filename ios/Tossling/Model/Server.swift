import Foundation
import TosslingKit

struct ServerError: LocalizedError {
    let code: Int
    let text: String

    var errorDescription: String? { text.isEmpty ? "HTTP \(code)" : "HTTP \(code) \(text)" }

    var isGone: Bool { code == 404 || code == 410 }
    var isAuth: Bool { code == 401 || code == 403 }
}

final class Server {

    static let shared = Server()

    private let session: URLSession
    private let live: URLSession
    private let gate = RefusalGate.shared

    private init() {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 60
        configuration.timeoutIntervalForResource = 600
        session = URLSession(configuration: configuration)
        let stream = URLSessionConfiguration.default
        stream.timeoutIntervalForRequest = 90
        stream.timeoutIntervalForResource = .infinity
        live = URLSession(configuration: stream)
    }

    func publish(_ config: RoomConfig, _ outgoing: Outgoing, topic: String? = nil) async throws {
        var request = request(config, path: "/\(topic ?? config.room)")
        request.setValue("4", forHTTPHeaderField: "X-Priority")
        if let body = outgoing.bodyData {
            request.httpMethod = "PUT"
            request.setValue(outgoing.message, forHTTPHeaderField: "X-Message")
            request.setValue("clip.bin", forHTTPHeaderField: "X-Filename")
            let (data, response) = try await exchange(config, request) { try await session.upload(for: $0, from: body) }
            try check(response, data)
        } else {
            request.httpMethod = "POST"
            let (data, response) = try await exchange(config, request) { try await session.upload(for: $0, from: Data(outgoing.message.utf8)) }
            try check(response, data)
        }
    }

    func publishFile(_ config: RoomConfig, message: String, file: URL) async throws {
        var request = request(config, path: "/\(config.room)")
        request.httpMethod = "PUT"
        request.timeoutInterval = 300
        request.setValue("4", forHTTPHeaderField: "X-Priority")
        request.setValue(message, forHTTPHeaderField: "X-Message")
        request.setValue("clip.bin", forHTTPHeaderField: "X-Filename")
        let (data, response) = try await exchange(config, request) { try await session.upload(for: $0, fromFile: file) }
        try check(response, data)
    }

    func events(_ config: RoomConfig, topics: [String]? = nil, since: String, onOpen: @escaping () -> Void) -> AsyncThrowingStream<NtfyEvent, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    let path = (topics ?? [config.room]).joined(separator: ",")
                    var components = URLComponents(string: "\(config.server)/\(path)/json")!
                    components.queryItems = [URLQueryItem(name: "since", value: since)]
                    var request = URLRequest(url: components.url!)
                    authorize(&request, config.token)
                    let (bytes, response) = try await exchange(config, request) { try await live.bytes(for: $0) }
                    try check(response, nil)
                    onOpen()
                    for try await line in bytes.lines {
                        if let event = NtfyModelsKt.parseEvent(line: line) { continuation.yield(event) }
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func poll(_ config: RoomConfig, topic: String? = nil, since: String) async throws -> [NtfyEvent] {
        var components = URLComponents(string: "\(config.server)/\(topic ?? config.room)/json")!
        components.queryItems = [URLQueryItem(name: "poll", value: "1"), URLQueryItem(name: "since", value: since)]
        var request = URLRequest(url: components.url!)
        authorize(&request, config.token)
        let (data, response) = try await exchange(config, request) { try await session.data(for: $0) }
        try check(response, data)
        return String(decoding: data, as: UTF8.self).split(separator: "\n").compactMap { NtfyModelsKt.parseEvent(line: String($0)) }
    }

    func message(_ config: RoomConfig, topic: String, id: String) async throws -> NtfyEvent? {
        var components = URLComponents(string: "\(config.server)/\(topic)/json")!
        components.queryItems = [URLQueryItem(name: "poll", value: "1"), URLQueryItem(name: "since", value: "all"), URLQueryItem(name: "id", value: id)]
        var request = URLRequest(url: components.url!)
        request.timeoutInterval = 15
        authorize(&request, config.token)
        let (data, response) = try await exchange(config, request) { try await session.data(for: $0) }
        try check(response, data)
        return String(decoding: data, as: UTF8.self).split(separator: "\n").compactMap { NtfyModelsKt.parseEvent(line: String($0)) }.first { $0.id == id }
    }

    func download(_ config: RoomConfig, url: String) async throws -> Data {
        var request = URLRequest(url: URL(string: url)!)
        authorize(&request, config.token)
        let (data, response) = try await exchange(config, request) { try await session.data(for: $0) }
        try check(response, data)
        return data
    }

    func downloadFile(_ config: RoomConfig, url: String) async throws -> URL {
        var request = URLRequest(url: URL(string: url)!)
        request.timeoutInterval = 300
        authorize(&request, config.token)
        let (file, response) = try await exchange(config, request) { try await session.download(for: $0) }
        try check(response, nil)
        let kept = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.moveItem(at: file, to: kept)
        return kept
    }

    func subscriptions(_ config: RoomConfig) async throws -> [NtfySubscription] {
        var request = URLRequest(url: URL(string: "\(config.server)/v1/account")!)
        authorize(&request, config.token)
        let (data, response) = try await exchange(config, request) { try await session.data(for: $0) }
        try check(response, data)
        return NtfyModelsKt.parseAccount(text: String(decoding: data, as: UTF8.self))?.subscriptions ?? []
    }

    func createProject(_ config: RoomConfig, topic: String, name: String) async throws -> TosslingProject? {
        let body = Data(NtfyModelsKt.encodeProjectRequest(topic: topic, name: name).utf8)
        let (data, _) = try await api(config, path: "projects", method: "POST", body: body)
        return NtfyModelsKt.parseProject(text: String(decoding: data, as: UTF8.self))
    }

    func deleteProject(_ config: RoomConfig, topic: String) async throws {
        _ = try await api(config, path: "projects/\(topic)", method: "DELETE", body: nil)
    }

    func createToken(_ config: RoomConfig) async throws -> String {
        var request = URLRequest(url: URL(string: "\(config.server)/v1/account/token")!)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        authorize(&request, config.token)
        let body = Data("{\"label\":\"tossling\",\"expires\":0}".utf8)
        let (data, response) = try await exchange(config, request) { try await session.upload(for: $0, from: body) }
        try check(response, data)
        guard let token = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["token"] as? String, !token.isEmpty else {
            throw ServerError(code: 0, text: "no token in the answer")
        }
        return token
    }

    func deleteToken(_ config: RoomConfig, token: String, authority: String? = nil) async throws {
        let authority = authority ?? config.token
        var request = URLRequest(url: URL(string: "\(config.server)/v1/account/token")!)
        request.httpMethod = "DELETE"
        request.setValue(token, forHTTPHeaderField: "X-Token")
        authorize(&request, authority)
        let (data, response) = try await exchange(server: config.server, token: authority, request) { try await session.data(for: $0) }
        try check(response, data)
    }

    func discardToken(_ config: RoomConfig, token: String) async {
        for authority in [token, config.token] {
            do {
                try await deleteToken(config, token: token, authority: authority)
                return
            } catch let error as ServerError where error.isGone {
                return
            } catch {
                print("tokens: could not delete the unused new token: \(error.localizedDescription)")
            }
        }
    }

    private func api(_ config: RoomConfig, path: String, method: String, body: Data?) async throws -> (Data, URLResponse) {
        for prefix in ["/v1/tossling", "/v1/tossy"] {
            var request = URLRequest(url: URL(string: "\(config.server)\(prefix)/\(path)")!)
            request.httpMethod = method
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            authorize(&request, config.token)
            let (data, response) = try await exchange(config, request) { request in
                if let body { try await session.upload(for: request, from: body) } else { try await session.data(for: request) }
            }
            if (response as? HTTPURLResponse)?.statusCode == 404, prefix == "/v1/tossling", !String(decoding: data, as: UTF8.self).contains("\"code\"") { continue }
            try check(response, data)
            return (data, response)
        }
        throw ServerError(code: 404, text: "")
    }

    func accountWorks(server: String, token: String) async throws -> Bool {
        var request = URLRequest(url: URL(string: "\(server)/v1/account")!)
        request.timeoutInterval = 20
        authorize(&request, token)
        let (_, response) = try await exchange(server: server, token: token, request) { try await session.data(for: $0) }
        return (200..<300).contains(Self.code(response))
    }

    func health(server: String) async -> TosslingHealth? {
        for prefix in ["/v1/tossling", "/v1/tossy"] {
            guard let url = URL(string: "\(server)\(prefix)/health") else { return nil }
            var request = URLRequest(url: url)
            request.timeoutInterval = 20
            guard let (data, response) = try? await session.data(for: request) else { return nil }
            if (response as? HTTPURLResponse)?.statusCode == 404 { continue }
            return NtfyModelsKt.parseHealth(text: String(decoding: data, as: UTF8.self))
        }
        return nil
    }

    func refusal(_ config: RoomConfig) -> ServerRefusal? {
        gate.refusal(server: config.server, token: config.token)
    }

    func pause(_ config: RoomConfig) -> TimeInterval {
        gate.pauseLeft(server: config.server, token: config.token)
    }

    private func exchange<T>(_ config: RoomConfig, _ request: URLRequest, _ work: (URLRequest) async throws -> (T, URLResponse)) async throws -> (T, URLResponse) {
        try await exchange(server: config.server, token: config.token, request, work)
    }

    private func exchange<T>(server: String, token: String, _ request: URLRequest, _ work: (URLRequest) async throws -> (T, URLResponse)) async throws -> (T, URLResponse) {
        try gate.check(server: server, token: token)
        let (value, response) = try await work(request)
        gate.record(code: Self.code(response), account: Self.isAccount(request.url), server: server, token: token)
        return (value, response)
    }

    private func request(_ config: RoomConfig, path: String) -> URLRequest {
        var request = URLRequest(url: URL(string: config.server + path)!)
        authorize(&request, config.token)
        return request
    }

    private func authorize(_ request: inout URLRequest, _ token: String) {
        if !token.isEmpty { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
    }

    private func check(_ response: URLResponse, _ data: Data?) throws {
        let code = Self.code(response)
        guard !(200..<300).contains(code) else { return }
        if let kind = ServerRefusal(code: code, account: Self.isAccount(response.url)) { throw ServerRefused(kind: kind) }
        throw ServerError(code: code, text: data.map { String(decoding: $0, as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines) } ?? "")
    }

    private static func code(_ response: URLResponse) -> Int {
        (response as? HTTPURLResponse)?.statusCode ?? 0
    }

    private static func isAccount(_ url: URL?) -> Bool {
        url?.path.contains("/v1/account") ?? false
    }
}
