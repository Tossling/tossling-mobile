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

    func forgetRoom() {
        config = nil
        defaults.removeObject(forKey: "members")
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
