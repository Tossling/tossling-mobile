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
            let (data, response) = try await session.upload(for: request, from: body)
            try check(response, data)
        } else {
            request.httpMethod = "POST"
            let (data, response) = try await session.upload(for: request, from: Data(outgoing.message.utf8))
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
        let (data, response) = try await session.upload(for: request, fromFile: file)
        try check(response, data)
    }

    func events(_ config: RoomConfig, since: String, onOpen: @escaping () -> Void) -> AsyncThrowingStream<NtfyEvent, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    var components = URLComponents(string: "\(config.server)/\(config.room)/json")!
                    components.queryItems = [URLQueryItem(name: "since", value: since)]
                    var request = URLRequest(url: components.url!)
                    authorize(&request, config.token)
                    let (bytes, response) = try await live.bytes(for: request)
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

    func poll(_ config: RoomConfig, since: String) async throws -> [NtfyEvent] {
        var components = URLComponents(string: "\(config.server)/\(config.room)/json")!
        components.queryItems = [URLQueryItem(name: "poll", value: "1"), URLQueryItem(name: "since", value: since)]
        var request = URLRequest(url: components.url!)
        authorize(&request, config.token)
        let (data, response) = try await session.data(for: request)
        try check(response, data)
        return String(decoding: data, as: UTF8.self).split(separator: "\n").compactMap { NtfyModelsKt.parseEvent(line: String($0)) }
    }

    func download(_ config: RoomConfig, url: String) async throws -> Data {
        var request = URLRequest(url: URL(string: url)!)
        authorize(&request, config.token)
        let (data, response) = try await session.data(for: request)
        try check(response, data)
        return data
    }

    func downloadFile(_ config: RoomConfig, url: String) async throws -> URL {
        var request = URLRequest(url: URL(string: url)!)
        request.timeoutInterval = 300
        authorize(&request, config.token)
        let (file, response) = try await session.download(for: request)
        try check(response, nil)
        let kept = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.moveItem(at: file, to: kept)
        return kept
    }

    func accountWorks(server: String, token: String) async throws -> Bool {
        var request = URLRequest(url: URL(string: "\(server)/v1/account")!)
        request.timeoutInterval = 20
        authorize(&request, token)
        let (_, response) = try await session.data(for: request)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        return (200..<300).contains(code)
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

    private func request(_ config: RoomConfig, path: String) -> URLRequest {
        var request = URLRequest(url: URL(string: config.server + path)!)
        authorize(&request, config.token)
        return request
    }

    private func authorize(_ request: inout URLRequest, _ token: String) {
        if !token.isEmpty { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
    }

    private func check(_ response: URLResponse, _ data: Data?) throws {
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard !(200..<300).contains(code) else { return }
        throw ServerError(code: code, text: data.map { String(decoding: $0, as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines) } ?? "")
    }
}
