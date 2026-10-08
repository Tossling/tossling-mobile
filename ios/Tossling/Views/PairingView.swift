import SwiftUI
import TosslingKit
import VisionKit

struct WelcomeView: View {

    @Environment(AppModel.self) private var model
    @Binding var path: [Route]
    @State private var restoring = false
    @State private var problem: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Image("Logo")
                    .resizable()
                    .frame(width: 112, height: 112)
                    .clipShape(RoundedRectangle(cornerRadius: 34, style: .continuous))
                    .shadow(color: Palette.shadow.opacity(3), radius: 18, y: 9)
                Text("Tossling").font(Fonts.largeTitle).kerning(-1).foregroundStyle(Palette.ink).padding(.top, 26)
                Text("One clipboard with your computer: copy there, paste here.")
                    .font(Fonts.lead).foregroundStyle(Palette.ink).multilineTextAlignment(.center).frame(maxWidth: 320).padding(.top, 14)
                Text("Through your own server, encrypted on the devices.")
                    .font(Fonts.body).foregroundStyle(Palette.ink2).multilineTextAlignment(.center).frame(maxWidth: 300).padding(.top, 14)
                if let saved = model.savedRoom {
                    VStack(spacing: 6) {
                        Text("This phone was in a room").font(Fonts.footnote).foregroundStyle(Palette.ink2)
                        Text("Room on \(URL(string: saved.server)?.host ?? saved.server)").font(Fonts.onest(19, .semibold)).foregroundStyle(Palette.ink)
                        Text(problem ?? String(localized: "The key stayed on this phone after Tossling was removed. Return as the same device, no QR code needed."))
                            .font(Fonts.body)
                            .foregroundStyle(problem == nil ? Palette.ink2 : Palette.dangerInk)
                            .multilineTextAlignment(.center)
                            .padding(.top, 4)
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 18)
                    .frame(maxWidth: 360)
                    .glassSurface()
                    .padding(.top, 28)
                    CapsuleButton(title: String(localized: "Pair with another computer"), style: .plain) { path.append(.pairing) }
                        .frame(maxWidth: 360)
                        .padding(.top, 6)
                }
            }
            .padding(.horizontal, 36)
            .padding(.top, 80)
            .padding(.bottom, 140)
            .frame(maxWidth: .infinity)
        }
        .background(Backdrop())
        .safeAreaInset(edge: .bottom, spacing: 0) {
            FloatingBar {
                if model.savedRoom != nil {
                    CapsuleButton(title: String(localized: "Return to the room"), icon: "laptopcomputer") { Task { await restore() } }
                        .disabled(restoring)
                } else {
                    CapsuleButton(title: String(localized: "Pair with a computer"), icon: "laptopcomputer") { path.append(.pairing) }
                }
            }
        }
        .toolbar(.hidden, for: .navigationBar)
    }

    private func restore() async {
        restoring = true
        defer { restoring = false }
        do {
            try await model.restore()
        } catch {
            problem = error.localizedDescription
        }
    }
}

struct PairingView: View {

    enum Stage: Equatable {
        case idle, connecting(String), failed(String, String), paired(String, String)
    }

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Binding var path: [Route]
    @State private var stage = Stage.idle
    @State private var scanning = false
    @State private var switching: PairedRoom?
    @State private var job: Task<Void, Never>?

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                switch stage {
                case .idle, .failed:
                    PageTitle(title: String(localized: "Pairing"), subtitle: String(localized: "About a minute, only once"), large: false)
                    Spacer().frame(height: 24)
                    if case let .failed(title, text) = stage {
                        VStack(spacing: 10) {
                            CircleBadge(icon: "exclamationmark.circle", tint: Palette.dangerInk, fill: Palette.dangerSoft).padding(.bottom, 6)
                            Text(title).font(Fonts.headline).foregroundStyle(Palette.ink)
                            Text(text).font(Fonts.body).foregroundStyle(Palette.ink2).multilineTextAlignment(.center).frame(maxWidth: 300)
                        }
                        .padding(.horizontal, 22)
                        .padding(.top, 28)
                        .padding(.bottom, 24)
                        .frame(maxWidth: .infinity)
                        .glassSurface()
                        .transition(.scale(scale: 0.9).combined(with: .opacity))
                    } else {
                        GlassGroup {
                            StepRow(number: 1, text: String(localized: "On a computer with Tossling open Devices, then Connect a Phone in its menu. On a Mac you can also run")) {
                                CommandBox(command: "tossling pair") { model.say(String(localized: "Copied"), .done) }
                            }
                            Hairline(inset: 58)
                            StepRow(number: 2, text: String(localized: "The computer shows a QR code"))
                            Hairline(inset: 58)
                            StepRow(number: 3, text: String(localized: "Scan it with Tossling"))
                        }
                        .transition(.scale(scale: 0.9).combined(with: .opacity))
                    }
                case let .connecting(server):
                    TossScene(landed: false, title: String(localized: "Pairing…"), text: String(localized: "Checking the token and exchanging keys"), pill: server)
                case let .paired(computer, server):
                    TossScene(landed: true, title: String(localized: "Paired"), text: String(localized: "\(computer) and this phone now share one clipboard"), pill: String(localized: "\(server) · key saved"))
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 140)
            .animation(.spring(response: 0.28, dampingFraction: 0.6), value: stage)
        }
        .background(Backdrop())
        .safeAreaInset(edge: .bottom, spacing: 0) {
            FloatingBar {
                switch stage {
                case .idle:
                    if scannerAvailable {
                        CapsuleButton(title: String(localized: "Scan QR"), icon: "qrcode.viewfinder") { scanning = true }
                    } else {
                        pasteButton
                    }
                case .connecting:
                    CapsuleButton(title: String(localized: "Cancel"), style: .glass) { job?.cancel(); stage = .idle }
                case .failed:
                    CapsuleButton(title: String(localized: "Cancel"), style: .glass) { dismiss() }
                    if scannerAvailable {
                        CapsuleButton(title: String(localized: "Scan again"), icon: "qrcode.viewfinder") { scanning = true }
                    } else {
                        pasteButton
                    }
                case .paired:
                    CapsuleButton(title: String(localized: "Done"), style: .success, icon: "checkmark") {
                        path = []
                        model.finishPairing()
                    }
                }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(stage != .idle && !isFailed)
        .sheet(isPresented: $scanning) {
            ScannerView { raw in
                scanning = false
                scanned(raw)
            }
            .ignoresSafeArea()
        }
        .confirmationDialog(String(localized: "Move to the room of \(switching?.computer ?? "")?"), isPresented: Binding(get: { switching != nil }, set: { if !$0 { switching = nil } }), titleVisibility: .visible) {
            Button(String(localized: "Move"), role: .destructive) {
                if let target = switching { connect(target) }
                switching = nil
            }
        } message: {
            Text("The phone is in one room at a time: it will leave the current one and stop sharing the clipboard with it.")
        }
    }

    private var isFailed: Bool { if case .failed = stage { true } else { false } }

    private var scannerAvailable: Bool { DataScannerViewController.isSupported && DataScannerViewController.isAvailable }

    private var pasteButton: some View {
        PasteButton(payloadType: String.self) { values in
            if let raw = values.first { Task { @MainActor in scanned(raw) } }
        }
        .labelStyle(.titleAndIcon)
        .buttonBorderShape(.capsule)
        .controlSize(.large)
        .frame(maxWidth: .infinity, minHeight: 56)
    }

    private func scanned(_ raw: String) {
        do {
            let target = try model.pairingTarget(raw: raw)
            if model.isPaired || !model.devices.isEmpty {
                switching = target
            } else {
                connect(target)
            }
        } catch let error as PairingError {
            stage = .failed(error.title, error.localizedDescription)
        } catch {
            stage = .failed(String(localized: "Something went wrong"), error.localizedDescription)
        }
    }

    private func connect(_ target: PairedRoom) {
        let host = URL(string: target.config.server)?.host ?? target.config.server
        stage = .connecting(host)
        job = Task {
            do {
                try await model.pair(target)
                stage = .paired(target.computer.isEmpty ? String(localized: "The computer") : target.computer, host)
            } catch let error as PairingError {
                stage = .failed(error.title, error.localizedDescription)
            } catch {
                stage = .failed(String(localized: "No connection to the server"), error.localizedDescription)
            }
        }
    }
}

struct TossScene: View {

    let landed: Bool
    let title: String
    let text: String
    let pill: String
    @State private var toss: CGFloat = 0
    @State private var done: CGFloat = 0

    var body: some View {
        VStack(spacing: 0) {
            ZStack(alignment: .topLeading) {
                Path { path in
                    path.move(to: CGPoint(x: 84, y: 84))
                    path.addQuadCurve(to: CGPoint(x: 196, y: 84), control: CGPoint(x: 140, y: 10))
                }
                .stroke(done > 0.5 ? Palette.success.opacity(0.8) : Palette.accentInk.opacity(0.35), style: StrokeStyle(lineWidth: 2.5, lineCap: .round, dash: [0.1, 9]))
                device("iphone").offset(x: 0, y: 50)
                device("laptopcomputer").offset(x: 200, y: 50).scaleEffect(1 + 0.08 * sin(.pi * done))
                Circle().fill(Palette.accent).padding(5).background(Palette.accentSoft, in: Circle())
                    .frame(width: 26, height: 26)
                    .position(point(toss))
                    .opacity(1 - done)
                Image(systemName: "checkmark").font(.system(size: 17, weight: .bold)).foregroundStyle(Palette.onSuccess)
                    .frame(width: 38, height: 38).background(Palette.success, in: Circle())
                    .position(x: 140, y: 47)
                    .scaleEffect(done)
                    .opacity(done)
            }
            .frame(width: 280, height: 150)
            Text(title).font(Fonts.title).foregroundStyle(Palette.ink).padding(.top, 30).contentTransition(.opacity)
            Text(text).font(Fonts.onest(16)).foregroundStyle(Palette.ink2).multilineTextAlignment(.center).frame(maxWidth: 300).padding(.top, 12)
            Text(pill).font(Fonts.mono(13)).foregroundStyle(Palette.ink2)
                .padding(.horizontal, 14).padding(.vertical, 8)
                .background(Palette.glassWeak, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Palette.glassStroke, lineWidth: 1))
                .padding(.top, 18)
        }
        .padding(.top, 80)
        .frame(maxWidth: .infinity)
        .onAppear { animate() }
        .onChange(of: landed) { _, _ in animate() }
    }

    private func device(_ icon: String) -> some View {
        Image(systemName: icon).font(.system(size: 28)).foregroundStyle(Palette.ink)
            .frame(width: 80, height: 80)
            .liquidGlass(in: Circle())
    }

    private func point(_ t: CGFloat) -> CGPoint {
        let a = CGPoint(x: 84, y: 84), c = CGPoint(x: 140, y: 10), b = CGPoint(x: 196, y: 84)
        let u = 1 - t
        return CGPoint(x: u * u * a.x + 2 * u * t * c.x + t * t * b.x, y: u * u * a.y + 2 * u * t * c.y + t * t * b.y)
    }

    private func animate() {
        if landed {
            withAnimation(.easeOut(duration: 0.35)) { toss = 1 }
            withAnimation(.spring(response: 0.31, dampingFraction: 0.55).delay(0.3)) { done = 1 }
        } else {
            toss = 0
            withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.9).repeatForever(autoreverses: true)) { toss = 1 }
        }
    }
}

struct ScannerView: UIViewControllerRepresentable {

    let found: (String) -> Void

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let scanner = DataScannerViewController(recognizedDataTypes: [.barcode(symbologies: [.qr])], qualityLevel: .balanced, isHighlightingEnabled: true)
        scanner.delegate = context.coordinator
        try? scanner.startScanning()
        return scanner
    }

    func updateUIViewController(_ controller: DataScannerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(found: found) }

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {

        let found: (String) -> Void
        private var done = false

        init(found: @escaping (String) -> Void) {
            self.found = found
        }

        func dataScanner(_ scanner: DataScannerViewController, didAdd items: [RecognizedItem], allItems: [RecognizedItem]) {
            for item in items {
                if case let .barcode(code) = item, let payload = code.payloadStringValue, !done {
                    done = true
                    scanner.stopScanning()
                    found(payload)
                    return
                }
            }
        }
    }
}
