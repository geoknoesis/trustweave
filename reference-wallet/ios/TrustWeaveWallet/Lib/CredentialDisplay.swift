// CredentialDisplay.swift
//
// Plain-language formatting, SF Symbols mapping, pass accents, portraits,
// relative timestamps, honest status badges, and actual claim value extraction.

import Foundation
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

enum BadgeTone {
    case success, info, warning, danger, neutral
}

struct StatusBadge {
    let label: String
    let tone: BadgeTone
    let symbolName: String
}

struct DisplayClaim: Identifiable {
    let id: String
    let label: String
    let value: String
    let shareable: Bool
}

struct ShareableClaimDetail: Identifiable {
    var id: String { name }
    let name: String
    let label: String
    let value: String?
}

enum CredentialDisplay {
    private static let envelopeKeys: Set<String> = [
        "iss", "sub", "aud", "iat", "exp", "nbf", "jti", "cnf",
        "vct", "vc", "type", "@context", "id", "_sd", "_sd_alg", "status", "proof",
    ]

    private static let passAccents: [Color] = [
        Color(red: 0.118, green: 0.227, blue: 0.541), // #1E3A8A
        Color(red: 0.059, green: 0.463, blue: 0.431), // #0F766E
        Color(red: 0.427, green: 0.157, blue: 0.851), // #6D28D9
        Color(red: 0.012, green: 0.412, blue: 0.631), // #0369A1
        Color(red: 0.706, green: 0.325, blue: 0.035), // #B45309
    ]

    static func passAccent(for title: String) -> Color {
        var hash = 0
        for (i, scalar) in title.unicodeScalars.enumerated() {
            hash = (hash + Int(scalar.value) * (i + 1)) % passAccents.count
        }
        return passAccents[max(0, hash % passAccents.count)]
    }

    static func humanClaimName(_ raw: String) -> String {
        let spaced = raw
            .replacingOccurrences(of: "([a-z])([A-Z])", with: "$1 $2", options: .regularExpression)
            .replacingOccurrences(of: "_", with: " ")
        return spaced
            .split(separator: " ")
            .map { $0.prefix(1).uppercased() + $0.dropFirst() }
            .joined(separator: " ")
    }

    static func formatRelativeTime(_ iso: String, now: Date = Date()) -> String {
        let formatter = ISO8601DateFormatter()
        guard let date = formatter.date(from: iso) else { return String(iso.prefix(10)) }
        let diff = max(0, Int(now.timeIntervalSince(date)))
        if diff < 60 { return "Just now" }
        if diff < 3600 { return "\(diff / 60)m ago" }
        if diff < 86_400 { return "\(diff / 3600)h ago" }
        if diff < 7 * 86_400 { return "\(diff / 86_400)d ago" }
        return DateFormatter.localizedString(from: date, dateStyle: .medium, timeStyle: .none)
    }

    /// Honest issuer label — never calls an arbitrary `did:key:` "Verified issuer".
    static func formatIssuerLabel(_ issuerDid: String, issuerName: String? = nil) -> String {
        if let name = issuerName?.trimmingCharacters(in: .whitespacesAndNewlines), !name.isEmpty {
            return name
        }
        if issuerDid.hasPrefix("did:web:") {
            let host = String(issuerDid.dropFirst("did:web:".count)).split(separator: ":").first.map(String.init)
            if let host, !host.isEmpty { return host }
        }
        if issuerDid.hasPrefix("did:") {
            return "Issuer · \(IssuerTrust.shortDidFingerprint(issuerDid))"
        }
        return issuerDid.count > 28 ? "\(issuerDid.prefix(28))…" : issuerDid
    }

    static func symbolName(types: [String], title: String) -> String {
        let haystack = (types.joined(separator: " ") + " " + title).lowercased()
        if haystack.range(of: "degree|diploma|university|student|education|bachelor|master", options: .regularExpression) != nil {
            return "graduationcap.fill"
        }
        if haystack.range(of: "faa|drone|airspace|pilot|aviation|uas|flight", options: .regularExpression) != nil {
            return "airplane"
        }
        if haystack.range(of: "identity|passport|driver|license|licence|pid|cac|employee|member", options: .regularExpression) != nil {
            return "person.text.rectangle.fill"
        }
        if haystack.range(of: "clearance|security|auth|access|permit|gate", options: .regularExpression) != nil {
            return "checkmark.shield.fill"
        }
        if haystack.range(of: "cert|award|badge|accomplishment", options: .regularExpression) != nil {
            return "rosette"
        }
        return "creditcard.fill"
    }

    static func extractPortraitData(_ cred: StoredCredential) -> Data? {
        let raw = extractRawClaimMap(cred)
        for (key, value) in raw {
            guard let text = value as? String else { continue }
            let isPortraitKey = key.range(of: "portrait|photo|image|picture|avatar", options: [.regularExpression, .caseInsensitive]) != nil
            let isDataImage = text.lowercased().hasPrefix("data:image/")
            guard isPortraitKey || isDataImage else { continue }
            let b64 = text.contains(",") ? String(text.split(separator: ",", maxSplits: 1).last ?? "") : text
            if let data = Data(base64Encoded: b64.trimmingCharacters(in: .whitespacesAndNewlines)), !data.isEmpty {
                return data
            }
        }
        return nil
    }

    #if canImport(UIKit)
    static func extractPortraitImage(_ cred: StoredCredential) -> UIImage? {
        guard let data = extractPortraitData(cred) else { return nil }
        return UIImage(data: data)
    }
    #endif

    static func extractExpiryEpochSeconds(_ cred: StoredCredential) -> Int64? {
        let payload: [String: Any]?
        if cred.format == "vc+sd-jwt" {
            payload = try? SdJwt.decode(cred.credential).issuerPayload
        } else {
            let parts = cred.credential.split(separator: ".", omittingEmptySubsequences: false)
            if parts.count == 3,
               let data = Crypto.base64UrlDecode(String(parts[1])) {
                payload = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            } else {
                payload = nil
            }
        }
        guard let num = payload?["exp"] as? NSNumber else { return nil }
        return Int64(num.doubleValue)
    }

    static func statusBadge(for cred: StoredCredential, nowEpochSeconds: Int64 = Int64(Date().timeIntervalSince1970)) -> StatusBadge {
        if let exp = extractExpiryEpochSeconds(cred) {
            if exp <= nowEpochSeconds {
                return StatusBadge(label: "Expired", tone: .danger, symbolName: "exclamationmark.circle.fill")
            }
            if exp - nowEpochSeconds < 30 * 24 * 3600 {
                return StatusBadge(label: "Expiring soon", tone: .warning, symbolName: "clock.fill")
            }
        }
        switch cred.trustSource {
        case "configured", "backend":
            return StatusBadge(label: "Trusted issuer", tone: .success, symbolName: "checkmark.shield.fill")
        case "confirmed":
            return StatusBadge(label: "Confirmed by you", tone: .info, symbolName: "checkmark.circle.fill")
        default:
            return StatusBadge(label: "Saved on device", tone: .neutral, symbolName: "doc.fill")
        }
    }

    static func extractClaimsForDisplay(_ cred: StoredCredential) -> [DisplayClaim] {
        let raw = extractRawClaimMap(cred)
        let shareableSet = Set(cred.selectivelyDisclosable)
        return raw.keys.sorted().compactMap { key in
            if envelopeKeys.contains(key) { return nil }
            if key.range(of: "portrait|photo|image|picture|avatar", options: [.regularExpression, .caseInsensitive]) != nil {
                return nil
            }
            guard let text = renderValue(raw[key]), !text.hasPrefix("data:image/") else { return nil }
            return DisplayClaim(
                id: key,
                label: humanClaimName(key),
                value: text,
                shareable: shareableSet.contains(key)
            )
        }
    }

    static func extractShareableClaimDetails(_ cred: StoredCredential) -> [ShareableClaimDetail] {
        let raw = extractRawClaimMap(cred)
        return cred.selectivelyDisclosable.map { name in
            ShareableClaimDetail(
                name: name,
                label: humanClaimName(name),
                value: renderValue(raw[name])
            )
        }
    }

    private static func extractRawClaimMap(_ cred: StoredCredential) -> [String: Any] {
        if cred.format == "vc+sd-jwt", let decoded = try? SdJwt.decode(cred.credential) {
            var map = decoded.issuerPayload
            for d in decoded.disclosures { map[d.name] = d.value }
            return map
        }
        let parts = cred.credential.split(separator: ".", omittingEmptySubsequences: false)
        if parts.count == 3,
           let data = Crypto.base64UrlDecode(String(parts[1])),
           let payload = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
           let vc = payload["vc"] as? [String: Any],
           let subject = vc["credentialSubject"] as? [String: Any] {
            return subject
        }
        return [:]
    }

    private static func renderValue(_ raw: Any?) -> String? {
        guard let raw else { return nil }
        if let s = raw as? String { return s.isEmpty ? nil : s }
        if let b = raw as? Bool { return b ? "Yes" : "No" }
        if let n = raw as? NSNumber { return n.stringValue }
        if let arr = raw as? [Any] {
            let joined = arr.compactMap { renderValue($0) }.joined(separator: ", ")
            return joined.isEmpty ? nil : joined
        }
        if let dict = raw as? [String: Any] {
            let joined = dict.values.compactMap { renderValue($0) }.joined(separator: " · ")
            return joined.isEmpty ? nil : joined
        }
        return nil
    }
}

#if canImport(UIKit)
extension Color {
    /// Adaptive TrustWeave primary tint that passes WCAG AA in both Light Mode and Dark Mode.
    static var trustWeaveAccent: Color {
        Color(uiColor: UIColor { traits in
            if traits.userInterfaceStyle == .dark {
                return UIColor(red: 0.376, green: 0.647, blue: 0.980, alpha: 1.0) // #60A5FA
            }
            return UIColor(red: 0.118, green: 0.227, blue: 0.541, alpha: 1.0) // #1E3A8A
        })
    }
}
#endif
