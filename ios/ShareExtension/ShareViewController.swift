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
        case sending, uploading, done(String), failed(String)
    }

    private(set) var state = State.sending
    /// The files this sheet started, while they are on their way.
    private(set) var uploads: [Upload] = []
    private let context: NSExtensionContext?
    private var ids: [String] = []
    private var sender: Sender?
    private var sentItems = 0

    init(context: NSExtensionContext?) {
        self.context = context
    }

    func run() async {
        let providers = (context?.inputItems as? [NSExtensionItem] ?? []).flatMap { $0.attachments ?? [] }
        let uploader = Uploader.shared
        uploader.onChange = { [weak self] all in MainActor.assumeIsolated { self?.changed(all) } }
        uploader.onSent = { [weak self] clip in
            MainActor.assumeIsolated {
                Outbox.add(clip)
                self?.sentItems += 1
            }
        }
        uploader.activate()
        do {
            let sender = try Sender()
            self.sender = sender
            // The same order as before: a small image as a picture, then a file, then a link or text.
            for provider in providers {
                if let clip = try await sendImage(provider, with: sender) {
                    Outbox.add(clip)
                    sentItems += 1
                } else if let id = try await upload(provider, with: sender) {
                    ids.append(id)
                    changed(uploader.uploads)
                } else if let clip = try await sendText(provider, with: sender) {
                    Outbox.add(clip)
                    sentItems += 1
                }
            }
            if ids.isEmpty {
                await finish()
            } else {
                state = .uploading
                changed(uploader.uploads)
            }
        } catch {
            state = .failed(error.localizedDescription)
        }
    }

    func pause(_ upload: Upload) {
        Uploader.shared.pause(upload.id)
    }

    func resume(_ upload: Upload) {
        guard let config = sender?.core.config else { return }
        Uploader.shared.resume(upload.id, config: config)
    }

    func cancel(_ upload: Upload) {
        Uploader.shared.cancel(upload.id)
    }

    /// The background session keeps sending after the sheet closes; the app shows the progress.
    var canHide: Bool { uploads.contains { $0.state == .uploading } && !uploads.contains { $0.state == .sealing } }

    func close() {
        context?.completeRequest(returningItems: nil)
    }

    private func changed(_ all: [Upload]) {
        uploads = all.filter { ids.contains($0.id) }
        guard state == .uploading, uploads.isEmpty else { return }
        Task { await finish() }
    }

    private func finish() async {
        guard sentItems > 0 else {
            if ids.isEmpty {
                state = .failed(String(localized: "Tossling cannot send this."))
            } else {
                close()
            }
            return
        }
        state = .done(sentItems == 1 ? String(localized: "Sent to the room") : String(localized: "Sent \(sentItems) items to the room"))
        try? await Task.sleep(for: .seconds(0.8))
        close()
    }

    /// Files go through the background session; the sealed copy lives in the app group.
    private func upload(_ provider: NSItemProvider, with sender: Sender) async throws -> String? {
        guard provider.hasItemConformingToTypeIdentifier(UTType.fileURL.identifier) || provider.hasItemConformingToTypeIdentifier(UTType.data.identifier),
              !provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) || provider.hasItemConformingToTypeIdentifier(UTType.fileURL.identifier),
              let file = try? await provider.file() else { return nil }
        defer { try? FileManager.default.removeItem(at: file.deletingLastPathComponent()) }
        return try await Uploader.shared.start(file: file, core: sender.core, target: nil)
    }

    private func sendImage(_ provider: NSItemProvider, with sender: Sender) async throws -> Clip? {
        guard provider.hasItemConformingToTypeIdentifier(UTType.image.identifier),
              let type = provider.registeredContentTypes.first(where: { $0.conforms(to: .image) }),
              let data = try? await provider.data(type), data.count <= 15_000_000 else { return nil }
        return try await sender.image(data, type: type)
    }

    private func sendText(_ provider: NSItemProvider, with sender: Sender) async throws -> Clip? {
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
            case .uploading:
                ForEach(model.uploads) { upload in
                    ShareUploadRow(upload: upload, model: model)
                }
                if model.canHide {
                    Button("Hide, keep sending") { model.close() }
                        .buttonStyle(.bordered)
                        .font(.subheadline.weight(.semibold))
                }
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
        .frame(maxWidth: 340)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.opacity(0.2))
        .animation(.default, value: model.state)
    }
}

struct ShareUploadRow: View {

    let upload: Upload
    let model: ShareModel

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 6) {
                Text(upload.name).font(.subheadline.weight(.semibold)).lineLimit(1)
                if upload.state == .uploading || upload.state == .sealing {
                    ProgressView(value: upload.fraction)
                }
                Text(status)
                    .font(.footnote)
                    .foregroundStyle(upload.state == .failed ? Color.red : Color.secondary)
                    .lineLimit(2)
                    .monospacedDigit()
            }
            switch upload.state {
            case .uploading: control("pause.fill", String(localized: "Pause")) { model.pause(upload) }
            case .paused: control("play.fill", String(localized: "Continue")) { model.resume(upload) }
            case .failed: control("arrow.clockwise", String(localized: "Try again")) { model.resume(upload) }
            case .sealing: EmptyView()
            }
            control("xmark", String(localized: "Cancel")) { model.cancel(upload) }
        }
    }

    private var status: String {
        let size = ByteCountFormatter.string(fromByteCount: upload.size, countStyle: .file)
        switch upload.state {
        case .sealing:
            return String(localized: "Encrypting · \(size)")
        case .uploading:
            let sent = ByteCountFormatter.string(fromByteCount: upload.sent, countStyle: .file)
            let total = ByteCountFormatter.string(fromByteCount: upload.total, countStyle: .file)
            return String(localized: "\(upload.percent)% · \(sent) of \(total)")
        case .paused:
            return String(localized: "Paused · \(size) · starts over")
        case .failed:
            return String(localized: "Not sent: \(upload.error ?? "")")
        }
    }

    private func control(_ symbol: String, _ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 14, weight: .semibold))
                .frame(width: 34, height: 34)
                .background(.thinMaterial, in: Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
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
