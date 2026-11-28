// CredentialVerification.swift
//
// Fail-closed import verification for the iOS wallet.
// Mirrors Android's CredentialVerification.kt and Web's credential-verification.ts.

import Foundation

enum CredentialVerificationError: LocalizedError {
    case rejected(String)

    var errorDescription: String? {
        switch self {
        case .rejected(let message): return message
        }
    }
}

enum CredentialVerification {
    private static let maxBytes = 1_048_576
    private static let reservedDisclosureNames: Set<String> = [
        "iss", "sub", "iat", "nbf", "exp", "vct", "cnf", "_sd", "_sd_alg", "...",
        "__proto__", "constructor", "prototype",
    ]

    static func verifyImportedCredential(
        compact: String,
        format: String,
        holderDid: String,
        nowEpochSeconds: Int64,
        issuerPolicy: IssuerTrustPolicy = .none,
        requireHolderKeyBinding: Bool = false
    ) throws {
        guard format == "vc+jwt" || format == "vc+sd-jwt" else {
            throw CredentialVerificationError.rejected("Unsupported credential format.")
        }
        guard compact.utf8.count <= maxBytes else {
            throw CredentialVerificationError.rejected("Credential exceeds the 1 MiB import limit.")
        }
        if format == "vc+jwt" && compact.contains("~") {
            throw CredentialVerificationError.rejected("SD-JWT cannot be imported as a plain VC-JWT.")
        }
        if format == "vc+sd-jwt" && !compact.contains("~") {
            throw CredentialVerificationError.rejected("SD-JWT serialization requires a separator.")
        }

        let jwt = String(compact.split(separator: "~", maxSplits: 1, omittingEmptySubsequences: false).first ?? "")
        let parts = jwt.split(separator: ".", omittingEmptySubsequences: false).map(String.init)
        guard parts.count == 3,
              let headerData = Crypto.base64UrlDecode(parts[0]),
              let header = (try? JSONSerialization.jsonObject(with: headerData)) as? [String: Any],
              let payloadData = Crypto.base64UrlDecode(parts[1]),
              let untrusted = (try? JSONSerialization.jsonObject(with: payloadData)) as? [String: Any] else {
            throw CredentialVerificationError.rejected("Issuer credential is not a valid compact JWS.")
        }

        guard let iss = untrusted["iss"] as? String, !iss.isEmpty else {
            throw CredentialVerificationError.rejected("Credential issuer is missing.")
        }
        guard (header["alg"] as? String) == "EdDSA",
              header["crit"] == nil,
              header["b64"] == nil else {
            throw CredentialVerificationError.rejected("Unsupported issuer signature profile.")
        }
        if let typ = header["typ"] as? String, typ != "JWT" && typ != format {
            throw CredentialVerificationError.rejected("Unsupported credential token type.")
        }
        let canonicalKid = "\(iss)#\(iss.dropFirst("did:key:".count))"
        if let kid = header["kid"] as? String, kid != iss && kid != canonicalKid {
            throw CredentialVerificationError.rejected("Issuer signing key does not match the credential issuer.")
        }

        guard let issuerPublicKey = Crypto.didKeyToPublicKey(iss) else {
            throw CredentialVerificationError.rejected("Only Ed25519 did:key issuers are supported.")
        }
        guard let verifiedPayloadData = Crypto.verifyCompactJws(jwt, publicKey: issuerPublicKey),
              let payload = (try? JSONSerialization.jsonObject(with: verifiedPayloadData)) as? [String: Any] else {
            throw CredentialVerificationError.rejected("Issuer signature is invalid.")
        }

        let titleHint: String? = (payload["vct"] as? String)
            ?? ((payload["vc"] as? [String: Any])?["type"] as? [String])?.first(where: { $0 != "VerifiableCredential" })

        guard issuerPolicy.isTrusted(iss) else {
            throw UntrustedIssuerError(issuerDid: iss, credentialTitle: titleHint)
        }

        guard let sub = payload["sub"] as? String, !sub.isEmpty else {
            throw CredentialVerificationError.rejected("Credential holder is missing.")
        }
        guard sub == holderDid else {
            throw CredentialVerificationError.rejected("This credential was issued to a different wallet identity.")
        }

        if format == "vc+jwt" {
            guard let vc = payload["vc"] as? [String: Any],
                  payload["vct"] == nil,
                  payload["_sd"] == nil else {
                throw CredentialVerificationError.rejected("Unsupported VC-JWT payload profile.")
            }
            guard let types = vc["type"] as? [String], !types.isEmpty, types.allSatisfy({ !$0.isEmpty }) else {
                throw CredentialVerificationError.rejected("Invalid credential types.")
            }
            if requireHolderKeyBinding {
                guard let cnf = payload["cnf"] as? [String: Any],
                      (cnf["kid"] as? String) == sub,
                      cnf.keys.allSatisfy({ $0 == "kid" }) else {
                    throw CredentialVerificationError.rejected("Credential has no holder key binding.")
                }
            }
            if let vcIssuer = vc["issuer"] as? String, vcIssuer != iss {
                throw CredentialVerificationError.rejected("Conflicting credential issuer.")
            } else if let vcIssuerObj = vc["issuer"] as? [String: Any],
                      let id = vcIssuerObj["id"] as? String, id != iss {
                throw CredentialVerificationError.rejected("Conflicting credential issuer.")
            }
            if let subject = vc["credentialSubject"] as? [String: Any],
               let subjectId = subject["id"] as? String, subjectId != sub {
                throw CredentialVerificationError.rejected("Conflicting credential holder.")
            }
        } else {
            guard let vct = payload["vct"] as? String, !vct.isEmpty, payload["vc"] == nil else {
                throw CredentialVerificationError.rejected("Unsupported SD-JWT payload profile.")
            }
            guard let cnf = payload["cnf"] as? [String: Any],
                  (cnf["kid"] as? String) == sub,
                  cnf.keys.allSatisfy({ $0 == "kid" }) else {
                throw CredentialVerificationError.rejected("Unsupported or conflicting holder key binding.")
            }
        }

        let exp = try parseEpochClaim(payload["exp"])
        let nbf = try parseEpochClaim(payload["nbf"])
        let iat = try parseEpochClaim(payload["iat"])
        if let exp, exp <= nowEpochSeconds {
            throw CredentialVerificationError.rejected("This credential has expired.")
        }
        if let nbf, nbf > nowEpochSeconds {
            throw CredentialVerificationError.rejected("This credential is not valid yet.")
        }
        if let iat, iat > nowEpochSeconds {
            throw CredentialVerificationError.rejected("Credential issue time is in the future.")
        }
        if let exp, (nbf != nil && nbf! >= exp) || (iat != nil && iat! >= exp) {
            throw CredentialVerificationError.rejected("Inconsistent credential validity interval.")
        }

        if format == "vc+sd-jwt" {
            try verifyDisclosures(compact: compact, payload: payload)
        }
    }

    private static func verifyDisclosures(compact: String, payload: [String: Any]) throws {
        if let alg = payload["_sd_alg"] as? String, alg != "sha-256" {
            throw CredentialVerificationError.rejected("Unsupported disclosure digest algorithm.")
        }
        let decoded = try SdJwt.decode(compact)
        if decoded.kbJwt != nil {
            throw CredentialVerificationError.rejected("Import the issuer credential, not a holder presentation.")
        }

        var digests = Set<String>()
        func collect(_ node: Any, depth: Int) throws {
            guard depth <= 32 else {
                throw CredentialVerificationError.rejected("Credential nesting exceeds 32 levels.")
            }
            if let arr = node as? [Any] {
                for item in arr { try collect(item, depth: depth + 1) }
            } else if let dict = node as? [String: Any] {
                for (key, value) in dict {
                    if key == "..." {
                        throw CredentialVerificationError.rejected("Array selective disclosures are not supported.")
                    }
                    if key == "_sd" {
                        guard depth == 0, let items = value as? [String] else {
                            throw CredentialVerificationError.rejected("Nested or invalid selective disclosures are not supported.")
                        }
                        for digest in items {
                            guard digests.insert(digest).inserted else {
                                throw CredentialVerificationError.rejected("Duplicate disclosure digest.")
                            }
                        }
                    } else {
                        try collect(value, depth: depth + 1)
                    }
                }
            }
        }
        try collect(payload, depth: 0)

        var seenNames = Set<String>()
        for disclosure in decoded.disclosures {
            guard digests.contains(disclosure.hash),
                  seenNames.insert(disclosure.name).inserted,
                  !reservedDisclosureNames.contains(disclosure.name),
                  payload[disclosure.name] == nil else {
                throw CredentialVerificationError.rejected("Disclosure is not uniquely bound to the issuer signature.")
            }
            try collect(disclosure.value, depth: 1)
        }
    }

    private static func parseEpochClaim(_ value: Any?) throws -> Int64? {
        guard let value else { return nil }
        if let num = value as? NSNumber, CFGetTypeID(num) != CFBooleanGetTypeID() {
            let d = num.doubleValue
            guard d.isFinite else { throw CredentialVerificationError.rejected("Invalid credential time claim.") }
            return Int64(d)
        }
        throw CredentialVerificationError.rejected("Invalid credential time claim.")
    }
}
