import SwiftUI

struct SettingsView: View {

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var confirmLeave = false
    @State private var name = ""

    var body: some View {
        @Bindable var model = model
        NavigationStack {
            Form {
                Section {
                    TextField("Name of this phone", text: $name)
                        .onSubmit { save() }
                } header: {
                    Text("Name")
                } footer: {
                    Text("The other devices of the room see this name.")
                }
                Section {
                    Toggle("Put received items into the clipboard", isOn: $model.autoCopy)
                } footer: {
                    Text("While Tossling is open, text and images from a computer go straight into the clipboard.")
                }
                Section {
                    LabeledContent("Server", value: model.host)
                    Button("Leave the Room", role: .destructive) { confirmLeave = true }
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        save()
                        dismiss()
                    }
                }
            }
            .confirmationDialog("Leave the room?", isPresented: $confirmLeave, titleVisibility: .visible) {
                Button("Leave", role: .destructive) {
                    Task {
                        await model.leave()
                        dismiss()
                    }
                }
            } message: {
                Text("To join again, scan the code on a computer.")
            }
            .onAppear { name = model.deviceName }
        }
    }

    private func save() {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty, trimmed != model.deviceName { model.deviceName = trimmed }
    }
}
