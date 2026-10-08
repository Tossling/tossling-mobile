import Foundation
import TosslingKit
import UniformTypeIdentifiers
import UserNotifications

final class NotificationService: UNNotificationServiceExtension {

    private var deliver: ((UNNotificationContent) -> Void)?
    private var task: Task<Void, Never>?

    override func didReceive(_ request: UNNotificationRequest, withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void) {
        deliver = contentHandler
        let info = request.content.userInfo
        task = Task {
            let content = await Self.content(topic: info["topic"] as? String, id: (info["poll_id"] as? String) ?? (info["id"] as? String))
            finish(content)
        }
    }

    override func serviceExtensionTimeWillExpire() {
        task?.cancel()
        finish(UNMutableNotificationContent())
    }

    private func finish(_ content: UNNotificationContent) {
        deliver?(content)
        deliver = nil
    }

    private static func content(topic: String?, id: String?) async -> UNNotificationContent {
        let hidden = UNMutableNotificationContent()
        guard let topic, let id else { return problem("no topic or id in the push") }
        guard let raw = Keychain.read("room"), let config = RoomCore.companion.decodeConfig(text: raw) else { return problem("no room in the Keychain") }
        guard topic == config.room else { return hidden }
        let event: NtfyEvent
        do {
            guard let found = try await Server.shared.message(config, topic: topic, id: id) else { return problem("the server has no message \(id)") }
            event = found
        } catch {
            return problem(error.localizedDescription)
        }
        let core = RoomCore(config: config, members: [:])
        guard let incoming = core.open(event: event, nowMs: Int64(Date().timeIntervalSince1970 * 1000)) as? IncomingContent else { return hidden }
        let meta = incoming.meta
        let content = UNMutableNotificationContent()
        content.title = incoming.from
        content.sound = .default
        content.threadIdentifier = config.room
        switch meta.kind {
        case "text":
            if let text = meta.text {
                content.body = String(text.prefix(1000))
            } else if let url = incoming.attachmentUrl, let data = try? await Server.shared.download(config, url: url) {
                content.body = String(String(decoding: core.openAttachment(data: data), as: UTF8.self).prefix(1000))
            } else {
                content.body = String(localized: "Text")
            }
        case "image":
            content.body = String(localized: "Image")
            if let url = incoming.attachmentUrl, let data = try? await Server.shared.download(config, url: url) {
                let type = UTType(mimeType: meta.mime) ?? .png
                let file = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString).\(type.preferredFilenameExtension ?? "png")")
                if (try? core.openAttachment(data: data).write(to: file)) != nil,
                   let attachment = try? UNNotificationAttachment(identifier: "image", url: file) {
                    content.attachments = [attachment]
                }
            }
        case "file":
            let size = ByteCountFormatter.string(fromByteCount: meta.size?.int64Value ?? 0, countStyle: .file)
            content.body = String(localized: "\(meta.fileName ?? "file"), \(size): open Tossling to receive it")
        default:
            return hidden
        }
        return content
    }

    private static func problem(_ reason: String) -> UNNotificationContent {
        let content = UNMutableNotificationContent()
        content.title = "Tossling"
        content.body = String(localized: "Something new arrived, open Tossling to see it.")
        #if DEBUG
        content.body += " (\(reason))"
        #endif
        return content
    }
}
