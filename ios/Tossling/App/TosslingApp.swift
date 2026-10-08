import SwiftUI

@main
struct TosslingApp: App {

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

struct RootView: View {

    @Environment(AppModel.self) private var model

    var body: some View {
        Group {
            if model.isPaired {
                HomeView()
            } else {
                WelcomeView()
            }
        }
        .overlay(alignment: .top) { NoticeView() }
        .animation(.default, value: model.isPaired)
    }
}

struct NoticeView: View {

    @Environment(AppModel.self) private var model

    var body: some View {
        if let notice = model.notice {
            Text(notice)
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .glassCapsule()
                .padding(.horizontal, 24)
                .padding(.top, 8)
                .transition(.move(edge: .top).combined(with: .opacity))
                .task(id: notice) {
                    try? await Task.sleep(for: .seconds(3))
                    withAnimation { model.notice = nil }
                }
                .onTapGesture { withAnimation { model.notice = nil } }
        }
    }
}
