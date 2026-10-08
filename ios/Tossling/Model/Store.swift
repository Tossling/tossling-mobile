import Foundation
import TosslingKit

final class Store {

    static let shared = Store()

    private let defaults = UserDefaults.standard

    var deviceId: String {
        if let saved = Keychain.read("device-id") { return saved }
        let created = RoomCore.companion.createDeviceId()
        Keychain.write("device-id", created)
        return created
    }

    var identity: String {
        if let saved = Keychain.read("identity") { return saved }
        let created = RoomCore.companion.createIdentity()
        Keychain.write("identity", created)
        return created
    }

    var config: RoomConfig? {
        get { Keychain.read("room").flatMap { RoomCore.companion.decodeConfig(text: $0) } }
        set { Keychain.write("room", newValue.map { RoomCore.companion.encodeConfig(config: $0) }) }
    }

    var members: [String: Member] {
        get { defaults.string(forKey: "members").map { RoomCore.companion.decodeMembers(text: $0) } ?? [:] }
        set { defaults.set(RoomCore.companion.encodeMembers(members: newValue), forKey: "members") }
    }

    var lastEventId: String {
        get { (try? String(contentsOf: lastEventURL, encoding: .utf8)) ?? "" }
        set { try? newValue.write(to: lastEventURL, atomically: true, encoding: .utf8) }
    }

    private var lastEventURL: URL { Paths.support.appendingPathComponent("last-event") }

    var deviceName: String {
        get { defaults.string(forKey: "device-name") ?? Self.defaultName }
        set { defaults.set(newValue, forKey: "device-name") }
    }

    var autoCopy: Bool {
        get { defaults.object(forKey: "auto-copy") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "auto-copy") }
    }

    var paused: Bool {
        get { defaults.bool(forKey: "paused") }
        set { defaults.set(newValue, forKey: "paused") }
    }

    var sendsImages: Bool {
        get { defaults.object(forKey: "sends-images") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "sends-images") }
    }

    var quietHours: Bool {
        get { shared.bool(forKey: "quiet-hours") }
        set { shared.set(newValue, forKey: "quiet-hours") }
    }

    var aliases: [String: String] {
        get { defaults.dictionary(forKey: "aliases") as? [String: String] ?? [:] }
        set { defaults.set(newValue, forKey: "aliases") }
    }

    var hasStarted: Bool {
        get { defaults.bool(forKey: "started") }
        set { defaults.set(newValue, forKey: "started") }
    }

    var retiredTokens: [String: Date] {
        get { (defaults.dictionary(forKey: "retired-tokens") as? [String: Double] ?? [:]).mapValues { Date(timeIntervalSince1970: $0) } }
        set { defaults.set(newValue.mapValues { $0.timeIntervalSince1970 }, forKey: "retired-tokens") }
    }

    private var shared: UserDefaults { UserDefaults(suiteName: Paths.groupID) ?? .standard }

    func forgetRoom() {
        config = nil
        defaults.removeObject(forKey: "members")
        defaults.removeObject(forKey: "aliases")
        lastEventId = ""
    }

    private static var defaultName: String {
        #if targetEnvironment(simulator)
        "iPhone Simulator"
        #else
        UIDeviceName.current
        #endif
    }
}

enum UIDeviceName {
    static var current: String {
        var info = utsname()
        uname(&info)
        let model = withUnsafeBytes(of: &info.machine) { String(decoding: $0.prefix { $0 != 0 }, as: UTF8.self) }
        return model.hasPrefix("iPad") ? "iPad" : "iPhone"
    }
}
