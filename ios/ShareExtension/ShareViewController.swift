import SwiftUI
import UIKit
import UniformTypeIdentifiers

final class ShareViewController: UIViewController {

    override func viewDidLoad() {
        super.viewDidLoad()
        let model = ShareModel(context: extensionContext)
        let host = UIHostingController(rootView: ShareView(model: model))
        host.view.backgroundColor = .clear
        view.backgroundColor = .clear
        addChild(host)
        host.view.frame = view.bounds
        host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(host.view)
        host.didMove(toParent: self)
        Task { await model.run() }
    }
}

@MainActor
@Observable
final class ShareModel {

    enum State: Equatable {
        case sending, done(String), failed(String)
    }

    private(set) var state = State.sending
    private let context: NSExtensionContext?

    init(context: NSExtensionContext?) {
        self.context = context
    }

    func run() async {
        let providers = (context?.inputItems as? [NSExtensionItem] ?? []).flatMap { $0.attachments ?? [] }
        do {
            let sender = try Sender()
            var sent = 0
            for provider in providers {
                guard let clip = try await send(provider, with: sender) else { continue }
                Outbox.add(clip)
                sent += 1
            }
            guard sent > 0 else {
                state = .failed(String(localized: "Tossling cannot send this."))
                return
            }
            state = .done(sent == 1 ? String(localized: "Sent to the room") : String(localized: "Sent \(sent) items to the room"))
            try? await Task.sleep(for: .seconds(0.8))
            close()
        } catch {
            state = .failed(error.localizedDescription)
        }
    }

    func close() {
        context?.completeRequest(returningItems: nil)
    }

    private func send(_ provider: NSItemProvider, with sender: Sender) async throws -> Clip? {
        if provider.hasItemConformingToTypeIdentifier(UTType.image.identifier),
           let type = provider.registeredContentTypes.first(where: { $0.conforms(to: .image) }),
           let data = try? await provider.data(type), data.count <= 15_000_000 {
            return try await sender.image(data, type: type)
        }
        if provider.hasItemConformingToTypeIdentifier(UTType.fileURL.identifier) || provider.hasItemConformingToTypeIdentifier(UTType.data.identifier),
           !provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) || provider.hasItemConformingToTypeIdentifier(UTType.fileURL.identifier),
           let file = try? await provider.file() {
            defer { try? FileManager.default.removeItem(at: file.deletingLastPathComponent()) }
            return try await sender.file(file)
        }
        if provider.canLoadObject(ofClass: URL.self), let url = try? await provider.object(URL.self) {
            return try await sender.text(url.absoluteString)
        }
        if provider.canLoadObject(ofClass: String.self), let text = try? await provider.object(String.self) {
            return try await sender.text(text)
        }
        return nil
    }
}

struct ShareView: View {

    let model: ShareModel

    var body: some View {
        VStack(spacing: 16) {
            switch model.state {
            case .sending:
                ProgressView()
                    .controlSize(.large)
                Text("Sending to the room…")
            case let .done(text):
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 44))
                    .foregroundStyle(.green)
                Text(text)
            case let .failed(text):
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 44))
                    .foregroundStyle(.orange)
                Text(text)
                    .multilineTextAlignment(.center)
                Button("Close") { model.close() }
                    .buttonStyle(.borderedProminent)
            }
        }
        .font(.headline)
        .padding(28)
        .frame(maxWidth: 320)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.opacity(0.2))
        .animation(.default, value: model.state)
    }
}

private extension NSItemProvider {

    func data(_ type: UTType) async throws -> Data {
        try await withCheckedThrowingContinuation { continuation in
            _ = loadDataRepresentation(for: type) { data, error in
                if let data { continuation.resume(returning: data) } else { continuation.resume(throwing: error ?? CocoaError(.fileReadUnknown)) }
            }
        }
    }

    func file() async throws -> URL {
        let type = registeredContentTypes.first { $0.conforms(to: .data) } ?? .data
        return try await withCheckedThrowingContinuation { continuation in
            _ = loadFileRepresentation(for: type, openInPlace: false) { url, _, error in
                guard let url else { return continuation.resume(throwing: error ?? CocoaError(.fileReadUnknown)) }
                let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
                do {
                    try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
                    let copy = dir.appendingPathComponent(self.suggestedName.map { url.pathExtension.isEmpty || $0.hasSuffix(".\(url.pathExtension)") ? $0 : "\($0).\(url.pathExtension)" } ?? url.lastPathComponent)
                    try FileManager.default.copyItem(at: url, to: copy)
                    continuation.resume(returning: copy)
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }

    func object<T: _ObjectiveCBridgeable>(_ type: T.Type) async throws -> T where T._ObjectiveCType: NSItemProviderReading {
        try await withCheckedThrowingContinuation { continuation in
            _ = loadObject(ofClass: type) { value, error in
                if let value { continuation.resume(returning: value) } else { continuation.resume(throwing: error ?? CocoaError(.fileReadUnknown)) }
            }
        }
    }
}
