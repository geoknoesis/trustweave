// DemoBackend.swift
//
// URLSession client supporting both the default demo backend AND dynamic issuer/verifier
// endpoints parsed from QR codes, CoreNFC tags, and `.onOpenURL` deep links.

import Foundation

struct CredentialOffer: Codable {
    let format: String
    let credential: String
    let issuer: String
    let selectivelyDisclosable: [String]?
}

struct PresentationRequestParams: Codable {
    let verifier: String
    let audience: String
    let nonce: String
    let acceptedTypes: [String]
}

struct VerificationCheck: Codable {
    let step: String
    let passed: Bool
    let detail: String?
}

struct VerifiedCredentialView: Codable {
    let type: [String]
    let issuer: String
    let subject: String
    let disclosedClaims: [String: JsonAny]
    let withheldClaimNames: [String]?
}

struct VerificationResponse: Codable {
    let valid: Bool
    let checks: [VerificationCheck]
    let holder: String?
    let credentials: [VerifiedCredentialView]?
}

private struct BackendIdentityResponse: Codable {
    let issuerDids: [String]
}

enum JsonAny: Codable {
    case string(String)
    case number(Double)
    case bool(Bool)
    case array([JsonAny])
    case object([String: JsonAny])
    case null

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { self = .null; return }
        if let v = try? c.decode(Bool.self) { self = .bool(v); return }
        if let v = try? c.decode(Double.self) { self = .number(v); return }
        if let v = try? c.decode(String.self) { self = .string(v); return }
        if let v = try? c.decode([JsonAny].self) { self = .array(v); return }
        if let v = try? c.decode([String: JsonAny].self) { self = .object(v); return }
        throw DecodingError.typeMismatch(JsonAny.self, .init(codingPath: decoder.codingPath, debugDescription: "Unknown JSON value"))
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        switch self {
        case .null: try c.encodeNil()
        case .bool(let v): try c.encode(v)
        case .number(let v): try c.encode(v)
        case .string(let v): try c.encode(v)
        case .array(let v): try c.encode(v)
        case .object(let v): try c.encode(v)
        }
    }

    var prettyString: String {
        switch self {
        case .string(let v): return v
        case .number(let v): return v == floor(v) ? String(Int64(v)) : String(v)
        case .bool(let v): return v ? "true" : "false"
        case .array, .object:
            if let data = try? JSONEncoder().encode(self), let s = String(data: data, encoding: .utf8) { return s }
            return "?"
        case .null: return "null"
        }
    }
}

enum DemoBackendError: LocalizedError {
    case invalidURL
    case httpStatus(Int)
    case emptyResponse

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "Configured server URL is invalid."
        case .httpStatus(let code): return "Server responded with HTTP \(code)."
        case .emptyResponse: return "Server returned an empty response."
        }
    }
}

final class DemoBackend {

    let baseURL: String

    init(baseURL: String = "http://localhost:3000") {
        self.baseURL = baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    }

    func receiveCredential(subjectDid: String) async throws -> CredentialOffer {
        guard var components = URLComponents(string: "\(baseURL)/api/demo-issuer/credential") else {
            throw DemoBackendError.invalidURL
        }
        components.queryItems = [URLQueryItem(name: "subject", value: subjectDid)]
        guard let url = components.url else { throw DemoBackendError.invalidURL }
        let (data, response) = try await URLSession.shared.data(from: url)
        try validateStatus(response)
        return try JSONDecoder().decode(CredentialOffer.self, from: data)
    }

    func claimIncomingOffer(_ offer: IncomingOffer, subjectDid: String) async throws -> CredentialOffer {
        switch offer {
        case .trustWeaveDirect(let issuerUrl, let endpoint, let studentId):
            let resolvedBase = PayloadRouter.resolveHostUrl(issuerUrl, fallbackBaseUrl: baseURL)
            let path = endpoint.hasPrefix("/") ? endpoint : "/\(endpoint)"
            guard var components = URLComponents(string: "\(resolvedBase)\(path)") else {
                throw DemoBackendError.invalidURL
            }
            var items = [URLQueryItem(name: "subject", value: subjectDid)]
            if let studentId { items.append(URLQueryItem(name: "studentId", value: studentId)) }
            components.queryItems = items
            guard let url = components.url else { throw DemoBackendError.invalidURL }
            let (data, response) = try await URLSession.shared.data(from: url)
            try validateStatus(response)
            return try JSONDecoder().decode(CredentialOffer.self, from: data)

        case .oid4VciPreAuthorized(let issuerBase, let code, _):
            let resolvedBase = PayloadRouter.resolveHostUrl(issuerBase, fallbackBaseUrl: baseURL)
            guard let url = URL(string: "\(resolvedBase)/api/public/credential-offer/redeem") else {
                throw DemoBackendError.invalidURL
            }
            var req = URLRequest(url: url)
            req.httpMethod = "POST"
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            let body: [String: String] = ["preAuthorizedCode": code, "subjectDid": subjectDid]
            req.httpBody = try JSONSerialization.data(withJSONObject: body)
            let (data, response) = try await URLSession.shared.data(for: req)
            try validateStatus(response)
            return try JSONDecoder().decode(CredentialOffer.self, from: data)
        }
    }

    /// Fetch the wallet's own configured backend issuer DIDs. Fails closed to an empty set on any error.
    func backendIssuers() async -> Set<String> {
        guard let url = URL(string: "\(baseURL)/api/demo-issuer/identity") else { return [] }
        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                return []
            }
            let decoded = try JSONDecoder().decode(BackendIdentityResponse.self, from: data)
            guard decoded.issuerDids.count <= 16, decoded.issuerDids.allSatisfy(IssuerTrust.isPlausibleDid) else {
                return []
            }
            return Set(decoded.issuerDids)
        } catch {
            return []
        }
    }

    func fetchPresentationRequest(target: PresentationRequestPayload? = nil) async throws -> PresentationRequestParams {
        let resolvedBase = target.map { PayloadRouter.resolveHostUrl($0.verifierUrl, fallbackBaseUrl: baseURL) } ?? baseURL
        let rawPath = target?.requestEndpoint ?? "/api/demo-verifier/request"
        let path = rawPath.hasPrefix("/") ? rawPath : "/\(rawPath)"
        guard let url = URL(string: "\(resolvedBase)\(path)") else {
            throw DemoBackendError.invalidURL
        }
        let (data, response) = try await URLSession.shared.data(from: url)
        try validateStatus(response)
        return try JSONDecoder().decode(PresentationRequestParams.self, from: data)
    }

    func verify(
        presentation: String,
        format: String,
        expectedNonce: String,
        target: PresentationRequestPayload? = nil
    ) async throws -> VerificationResponse {
        let resolvedBase = target.map { PayloadRouter.resolveHostUrl($0.verifierUrl, fallbackBaseUrl: baseURL) } ?? baseURL
        let rawPath = target?.verifyEndpoint ?? "/api/demo-verifier/verify"
        let path = rawPath.hasPrefix("/") ? rawPath : "/\(rawPath)"
        guard let url = URL(string: "\(resolvedBase)\(path)") else {
            throw DemoBackendError.invalidURL
        }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let body: [String: String] = [
            "presentation": presentation,
            "format": format,
            "expectedNonce": expectedNonce,
        ]
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, _) = try await URLSession.shared.data(for: req)
        return try JSONDecoder().decode(VerificationResponse.self, from: data)
    }

    private func validateStatus(_ response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw DemoBackendError.httpStatus((response as? HTTPURLResponse)?.statusCode ?? -1)
        }
    }
}
