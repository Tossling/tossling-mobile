import PhotosUI
import QuickLook
import SwiftUI
import UniformTypeIdentifiers

struct HomeView: View {

    @Environment(AppModel.self) private var model
    @State private var showDevices = false
    @State private var showSettings = false
    @State private var importing = false
    @State private var photo: PhotosPickerItem?
    @State private var preview: URL?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if model.clips.isEmpty {
                        ContentUnavailableView("Nothing yet", systemImage: "doc.on.clipboard", description: Text("What you copy on a computer appears here and in the clipboard."))
                            .listRowBackground(Color.clear)
                    }
                    ForEach(model.clips) { clip in
                        ClipRow(clip: clip)
                            .contentShape(Rectangle())
                            .onTapGesture { open(clip) }
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) { model.delete(clip) } label: { Label("Delete", systemImage: "trash") }
                            }
                            .swipeActions(edge: .leading) {
                                Button { model.copy(clip) } label: { Label("Copy", systemImage: "doc.on.doc") }.tint(.accentColor)
                            }
                            .contextMenu {
                                Button { model.copy(clip) } label: { Label("Copy", systemImage: "doc.on.doc") }
                                if let url = Paths.url(of: clip) {
                                    ShareLink(item: url) { Label("Share", systemImage: "square.and.arrow.up") }
                                } else if let text = clip.text {
                                    ShareLink(item: text) { Label("Share", systemImage: "square.and.arrow.up") }
                                }
                                Button(role: .destructive) { model.delete(clip) } label: { Label("Delete", systemImage: "trash") }
                            }
                    }
                } header: {
                    StatusLine()
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(Backdrop())
            .navigationTitle("Tossling")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { showDevices = true } label: { Label("Devices", systemImage: "laptopcomputer.and.iphone") }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showSettings = true } label: { Label("Settings", systemImage: "gearshape") }
                }
            }
            .safeAreaInset(edge: .bottom) { SendBar(importing: $importing, photo: $photo) }
            .sheet(isPresented: $showDevices) { DevicesView() }
            .sheet(isPresented: $showSettings) { SettingsView() }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.item], allowsMultipleSelection: true) { result in
                guard case let .success(urls) = result else { return }
                Task { for url in urls { await model.sendFile(url) } }
            }
            .onChange(of: photo) { _, item in
                guard let item else { return }
                photo = nil
                Task {
                    guard let data = try? await item.loadTransferable(type: Data.self) else { return }
                    await model.sendImage(data, type: item.supportedContentTypes.first(where: { $0.conforms(to: .image) }) ?? .jpeg)
                }
            }
            .quickLookPreview($preview)
        }
    }

    private func open(_ clip: Clip) {
        if clip.kind == .text {
            model.copy(clip)
        } else {
            preview = Paths.url(of: clip)
        }
    }
}

struct StatusLine: View {

    @Environment(AppModel.self) private var model

    var body: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(color)
                .frame(width: 8, height: 8)
            Text(text)
                .textCase(nil)
        }
        .font(.footnote)
        .foregroundStyle(.secondary)
    }

    private var color: Color {
        switch model.connection {
        case .online: .green
        case .connecting: .orange
        case .offline: .gray
        }
    }

    private var text: String {
        switch model.connection {
        case .online: String(localized: "Connected to \(model.host)")
        case .connecting: String(localized: "Connecting to \(model.host)…")
        case .offline: String(localized: "Not connected")
        }
    }
}

struct SendBar: View {

    @Environment(AppModel.self) private var model
    @Binding var importing: Bool
    @Binding var photo: PhotosPickerItem?

    var body: some View {
        GlassGroup {
            HStack(spacing: 12) {
                PasteButton(supportedContentTypes: [.plainText, .url, .image]) { providers in
                    Task { await send(providers) }
                }
                .labelStyle(.titleAndIcon)
                .buttonBorderShape(.capsule)
                .disabled(model.busy)
                PhotosPicker(selection: $photo, matching: .images) {
                    Image(systemName: "photo")
                        .frame(width: 22, height: 22)
                }
                .glassButton()
                .buttonBorderShape(.circle)
                .accessibilityLabel(Text("Send a Photo"))
                Button { importing = true } label: {
                    Image(systemName: "doc")
                        .frame(width: 22, height: 22)
                }
                .glassButton()
                .buttonBorderShape(.circle)
                .accessibilityLabel(Text("Send a File"))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .overlay(alignment: .top) {
            if model.busy { ProgressView().offset(y: -18) }
        }
    }

    private func send(_ providers: [NSItemProvider]) async {
        for provider in providers {
            if provider.hasItemConformingToTypeIdentifier(UTType.image.identifier),
               let type = provider.registeredContentTypes.first(where: { $0.conforms(to: .image) }),
               let data = try? await provider.loadData(type) {
                await model.sendImage(data, type: type)
            } else if provider.canLoadObject(ofClass: URL.self), let url = try? await provider.loadURL() {
                await model.sendText(url.absoluteString)
            } else if let text = try? await provider.loadString() {
                await model.sendText(text)
            }
        }
    }
}

struct ClipRow: View {

    let clip: Clip

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            if clip.kind == .image, let url = Paths.url(of: clip), let image = UIImage(contentsOfFile: url.path) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .frame(width: 44, height: 44)
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            } else {
                Image(systemName: icon)
                    .font(.title3)
                    .foregroundStyle(.tint)
                    .frame(width: 44, height: 44)
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .lineLimit(3)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }

    private var icon: String {
        switch clip.kind {
        case .text: "text.alignleft"
        case .image: "photo"
        case .file: "doc"
        }
    }

    private var title: String {
        switch clip.kind {
        case .text: clip.text ?? ""
        case .image: String(localized: "Image, \(ByteCountFormatter.string(fromByteCount: clip.size, countStyle: .file))")
        case .file: "\(clip.name ?? "file"), \(ByteCountFormatter.string(fromByteCount: clip.size, countStyle: .file))"
        }
    }

    private var subtitle: String {
        let time = clip.date.formatted(.relative(presentation: .named))
        return clip.incoming ? String(localized: "From \(clip.device), \(time)") : String(localized: "Sent, \(time)")
    }
}

private extension NSItemProvider {

    func loadData(_ type: UTType) async throws -> Data {
        try await withCheckedThrowingContinuation { continuation in
            _ = loadDataRepresentation(for: type) { data, error in
                if let data { continuation.resume(returning: data) } else { continuation.resume(throwing: error ?? CocoaError(.fileReadUnknown)) }
            }
        }
    }

    func loadURL() async throws -> URL {
        try await withCheckedThrowingContinuation { continuation in
            _ = loadObject(ofClass: URL.self) { url, error in
                if let url { continuation.resume(returning: url) } else { continuation.resume(throwing: error ?? CocoaError(.fileReadUnknown)) }
            }
        }
    }

    func loadString() async throws -> String {
        try await withCheckedThrowingContinuation { continuation in
            _ = loadObject(ofClass: String.self) { text, error in
                if let text { continuation.resume(returning: text) } else { continuation.resume(throwing: error ?? CocoaError(.fileReadUnknown)) }
            }
        }
    }
}
