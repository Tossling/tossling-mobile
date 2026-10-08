import SwiftUI

enum Tab: Int {
    case clipboard, notifications
}

struct MainView: View {

    @Environment(AppModel.self) private var model
    @Binding var path: [Route]
    @AppStorage("tab") private var tab = Tab.clipboard
    @State private var picking: Payload?

    var body: some View {
        GeometryReader { geometry in
            let width = geometry.size.width
            ZStack {
                switch tab {
                case .clipboard:
                    ClipboardPage(path: $path).transition(slide(width: width, edge: .leading))
                case .notifications:
                    FeedPage(path: $path).transition(slide(width: width, edge: .trailing))
                }
            }
            .frame(width: width, height: geometry.size.height)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            TabBar(tab: $tab, unread: model.unread) { send() }
        }
        .sheet(isPresented: Binding(get: { picking != nil }, set: { if !$0 { picking = nil } })) {
            if let picking {
                PickSheet(outgoing: picking) { target in
                    self.picking = nil
                    Task { await model.send(picking, to: target) }
                }
            }
        }
    }

    private func slide(width: CGFloat, edge: Edge) -> AnyTransition {
        let away: CGFloat = edge == .leading ? -0.3 : 0.3
        return .asymmetric(
            insertion: .move(edge: edge),
            removal: .offset(x: away * width).combined(with: .opacity.animation(.easeOut(duration: 0.2)))
        )
    }

    private func send() {
        tab = .clipboard
        switch model.readClipboard() {
        case let .failure(refusal):
            model.say(refusal.localizedDescription, .info)
        case let .success(outgoing):
            if model.others.count <= 1 {
                Task { await model.send(outgoing) }
            } else {
                picking = outgoing
            }
        }
    }
}

struct TabBar: View {

    @Binding var tab: Tab
    let unread: Int
    let send: () -> Void
    @Namespace private var pill

    var body: some View {
        Group {
            if #available(iOS 26, *) {
                GlassEffectContainer(spacing: 10) { content }
            } else {
                content
            }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 4)
        .sensoryFeedback(.selection, trigger: tab)
    }

    private var content: some View {
        HStack(spacing: 10) {
            HStack(spacing: 0) {
                item(.clipboard, icon: "doc.on.clipboard", title: String(localized: "Clipboard"))
                item(.notifications, icon: "bell", title: String(localized: "Notifications"), badge: unread)
            }
            .padding(4)
            .frame(height: 64)
            .liquidGlass(in: Capsule(), tint: nil, interactive: true)
            Button(action: send) {
                HStack(spacing: 8) {
                    Image(systemName: "arrow.up").font(.system(size: 16, weight: .bold))
                    Text("To Computer").font(Fonts.button).lineLimit(1)
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 18)
                .frame(minWidth: 122, minHeight: 64)
                .liquidGlass(in: Capsule(), tint: Palette.accent, interactive: true)
            }
            .pressable(scale: 0.92)
        }
    }

    private func item(_ value: Tab, icon: String, title: String, badge: Int = 0) -> some View {
        Button {
            withAnimation(.spring(response: 0.28, dampingFraction: 1)) { tab = value }
        } label: {
            VStack(spacing: 2) {
                Image(systemName: tab == value ? "\(icon).fill" : icon)
                    .font(.system(size: 19, weight: .medium))
                    .frame(width: 22, height: 22)
                    .overlay(alignment: .topTrailing) {
                        if badge > 0 { CountBadge(count: badge).offset(x: 14, y: -9) }
                    }
                Text(title).font(Fonts.small).lineLimit(1)
            }
            .foregroundStyle(tab == value ? Palette.accentInk : Palette.ink)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background {
                if tab == value {
                    Capsule().fill(Palette.tabIndicator).matchedGeometryEffect(id: "pill", in: pill)
                }
            }
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(tab == value ? .isSelected : [])
    }
}

struct PickSheet: View {

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let outgoing: Payload
    let pick: (DeviceItem?) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Send where?").font(Fonts.title2).foregroundStyle(Palette.ink).padding(.horizontal, 6).padding(.top, 18)
            Text("On the clipboard: \"\(outgoing.preview)\"").font(Fonts.body).foregroundStyle(Palette.ink2).lineLimit(1)
                .padding(.horizontal, 6).padding(.top, 4).padding(.bottom, 16)
            VStack(spacing: 0) {
                row(icon: "laptopcomputer.and.iphone", title: String(localized: "All devices"), note: String(localized: "\(model.others.count) devices: computers and phones"), primary: true) { pick(nil) }
                ForEach(model.others) { device in
                    Hairline(inset: 68)
                    row(icon: device.computer ? "laptopcomputer" : "iphone", title: device.name,
                        note: device.online ? String(localized: "online") : String(localized: "\(When.seenAgo(device.seen)) · will get it when back"), primary: false) { pick(device) }
                }
            }
            .background(Palette.glassWeak, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(Palette.hairline, lineWidth: 1))
            Text("Phones and computers in the room receive it at the same time").font(Fonts.footnote).foregroundStyle(Palette.ink2).padding(.horizontal, 8).padding(.top, 10)
            CapsuleButton(title: String(localized: "Cancel"), style: .glass) { dismiss() }.padding(.top, 12)
        }
        .padding(16)
        .presentationDetents([.medium, .large])
        .presentationBackground(.thinMaterial)
    }

    private func row(icon: String, title: String, note: String, primary: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                CircleBadge(icon: icon, tint: primary ? Palette.onAccent : Palette.accentInk, fill: primary ? Palette.accent : Palette.accentSoft, size: 40, iconSize: 18)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(Fonts.onest(16, .semibold)).foregroundStyle(Palette.ink).lineLimit(1)
                    Text(note).font(Fonts.footnote).foregroundStyle(Palette.ink2).lineLimit(1)
                }
                Spacer()
                Image(systemName: "arrow.up").font(.system(size: 15, weight: .semibold)).foregroundStyle(Palette.accentInk)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .frame(minHeight: 64)
        }
        .pressable(scale: 0.97)
    }
}

extension View {

    func glassRow() -> some View {
        listRowBackground(Palette.glassFill)
            .listRowSeparatorTint(Palette.hairline)
    }

    func glassList() -> some View {
        listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(Backdrop())
            .environment(\.defaultMinListRowHeight, 44)
    }
}
