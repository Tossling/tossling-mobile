import SwiftUI

struct GlassSurface: ViewModifier {

    var radius: CGFloat = 28
    var fill: Color = Palette.glassFill
    var shadow = true

    func body(content: Content) -> some View {
        content
            .background(fill, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).strokeBorder(Palette.glassStroke, lineWidth: 1))
            .shadow(color: shadow ? Palette.shadow : .clear, radius: 10, y: 5)
    }
}

extension View {

    func glassSurface(radius: CGFloat = 28, fill: Color = Palette.glassFill, shadow: Bool = true) -> some View {
        modifier(GlassSurface(radius: radius, fill: fill, shadow: shadow))
    }

    @ViewBuilder
    func liquidGlass(in shape: some Shape, tint: Color? = nil, interactive: Bool = false) -> some View {
        if #available(iOS 26, *) {
            glassEffect(interactive ? Glass.regular.tint(tint).interactive() : Glass.regular.tint(tint), in: shape)
        } else {
            background(.ultraThinMaterial, in: shape)
                .background(tint?.opacity(0.85) ?? Palette.glassFill, in: shape)
                .overlay(shape.stroke(Palette.glassStroke, lineWidth: 1))
                .shadow(color: Palette.shadow, radius: 10, y: 5)
        }
    }

    func pressable(scale: CGFloat = 0.95) -> some View {
        buttonStyle(PressStyle(scale: scale))
    }
}

struct PressStyle: ButtonStyle {

    var scale: CGFloat

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(Rectangle())
            .scaleEffect(configuration.isPressed ? scale : 1)
            .animation(.spring(response: 0.26, dampingFraction: 0.55), value: configuration.isPressed)
    }
}

struct GlassGroup<Content: View>: View {

    @ViewBuilder var content: Content

    var body: some View {
        VStack(spacing: 0) { content }
            .frame(maxWidth: .infinity)
            .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
            .glassSurface()
    }
}

struct Hairline: View {

    var inset: CGFloat = 0

    var body: some View {
        Rectangle().fill(Palette.hairline).frame(height: 1).padding(.leading, inset)
    }
}

enum CapsuleStyle {
    case primary, success, glass, danger, quiet, plain

    var background: Color {
        switch self {
        case .primary: Palette.accent
        case .success: Palette.success
        case .glass, .quiet: Palette.glassWeak
        case .danger: Palette.danger
        case .plain: Palette.glassFill
        }
    }

    var ink: Color {
        switch self {
        case .primary: Palette.onAccent
        case .success: Palette.onSuccess
        case .glass, .plain: Palette.ink
        case .danger: Palette.onDanger
        case .quiet: Palette.ink2
        }
    }
}

struct CapsuleButton: View {

    let title: String
    var style = CapsuleStyle.primary
    var icon: String?
    var ink: Color?
    var height: CGFloat = 56
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if let icon { Image(systemName: icon).font(.system(size: 17, weight: .semibold)) }
                Text(title).font(Fonts.button).lineLimit(1)
            }
            .foregroundStyle(ink ?? style.ink)
            .padding(.horizontal, 22)
            .frame(maxWidth: .infinity, minHeight: height)
            .background(style.background, in: Capsule())
            .overlay {
                if style == .plain { Capsule().strokeBorder(Palette.glassStroke, lineWidth: 1) }
            }
        }
        .pressable()
    }
}

struct GlassIconButton: View {

    let icon: String
    var label: String
    var size: CGFloat = 44
    var tint = Palette.ink
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(tint)
                .frame(width: size, height: size)
                .liquidGlass(in: Circle(), interactive: true)
        }
        .pressable(scale: 0.9)
        .accessibilityLabel(label)
    }
}

struct CircleBadge: View {

    let icon: String
    var tint = Palette.accentInk
    var fill = Palette.accentSoft
    var size: CGFloat = 56
    var iconSize: CGFloat = 24

    var body: some View {
        Image(systemName: icon)
            .font(.system(size: iconSize, weight: .medium))
            .foregroundStyle(tint)
            .frame(width: size, height: size)
            .background(fill, in: Circle())
            .animation(.easeInOut(duration: 0.32), value: tint)
    }
}

struct FilterChip: View {

    let label: String
    let selected: Bool
    var dot: Color?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let dot {
                    Circle().fill(dot).frame(width: 7, height: 7)
                        .padding(1.5)
                        .overlay(Circle().strokeBorder(.white.opacity(0.7), lineWidth: 1.5))
                }
                Text(label).font(Fonts.onest(15, .medium)).lineLimit(1)
            }
            .foregroundStyle(selected ? Palette.onAccent : Palette.ink)
            .padding(.horizontal, 16)
            .frame(height: 44)
            .background(selected ? Palette.accent : Palette.glassWeak, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay {
                if !selected { RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(Palette.glassStroke, lineWidth: 1) }
            }
        }
        .pressable(scale: 0.93)
    }
}

struct ProjectAvatar: View {

    let initials: String
    let color: Color
    var size: CGFloat = 36
    var image: UIImage?

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                ZStack {
                    color
                    Text(initials)
                        .font(Fonts.onest(fontSize, .bold))
                        .foregroundStyle(.white)
                }
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }

    private var fontSize: CGFloat {
        if size >= 48 { return 20 }
        if size <= 28 { return 12 }
        return initials.count == 2 ? 13 : 15
    }
}

struct CountBadge: View {

    let count: Int

    var body: some View {
        Text(count > 99 ? "99+" : "\(count)")
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(.white)
            .padding(.horizontal, 5)
            .frame(minWidth: 18, minHeight: 18)
            .background(Palette.badge, in: Capsule())
            .padding(2)
            .background(Palette.badgeRing, in: Capsule())
    }
}

struct SelfChip: View {

    let text: String

    var body: some View {
        Text(text)
            .font(Fonts.small)
            .foregroundStyle(Palette.ink2)
            .padding(.horizontal, 8)
            .padding(.vertical, 1)
            .background(Palette.glassWeak, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 9, style: .continuous).strokeBorder(Palette.hairline, lineWidth: 1))
    }
}

struct StatusLine: View {

    let online: Bool
    let text: String

    var body: some View {
        HStack(spacing: 6) {
            ZStack {
                Circle().strokeBorder(Palette.ink2, lineWidth: 1.5).opacity(online ? 0 : 1)
                Circle().fill(Palette.accent).scaleEffect(online ? 1 : 0.01)
            }
            .frame(width: 7, height: 7)
            Text(text)
                .contentTransition(.opacity)
        }
        .font(Fonts.onest(13, .medium))
        .foregroundStyle(online ? Palette.accentInk : Palette.ink2)
        .animation(.spring(response: 0.31, dampingFraction: 0.5), value: online)
    }
}

struct SectionLabel: View {

    let text: String
    var trailing: String?

    var body: some View {
        HStack(alignment: .lastTextBaseline) {
            Text(text).font(Fonts.onest(15, .semibold)).foregroundStyle(Palette.ink2)
            Spacer()
            if let trailing { Text(trailing).font(Fonts.onest(13, .medium)).foregroundStyle(Palette.ink2) }
        }
        .padding(.horizontal, 6)
        .padding(.top, 24)
        .padding(.bottom, 8)
    }
}

struct FootNote: View {

    let text: String

    var body: some View {
        Text(text)
            .font(Fonts.footnote)
            .foregroundStyle(Palette.ink2)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 6)
            .padding(.top, 10)
    }
}

struct ValueRow: View {

    let label: String
    let value: String
    var mono = false

    var body: some View {
        HStack(spacing: 12) {
            Text(label).font(Fonts.body).foregroundStyle(Palette.ink2)
            Spacer(minLength: 12)
            Text(value)
                .font(mono ? Fonts.mono(13) : Fonts.onest(15, .medium))
                .foregroundStyle(Palette.ink)
                .multilineTextAlignment(.trailing)
                .lineLimit(2)
                .textSelection(.enabled)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .frame(minHeight: 52)
    }
}

struct ToggleRow: View {

    let label: String
    var note: String?
    @Binding var isOn: Bool
    var enabled = true

    var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(Fonts.row).foregroundStyle(Palette.ink)
                if let note { Text(note).font(Fonts.footnote).foregroundStyle(Palette.ink2) }
            }
        }
        .tint(Palette.accent)
        .disabled(!enabled)
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(minHeight: 56)
    }
}

struct LinkRow: View {

    let label: String
    var value: String?
    var note: String?

    var body: some View {
        HStack(spacing: 14) {
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(Fonts.row).foregroundStyle(Palette.ink)
                if let note { Text(note).font(Fonts.footnote).foregroundStyle(Palette.ink2) }
            }
            Spacer()
            HStack(spacing: 8) {
                if let value { Text(value).font(Fonts.body).foregroundStyle(Palette.ink2) }
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Palette.ink2)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(minHeight: 56)
        .contentShape(Rectangle())
    }
}

struct AddRow: View {

    let label: String
    var badge: CGFloat = 44

    var body: some View {
        HStack(spacing: 14) {
            CircleBadge(icon: "plus", size: badge, iconSize: 16)
            Text(label).font(Fonts.row).foregroundStyle(Palette.accentInk)
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .frame(minHeight: 60)
        .contentShape(Rectangle())
    }
}

struct DeviceRow: View {

    let name: String
    let computer: Bool
    let online: Bool
    let status: String
    var selfLabel: String?
    var ownName: String?

    var body: some View {
        HStack(spacing: 14) {
            CircleBadge(icon: computer ? "laptopcomputer" : "iphone", tint: online ? Palette.onAccent : Palette.ink2, fill: online ? Palette.accent : Palette.glassWeak, size: 44, iconSize: 20)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 8) {
                    Text(name).font(Fonts.onest(17, .semibold)).foregroundStyle(Palette.ink).lineLimit(1)
                    if let selfLabel { SelfChip(text: selfLabel) }
                }
                if let ownName { Text(ownName).font(Fonts.onest(12)).foregroundStyle(Palette.ink2) }
                StatusLine(online: online, text: status)
            }
            Spacer(minLength: 8)
            Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Palette.ink2)
        }
        .padding(.leading, 16)
        .padding(.trailing, 12)
        .padding(.vertical, 12)
        .frame(minHeight: 76)
        .contentShape(Rectangle())
    }
}

struct GlassField: View {

    let label: String
    @Binding var text: String
    var placeholder = ""
    var mono = false
    var enabled = true

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(Fonts.footnote).foregroundStyle(Palette.ink2)
            TextField(placeholder, text: $text)
                .font(mono ? Fonts.mono(15) : Fonts.onest(17, .medium))
                .foregroundStyle(enabled ? Palette.ink : Palette.ink2)
                .tint(Palette.accent)
                .disabled(!enabled)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .frame(minHeight: 66, alignment: .leading)
    }
}

struct CommandBox: View {

    let command: String
    var copy: String?
    let copied: () -> Void

    var body: some View {
        HStack {
            Text(command).font(Fonts.mono(13)).foregroundStyle(Palette.ink).padding(.vertical, 6)
            Spacer()
            Button {
                UIPasteboard.general.string = copy ?? command
                copied()
            } label: {
                Image(systemName: "doc.on.doc").font(.system(size: 15)).foregroundStyle(Palette.ink2).frame(width: 44, height: 44)
            }
            .pressable(scale: 0.9)
            .accessibilityLabel(String(localized: "Copy"))
        }
        .padding(.leading, 14)
        .padding(.trailing, 4)
        .padding(.vertical, 4)
        .background(Palette.glassWeak, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Palette.hairline, lineWidth: 1))
    }
}

struct StepRow<Content: View>: View {

    let number: Int
    let text: String
    @ViewBuilder var content: Content

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            Text("\(number)")
                .font(Fonts.onest(14, .semibold))
                .foregroundStyle(Palette.accentInk)
                .frame(width: 28, height: 28)
                .background(Palette.accentSoft, in: Circle())
            VStack(alignment: .leading, spacing: 10) {
                Text(text).font(Fonts.row).foregroundStyle(Palette.ink).padding(.top, 3)
                content
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

extension StepRow where Content == EmptyView {
    init(number: Int, text: String) {
        self.init(number: number, text: text) { EmptyView() }
    }
}

struct PageTitle: View {

    let title: String
    var subtitle: String?
    var large = true

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(large ? Fonts.largeTitle : Fonts.title).foregroundStyle(Palette.ink).kerning(large ? -1 : -0.85)
            if let subtitle { Text(subtitle).font(Fonts.body).foregroundStyle(Palette.ink2) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 4)
        .padding(.top, 20)
    }
}

struct FloatingBar<Content: View>: View {

    @ViewBuilder var content: Content

    var body: some View {
        HStack(spacing: 8) { content }
            .padding(8)
            .liquidGlass(in: RoundedRectangle(cornerRadius: 36, style: .continuous))
            .padding(.horizontal, 16)
            .padding(.bottom, 8)
    }
}

struct ScrollPage<Content: View, Bar: View>: View {

    @ViewBuilder var content: Content
    @ViewBuilder var bar: Bar

    var body: some View {
        ScrollView {
            VStack(spacing: 0) { content }
                .padding(.horizontal, 16)
                .padding(.bottom, 140)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Backdrop())
        .safeAreaInset(edge: .bottom, spacing: 0) { bar }
        .toolbarBackground(.hidden, for: .navigationBar)
    }
}

extension ScrollPage where Bar == EmptyView {
    init(@ViewBuilder content: () -> Content) {
        self.init(content: content) { EmptyView() }
    }
}
