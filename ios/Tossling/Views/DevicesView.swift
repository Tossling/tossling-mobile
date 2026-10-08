import SwiftUI

struct DevicesView: View {

    @Environment(AppModel.self) private var model
    @Binding var path: [Route]

    var body: some View {
        ScrollPage {
            PageTitle(title: String(localized: "Devices"), subtitle: String(localized: "One room: whatever is copied on any of them, the others see"))
            GlassGroup {
                ForEach(model.devices) { device in
                    Button { path.append(.device(device.id)) } label: {
                        DeviceRow(name: device.name, computer: device.computer, online: device.online,
                                  status: device.online ? String(localized: "online") : When.seenAgo(device.seen),
                                  selfLabel: device.isSelf ? String(localized: "this device") : nil,
                                  ownName: device.hasAlias ? device.ownName : nil)
                    }
                    .pressable(scale: 0.98)
                    Hairline(inset: 74)
                }
                Button { path.append(.addComputer) } label: { AddRow(label: String(localized: "Add a computer")) }
                    .pressable(scale: 0.98)
            }
            .padding(.top, 24)
            FootNote(text: String(localized: "A device that is offline gets the clipboard when it is back. Another phone joins by scanning the QR code of a computer in the room: Devices, then Connect a Phone in the Tossling menu, or tossling pair on a Mac."))
        }
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { model.probe() }
    }
}

struct DeviceView: View {

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let id: String
    @Binding var path: [Route]
    @State private var name = ""
    @State private var confirm = false
    @FocusState private var editing: Bool

    var body: some View {
        if let device = model.devices.first(where: { $0.id == id }) {
            ScrollPage {
                VStack(spacing: 10) {
                    CircleBadge(icon: device.computer ? "laptopcomputer" : "iphone", tint: device.online ? Palette.onAccent : Palette.ink2,
                                fill: device.online ? Palette.accent : Palette.glassWeak, size: 80, iconSize: 34)
                    Text(device.name).font(Fonts.title).foregroundStyle(Palette.ink).multilineTextAlignment(.center)
                    if device.hasAlias { Text(device.ownName).font(Fonts.hint).foregroundStyle(Palette.ink2) }
                    if device.isSelf || device.isOwner {
                        Text(device.isSelf ? String(localized: "This device") : String(localized: "Room creator")).font(Fonts.hint).foregroundStyle(Palette.ink2)
                    }
                    StatusLine(online: device.online, text: device.online ? String(localized: "online") : When.seenAgo(device.seen))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(device.online ? Palette.accentSoft : Palette.glassWeak, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .padding(.top, 12)
                GlassGroup {
                    GlassField(label: String(localized: "Name"), text: $name)
                        .focused($editing)
                        .onSubmit { commit(device) }
                    if !device.isSelf || device.ownName != device.name {
                        Hairline(inset: 16)
                        ValueRow(label: device.isSelf ? String(localized: "Name in iOS") : String(localized: "Its own name"), value: device.ownName)
                    }
                    Hairline(inset: 16)
                    ValueRow(label: String(localized: "Server"), value: model.host, mono: true)
                    Hairline(inset: 16)
                    ValueRow(label: String(localized: "Last contact"), value: device.online ? String(localized: "now") : device.seen.map(When.contact) ?? "")
                    if let since = device.since {
                        Hairline(inset: 16)
                        ValueRow(label: String(localized: "Joined"), value: When.long(since))
                    }
                }
                .padding(.top, 24)
                FootNote(text: device.isSelf ? String(localized: "Every device in the room sees this name.") : String(localized: "This name is shown on this phone only. Leave it empty to go back to the device's own name."))
                if device.isOwner, !device.isSelf {
                    FootNote(text: String(localized: "The room was created on this computer, so it can't be removed from the room. If you no longer want its clipboard, leave the room on the main screen."))
                }
                if !device.isSelf, !device.isOwner {
                    CapsuleButton(title: device.computer ? String(localized: "Disconnect this computer") : String(localized: "Disconnect this device"), style: .plain, ink: Palette.dangerInk) { confirm = true }
                        .padding(.top, 24)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { name = device.name }
            .onChange(of: editing) { _, focused in if !focused { commit(device) } }
            .onDisappear { commit(device) }
            .confirmationDialog(String(localized: "Disconnect \(device.name)?"), isPresented: $confirm, titleVisibility: .visible) {
                Button(String(localized: "Disconnect and revoke the key"), role: .destructive) { Task { await revoke(device) } }
            } message: {
                Text("The device loses access to the room: the others move to a new key. Everyone else keeps working.")
            }
        }
    }

    private func commit(_ device: DeviceItem) {
        if device.isSelf {
            model.deviceName = name
        } else if name != device.name {
            model.setAlias(device, name)
        }
    }

    private func revoke(_ device: DeviceItem) async {
        model.say(String(localized: "Disconnecting"), .busy)
        do {
            try await model.revoke(device)
            model.say(String(localized: "\(device.name) disconnected"), .done)
            dismiss()
        } catch {
            model.say(error.localizedDescription, .error)
        }
    }
}

struct AddComputerView: View {

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Binding var path: [Route]
    @State private var known: Set<String> = []
    @State private var joined: String?

    var body: some View {
        ScrollPage {
            PageTitle(title: String(localized: "Add a computer"), subtitle: String(localized: "The new computer joins the room and sees the shared clipboard"), large: false)
            GlassGroup {
                StepRow(number: 1, text: String(localized: "On a computer that is already in the room choose Devices, then Invite a Computer in the Tossling menu, or run on a Mac")) {
                    CommandBox(command: "tossling invite") { model.say(String(localized: "Copied"), .done) }
                }
                Hairline(inset: 58)
                StepRow(number: 2, text: String(localized: "On the new Mac run the command it shows. On Windows and Linux choose Join Another Room and paste the address with the code")) {
                    CommandBox(command: "tossling join \(model.host)/ABCD-EFGH", copy: "tossling join \(model.host)/") { model.say(String(localized: "Copied"), .done) }
                    Text("Example: the new computer gets its own code").font(Fonts.footnote).foregroundStyle(Palette.ink2)
                }
                Hairline(inset: 58)
                HStack(spacing: 14) {
                    if let joined {
                        CircleBadge(icon: "checkmark", tint: Palette.onAccent, fill: Palette.accent, size: 28, iconSize: 13)
                        Text("\(joined) is in the room").font(Fonts.onest(16, .semibold)).foregroundStyle(Palette.ink)
                    } else {
                        ProgressView().frame(width: 28, height: 28)
                        Text("Waiting for the new device…").font(Fonts.onest(16)).foregroundStyle(Palette.ink2)
                    }
                    Spacer()
                }
                .padding(16)
                .animation(.spring(response: 0.31, dampingFraction: 0.55), value: joined)
            }
            .padding(.top, 24)
        } bar: {
            FloatingBar {
                if joined != nil {
                    CapsuleButton(title: String(localized: "Done")) { dismiss() }
                } else {
                    CapsuleButton(title: String(localized: "or scan the QR code of the new computer"), style: .glass, icon: "qrcode") { path.append(.pairing) }
                }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { known = Set(model.devices.map(\.id)) }
        .onChange(of: model.devices) { _, devices in
            if let new = devices.first(where: { !known.contains($0.id) }) { joined = new.name }
        }
        .task {
            while !Task.isCancelled, joined == nil {
                _ = await model.refresh()
                try? await Task.sleep(for: .seconds(5))
            }
        }
    }
}

struct SettingsView: View {

    @Environment(AppModel.self) private var model
    @Environment(\.openURL) private var openURL
    @Binding var path: [Route]

    var body: some View {
        @Bindable var model = model
        ScrollPage {
            PageTitle(title: String(localized: "Settings"))
            SectionLabel(text: String(localized: "Clipboard"))
            GlassGroup {
                ToggleRow(label: String(localized: "Pause sending"), note: String(localized: "The phone clipboard does not go to the computer"), isOn: $model.paused)
                Hairline(inset: 16)
                ToggleRow(label: String(localized: "Send images"), note: String(localized: "When off, only text"), isOn: $model.sendsImages)
                Hairline(inset: 16)
                ToggleRow(label: String(localized: "Put received items into the clipboard"), note: String(localized: "While Tossling is open, text and images from a computer go straight into the clipboard"), isOn: $model.autoCopy)
                Hairline(inset: 16)
                Button { if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) } } label: {
                    LinkRow(label: String(localized: "Paste without asking"), note: String(localized: "In iOS Settings set Paste from Other Apps to Allow, then To Computer sends at once"))
                }
                .pressable(scale: 0.98)
            }
            SectionLabel(text: String(localized: "Notifications"))
            GlassGroup {
                Button { path.append(.projects) } label: { LinkRow(label: String(localized: "Projects"), value: "\(model.projects.count)") }
                    .pressable(scale: 0.98)
                Hairline(inset: 16)
                ToggleRow(label: String(localized: "Quiet hours"), note: String(localized: "23:00 to 08:00, urgent ones always come through"), isOn: $model.quietHours)
            }
            SectionLabel(text: String(localized: "Devices"))
            GlassGroup {
                Button { path.append(.devices) } label: { LinkRow(label: String(localized: "Devices in the room"), value: "\(model.devices.count)") }
                    .pressable(scale: 0.98)
            }
            SectionLabel(text: String(localized: "About"))
            GlassGroup {
                LinkRow(label: String(localized: "Tossling for iOS"), value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }
}
