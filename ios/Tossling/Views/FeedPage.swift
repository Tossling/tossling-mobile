import SwiftUI

struct FeedPage: View {

    @Environment(AppModel.self) private var model
    @Binding var path: [Route]
    @State private var filter: String?
    @State private var confirmClear = false

    var body: some View {
        List {
            Section {
                Text(model.unread > 0 ? String(localized: "\(model.unread) unread") : String(localized: "All read"))
                    .font(Fonts.body)
                    .foregroundStyle(Palette.ink2)
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                    .listRowInsets(EdgeInsets(top: 0, leading: 20, bottom: 0, trailing: 20))
                if !model.projects.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            FilterChip(label: String(localized: "All"), selected: filter == nil) { filter = nil }
                            ForEach(model.projects) { project in
                                FilterChip(label: project.name, selected: filter == project.topic, dot: project.tint) { filter = project.topic }
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 4)
                    }
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                    .listRowInsets(EdgeInsets())
                }
            }
            if days.isEmpty {
                Section {
                    VStack(spacing: 0) {
                        if let filter, let project = model.project(filter), !model.alerts.isEmpty {
                            EmptyState(icon: "bell", title: String(localized: "Nothing in \"\(project.name)\""), text: String(localized: "This project has no events yet."))
                        } else {
                            EmptyState(icon: "bell", title: String(localized: "Quiet"), text: String(localized: "Events from your projects show up here: sign-ins, denials, rule hits."))
                        }
                        Button { path.append(.projects) } label: {
                            Text("Projects").font(Fonts.onest(15, .semibold)).foregroundStyle(Palette.accentInk)
                                .padding(.horizontal, 18).padding(.vertical, 11)
                                .background(Palette.glassWeak, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                        }
                        .pressable(scale: 0.94)
                        .padding(.bottom, 28)
                    }
                    .listRowBackground(Palette.glassFill)
                }
            }
            ForEach(days, id: \.0) { day, alerts in
                Section {
                    ForEach(alerts) { alert in
                        Button {
                            model.markRead(alert)
                            path.append(.alert(alert.id))
                        } label: { AlertRow(alert: alert, project: model.project(alert.topic)) }
                            .buttonStyle(.plain)
                            .listRowBackground(ZStack { Palette.glassFill; if alert.isUrgent { Palette.urgentRow } })
                            .listRowSeparatorTint(Palette.hairline)
                            .alignmentGuide(.listRowSeparatorLeading) { _ in 64 }
                            .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                Button(role: .destructive) { withAnimation { model.delete(alert) } } label: { Label("Delete", systemImage: "trash") }
                                Button { model.markRead(alert, !alert.isRead) } label: {
                                    Label(alert.isRead ? String(localized: "Unread") : String(localized: "Read"), systemImage: alert.isRead ? "envelope.badge" : "envelope.open")
                                }
                                .tint(Palette.accent)
                            }
                    }
                } header: {
                    Text(day).font(Fonts.onest(15, .semibold)).foregroundStyle(Palette.ink2).textCase(nil)
                }
            }
        }
        .glassList()
        .animation(.default, value: model.alerts)
        .navigationTitle(String(localized: "Notifications"))
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button { model.markAllRead() } label: { Label("Mark all read", systemImage: "checkmark.circle") }
                    Button { path.append(.projects) } label: { Label("Projects", systemImage: "square.grid.2x2") }
                    Button(role: .destructive) { confirmClear = true } label: { Label("Clear", systemImage: "trash") }
                } label: {
                    Image(systemName: "ellipsis")
                }
                .accessibilityLabel(String(localized: "Menu"))
            }
        }
        .refreshable {
            let ok = await model.refresh()
            model.say(ok ? String(localized: "Updated") : String(localized: "No connection to the server"), ok ? .done : .error)
        }
        .confirmationDialog(String(localized: "Clear the feed?"), isPresented: $confirmClear, titleVisibility: .visible) {
            Button(String(localized: "Clear"), role: .destructive) { model.clearFeed() }
        } message: {
            Text("All notifications will be removed from the phone. Projects and their settings stay.")
        }
        .onChange(of: model.projects) { _, projects in
            if let filter, !projects.contains(where: { $0.topic == filter }) { self.filter = nil }
        }
    }

    private var days: [(String, [ProjectAlert])] {
        let visible = model.alerts.filter { filter == nil || $0.topic == filter }
        var result: [(String, [ProjectAlert])] = []
        for alert in visible {
            let day = When.day(alert.time)
            if let index = result.firstIndex(where: { $0.0 == day }) { result[index].1.append(alert) } else { result.append((day, [alert])) }
        }
        return result
    }
}

struct PriorityChip: View {

    let priority: Int
    var large = false

    var body: some View {
        if let (text, fill, ink) = style {
            Text(text)
                .font(Fonts.small)
                .foregroundStyle(ink)
                .padding(.horizontal, large ? 9 : 8)
                .padding(.vertical, large ? 2 : 1)
                .background(fill, in: RoundedRectangle(cornerRadius: large ? 10 : 9, style: .continuous))
        }
    }

    static func word(_ priority: Int) -> String {
        if priority >= 5 { return String(localized: "urgent") }
        if priority == 4 { return String(localized: "important") }
        return priority <= 2 ? String(localized: "quiet") : String(localized: "normal")
    }

    private var style: (String, Color, Color)? {
        if priority >= 5 { return (String(localized: "urgent"), Palette.danger, Palette.onDanger) }
        if priority == 4 { return (String(localized: "important"), Palette.importantSoft, Palette.importantInk) }
        guard large else { return nil }
        return priority <= 2 ? (String(localized: "quiet"), Palette.glassWeak, Palette.ink2) : (String(localized: "normal"), Palette.glassWeak, Palette.ink)
    }
}

struct AlertRow: View {

    let alert: ProjectAlert
    let project: Project?

    var body: some View {
        let name = project?.name ?? alert.topic
        HStack(alignment: .top, spacing: 12) {
            ProjectAvatar(initials: project?.initials ?? String(alert.topic.prefix(1)).uppercased(), color: project?.tint ?? Palette.projects[0])
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 8) {
                    Text(name).font(Fonts.footnote).foregroundStyle(Palette.ink2).lineLimit(1)
                    Spacer(minLength: 4)
                    PriorityChip(priority: alert.priority)
                    Text(When.clock(alert.time)).font(Fonts.footnote).foregroundStyle(Palette.ink2)
                    if !alert.isRead { Circle().fill(Palette.accent).frame(width: 8, height: 8) }
                }
                let title = alert.title(for: name)
                if !title.isEmpty {
                    Text(title).font(Fonts.onest(16, .semibold)).foregroundStyle(alert.isQuiet ? Palette.ink2 : Palette.ink)
                }
                if !alert.plainMessage.isEmpty {
                    Text(alert.plainMessage).font(Fonts.body).foregroundStyle(Palette.ink2).lineLimit(3)
                }
            }
        }
        .padding(.vertical, 6)
        .contentShape(Rectangle())
    }
}

struct AlertView: View {

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    let id: String

    var body: some View {
        if let alert = model.alerts.first(where: { $0.id == id }) {
            let project = model.project(alert.topic)
            let name = project?.name ?? alert.topic
            ScrollPage {
                HStack(spacing: 10) {
                    ProjectAvatar(initials: project?.initials ?? "?", color: project?.tint ?? Palette.projects[0], size: 28)
                    Text(name).font(Fonts.onest(15, .medium)).foregroundStyle(Palette.ink2)
                    PriorityChip(priority: alert.priority, large: true)
                    Spacer()
                }
                .padding(.horizontal, 4)
                .padding(.top, 22)
                Text(alert.title(for: name).isEmpty ? name : alert.title(for: name))
                    .font(Fonts.title)
                    .foregroundStyle(Palette.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 4)
                    .padding(.top, 8)
                MarkdownBody(text: alert.message, markdown: alert.isMarkdown || Markdown.looksLikeMarkdown(alert.message))
                    .padding(18)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .glassSurface()
                    .padding(.top, 20)
                GlassGroup {
                    ValueRow(label: String(localized: "Project"), value: name)
                    Hairline(inset: 16)
                    ValueRow(label: String(localized: "Channel"), value: alert.topic, mono: true)
                    Hairline(inset: 16)
                    ValueRow(label: String(localized: "Priority"), value: "\(alert.priority) · \(PriorityChip.word(alert.priority))")
                    Hairline(inset: 16)
                    ValueRow(label: String(localized: "Time"), value: When.exact(alert.time))
                }
                .padding(.top, 12)
            } bar: {
                FloatingBar {
                    CapsuleButton(title: String(localized: "Copy text"), style: alert.click == nil ? .primary : .glass) {
                        UIPasteboard.general.string = [alert.title, alert.plainMessage].filter { !$0.isEmpty }.joined(separator: "\n")
                        model.say(String(localized: "Copied"), .done)
                    }
                    if let click = alert.click, let url = URL(string: click) {
                        CapsuleButton(title: String(localized: "Open link"), icon: "arrow.up.right") { openURL(url) }
                    }
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(role: .destructive) {
                        model.delete(alert)
                        dismiss()
                    } label: { Image(systemName: "trash") }
                        .accessibilityLabel(String(localized: "Delete"))
                }
            }
            .onAppear { model.markRead(alert) }
        }
    }
}

struct MarkdownBody: View {

    let text: String
    let markdown: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { _, block in
                switch block {
                case let .code(code):
                    ScrollView(.horizontal, showsIndicators: false) {
                        Text(code).font(Fonts.mono(13)).foregroundStyle(Palette.ink).textSelection(.enabled)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .background(Palette.codeInline, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Palette.hairline, lineWidth: 1))
                case let .heading(line):
                    Text(inline(line)).font(Fonts.headline).foregroundStyle(Palette.ink)
                case let .bullet(line):
                    Text("•  ") + Text(inline(line))
                case let .paragraph(line):
                    Text(inline(line)).font(Fonts.onest(16)).foregroundStyle(Palette.ink).lineSpacing(4).textSelection(.enabled)
                }
            }
        }
        .tint(Palette.accentInk)
    }

    private enum Block {
        case code(String), heading(String), bullet(String), paragraph(String)
    }

    private var blocks: [Block] {
        guard markdown else { return [.paragraph(text.trimmingCharacters(in: .whitespacesAndNewlines))] }
        var result: [Block] = []
        var paragraph: [String] = []
        var code: [String]?
        func flush() {
            if !paragraph.isEmpty { result.append(.paragraph(paragraph.joined(separator: " "))) }
            paragraph = []
        }
        for line in text.components(separatedBy: "\n") {
            if line.trimmingCharacters(in: .whitespaces).hasPrefix("```") {
                if let lines = code {
                    result.append(.code(lines.joined(separator: "\n")))
                    code = nil
                } else {
                    flush()
                    code = []
                }
                continue
            }
            if code != nil {
                code?.append(line)
                continue
            }
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.isEmpty {
                flush()
            } else if trimmed.hasPrefix("#") {
                flush()
                result.append(.heading(trimmed.drop { $0 == "#" }.trimmingCharacters(in: .whitespaces)))
            } else if trimmed.hasPrefix("- ") || trimmed.hasPrefix("* ") {
                flush()
                result.append(.bullet(String(trimmed.dropFirst(2))))
            } else {
                paragraph.append(trimmed)
            }
        }
        if let lines = code { result.append(.code(lines.joined(separator: "\n"))) }
        flush()
        return result
    }

    private func inline(_ line: String) -> AttributedString {
        (try? AttributedString(markdown: line, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))) ?? AttributedString(line)
    }
}
