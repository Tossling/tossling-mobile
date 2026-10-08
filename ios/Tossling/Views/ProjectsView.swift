import SwiftUI

struct ProjectsView: View {

    @Environment(AppModel.self) private var model
    @Binding var path: [Route]

    var body: some View {
        ScrollPage {
            PageTitle(title: String(localized: "Projects"), subtitle: String(localized: "Services that send events to Tossling"))
            SectionLabel(text: String(localized: "\(model.projects.count) projects"), trailing: model.projects.isEmpty ? nil : String(localized: "Muted"))
            GlassGroup {
                ForEach(model.projects) { project in
                    HStack(spacing: 8) {
                        Button { path.append(.project(project.topic)) } label: {
                            HStack(spacing: 14) {
                                ProjectAvatar(initials: project.initials, color: project.tint, size: 40)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(project.name).font(Fonts.onest(16, .semibold)).foregroundStyle(Palette.ink).lineLimit(1)
                                    Text(project.topic).font(Fonts.mono(12)).foregroundStyle(Palette.ink2).lineLimit(1)
                                    Text(last(project)).font(Fonts.footnote).foregroundStyle(Palette.ink2).lineLimit(1)
                                }
                                Spacer(minLength: 0)
                            }
                            .contentShape(Rectangle())
                        }
                        .pressable(scale: 0.98)
                        Toggle("", isOn: Binding(get: { project.isMuted }, set: { _ in model.toggleMute(project) }))
                            .labelsHidden()
                            .tint(Palette.accent)
                    }
                    .padding(.leading, 16)
                    .padding(.trailing, 12)
                    .padding(.vertical, 10)
                    .frame(minHeight: 76)
                    Hairline(inset: 70)
                }
                Button { path.append(.project(nil)) } label: { AddRow(label: String(localized: "Add a project"), badge: 40) }
                    .pressable(scale: 0.98)
            }
            FootNote(text: String(localized: "Muted: events land in the feed but the push comes silently. Urgent ones (5) always ring."))
        }
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.syncProjects(force: true) }
    }

    private func last(_ project: Project) -> String {
        guard let alert = model.alerts.first(where: { $0.topic == project.topic }) else { return String(localized: "no events yet") }
        let title = alert.title(for: project.name)
        return "\(title.isEmpty ? alert.plainMessage : title) · \(When.relative(alert.time))"
    }
}

struct ProjectView: View {

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let topic: String?
    @State private var name = ""
    @State private var channel = ""
    @State private var color = 0
    @State private var channelEdited = false
    @State private var saving = false
    @State private var created: (token: String, example: String?)?
    @State private var confirmDelete = false

    var body: some View {
        ScrollPage {
            HStack(spacing: 14) {
                ProjectAvatar(initials: Project(topic: channel, name: name.isEmpty ? "?" : name).initials, color: Palette.projects[color], size: 48)
                Text(created != nil ? String(localized: "Project created") : (existing?.name ?? String(localized: "New project")))
                    .font(Fonts.title).foregroundStyle(Palette.ink).lineLimit(1)
                Spacer()
            }
            .padding(.horizontal, 4)
            .padding(.top, 20)
            if let created {
                GlassGroup {
                    ValueRow(label: String(localized: "Name"), value: name)
                    Hairline(inset: 16)
                    ValueRow(label: String(localized: "Channel"), value: channel, mono: true)
                }
                .padding(.top, 22)
                SectionLabel(text: String(localized: "Publisher token"))
                CopyBlock(label: "token", text: created.token) { model.say(String(localized: "Copied"), .done) }
                FootNote(text: String(localized: "Shown once: put it into the service settings now. It can only publish to this channel."))
            } else {
                GlassGroup {
                    GlassField(label: String(localized: "Name"), text: Binding(get: { name }, set: { value in
                        name = String(value.prefix(40))
                        if !channelEdited, existing == nil { channel = Self.slug(name) }
                    }), placeholder: String(localized: "For example, Home NAS"))
                    Hairline(inset: 16)
                    GlassField(label: String(localized: "Channel"), text: Binding(get: { channel }, set: { value in
                        channelEdited = true
                        channel = String(value.lowercased().filter { $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "-" || $0 == "_") }.prefix(64))
                    }), placeholder: "home-nas", mono: true, enabled: existing == nil)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    Hairline(inset: 16)
                    HStack {
                        Text("Color").font(Fonts.row).foregroundStyle(Palette.ink)
                        Spacer()
                        ForEach(0..<6, id: \.self) { index in
                            Button { color = index } label: {
                                Circle().fill(Palette.projects[index]).frame(width: 26, height: 26)
                                    .padding(4)
                                    .overlay(Circle().strokeBorder(color == index ? Palette.projects[index] : .clear, lineWidth: 2))
                                    .frame(width: 40, height: 44)
                            }
                            .pressable(scale: 0.85)
                        }
                    }
                    .padding(.leading, 16)
                    .padding(.trailing, 6)
                    .frame(minHeight: 60)
                }
                .padding(.top, 22)
            }
            SectionLabel(text: String(localized: "Request example"))
            CopyBlock(label: "curl", text: created?.example ?? example) { model.say(String(localized: "Copied"), .done) }
            FootNote(text: String(localized: "priority is 1 to 5, 3 by default. With \"markdown\": true the message understands Markdown, click opens on tap."))
        } bar: {
            FloatingBar {
                if created != nil {
                    CapsuleButton(title: String(localized: "Done")) { dismiss() }
                } else {
                    if existing != nil {
                        CapsuleButton(title: String(localized: "Delete"), style: .plain, ink: Palette.dangerInk) { confirmDelete = true }
                    }
                    CapsuleButton(title: existing == nil ? String(localized: "Add project") : String(localized: "Save")) { Task { await save() } }
                        .disabled(!canSave || saving)
                        .opacity(canSave && !saving ? 1 : 0.45)
                }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if let existing {
                name = existing.name
                channel = existing.topic
                color = existing.colorIndex
            } else {
                color = model.projects.count % 6
            }
        }
        .confirmationDialog(String(localized: "Delete project \(name)?"), isPresented: $confirmDelete, titleVisibility: .visible) {
            Button(String(localized: "Delete project"), role: .destructive) { Task { await remove() } }
        } message: {
            Text("Tossling stops listening to \(channel) and its notifications leave the feed.")
        }
    }

    private var existing: Project? { topic.flatMap { model.project($0) } }

    private var canSave: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
            && channel.range(of: "^[a-z0-9_-]{1,64}$", options: .regularExpression) != nil
            && (existing != nil || model.project(channel) == nil)
    }

    private var example: String {
        """
        curl \(model.host.isEmpty ? "https://ntfy.example.com" : "https://\(model.host)") \\
          -H "Authorization: Bearer $NTFY_TOKEN" \\
          -d '{"topic":"\(channel.isEmpty ? "home-nas" : channel)","title":"\(String(localized: "New user"))","message":"\(String(localized: "Signed up **dmitry**"))","priority":3,"markdown":true}'
        """
    }

    private func save() async {
        if var project = existing {
            project.name = name
            project.color = color
            model.updateProject(project)
            model.say(String(localized: "Saved"), .done)
            dismiss()
            return
        }
        saving = true
        defer { saving = false }
        model.say(String(localized: "Checking the channel"), .busy)
        do {
            let result = try await model.addProject(name: name, topic: channel, color: color)
            model.say(String(localized: "Project added"), .done)
            if let token = result?.token {
                created = (token, result?.example)
            } else {
                dismiss()
            }
        } catch {
            model.say(error.localizedDescription, .error)
        }
    }

    private func remove() async {
        guard let existing else { return }
        do {
            try await model.removeProject(existing)
            model.say(String(localized: "Project deleted"), .done)
            dismiss()
        } catch {
            model.say(String(localized: "No connection to the server"), .error)
        }
    }

    static func slug(_ name: String) -> String {
        let map: [Character: String] = ["а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "е": "e", "ё": "e", "ж": "zh", "з": "z", "и": "i", "й": "y", "к": "k", "л": "l", "м": "m", "н": "n", "о": "o", "п": "p", "р": "r", "с": "s", "т": "t", "у": "u", "ф": "f", "х": "h", "ц": "c", "ч": "ch", "ш": "sh", "щ": "sch", "ъ": "", "ы": "y", "ь": "", "э": "e", "ю": "yu", "я": "ya"]
        let latin = name.lowercased().map { map[$0] ?? String($0) }.joined()
        let dashed = latin.replacingOccurrences(of: "[^a-z0-9]+", with: "-", options: .regularExpression)
        return String(dashed.trimmingCharacters(in: CharacterSet(charactersIn: "-")).prefix(64))
    }
}

struct CopyBlock: View {

    let label: String
    let text: String
    let copied: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(label).font(Fonts.mono(12)).foregroundStyle(Palette.ink2)
                Spacer()
                Button {
                    UIPasteboard.general.string = text
                    copied()
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "doc.on.doc").font(.system(size: 14))
                        Text("Copy").font(Fonts.onest(14, .semibold))
                    }
                    .foregroundStyle(Palette.accentInk)
                    .padding(.horizontal, 12)
                    .frame(minHeight: 44)
                }
                .pressable(scale: 0.94)
            }
            .padding(.leading, 14)
            .padding(.trailing, 4)
            ScrollView(.horizontal, showsIndicators: false) {
                Text(text).font(Fonts.mono(12)).foregroundStyle(Palette.ink).lineSpacing(5).textSelection(.enabled)
                    .padding(.horizontal, 14)
                    .padding(.bottom, 14)
            }
        }
        .glassSurface(radius: 20, shadow: false)
    }
}
