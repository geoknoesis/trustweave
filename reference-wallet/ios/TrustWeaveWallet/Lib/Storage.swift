// Storage.swift
//
// Credential storage. Credentials themselves go in Keychain (`kSecClassGenericPassword`,
// one item per credential keyed by UUID). The credential index (list of IDs +
// preview metadata) lives in UserDefaults for cheap browsing without unlocking
// Keychain on every list operation.
//
// HolderKey is managed separately by [HolderKey].

import Foundation
import Security

struct StoredCredential: Codable, Identifiable {
    let id: String
    let format: String         // "vc+jwt" or "vc+sd-jwt"
    let credential: String     // Stored in Keychain; this is the materialised value
    let receivedAt: String
    let issuerDid: String
    let subjectDid: String
    let type: [String]
    let previewTitle: String
    let previewSubtitle: String?
    let selectivelyDisclosable: [String]
}

enum StorageError: Error {
    case keychainStoreFailed(OSStatus)
    case keychainLoadFailed(OSStatus)
    case credentialNotFound
}

final class Storage {

    private static let credentialService = "org.trustweave.referencewallet.credentials"
    private static let indexKey = "trustweave.wallet.credential-index"

    private let defaults = UserDefaults.standard

    /// List all stored credentials (metadata + materialised credential strings).
    func loadCredentials() -> [StoredCredential] {
        guard let data = defaults.data(forKey: Self.indexKey) else { return [] }
        let metas = (try? JSONDecoder().decode([CredentialIndexEntry].self, from: data)) ?? []
        return metas.compactMap { meta in
            guard let credString = try? loadCredentialString(id: meta.id) else { return nil }
            return StoredCredential(
                id: meta.id,
                format: meta.format,
                credential: credString,
                receivedAt: meta.receivedAt,
                issuerDid: meta.issuerDid,
                subjectDid: meta.subjectDid,
                type: meta.type,
                previewTitle: meta.previewTitle,
                previewSubtitle: meta.previewSubtitle,
                selectivelyDisclosable: meta.selectivelyDisclosable,
            )
        }
    }

    func addCredential(_ cred: StoredCredential) throws {
        try storeCredentialString(id: cred.id, credential: cred.credential)
        var index = loadIndex()
        index.append(CredentialIndexEntry(from: cred))
        saveIndex(index)
    }

    func deleteCredential(id: String) {
        try? deleteCredentialString(id: id)
        let index = loadIndex().filter { $0.id != id }
        saveIndex(index)
    }

    func reset() {
        for meta in loadIndex() { try? deleteCredentialString(id: meta.id) }
        defaults.removeObject(forKey: Self.indexKey)
    }

    // MARK: - Index (UserDefaults)

    private struct CredentialIndexEntry: Codable {
        let id: String
        let format: String
        let receivedAt: String
        let issuerDid: String
        let subjectDid: String
        let type: [String]
        let previewTitle: String
        let previewSubtitle: String?
        let selectivelyDisclosable: [String]

        init(from c: StoredCredential) {
            id = c.id; format = c.format; receivedAt = c.receivedAt
            issuerDid = c.issuerDid; subjectDid = c.subjectDid; type = c.type
            previewTitle = c.previewTitle; previewSubtitle = c.previewSubtitle
            selectivelyDisclosable = c.selectivelyDisclosable
        }
    }

    private func loadIndex() -> [CredentialIndexEntry] {
        guard let data = defaults.data(forKey: Self.indexKey) else { return [] }
        return (try? JSONDecoder().decode([CredentialIndexEntry].self, from: data)) ?? []
    }

    private func saveIndex(_ entries: [CredentialIndexEntry]) {
        guard let data = try? JSONEncoder().encode(entries) else { return }
        defaults.set(data, forKey: Self.indexKey)
    }

    // MARK: - Credential strings (Keychain)

    private func storeCredentialString(id: String, credential: String) throws {
        let deleteQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.credentialService,
            kSecAttrAccount as String: id,
        ]
        SecItemDelete(deleteQuery as CFDictionary)

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.credentialService,
            kSecAttrAccount as String: id,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
            kSecValueData as String: Data(credential.utf8),
        ]
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else { throw StorageError.keychainStoreFailed(status) }
    }

    private func loadCredentialString(id: String) throws -> String {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.credentialService,
            kSecAttrAccount as String: id,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecReturnData as String: true,
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { throw StorageError.credentialNotFound }
        guard status == errSecSuccess, let data = result as? Data,
              let s = String(data: data, encoding: .utf8) else {
            throw StorageError.keychainLoadFailed(status)
        }
        return s
    }

    private func deleteCredentialString(id: String) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.credentialService,
            kSecAttrAccount as String: id,
        ]
        SecItemDelete(query as CFDictionary)
    }
}
