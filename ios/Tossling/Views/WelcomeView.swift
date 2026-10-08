import SwiftUI
import VisionKit

struct WelcomeView: View {

    @Environment(AppModel.self) private var model
    @State private var scanning = false
    @State private var working = false
    @State private var problem: String?

    var body: some View {
        @Bindable var model = model
        ScrollView {
            VStack(spacing: 24) {
                Image("Logo")
                    .resizable()
                    .frame(width: 112, height: 112)
                    .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
                    .shadow(color: .black.opacity(0.12), radius: 12, y: 6)
                    .padding(.top, 48)
                VStack(spacing: 8) {
                    Text("Tossling")
                        .font(.largeTitle.bold())
                    Text("One clipboard for your computers and this phone, through your own server.")
                        .font(.body)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
                VStack(alignment: .leading, spacing: 12) {
                    Text("On a computer with Tossling open Devices, then Connect a Phone, and scan the code it shows. On a Mac you can also run tossling pair.")
                        .font(.callout)
                    TextField("Name of this phone", text: $model.deviceName)
                        .textFieldStyle(.roundedBorder)
                        .textInputAutocapitalization(.words)
                }
                .padding(20)
                .glassCard()
                GlassGroup {
                    VStack(spacing: 12) {
                        Button {
                            scanning = true
                        } label: {
                            Label("Scan the QR Code", systemImage: "qrcode.viewfinder")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 6)
                        }
                        .prominentGlassButton()
                        .disabled(!scannerAvailable || working)
                        PasteButton(payloadType: String.self) { values in
                            guard let raw = values.first else { return }
                            Task { await pair(raw) }
                        }
                        .disabled(working)
                    }
                }
                if !scannerAvailable {
                    Text("The camera is not available here: copy the code text on the computer and paste it.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
                if working {
                    ProgressView()
                }
                if let problem {
                    Text(problem)
                        .font(.callout)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                }
            }
            .padding(.horizontal, 24)
            .frame(maxWidth: 560)
            .frame(maxWidth: .infinity)
        }
        .background(Backdrop())
        .sheet(isPresented: $scanning) {
            ScannerView { raw in
                scanning = false
                Task { await pair(raw) }
            }
            .ignoresSafeArea()
        }
    }

    private var scannerAvailable: Bool { DataScannerViewController.isSupported && DataScannerViewController.isAvailable }

    private func pair(_ raw: String) async {
        working = true
        problem = nil
        defer { working = false }
        do {
            try await model.pair(raw: raw)
        } catch {
            problem = error.localizedDescription
        }
    }
}

struct Backdrop: View {
    var body: some View {
        LinearGradient(colors: [Color(red: 0.82, green: 0.79, blue: 0.99).opacity(0.55), Color(red: 0.61, green: 0.83, blue: 0.94).opacity(0.45)], startPoint: .topLeading, endPoint: .bottomTrailing)
            .ignoresSafeArea()
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
