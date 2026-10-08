import FirebaseCore
import FirebaseMessaging
import UIKit
import UserNotifications

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        Keychain.moveToSharedGroup()
        Push.shared.start()
        if let large = UIFont(name: "Onest-Bold", size: 34), let small = UIFont(name: "Onest-SemiBold", size: 17) {
            UINavigationBar.appearance().largeTitleTextAttributes = [.font: UIFontMetrics(forTextStyle: .largeTitle).scaledFont(for: large)]
            UINavigationBar.appearance().titleTextAttributes = [.font: UIFontMetrics(forTextStyle: .headline).scaledFont(for: small)]
        }
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Push.shared.apnsToken(deviceToken)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("push: APNs registration failed: \(error.localizedDescription)")
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        guard let id = response.notification.request.content.userInfo["alert"] as? String else { return }
        await MainActor.run {
            Push.shared.openedAlert = id
            NotificationCenter.default.post(name: .openAlert, object: id)
        }
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
    private var wanted: Set<String> = []
    var openedAlert: String?

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

    func follow(topics: [String]) {
        guard available else { return }
        wanted = Set(topics)
        if topics.isEmpty {
            unsubscribeStale()
            return
        }
        Task { @MainActor in
            let granted = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
            guard granted else { return }
            UIApplication.shared.registerForRemoteNotifications()
            subscribe()
        }
    }

    private var followed: Set<String> {
        get { Set(defaults.stringArray(forKey: "push-topics") ?? []) }
        set { defaults.set(Array(newValue), forKey: "push-topics") }
    }

    private func subscribe() {
        guard hasToken else { return }
        unsubscribeStale()
        for topic in wanted.subtracting(followed) {
            Messaging.messaging().subscribe(toTopic: topic) { [weak self] error in
                if let error {
                    print("push: could not follow \(topic): \(error.localizedDescription)")
                } else {
                    DispatchQueue.main.async { self?.followed.insert(topic) }
                }
            }
        }
    }

    private func unsubscribeStale() {
        guard hasToken else { return }
        let legacy = defaults.string(forKey: "push-topic")
        if let legacy, !wanted.contains(legacy) { Messaging.messaging().unsubscribe(fromTopic: legacy) }
        defaults.removeObject(forKey: "push-topic")
        for topic in followed.subtracting(wanted) {
            Messaging.messaging().unsubscribe(fromTopic: topic)
            followed.remove(topic)
        }
    }
}

extension Notification.Name {
    static let openAlert = Notification.Name("com.kopylovis.tossling.open-alert")
}
