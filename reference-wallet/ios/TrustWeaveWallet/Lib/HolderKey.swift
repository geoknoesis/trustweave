// HolderKey.swift
//
// Ed25519 holder identity management on iOS.
// Stores the 32-byte seed in iOS Keychain with `WhenUnlockedThisDeviceOnly`
// protection (never synced off-device).

import CryptoKit
import Foundation
import Security

enum HolderKeyError: Error {
    case keychainStoreFailed(OSStatus)
    case keychainLoadFailed(OSStatus)
    case keychainDeleteFailed(OSStatus)
    case rawRepresentationFailed
}

final class HolderKey {

    private static let service = "org.trustweave.referencewallet.holder-key"
    private static let account = "ed25519-seed"

    let publicKey: Data
    private let privateKey: Curve25519.Signing.PrivateKey

    private init(privateKey: Curve25519.Signing.PrivateKey) {
        self.privateKey = privateKey
        self.publicKey = privateKey.publicKey.rawRepresentation
    }

    static func loadOrCreate() throws -> HolderKey {
        if let existing = try loadSeedFromKeychain() {
            return HolderKey(privateKey: try Curve25519.Signing.PrivateKey(rawRepresentation: existing))
        }
        let fresh = Curve25519.Signing.PrivateKey()
        try storeSeedInKeychain(fresh.rawRepresentation)
        return HolderKey(privateKey: fresh)
    }

    static func reset() throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        let status = SecItemDelete(query as CFDictionary)
        if status != errSecSuccess && status != errSecItemNotFound {
            throw HolderKeyError.keychainDeleteFailed(status)
        }
    }

    /// Sign [data] with the holder's Ed25519 private key. Returns the raw 64-byte signature.
    func sign(_ data: Data) throws -> Data {
        return try privateKey.signature(for: data)
    }

    var did: String {
        return Crypto.publicKeyToDidKey(publicKey)
    }

    // MARK: - Keychain plumbing

    private static func storeSeedInKeychain(_ seed: Data) throws {
        let deleteQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(deleteQuery as CFDictionary)

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecValueData as String: seed,
        ]
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else { throw HolderKeyError.keychainStoreFailed(status) }
    }

    private static func loadSeedFromKeychain() throws -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecReturnData as String: true,
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess else { throw HolderKeyError.keychainLoadFailed(status) }
        return result as? Data
    }
}
