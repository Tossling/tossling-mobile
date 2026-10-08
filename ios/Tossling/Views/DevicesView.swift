import SwiftUI
import TosslingKit

struct DevicesView: View {

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Label {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(model.deviceName)
                            Text("This phone").font(.caption).foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "iphone").foregroundStyle(.tint)
                    }
                }
                Section {
                    if model.devices.isEmpty {
                        Text("No other devices yet. Copy something on a computer and it shows up here.")
                            .foregroundStyle(.secondary)
                    }
                    ForEach(model.devices, id: \.id) { member in
                        Label {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(member.title)
                                Text(status(member)).font(.caption).foregroundStyle(.secondary)
                            }
                        } icon: {
                            Image(systemName: model.isComputer(member) ? "laptopcomputer" : "iphone")
                                .foregroundStyle(model.isOnline(member) ? Color.green : Color.secondary)
                        }
                    }
                } header: {
                    Text("In the room")
                }
            }
            .navigationTitle("Devices")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .onAppear { model.probe() }
        }
        .presentationDetents([.medium, .large])
    }

    private func status(_ member: Member) -> String {
        if model.isOnline(member) { return String(localized: "Online") }
        let seen = Date(timeIntervalSince1970: TimeInterval(member.seen) / 1000)
        return String(localized: "Seen \(seen.formatted(.relative(presentation: .named)))")
    }
}
