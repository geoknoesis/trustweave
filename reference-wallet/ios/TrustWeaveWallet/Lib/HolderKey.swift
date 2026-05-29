// HolderKey.swift
//
// Ed25519 holder identity management on iOS.
//
// Phase 2.5d baseline: CryptoKit `Curve25519.Signing.PrivateKey` is generated
// in-process; the raw 32-byte seed is stored in Keychain (`kSecClassGenericPassword`).
// Signing rehydrates the key from the stored seed.
//
// Phase 2.5e upgrade: bind the key directly to Secure Enclave or kSecAttrTokenID
// for hardware-backed non-extractability. CryptoKit's `SecureEnclave.P256` works
// natively on Secure Enclave but Ed25519 doesn't — that route requires switching to
// P-256 + ES256 JWS alg, which is a bigger change. Most production iOS wallets pick
// that tradeoff.

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
    func sign(_ data: Data) -> Data {
        return try! privateKey.signature(for: data)
    }

    var did: String {
        return Crypto.publicKeyToDidKey(publicKey)
    }

    // MARK: - Keychain plumbing

    private static func storeSeedInKeychain(_ seed: Data) throws {
        // Delete any existing entry first; Keychain has no atomic upsert.
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
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
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
