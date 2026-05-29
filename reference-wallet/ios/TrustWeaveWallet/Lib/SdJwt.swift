// SdJwt.swift
//
// SD-JWT VC encode / decode / present. Mirror of:
//  - reference-wallet/lib/sdjwt.ts (web)
//  - reference-wallet/android/app/.../lib/SdJwt.kt + shared/.../SdJwtVc.kt (Android)
//
// Spec: draft-ietf-oauth-sd-jwt-vc.
// Format: <issuer-jwt>~<disclosure>~<disclosure>~...~<kb-jwt>

import Foundation

enum SdJwtError: Error {
    case emptyCredential
    case malformedDisclosure(String)
    case malformedJwt
}

struct Disclosure {
    let raw: String     // base64url([salt, name, value])
    let hash: String    // base64url(SHA-256(raw))
    let salt: String
    let name: String
    let value: Any      // JSON-decoded value
}

struct DecodedSdJwtVc {
    let issuerJwt: String
    let issuerPayload: [String: Any]
    let disclosures: [Disclosure]
    let kbJwt: String?
}

enum SdJwt {

    static func disclosureHash(_ disclosure: String) -> String {
        return Crypto.base64UrlEncode(Crypto.sha256(disclosure))
    }

    static func randomSalt() -> String {
        var bytes = [UInt8](repeating: 0, count: 16)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        precondition(status == errSecSuccess, "SecRandomCopyBytes failed: \(status)")
        return Crypto.base64UrlEncode(Data(bytes))
    }

    /// Decode (without verifying) an SD-JWT VC into its constituent parts.
    static func decode(_ sdJwtVc: String) throws -> DecodedSdJwtVc {
        let parts = sdJwtVc.split(separator: "~", omittingEmptySubsequences: false).map(String.init)
        guard !parts.isEmpty else { throw SdJwtError.emptyCredential }
        let issuerJwt = parts[0]
        var lastIdx = parts.count - 1
        let kbJwt: String? = parts[lastIdx].isEmpty ? nil : {
            let kb = parts[lastIdx]; lastIdx -= 1; return kb
        }()
        let disclosureSegments = lastIdx >= 1 ? parts[1...lastIdx].filter { !$0.isEmpty } : []
        let disclosures = try disclosureSegments.map { try parseDisclosure($0) }

        let jwtParts = issuerJwt.split(separator: ".", omittingEmptySubsequences: false)
        guard jwtParts.count == 3,
              let payloadData = Crypto.base64UrlDecode(String(jwtParts[1])),
              let payload = try? JSONSerialization.jsonObject(with: payloadData) as? [String: Any] else {
            throw SdJwtError.malformedJwt
        }
        return DecodedSdJwtVc(issuerJwt: issuerJwt, issuerPayload: payload, disclosures: disclosures, kbJwt: kbJwt)
    }

    static func parseDisclosure(_ raw: String) throws -> Disclosure {
        guard let json = Crypto.base64UrlDecodeString(raw),
              let arr = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [Any],
              arr.count == 3,
              let salt = arr[0] as? String,
              let name = arr[1] as? String else {
            throw SdJwtError.malformedDisclosure(raw)
        }
        return Disclosure(raw: raw, hash: disclosureHash(raw), salt: salt, name: name, value: arr[2])
    }

    /// Build a holder presentation: choose disclosures + append KB-JWT.
    static func present(
        sdJwtVc: String,
        selectDisclose: Set<String>,
        holderSigner: (Data) -> Data,
        holderDid: String,
        audience: String,
        nonce: String,
        nowEpochSeconds: Int64
    ) throws -> String {
        let decoded = try decode(sdJwtVc)
        let selected = decoded.disclosures.filter { selectDisclose.contains($0.name) }
        let prefix = ([decoded.issuerJwt] + selected.map { $0.raw } + [""]).joinedTilde()
        let sdHash = Crypto.base64UrlEncode(Crypto.sha256(prefix))
        let didTail = holderDid.hasPrefix("did:key:") ? String(holderDid.dropFirst("did:key:".count)) : holderDid
        let kbPayload: [String: Any] = [
            "iat": nowEpochSeconds,
            "aud": audience,
            "nonce": nonce,
            "sd_hash": sdHash,
        ]
        let payloadJson = String(data: try JSONSerialization.data(withJSONObject: kbPayload, options: [.sortedKeys]), encoding: .utf8) ?? "{}"
        let kbJwt = Crypto.signCompactJws(
            jsonPayload: payloadJson,
            kid: "\(holderDid)#\(didTail)",
            typ: "kb+jwt",
            signer: holderSigner,
        )
        return prefix + kbJwt
    }
}

private extension Array where Element == String {
    func joinedTilde() -> String { return joined(separator: "~") }
}
