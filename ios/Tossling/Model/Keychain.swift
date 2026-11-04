import Foundation
import Security

enum Keychain {

    private static let service = "com.kopylovis.tossling"
    private static let accounts = ["device-id", "identity", "room"]

    static func read(_ account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func write(_ account: String, _ value: String?) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
        guard let value else { return }
        var item = query
        item[kSecValueData as String] = Data(value.utf8)
        // ThisDeviceOnly keeps the room key, tokens, identity and device id out of backups: restored onto another
        // iPhone they made two phones with one device id, which skip each other's messages and take each other's token.
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(item as CFDictionary, nil)
    }

    /// Runs at every launch: writes each item again, into the shared group and with the accessibility above.
    static func moveToSharedGroup() {
        for account in accounts {
            if let value = read(account) { write(account, value) }
        }
    }
}
