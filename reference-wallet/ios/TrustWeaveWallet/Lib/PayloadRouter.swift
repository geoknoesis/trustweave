// PayloadRouter.swift — unified QR / CoreNFC / `.onOpenURL` classifier for iOS.
// Mirrors Expo's `qrRouter.ts` and Android's `PayloadRouter.kt`.

import Foundation

enum IncomingOffer {
    case trustWeaveDirect(issuerUrl: String, credentialEndpoint: String, studentId: String?)
    case oid4VciPreAuthorized(issuerBase: String, preAuthorizedCode: String, credentialConfigurationIds: [String])
}

struct PresentationRequestPayload {
    let verifierUrl: String
    let requestEndpoint: String
    let verifyEndpoint: String

    init(
        verifierUrl: String,
        requestEndpoint: String = "/api/demo-verifier/request",
        verifyEndpoint: String = "/api/demo-verifier/verify"
    ) {
        self.verifierUrl = verifierUrl
        self.requestEndpoint = requestEndpoint
        self.verifyEndpoint = verifyEndpoint
    }
}

enum RoutedPayload {
    case offer(IncomingOffer, raw: String)
    case presentation(PresentationRequestPayload, raw: String)
    case publicVerify(url: URL, raw: String)
    case holderDid(did: String, raw: String)
    case unknown(reason: String, raw: String)
}

enum PayloadRouter {

    static func unwrapDeepLink(_ input: String) -> String {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.lowercased().hasPrefix("trustweavewallet://"),
              let components = URLComponents(string: trimmed),
              let items = components.queryItems else {
            return trimmed
        }
        for key in ["data", "url", "offer", "request"] {
            if let value = items.first(where: { $0.name == key })?.value, !value.isEmpty {
                return value.trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }
        return trimmed
    }

    static func classify(_ input: String) -> RoutedPayload {
        let raw = unwrapDeepLink(input)
        guard !raw.isEmpty else {
            return .unknown(reason: "No code or link was detected. Try scanning again.", raw: raw)
        }

        if let offer = parseIncomingOffer(raw) {
            return .offer(offer, raw: raw)
        }
        if let verifyUrl = parsePublicVerifyUrl(raw) {
            return .publicVerify(url: verifyUrl, raw: raw)
        }
        if let presentation = parsePresentationRequest(raw) {
            return .presentation(presentation, raw: raw)
        }
        if let data = raw.data(using: .utf8),
           let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
           (obj["type"] as? String) == "trustweave-holder",
           let did = obj["did"] as? String {
            return .holderDid(did: did, raw: raw)
        }
        if raw.hasPrefix("did:") {
            return .holderDid(did: raw, raw: raw)
        }
        return .unknown(
            reason: "This QR code is not a supported credential offer or verification request.",
            raw: raw
        )
    }

    static func parseIncomingOffer(_ raw: String) -> IncomingOffer? {
        if let oid = parseOid4VciOffer(raw) { return oid }
        return parseDirectOffer(raw)
    }

    private static func parseOid4VciOffer(_ raw: String) -> IncomingOffer? {
        guard raw.lowercased().hasPrefix("openid-credential-offer://") else { return nil }
        let normalized = "https://oid4vci.local/" + raw.dropFirst("openid-credential-offer://".count)
        guard let components = URLComponents(string: normalized),
              let offerStr = components.queryItems?.first(where: { $0.name == "credential_offer" })?.value,
              let data = offerStr.data(using: .utf8),
              let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let issuer = obj["credential_issuer"] as? String,
              let grants = obj["grants"] as? [String: Any],
              let preAuth = grants["urn:ietf:params:oauth:grant-type:pre-authorized_code"] as? [String: Any],
              let code = preAuth["pre-authorized_code"] as? String else {
            return nil
        }
        let ids = (obj["credential_configuration_ids"] as? [String]) ?? []
        return .oid4VciPreAuthorized(
            issuerBase: issuer.trimmingCharacters(in: CharacterSet(charactersIn: "/")),
            preAuthorizedCode: code,
            credentialConfigurationIds: ids
        )
    }

    private static func parseDirectOffer(_ raw: String) -> IncomingOffer? {
        if raw.hasPrefix("http://") || raw.hasPrefix("https://"),
           let components = URLComponents(string: raw),
           components.path.contains("/issuer/"),
           let scheme = components.scheme,
           let host = components.host {
            let port = components.port.map { ":\($0)" } ?? ""
            let studentId = components.queryItems?.first(where: { $0.name == "studentId" || $0.name == "id" })?.value
            return .trustWeaveDirect(
                issuerUrl: "\(scheme)://\(host)\(port)",
                credentialEndpoint: "/api/demo-issuer/credential",
                studentId: studentId
            )
        }
        guard let data = raw.data(using: .utf8),
              let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              (obj["type"] as? String) == "trustweave-credential-offer",
              let issuerUrl = obj["issuerUrl"] as? String else {
            return nil
        }
        let endpoint = (obj["credentialEndpoint"] as? String) ?? "/api/demo-issuer/credential"
        return .trustWeaveDirect(
            issuerUrl: issuerUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")),
            credentialEndpoint: endpoint,
            studentId: obj["studentId"] as? String
        )
    }

    private static func parsePublicVerifyUrl(_ raw: String) -> URL? {
        guard (raw.hasPrefix("http://") || raw.hasPrefix("https://")),
              let url = URL(string: raw) else {
            return nil
        }
        return (url.path == "/verify" || url.path.hasPrefix("/verify/")) ? url : nil
    }

    static func parsePresentationRequest(_ raw: String) -> PresentationRequestPayload? {
        if raw.lowercased().hasPrefix("openid4vp://") {
            let normalized = "https://openid4vp.local/" + raw.dropFirst("openid4vp://".count)
            guard let components = URLComponents(string: normalized) else { return nil }
            if let reqUri = components.queryItems?.first(where: { $0.name == "request_uri" })?.value,
               let url = URL(string: reqUri),
               let scheme = url.scheme,
               let host = url.host {
                let port = url.port.map { ":\($0)" } ?? ""
                let query = url.query.map { "?\($0)" } ?? ""
                return PresentationRequestPayload(
                    verifierUrl: "\(scheme)://\(host)\(port)",
                    requestEndpoint: "\(url.path)\(query)"
                )
            }
            if let respUri = components.queryItems?.first(where: { $0.name == "response_uri" })?.value,
               let url = URL(string: respUri),
               let scheme = url.scheme,
               let host = url.host {
                let port = url.port.map { ":\($0)" } ?? ""
                return PresentationRequestPayload(
                    verifierUrl: "\(scheme)://\(host)\(port)",
                    verifyEndpoint: url.path
                )
            }
            return nil
        }

        if (raw.hasPrefix("http://") || raw.hasPrefix("https://")),
           let components = URLComponents(string: raw),
           let scheme = components.scheme,
           let host = components.host,
           (components.path == "/verifier" || components.path.hasPrefix("/verifier/")) {
            let port = components.port.map { ":\($0)" } ?? ""
            return PresentationRequestPayload(verifierUrl: "\(scheme)://\(host)\(port)")
        }

        guard let data = raw.data(using: .utf8),
              let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              (obj["type"] as? String) == "trustweave-presentation-request",
              let verifierUrl = obj["verifierUrl"] as? String else {
            return nil
        }
        return PresentationRequestPayload(
            verifierUrl: verifierUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")),
            requestEndpoint: (obj["requestEndpoint"] as? String) ?? "/api/demo-verifier/request",
            verifyEndpoint: (obj["verifyEndpoint"] as? String) ?? "/api/demo-verifier/verify"
        )
    }

    static func resolveHostUrl(_ url: String, fallbackBaseUrl: String) -> String {
        let clean = url.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard let components = URLComponents(string: clean),
              let host = components.host,
              ["localhost", "127.0.0.1", "0.0.0.0", "::1"].contains(host),
              let fb = URLComponents(string: fallbackBaseUrl),
              let fbHost = fb.host,
              let scheme = components.scheme else {
            return clean
        }
        let port = (components.port ?? fb.port).map { ":\($0)" } ?? ""
        return "\(scheme)://\(fbHost)\(port)"
    }
}
