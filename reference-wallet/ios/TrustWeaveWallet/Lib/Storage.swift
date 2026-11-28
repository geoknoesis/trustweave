// Storage.swift
//
// Credential, accepted-issuer, activity, and preferences storage on iOS.
// Credentials live in Keychain (`WhenUnlockedThisDeviceOnly`); lightweight metadata
// indices and preferences live in UserDefaults.

import Foundation
import Security

struct StoredCredential: Codable, Identifiable {
    let id: String
    let format: String
    let credential: String
    let receivedAt: String
    let issuerDid: String
    let subjectDid: String
    let type: [String]
    let previewTitle: String
    let previewSubtitle: String?
    let selectivelyDisclosable: [String]
    let trustSource: String
    let issuerName: String?

    init(
        id: String,
        format: String,
        credential: String,
        receivedAt: String,
        issuerDid: String,
        subjectDid: String,
        type: [String],
        previewTitle: String,
        previewSubtitle: String?,
        selectivelyDisclosable: [String],
        trustSource: String = "confirmed",
        issuerName: String? = nil
    ) {
        self.id = id
        self.format = format
        self.credential = credential
        self.receivedAt = receivedAt
        self.issuerDid = issuerDid
        self.subjectDid = subjectDid
        self.type = type
        self.previewTitle = previewTitle
        self.previewSubtitle = previewSubtitle
        self.selectivelyDisclosable = selectivelyDisclosable
        self.trustSource = trustSource
        self.issuerName = issuerName
    }
}

struct ActivityEntry: Codable, Identifiable {
    let id: String
    let timestamp: String
    let kind: String // "added", "shared", "removed", "trusted_issuer", "backup", "restored"
    let title: String
    let subtitle: String?
    let counterparty: String?
    let detailsShared: Int?
    let totalDetails: Int?
    let success: Bool
}

struct WalletPreferences: Codable {
    var onboardingComplete: Bool = false
    var biometricLockEnabled: Bool = true
    var developerMode: Bool = false
}

enum StorageError: LocalizedError {
    case keychainStoreFailed(OSStatus)
    case keychainLoadFailed(OSStatus)
    case credentialNotFound

    var errorDescription: String? {
        switch self {
        case .keychainStoreFailed(let status): return "Could not save securely to Keychain (\(status))."
        case .keychainLoadFailed(let status): return "Could not read securely from Keychain (\(status))."
        case .credentialNotFound: return "Credential could not be found in storage."
        }
    }
}

final class Storage {

    private static let credentialService = "org.trustweave.referencewallet.credentials"
    private static let indexKey = "trustweave.wallet.credential-index"
    private static let acceptedIssuersKey = "trustweave.wallet.accepted-issuers"
    private static let activityKey = "trustweave.wallet.activity-v1"
    private static let prefsKey = "trustweave.wallet.prefs-v1"
    private static let maxActivity = 100

    private let defaults = UserDefaults.standard

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
                trustSource: meta.trustSource ?? "confirmed",
                issuerName: meta.issuerName
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

    func loadAcceptedIssuers() -> [String] {
        guard let data = defaults.data(forKey: Self.acceptedIssuersKey),
              let raw = try? JSONDecoder().decode([String].self, from: data) else {
            return []
        }
        return IssuerTrust.sanitize(raw)
    }

    func addAcceptedIssuer(_ did: String) throws {
        let next = try IssuerTrust.withAccepted(loadAcceptedIssuers(), did: did)
        if let data = try? JSONEncoder().encode(next) {
            defaults.set(data, forKey: Self.acceptedIssuersKey)
        }
    }

    func removeAcceptedIssuer(_ did: String) {
        let next = loadAcceptedIssuers().filter { $0 != did }
        if let data = try? JSONEncoder().encode(next) {
            defaults.set(data, forKey: Self.acceptedIssuersKey)
        }
    }

    func loadActivity() -> [ActivityEntry] {
        guard let data = defaults.data(forKey: Self.activityKey),
              let items = try? JSONDecoder().decode([ActivityEntry].self, from: data) else {
            return []
        }
        return items
    }

    func appendActivity(_ entry: ActivityEntry) {
        let next = Array(([entry] + loadActivity()).prefix(Self.maxActivity))
        if let data = try? JSONEncoder().encode(next) {
            defaults.set(data, forKey: Self.activityKey)
        }
    }

    func clearActivity() {
        defaults.removeObject(forKey: Self.activityKey)
    }

    func loadPreferences() -> WalletPreferences {
        guard let data = defaults.data(forKey: Self.prefsKey),
              let prefs = try? JSONDecoder().decode(WalletPreferences.self, from: data) else {
            return WalletPreferences()
        }
        return prefs
    }

    func savePreferences(_ prefs: WalletPreferences) {
        if let data = try? JSONEncoder().encode(prefs) {
            defaults.set(data, forKey: Self.prefsKey)
        }
    }

    func reset() {
        for meta in loadIndex() { try? deleteCredentialString(id: meta.id) }
        defaults.removeObject(forKey: Self.indexKey)
        defaults.removeObject(forKey: Self.acceptedIssuersKey)
        defaults.removeObject(forKey: Self.activityKey)
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
        let trustSource: String?
        let issuerName: String?

        init(from c: StoredCredential) {
            id = c.id; format = c.format; receivedAt = c.receivedAt
            issuerDid = c.issuerDid; subjectDid = c.subjectDid; type = c.type
            previewTitle = c.previewTitle; previewSubtitle = c.previewSubtitle
            selectivelyDisclosable = c.selectivelyDisclosable
            trustSource = c.trustSource; issuerName = c.issuerName
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
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
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
