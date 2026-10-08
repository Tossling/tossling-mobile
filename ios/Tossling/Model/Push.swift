import FirebaseCore
import FirebaseMessaging
import UIKit
import UserNotifications

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        Keychain.moveToSharedGroup()
        Push.shared.start()
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Push.shared.apnsToken(deviceToken)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("push: APNs registration failed: \(error.localizedDescription)")
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        UIApplication.shared.applicationState == .active ? [] : [.banner, .list, .sound]
    }
}

final class Push {

    static let shared = Push()

    private let defaults = UserDefaults.standard
    private var available = false
    private var hasToken = false
    private var wanted: String?

    func start() {
        guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else { return }
        FirebaseApp.configure()
        available = true
    }

    func apnsToken(_ token: Data) {
        guard available else { return }
        Messaging.messaging().apnsToken = token
        hasToken = true
        subscribe()
    }

    func follow(room: String?) {
        guard available else { return }
        wanted = room
        let current = defaults.string(forKey: "push-topic")
        if let current, current != room, hasToken {
            Messaging.messaging().unsubscribe(fromTopic: current)
            defaults.removeObject(forKey: "push-topic")
        }
        guard room != nil else {
            if current == nil || hasToken { defaults.removeObject(forKey: "push-topic") }
            return
        }
        Task { @MainActor in
            let granted = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
            guard granted else { return }
            UIApplication.shared.registerForRemoteNotifications()
            subscribe()
        }
    }

    private func subscribe() {
        guard hasToken, let room = wanted, defaults.string(forKey: "push-topic") != room else { return }
        Messaging.messaging().subscribe(toTopic: room) { [defaults] error in
            if let error {
                print("push: could not follow the room: \(error.localizedDescription)")
            } else {
                defaults.set(room, forKey: "push-topic")
            }
        }
    }
}
