// DemoBackend.swift
//
// URLSession client for the in-repo Next.js demo backend. Same endpoint shape as
// the Android DemoBackend.kt. baseURL is "http://localhost:3000" by default —
// the iOS simulator reaches the host machine's localhost directly (unlike Android
// emulator which needs 10.0.2.2). For a real device, set baseURL to your LAN IP.

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

/// Type-erased JSON value, since `disclosedClaims` can hold strings, numbers,
/// booleans, arrays, or objects. Swift's Codable doesn't have a built-in for this.
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

enum DemoBackendError: Error {
    case httpStatus(Int)
    case emptyResponse
}

final class DemoBackend {

    let baseURL: String

    init(baseURL: String = "http://localhost:3000") {
        self.baseURL = baseURL
    }

    func receiveCredential(subjectDid: String) async throws -> CredentialOffer {
        let encoded = subjectDid.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? subjectDid
        let url = URL(string: "\(baseURL)/api/demo-issuer/credential?subject=\(encoded)")!
        let (data, response) = try await URLSession.shared.data(from: url)
        try validateStatus(response)
        return try JSONDecoder().decode(CredentialOffer.self, from: data)
    }

    func fetchPresentationRequest() async throws -> PresentationRequestParams {
        let url = URL(string: "\(baseURL)/api/demo-verifier/request")!
        let (data, response) = try await URLSession.shared.data(from: url)
        try validateStatus(response)
        return try JSONDecoder().decode(PresentationRequestParams.self, from: data)
    }

    func verify(presentation: String, format: String, expectedNonce: String) async throws -> VerificationResponse {
        let url = URL(string: "\(baseURL)/api/demo-verifier/verify")!
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let body: [String: String] = [
            "presentation": presentation,
            "format": format,
            "expectedNonce": expectedNonce,
        ]
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await URLSession.shared.data(for: req)
        // Don't throw on non-2xx — the verifier returns a structured failure body too.
        _ = response
        return try JSONDecoder().decode(VerificationResponse.self, from: data)
    }

    private func validateStatus(_ response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw DemoBackendError.httpStatus((response as? HTTPURLResponse)?.statusCode ?? -1)
        }
    }
}
