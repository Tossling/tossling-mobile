import AppIntents
import UIKit
import UniformTypeIdentifiers

struct SendIntent: AppIntent {

    static let title: LocalizedStringResource = "Send to Tossling"
    static let description = IntentDescription("Sends text, a picture or a file to the other devices of the room. Put Get Clipboard before it to send the clipboard.")

    @Parameter(title: "Content", inputConnectionBehavior: .connectToPreviousIntentResult)
    var content: IntentFile

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let sender = try Sender()
        let type = content.type ?? UTType(filenameExtension: (content.filename as NSString).pathExtension) ?? .data
        if type.conforms(to: .text) || type.conforms(to: .url) {
            let text = Self.plainText(content.data, type: type).trimmingCharacters(in: .newlines)
            guard !text.isEmpty else { return .result(dialog: "Nothing to send") }
            Outbox.add(try await sender.text(text))
        } else if type.conforms(to: .image), content.data.count <= 15_000_000 {
            Outbox.add(try await sender.image(content.data, type: type))
        } else {
            let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            defer { try? FileManager.default.removeItem(at: dir) }
            let url = dir.appendingPathComponent(content.filename.isEmpty ? "file" : content.filename)
            try content.data.write(to: url)
            Outbox.add(try await sender.file(url))
        }
        return .result(dialog: "Sent to the room")
    }

    private static func plainText(_ data: Data, type: UTType) -> String {
        let format: NSAttributedString.DocumentType? =
            type.conforms(to: .rtf) ? .rtf : type.conforms(to: .flatRTFD) ? .rtfd : type.conforms(to: .html) ? .html : nil
        if let format, let rich = try? NSAttributedString(data: data, options: [.documentType: format, .characterEncoding: String.Encoding.utf8.rawValue], documentAttributes: nil) {
            return rich.string
        }
        return String(decoding: data, as: UTF8.self)
    }
}
