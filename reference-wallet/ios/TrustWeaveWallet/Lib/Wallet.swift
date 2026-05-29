// Wallet.swift
//
// Holder-side wallet facade. Mirrors:
//  - reference-wallet/lib/wallet.ts (web)
//  - reference-wallet/android/.../lib/Wallet.kt (Android)
//
// Phase 2.5d baseline:
//  - HolderKey lives in Keychain (CryptoKit Ed25519 + seed in kSecClassGenericPassword).
//  - Credentials live in Keychain (one item per credential); index in UserDefaults.

import Foundation

@MainActor
final class Wallet: ObservableObject {

    @Published private(set) var holderDid: String?
    @Published private(set) var credentials: [StoredCredential] = []

    private let storage = Storage()
    private var holderKey: HolderKey?

    /// First call generates the holder identity; subsequent calls load it.
    func bootstrap() throws {
        let key = try HolderKey.loadOrCreate()
        holderKey = key
        holderDid = key.did
        credentials = storage.loadCredentials()
    }

    func reset() throws {
        try HolderKey.reset()
        storage.reset()
        try bootstrap()
    }

    func deleteCredential(id: String) {
        storage.deleteCredential(id: id)
        credentials = storage.loadCredentials()
    }

    func receive(credential: String, format: String, selectivelyDisclosable: [String]) throws -> StoredCredential {
        let meta = try extractMeta(credential: credential, format: format)
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
        )
        try storage.addCredential(cred)
        credentials = storage.loadCredentials()
        return cred
    }

    func createPresentation(
        credentialId: String,
        audience: String,
        nonce: String,
        disclose: Set<String>,
    ) throws -> String {
        guard let key = holderKey, let did = holderDid else {
            throw WalletError.notBootstrapped
        }
        guard let cred = credentials.first(where: { $0.id == credentialId }) else {
            throw WalletError.credentialNotFound
        }
        let now = Int64(Date().timeIntervalSince1970)

        if cred.format == "vc+sd-jwt" {
            return try SdJwt.present(
                sdJwtVc: cred.credential,
                selectDisclose: disclose,
                holderSigner: { key.sign($0) },
                holderDid: did,
                audience: audience,
                nonce: nonce,
                nowEpochSeconds: now,
            )
        }

        // Legacy VP-JWT path — wraps the VC-JWT in a VP envelope, signed by the holder.
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
        return Crypto.signCompactJws(
            jsonPayload: json,
            kid: "\(did)#\(didTail)",
            signer: { key.sign($0) },
        )
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
                subtitle: (nameDisclosure?.value as? String),
            )
        }
        // VC-JWT path.
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

enum WalletError: Error {
    case notBootstrapped
    case credentialNotFound
    case malformedCredential
}
