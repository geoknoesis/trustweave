// Wallet.swift
//
// Shared `@MainActor` ObservableObject wallet facade for iOS.
// Enforces fail-closed verification, issuer trust confirmation, activity logging,
// preferences, and credential-only backup/restore.

import Combine
import Foundation

struct CredentialBackupItem: Codable {
    let format: String
    let credential: String
    let selectivelyDisclosable: [String]
}

struct CredentialBackupPayload: Codable {
    let schema: String
    let exportedAt: String
    let holderDid: String
    let containsPrivateKeys: Bool
    let credentials: [CredentialBackupItem]
}

struct RestoreBackupResult {
    let restored: Int
    let skipped: Int
    let untrustedIssuers: [String]
}

@MainActor
final class Wallet: ObservableObject {

    @Published private(set) var holderDid: String?
    @Published private(set) var credentials: [StoredCredential] = []
    @Published private(set) var acceptedIssuers: [String] = []
    @Published private(set) var activity: [ActivityEntry] = []
    @Published var preferences: WalletPreferences = WalletPreferences()
    @Published var preselectedCredentialId: String?
    @Published var pendingOffer: IncomingOffer?
    @Published var pendingPresentation: PresentationRequestPayload?

    let configuredIssuers: Set<String>
    private let storage = Storage()
    private var holderKey: HolderKey?

    init(configuredIssuers: Set<String> = []) {
        self.configuredIssuers = configuredIssuers
    }

    func bootstrap() throws {
        let key = try HolderKey.loadOrCreate()
        holderKey = key
        holderDid = key.did
        refreshPublishedState()
    }

    func refreshPublishedState() {
        credentials = storage.loadCredentials()
        acceptedIssuers = storage.loadAcceptedIssuers()
        activity = storage.loadActivity()
        preferences = storage.loadPreferences()
    }

    func updatePreferences(_ update: (inout WalletPreferences) -> Void) {
        var next = preferences
        update(&next)
        storage.savePreferences(next)
        preferences = next
    }

    func reset() throws {
        try HolderKey.reset()
        storage.reset()
        preselectedCredentialId = nil
        try bootstrap()
    }

    func deleteCredential(id: String) {
        let existing = credentials.first(where: { $0.id == id })
        storage.deleteCredential(id: id)
        if let existing {
            recordActivity(kind: "removed", title: existing.previewTitle, subtitle: existing.previewSubtitle)
        }
        refreshPublishedState()
    }

    func removeAcceptedIssuer(_ did: String) {
        storage.removeAcceptedIssuer(did)
        refreshPublishedState()
    }

    func clearActivity() {
        storage.clearActivity()
        refreshPublishedState()
    }

    func recordActivity(
        kind: String,
        title: String,
        subtitle: String? = nil,
        counterparty: String? = nil,
        detailsShared: Int? = nil,
        totalDetails: Int? = nil,
        success: Bool = true
    ) {
        let entry = ActivityEntry(
            id: UUID().uuidString,
            timestamp: ISO8601DateFormatter().string(from: Date()),
            kind: kind,
            title: title,
            subtitle: subtitle,
            counterparty: counterparty,
            detailsShared: detailsShared,
            totalDetails: totalDetails,
            success: success
        )
        storage.appendActivity(entry)
        activity = storage.loadActivity()
    }

    /// Fail-closed verified import matching Android and Web.
    /// Throws `UntrustedIssuerError` when an issuer is unknown; persists trust only after
    /// explicit confirmation AND passing every cryptographic check.
    func receive(
        credential: String,
        format: String,
        selectivelyDisclosable: [String],
        backendIssuers: Set<String> = [],
        confirmedIssuer: String? = nil
    ) throws -> StoredCredential {
        guard let did = holderDid else { throw WalletError.notBootstrapped }
        let existingAccepted = storage.loadAcceptedIssuers()
        let policy = IssuerTrust.policy(
            configured: configuredIssuers,
            backend: backendIssuers,
            accepted: existingAccepted,
            confirmedIssuer: confirmedIssuer
        )
        try CredentialVerification.verifyImportedCredential(
            compact: credential,
            format: format,
            holderDid: did,
            nowEpochSeconds: Int64(Date().timeIntervalSince1970),
            issuerPolicy: policy
        )

        let meta = try extractMeta(credential: credential, format: format)
        let trustSource: String
        if configuredIssuers.contains(meta.issuerDid) {
            trustSource = "configured"
        } else if backendIssuers.contains(meta.issuerDid) {
            trustSource = "backend"
        } else {
            trustSource = "confirmed"
        }

        let cred = StoredCredential(
            id: UUID().uuidString,
            format: format,
            credential: credential,
            receivedAt: ISO8601DateFormatter().string(from: Date()),
            issuerDid: meta.issuerDid,
            subjectDid: meta.subjectDid,
            type: meta.types,
            previewTitle: meta.title,
            previewSubtitle: meta.subtitle,
            selectivelyDisclosable: selectivelyDisclosable,
            trustSource: trustSource
        )
        try storage.addCredential(cred)

        let basePolicy = IssuerTrust.policy(
            configured: configuredIssuers,
            backend: backendIssuers,
            accepted: existingAccepted
        )
        if let confirmedIssuer, confirmedIssuer == meta.issuerDid, !basePolicy.isTrusted(confirmedIssuer) {
            try storage.addAcceptedIssuer(confirmedIssuer)
            recordActivity(
                kind: "trusted_issuer",
                title: "Trusted new issuer",
                subtitle: meta.title,
                counterparty: meta.issuerDid
            )
        }

        recordActivity(
            kind: "added",
            title: meta.title,
            subtitle: meta.subtitle,
            counterparty: meta.issuerDid
        )
        refreshPublishedState()
        return cred
    }

    func createPresentation(
        credentialId: String,
        audience: String,
        nonce: String,
        disclose: Set<String>,
        backendIssuers: Set<String> = []
    ) throws -> String {
        guard let key = holderKey, let did = holderDid else {
            throw WalletError.notBootstrapped
        }
        guard let cred = credentials.first(where: { $0.id == credentialId }) else {
            throw WalletError.credentialNotFound
        }

        let policy = IssuerTrust.policy(
            configured: configuredIssuers,
            backend: backendIssuers,
            accepted: storage.loadAcceptedIssuers()
        )
        let stillTrusted = policy.isTrusted(cred.issuerDid) || (cred.trustSource == "backend" && backendIssuers.isEmpty)
        guard stillTrusted else {
            throw WalletError.issuerRemoved
        }

        let now = Int64(Date().timeIntervalSince1970)

        if cred.format == "vc+sd-jwt" {
            return try SdJwt.present(
                sdJwtVc: cred.credential,
                selectDisclose: disclose,
                holderSigner: { try key.sign($0) },
                holderDid: did,
                audience: audience,
                nonce: nonce,
                nowEpochSeconds: now
            )
        }

        let vpPayload: [String: Any] = [
            "iss": did,
            "sub": did,
            "aud": audience,
            "nonce": nonce,
            "iat": now,
            "exp": now + 300,
            "vp": [
                "@context": ["https://www.w3.org/ns/credentials/v2"],
                "type": ["VerifiablePresentation"],
                "holder": did,
                "verifiableCredential": [cred.credential],
            ] as [String: Any],
        ]
        let json = String(data: try JSONSerialization.data(withJSONObject: vpPayload, options: [.sortedKeys]), encoding: .utf8) ?? "{}"
        let didTail = String(did.dropFirst("did:key:".count))
        return try Crypto.signCompactJws(
            jsonPayload: json,
            kid: "\(did)#\(didTail)",
            signer: { try key.sign($0) }
        )
    }

    /// Export credential records ONLY — never includes private key material.
    func exportCredentialBackup() throws -> String {
        guard let did = holderDid else { throw WalletError.notBootstrapped }
        let payload = CredentialBackupPayload(
            schema: "trustweave-credential-backup-v1",
            exportedAt: ISO8601DateFormatter().string(from: Date()),
            holderDid: did,
            containsPrivateKeys: false,
            credentials: credentials.map {
                CredentialBackupItem(
                    format: $0.format,
                    credential: $0.credential,
                    selectivelyDisclosable: $0.selectivelyDisclosable
                )
            }
        )
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let data = try encoder.encode(payload)
        recordActivity(
            kind: "backup",
            title: "Exported credential backup",
            subtitle: "\(payload.credentials.count) credential(s) · no private keys included"
        )
        return String(data: data, encoding: .utf8) ?? "{}"
    }

    func restoreCredentialBackup(
        rawJson: String,
        backendIssuers: Set<String> = [],
        confirmedIssuer: String? = nil
    ) throws -> RestoreBackupResult {
        guard let did = holderDid else { throw WalletError.notBootstrapped }
        guard let data = rawJson.data(using: .utf8),
              let payload = try? JSONDecoder().decode(CredentialBackupPayload.self, from: data),
              payload.schema == "trustweave-credential-backup-v1" else {
            throw WalletError.invalidBackupFormat
        }
        guard payload.holderDid == did else {
            throw WalletError.backupHolderMismatch
        }

        var restored = 0
        var skipped = 0
        var untrusted: [String] = []
        for item in payload.credentials {
            do {
                _ = try receive(
                    credential: item.credential,
                    format: item.format,
                    selectivelyDisclosable: item.selectivelyDisclosable,
                    backendIssuers: backendIssuers,
                    confirmedIssuer: confirmedIssuer
                )
                restored += 1
            } catch let err as UntrustedIssuerError {
                if !untrusted.contains(err.issuerDid) { untrusted.append(err.issuerDid) }
                skipped += 1
            } catch {
                skipped += 1
            }
        }
        if restored > 0 {
            recordActivity(
                kind: "restored",
                title: "Restored credential backup",
                subtitle: "\(restored) credential(s) verified and restored"
            )
        }
        return RestoreBackupResult(restored: restored, skipped: skipped, untrustedIssuers: untrusted)
    }

    // MARK: - Internal helpers

    private struct Meta {
        let issuerDid: String
        let subjectDid: String
        let types: [String]
        let title: String
        let subtitle: String?
    }

    private func extractMeta(credential: String, format: String) throws -> Meta {
        if format == "vc+sd-jwt" {
            let decoded = try SdJwt.decode(credential)
            let issuer = (decoded.issuerPayload["iss"] as? String) ?? ""
            let subject = (decoded.issuerPayload["sub"] as? String) ?? ""
            let vct = (decoded.issuerPayload["vct"] as? String) ?? "Credential"
            let nameDisclosure = decoded.disclosures.first { ["name", "degree", "title"].contains($0.name) }
            return Meta(
                issuerDid: issuer,
                subjectDid: subject,
                types: [vct],
                title: vct,
                subtitle: (nameDisclosure?.value as? String)
            )
        }
        let parts = credential.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 3,
              let payloadData = Crypto.base64UrlDecode(String(parts[1])),
              let payload = try? JSONSerialization.jsonObject(with: payloadData) as? [String: Any] else {
            throw WalletError.malformedCredential
        }
        let vc = payload["vc"] as? [String: Any]
        let issuer = (payload["iss"] as? String) ?? (vc?["issuer"] as? String) ?? ""
        let subject = (payload["sub"] as? String) ?? ""
        let typesRaw = vc?["type"]
        let types: [String]
        if let arr = typesRaw as? [String] { types = arr }
        else if let s = typesRaw as? String { types = [s] }
        else { types = ["VerifiableCredential"] }
        let title = types.first(where: { $0 != "VerifiableCredential" }) ?? "Credential"
        let subjectClaims = vc?["credentialSubject"] as? [String: Any]
        let subtitle = (subjectClaims?["name"] as? String)
            ?? (subjectClaims?["degree"] as? String)
            ?? (subjectClaims?["title"] as? String)
        return Meta(issuerDid: issuer, subjectDid: subject, types: types, title: title, subtitle: subtitle)
    }
}

enum WalletError: LocalizedError {
    case notBootstrapped
    case credentialNotFound
    case malformedCredential
    case invalidIssuerIdentifier
    case acceptedIssuerLimitReached
    case issuerRemoved
    case invalidBackupFormat
    case backupHolderMismatch

    var errorDescription: String? {
        switch self {
        case .notBootstrapped: return "Your wallet is still initializing. Please try again."
        case .credentialNotFound: return "The selected credential could not be found."
        case .malformedCredential: return "This credential is not in a supported format."
        case .invalidIssuerIdentifier: return "Not a valid issuer identifier."
        case .acceptedIssuerLimitReached: return "Maximum number of trusted issuers reached. Remove one in Settings first."
        case .issuerRemoved: return "This credential's issuer was removed from your trusted issuers list and can no longer be shared."
        case .invalidBackupFormat: return "Unrecognized credential backup format."
        case .backupHolderMismatch: return "This backup belongs to a different wallet key. Because backups never contain private keys, ask your issuers to reissue credentials for this device."
        }
    }
}
