import SwiftUI

struct IslandMessage: Equatable {

    enum Kind {
        case busy, done, error, device, info

        var duration: Double {
            switch self {
            case .info, .done: 1.5
            case .error, .device: 3
            case .busy: 20
            }
        }
    }

    let id = UUID()
    let text: String
    let kind: Kind

    static func == (lhs: Self, rhs: Self) -> Bool { lhs.id == rhs.id }
}

struct IslandView: View {

    let message: IslandMessage

    var body: some View {
        HStack(spacing: 8) {
            switch message.kind {
            case .busy: ProgressView().controlSize(.small).tint(.white)
            case .done: Image(systemName: "checkmark").font(.system(size: 13, weight: .bold)).foregroundStyle(Color(hex: 0x96C0FE))
            case .error: Image(systemName: "exclamationmark.circle").font(.system(size: 15, weight: .semibold)).foregroundStyle(Color(hex: 0xFF9E96))
            case .device: Image(systemName: "laptopcomputer").font(.system(size: 15, weight: .semibold)).foregroundStyle(.white)
            case .info: Circle().fill(.white.opacity(0.7)).frame(width: 8, height: 8)
            }
            Text(message.text)
                .font(Fonts.onest(14, .medium))
                .foregroundStyle(.white)
                .lineLimit(2)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 20)
        .frame(minWidth: 180, minHeight: 44)
        .background(Palette.island, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding(.horizontal, 24)
    }
}
