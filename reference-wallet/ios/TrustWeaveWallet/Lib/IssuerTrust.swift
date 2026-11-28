// IssuerTrust.swift
//
// Fail-closed issuer trust policy for the iOS wallet.
// Mirrors Android's IssuerTrustPolicy.kt and Web's issuer-trust.ts.

import Foundation

struct IssuerTrustPolicy {
    private let checker: (String) -> Bool

    init(_ checker: @escaping (String) -> Bool) {
        self.checker = checker
    }

    func isTrusted(_ issuerDid: String) -> Bool {
        checker(issuerDid)
    }

    static let none = IssuerTrustPolicy { _ in false }

    static func allowList(_ trusted: Set<String>) -> IssuerTrustPolicy {
        IssuerTrustPolicy { trusted.contains($0) }
    }
}

struct UntrustedIssuerError: LocalizedError {
    let issuerDid: String
    let credentialTitle: String?

    var errorDescription: String? {
        "Issuer \(issuerDid) is not trusted by this wallet."
    }
}

enum IssuerTrust {
    static let maxAccepted = 200
    static let maxDidLength = 256

    static func isPlausibleDid(_ value: String) -> Bool {
        guard value.count <= maxDidLength else { return false }
        return value.range(of: "^did:[a-z0-9]+:[A-Za-z0-9._:%#-]+$", options: .regularExpression) != nil
    }

    static func sanitize(_ raw: [String]) -> [String] {
        var seen = Set<String>()
        var out: [String] = []
        for item in raw where isPlausibleDid(item) {
            if seen.insert(item).inserted {
                out.append(item)
                if out.count >= maxAccepted { break }
            }
        }
        return out
    }

    static func withAccepted(_ current: [String], did: String) throws -> [String] {
        guard isPlausibleDid(did) else {
            throw WalletError.invalidIssuerIdentifier
        }
        let clean = sanitize(current)
        if clean.contains(did) { return clean }
        guard clean.count < maxAccepted else {
            throw WalletError.acceptedIssuerLimitReached
        }
        return clean + [did]
    }

    static func policy(
        configured: Set<String>,
        backend: Set<String>,
        accepted: [String],
        confirmedIssuer: String? = nil
    ) -> IssuerTrustPolicy {
        var allowed = configured.union(backend).union(accepted)
        if let confirmedIssuer { allowed.insert(confirmedIssuer) }
        return .allowList(allowed)
    }

    /// Short scannable key fingerprint (e.g. `z6MkpT…vktH`) for trust dialogs and cards.
    static func shortDidFingerprint(_ did: String) -> String {
        guard !did.isEmpty else { return "Unknown" }
        let tail = did.split(separator: ":").last.map(String.init) ?? did
        guard tail.count > 12 else { return tail }
        return "\(tail.prefix(6))…\(tail.suffix(4))"
    }
}
