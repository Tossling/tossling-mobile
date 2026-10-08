import SwiftUI
import UIKit

extension Color {

    init(light: UInt32, _ lightAlpha: Double = 1, dark: UInt32, _ darkAlpha: Double = 1) {
        self.init(UIColor { traits in
            traits.userInterfaceStyle == .dark ? UIColor(hex: dark, alpha: darkAlpha) : UIColor(hex: light, alpha: lightAlpha)
        })
    }

    init(hex: UInt32, alpha: Double = 1) {
        self.init(UIColor(hex: hex, alpha: alpha))
    }
}

extension UIColor {
    convenience init(hex: UInt32, alpha: Double) {
        self.init(red: CGFloat((hex >> 16) & 0xff) / 255, green: CGFloat((hex >> 8) & 0xff) / 255, blue: CGFloat(hex & 0xff) / 255, alpha: alpha)
    }
}

enum Palette {
    static let background = Color(light: 0xEEF0F5, dark: 0x0E1016)
    static let blob1 = Color(light: 0xD0CAFD, dark: 0x3D3368)
    static let blob2 = Color(light: 0xFBCDAA, dark: 0x512E1A)
    static let blob3 = Color(light: 0xA9DEF0, dark: 0x00415C)
    static let ink = Color(light: 0x15171C, dark: 0xF2F3F7)
    static let ink2 = Color(light: 0x15171C, 0.62, dark: 0xF2F3F7, 0.66)
    static let hairline = Color(light: 0x15171C, 0.078, dark: 0xFFFFFF, 0.10)
    static let glassFill = Color(light: 0xFFFFFF, 0.55, dark: 0x2C303E, 0.50)
    static let glassStrong = Color(light: 0xFFFFFF, 0.70, dark: 0x383C4C, 0.66)
    static let glassWeak = Color(light: 0xFFFFFF, 0.40, dark: 0xFFFFFF, 0.078)
    static let glassStroke = Color(light: 0xFFFFFF, 0.80, dark: 0xFFFFFF, 0.14)
    static let shadow = Color(light: 0x28325A, 0.078, dark: 0x000000, 0.35)
    static let accent = Color(light: 0x3067B8, dark: 0x7AACF6)
    static let onAccent = Color(light: 0xFFFFFF, dark: 0x0B1530)
    static let accentInk = Color(light: 0x1D3F8A, dark: 0xA0C6FF)
    static let accentSoft = Color(light: 0x1D3F8A, 0.10, dark: 0x8CAAFF, 0.15)
    static let danger = Color(light: 0xC53637, dark: 0xF2716A)
    static let onDanger = Color(light: 0xFFFFFF, dark: 0x2A0805)
    static let dangerInk = Color(light: 0xA3261B, dark: 0xFF9E96)
    static let dangerSoft = Color(light: 0xC8321E, 0.10, dark: 0xFF6E5A, 0.14)
    static let success = Color(light: 0x2E9A57, dark: 0x5CCB88)
    static let onSuccess = Color(light: 0xFFFFFF, dark: 0x06210F)
    static let island = Color(light: 0x12141A, 0.84, dark: 0x000000, 0.82)
    static let badge = Color(light: 0xD02B31, dark: 0xF05653)
    static let badgeRing = Color(light: 0xFFFFFF, 0.90, dark: 0x282C3A, 0.90)
    static let urgentRow = Color(light: 0xC8321E, 0.059, dark: 0xFF6E5A, 0.078)
    static let importantSoft = Color(light: 0xBE7814, 0.14, dark: 0xFFBE5A, 0.16)
    static let importantInk = Color(light: 0x7A4A00, dark: 0xF8BF6C)
    static let codeInline = Color(light: 0x15171C, 0.051, dark: 0x000000, 0.28)
    static let tabContainer = Color(light: 0xFFFFFF, 0.45, dark: 0xFFFFFF, 0.06)
    static let tabIndicator = Color(light: 0x000000, 0.08, dark: 0xFFFFFF, 0.12)

    static let projects: [Color] = [0x794DB6, 0x007A7B, 0x9C5313, 0x0076A0, 0x287C42, 0xAD3A55].map { Color(hex: $0) }
}

enum Fonts {
    static func onest(_ size: CGFloat, _ weight: Font.Weight = .regular, relativeTo style: Font.TextStyle = .body) -> Font {
        let name = switch weight {
        case .bold, .heavy, .black: "Onest-Bold"
        case .semibold: "Onest-SemiBold"
        case .medium: "Onest-Medium"
        default: "Onest-Regular"
        }
        return .custom(name, size: size, relativeTo: style)
    }

    static func mono(_ size: CGFloat) -> Font { .custom("JetBrainsMono-Medium", size: size, relativeTo: .body) }

    static let largeTitle = onest(40, .bold, relativeTo: .largeTitle)
    static let title = onest(34, .bold, relativeTo: .title)
    static let title2 = onest(22, .bold, relativeTo: .title2)
    static let headline = onest(19, .semibold, relativeTo: .headline)
    static let lead = onest(19, .medium, relativeTo: .body)
    static let button = onest(16, .semibold, relativeTo: .body)
    static let row = onest(16, .medium, relativeTo: .body)
    static let body = onest(15, relativeTo: .body)
    static let hint = onest(14, relativeTo: .callout)
    static let footnote = onest(13, relativeTo: .footnote)
    static let small = onest(12, .semibold, relativeTo: .caption)
}

struct Backdrop: View {

    var body: some View {
        GeometryReader { geometry in
            let size = geometry.size
            ZStack {
                Palette.background
                blob(Palette.blob1, side: 420, soft: 60).position(x: 70, y: 150)
                blob(Palette.blob2, side: 380, soft: 70).position(x: size.width - 170, y: 450)
                blob(Palette.blob3, side: 420, soft: 70).position(x: 120, y: size.height - 230)
            }
        }
        .ignoresSafeArea()
    }

    private func blob(_ color: Color, side: CGFloat, soft: CGFloat) -> some View {
        let radius = side / 2 + soft
        let core = (side / 2 - soft) / radius
        let edge = (side / 2) / radius
        return RadialGradient(
            stops: [.init(color: color, location: 0), .init(color: color, location: core), .init(color: color.opacity(0.5), location: edge), .init(color: color.opacity(0), location: 1)],
            center: .center, startRadius: 0, endRadius: radius
        )
        .frame(width: radius * 2, height: radius * 2)
    }
}

extension Project {
    var tint: Color { Palette.projects[colorIndex] }
}
