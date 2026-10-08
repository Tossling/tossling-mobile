import SwiftUI

@main
struct TosslingApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var model = AppModel()
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .onChange(of: phase) { _, phase in
                    switch phase {
                    case .active: model.becameActive()
                    case .background: model.wentToBackground()
                    default: break
                    }
                }
        }
    }
}

enum Route: Hashable {
    case settings, devices, device(String), addComputer, projects, project(String?), alert(String), pairing
}

struct RootView: View {

    @Environment(AppModel.self) private var model
    @State private var path: [Route] = []

    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if model.isPaired {
                    MainView(path: $path)
                } else {
                    WelcomeView(path: $path)
                }
            }
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .settings: SettingsView(path: $path)
                case .devices: DevicesView(path: $path)
                case let .device(id): DeviceView(id: id, path: $path)
                case .addComputer: AddComputerView(path: $path)
                case .projects: ProjectsView(path: $path)
                case let .project(topic): ProjectView(topic: topic)
                case let .alert(id): AlertView(id: id)
                case .pairing: PairingView(path: $path)
                }
            }
        }
        .tint(Palette.accent)
        .overlay(alignment: .top) {
            if let message = model.island {
                IslandView(message: message)
                    .padding(.top, 8)
                    .transition(.scale(scale: 0.6, anchor: .top).combined(with: .opacity))
                    .id(message.id)
            }
        }
        .onChange(of: model.isPaired) { _, _ in path = [] }
        .onAppear { openPendingAlert() }
        .onReceive(NotificationCenter.default.publisher(for: .openAlert)) { _ in openPendingAlert() }
    }

    private func openPendingAlert() {
        guard let id = Push.shared.openedAlert, model.isPaired else { return }
        Push.shared.openedAlert = nil
        guard model.openAlert(id) else { return }
        UserDefaults.standard.set(Tab.notifications.rawValue, forKey: "tab")
        path = [.alert(id)]
    }
}
