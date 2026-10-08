import QuickLook
import SwiftUI

struct ClipboardPage: View {

    @Environment(AppModel.self) private var model
    @Binding var path: [Route]
    @State private var query = ""
    @State private var preview: URL?
    @State private var confirmLeave = false

    var body: some View {
        List {
            Section {
                Text("Room · \(model.devices.count) devices")
                    .font(Fonts.body)
                    .foregroundStyle(Palette.ink2)
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                    .listRowInsets(EdgeInsets(top: 0, leading: 20, bottom: 0, trailing: 20))
            }
            Section {
                ForEach(model.devices) { device in
                    Button { path.append(.device(device.id)) } label: {
                        DeviceRow(
                            name: device.name,
                            computer: device.computer,
                            online: device.online,
                            status: device.online ? String(localized: "online") : When.seenAgo(device.seen),
                            selfLabel: device.isSelf ? String(localized: "this device") : (device.isOwner ? String(localized: "creator") : nil),
                            ownName: device.hasAlias ? device.ownName : nil
                        )
                    }
                    .buttonStyle(.plain)
                    .glassRow()
                    .listRowInsets(EdgeInsets())
                    .alignmentGuide(.listRowSeparatorLeading) { _ in 74 }
                }
                Button { path.append(.addComputer) } label: { AddRow(label: String(localized: "Add a computer")) }
                    .buttonStyle(.plain)
                    .glassRow()
                    .listRowInsets(EdgeInsets())
                    .alignmentGuide(.listRowSeparatorLeading) { _ in 74 }
            } header: {
                HStack {
                    Text("Devices").font(Fonts.onest(15, .semibold)).foregroundStyle(Palette.ink2)
                    Spacer()
                    Button(String(localized: "Leave the room")) { confirmLeave = true }
                        .font(Fonts.onest(13, .semibold))
                        .foregroundStyle(Palette.dangerInk)
                }
                .textCase(nil)
            }
            Section {
                if model.clips.isEmpty {
                    EmptyState(icon: "doc.on.clipboard", title: String(localized: "Nothing yet"), text: String(localized: "Copy text or an image on the computer and it shows up here in a second."))
                        .listRowBackground(Palette.glassFill)
                } else if visible.isEmpty {
                    Text("Nothing found").font(Fonts.body).foregroundStyle(Palette.ink2).listRowBackground(Color.clear)
                }
                ForEach(visible) { clip in
                    Button { open(clip) } label: { ClipRow(clip: clip, toAllShown: model.others.count > 1) }
                        .buttonStyle(.plain)
                        .glassRow()
                        .alignmentGuide(.listRowSeparatorLeading) { _ in 56 }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) { withAnimation { model.delete(clip) } } label: { Label("Delete", systemImage: "trash") }
                            Button { withAnimation { model.togglePin(clip) } } label: {
                                Label(clip.isPinned ? String(localized: "Unpin") : String(localized: "Pin"), systemImage: clip.isPinned ? "pin.slash" : "pin")
                            }
                            .tint(Palette.accent)
                        }
                        .contextMenu {
                            Button { model.copy(clip) } label: { Label("Copy", systemImage: "doc.on.doc") }
                            if let url = Paths.url(of: clip) {
                                ShareLink(item: url) { Label("Share", systemImage: "square.and.arrow.up") }
                            } else if let text = clip.text {
                                ShareLink(item: text) { Label("Share", systemImage: "square.and.arrow.up") }
                            }
                            Button { model.togglePin(clip) } label: { Label(clip.isPinned ? String(localized: "Unpin") : String(localized: "Pin"), systemImage: "pin") }
                            Button(role: .destructive) { model.delete(clip) } label: { Label("Delete", systemImage: "trash") }
                        }
                }
            } header: {
                HStack(alignment: .lastTextBaseline) {
                    Text("Recent").font(Fonts.title2).foregroundStyle(Palette.ink)
                    Spacer()
                    if !model.clips.isEmpty { Text("\(model.clips.count)").font(Fonts.footnote).foregroundStyle(Palette.ink2) }
                }
                .textCase(nil)
                .padding(.top, 8)
            }
        }
        .glassList()
        .animation(.spring(response: 0.31, dampingFraction: 0.8), value: model.clips)
        .navigationTitle(String(localized: "Clipboard"))
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { path.append(.settings) } label: { Image(systemName: "slider.horizontal.3") }
                    .accessibilityLabel(String(localized: "Settings"))
            }
        }
        .modifier(Search(enabled: model.clips.count > 6 || !query.isEmpty, query: $query))
        .refreshable {
            let ok = await model.refresh()
            model.say(ok ? String(localized: "Updated") : String(localized: "No connection to the server"), ok ? .done : .error)
        }
        .quickLookPreview($preview)
        .onAppear { model.probe() }
        .confirmationDialog(String(localized: "Leave the room?"), isPresented: $confirmLeave, titleVisibility: .visible) {
            Button(String(localized: "Leave and remove the key"), role: .destructive) { Task { await leave() } }
        } message: {
            Text("The phone removes the room key and stops sharing the clipboard with its devices. To come back, scan the QR code on a computer.")
        }
    }

    private var visible: [Clip] {
        let matching = model.clips.filter { $0.matches(query) }
        return matching.filter(\.isPinned) + matching.filter { !$0.isPinned }
    }

    private func open(_ clip: Clip) {
        switch clip.kind {
        case .text:
            model.copy(clip)
        case .image, .file:
            if let url = Paths.url(of: clip), FileManager.default.fileExists(atPath: url.path) {
                preview = url
            } else {
                model.say(clip.kind == .image ? String(localized: "The image is gone") : String(localized: "The file is gone"), .info)
            }
        }
    }

    private func leave() async {
        model.say(String(localized: "Leaving the room"), .busy)
        do {
            try await model.leave()
            model.say(String(localized: "The phone left the room"), .done)
        } catch {
            model.say(String(localized: "Could not leave"), .error)
        }
    }
}

struct EmptyState: View {

    let icon: String
    let title: String
    let text: String

    var body: some View {
        VStack(spacing: 8) {
            CircleBadge(icon: icon, size: 56, iconSize: 22).padding(.bottom, 8)
            Text(title).font(Fonts.headline).foregroundStyle(Palette.ink)
            Text(text).font(Fonts.body).foregroundStyle(Palette.ink2).multilineTextAlignment(.center).frame(maxWidth: 290)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 28)
        .padding(.vertical, 36)
    }
}

struct ClipRow: View {

    let clip: Clip
    let toAllShown: Bool

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: clip.incoming ? "arrow.down" : "arrow.up")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Palette.accentInk)
                .frame(width: 28, height: 28)
                .background(Palette.accentSoft, in: Circle())
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 6) {
                    Text(meta).lineLimit(1)
                    Spacer(minLength: 4)
                    if clip.isPinned { Image(systemName: "pin.fill").font(.system(size: 11)).foregroundStyle(Palette.accentInk) }
                    Text(When.relative(clip.date))
                }
                .font(Fonts.footnote)
                .foregroundStyle(Palette.ink2)
                content
            }
        }
        .padding(.vertical, 6)
        .contentShape(Rectangle())
    }

    private var meta: String {
        if clip.incoming { return clip.device.isEmpty ? String(localized: "From the computer") : String(localized: "From \(clip.device)") }
        if clip.toAll, toAllShown { return String(localized: "To all devices") }
        return clip.device.isEmpty ? String(localized: "To the computer") : String(localized: "To \(clip.device)")
    }

    @ViewBuilder
    private var content: some View {
        switch clip.kind {
        case .text:
            Text(clip.text ?? "").font(Fonts.body).foregroundStyle(Palette.ink).lineLimit(3)
        case .image:
            Thumbnail(url: Paths.url(of: clip), size: clip.size)
        case .file:
            HStack(spacing: 10) {
                Image(systemName: "doc").font(.system(size: 19)).foregroundStyle(Palette.accentInk)
                VStack(alignment: .leading, spacing: 1) {
                    Text(clip.name ?? "file").font(Fonts.onest(15, .medium)).foregroundStyle(Palette.ink).lineLimit(1)
                    if clip.size > 0 { Text(ByteCountFormatter.string(fromByteCount: clip.size, countStyle: .file)).font(Fonts.footnote).foregroundStyle(Palette.ink2) }
                }
                Spacer()
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(Palette.glassWeak, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Palette.hairline, lineWidth: 1))
        }
    }
}

struct Thumbnail: View {

    let url: URL?
    let size: Int64

    var body: some View {
        let image = url.flatMap { UIImage(contentsOfFile: $0.path) }
        let aspect = image.map { $0.size.width / max($0.size.height, 1) } ?? 1.6
        Color.clear
            .frame(maxWidth: .infinity)
            .frame(height: min(max(260 / aspect, 96), 240))
            .overlay {
                if let image { Image(uiImage: image).resizable().scaledToFill() }
            }
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .background(Palette.glassWeak, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Palette.hairline, lineWidth: 1))
            .overlay(alignment: .bottomLeading) {
                Text("\((url?.pathExtension ?? "").uppercased()) · \(ByteCountFormatter.string(fromByteCount: size, countStyle: .file))")
                    .font(Fonts.mono(11))
                    .foregroundStyle(Palette.ink)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Palette.glassStrong, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                    .padding(8)
            }
    }
}

struct Search: ViewModifier {

    let enabled: Bool
    @Binding var query: String

    func body(content: Content) -> some View {
        if enabled {
            content.searchable(text: $query, placement: .navigationBarDrawer(displayMode: .automatic), prompt: Text("Text, file name or device"))
        } else {
            content
        }
    }
}
