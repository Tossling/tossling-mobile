import BackgroundTasks
import Foundation

/// On iOS 26 a file sent from the app gets the system's progress panel on the Lock Screen and in the Dynamic Island,
/// and iOS keeps the app running while it reports progress. Stopping it there pauses the upload: the file stays,
/// Continue or Cancel are in the app.
///
/// Only the app on screen may ask for one, so files from the share sheet go without it.
@available(iOS 26, *)
@MainActor
enum ContinuedUploads {

    private static let prefix = "com.kopylovis.tossling.upload"
    private static var isRegistered = false
    private static var tasks: [String: BGContinuedProcessingTask] = [:]
    private static var seen: Set<String> = []

    static func begin(id: String, name: String, subtitle: String) {
        register()
        let request = BGContinuedProcessingTaskRequest(identifier: "\(prefix).\(id)", title: name, subtitle: subtitle)
        request.strategy = .fail
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            print("upload: no continued task: \(error.localizedDescription)")
        }
    }

    /// Closes the panel of an upload the person paused or cancelled in the app.
    static func end(_ id: String) {
        finish(id, success: false)
    }

    /// Mirrors the uploads into their panels and closes the panels of the ones that ended.
    static func update(_ uploads: [Upload]) {
        for (id, task) in tasks {
            guard let upload = uploads.first(where: { $0.id == id }) else {
                // The panel may start before the record exists; once seen, a missing record means it was sent.
                if seen.contains(id) { finish(id, success: true) }
                continue
            }
            seen.insert(id)
            switch upload.state {
            case .sealing:
                task.progress.completedUnitCount = 0
            case .uploading:
                task.progress.completedUnitCount = Int64(upload.fraction * Double(units))
                task.updateTitle(upload.name, subtitle: String(localized: "\(upload.percent)% · \(bytes(upload.sent)) of \(bytes(upload.total))"))
            case .paused, .failed:
                finish(id, success: false)
            }
        }
    }

    private static let units: Int64 = 1000

    private static func register() {
        guard !isRegistered else { return }
        isRegistered = BGTaskScheduler.shared.register(forTaskWithIdentifier: "\(prefix).*", using: .main) { task in
            guard let task = task as? BGContinuedProcessingTask else { return task.setTaskCompleted(success: false) }
            MainActor.assumeIsolated { started(task) }
        }
    }

    private static func started(_ task: BGContinuedProcessingTask) {
        let id = String(task.identifier.dropFirst(prefix.count + 1))
        task.progress.totalUnitCount = units
        task.expirationHandler = {
            // The person stopped it on the Lock Screen, or iOS needs the resources back.
            DispatchQueue.main.async {
                tasks[id] = nil
                Uploader.shared.pause(id)
            }
        }
        tasks[id] = task
        update(Uploader.shared.uploads)
    }

    private static func finish(_ id: String, success: Bool) {
        seen.remove(id)
        guard let task = tasks.removeValue(forKey: id) else { return }
        if success { task.progress.completedUnitCount = units }
        task.setTaskCompleted(success: success)
    }

    private static func bytes(_ count: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: count, countStyle: .file)
    }
}
