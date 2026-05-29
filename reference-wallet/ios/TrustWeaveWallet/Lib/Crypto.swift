// Crypto.swift
//
// base64url, did:key encoding (Ed25519 multicodec 0xED 0x01 + Base58btc),
// SHA-256, and compact JWS construction.
//
// CryptoKit provides Ed25519 (`Curve25519.Signing.PrivateKey`) on iOS 17+.
// SHA-256 via `SHA256.hash(data:)`.

import CryptoKit
import Foundation

enum Crypto {

    // MARK: - base64url

    static func base64UrlEncode(_ data: Data) -> String {
        return data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    static func base64UrlEncodeString(_ s: String) -> String {
        return base64UrlEncode(Data(s.utf8))
    }

    static func base64UrlDecode(_ s: String) -> Data? {
        var s = s
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        // Re-pad to a multiple of 4.
        let padding = (4 - s.count % 4) % 4
        s.append(String(repeating: "=", count: padding))
        return Data(base64Encoded: s)
    }

    static func base64UrlDecodeString(_ s: String) -> String? {
        guard let data = base64UrlDecode(s) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    // MARK: - SHA-256

    static func sha256(_ data: Data) -> Data {
        return Data(SHA256.hash(data: data))
    }

    static func sha256(_ s: String) -> Data {
        return sha256(Data(s.utf8))
    }

    // MARK: - did:key encoding (Ed25519, multicodec 0xED 0x01)

    static func publicKeyToDidKey(_ publicKey: Data) -> String {
        precondition(publicKey.count == 32, "Ed25519 public key must be 32 bytes")
        var prefixed = Data()
        prefixed.append(0xED)
        prefixed.append(0x01)
        prefixed.append(publicKey)
        return "did:key:z" + Base58.encode(prefixed)
    }

    static func didKeyToPublicKey(_ did: String) -> Data? {
        guard did.hasPrefix("did:key:z") else { return nil }
        let multibase = String(did.dropFirst("did:key:z".count))
        guard let decoded = Base58.decode(multibase),
              decoded.count == 34,
              decoded[0] == 0xED, decoded[1] == 0x01 else { return nil }
        return decoded.subdata(in: 2..<34)
    }

    // MARK: - Compact JWS (EdDSA, Ed25519)

    /// Build a compact JWS over [jsonPayload]. The [signer] closure does the actual
    /// signing — that lets the caller route through CryptoKit (in-process) or a
    /// future Keychain-backed signer. Returns "<header>.<payload>.<signature>".
    static func signCompactJws(
        jsonPayload: String,
        kid: String,
        typ: String = "JWT",
        signer: (Data) -> Data
    ) -> String {
        let header = "{\"alg\":\"EdDSA\",\"typ\":\"\(typ)\",\"kid\":\"\(kid)\"}"
        let encodedHeader = base64UrlEncodeString(header)
        let encodedPayload = base64UrlEncodeString(jsonPayload)
        let signingInput = "\(encodedHeader).\(encodedPayload)"
        let signature = signer(Data(signingInput.utf8))
        return "\(signingInput).\(base64UrlEncode(signature))"
    }

    /// Verify a compact JWS produced by [signCompactJws]. Returns the parsed JSON
    /// payload on success, or nil if the signature is invalid / the JWS is malformed.
    static func verifyCompactJws(_ jws: String, publicKey: Data) -> Data? {
        let parts = jws.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 3 else { return nil }
        let signingInput = "\(parts[0]).\(parts[1])"
        guard let signature = base64UrlDecode(String(parts[2])),
              let payload = base64UrlDecode(String(parts[1])) else { return nil }
        do {
            let pubKey = try Curve25519.Signing.PublicKey(rawRepresentation: publicKey)
            guard pubKey.isValidSignature(signature, for: Data(signingInput.utf8)) else { return nil }
            return payload
        } catch {
            return nil
        }
    }
}

// MARK: - Base58btc (Bitcoin alphabet)

/// Standalone Base58 codec — Apple doesn't ship one in stdlib. Mirrors the
/// android/shared Base58 implementation byte-for-byte (alphabet, divmod logic).
enum Base58 {
    private static let alphabet = Array("123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz")
    private static let indexes: [Int8] = {
        var arr = [Int8](repeating: -1, count: 128)
        for (i, c) in alphabet.enumerated() {
            arr[Int(c.asciiValue!)] = Int8(i)
        }
        return arr
    }()

    static func encode(_ input: Data) -> String {
        if input.isEmpty { return "" }
        var leadingZeros = 0
        while leadingZeros < input.count && input[leadingZeros] == 0 { leadingZeros += 1 }

        var source = Array(input)
        var encoded = [Character](repeating: " ", count: input.count * 2)
        var outputStart = encoded.count
        var startAt = leadingZeros
        while startAt < source.count {
            let mod = divmod(&source, firstDigit: startAt, base: 256, divisor: 58)
            if source[startAt] == 0 { startAt += 1 }
            outputStart -= 1
            encoded[outputStart] = alphabet[Int(mod)]
        }
        // Strip leading "1"s left by divmod.
        while outputStart < encoded.count && encoded[outputStart] == alphabet[0] { outputStart += 1 }
        // Prepend one "1" for each leading zero byte.
        for _ in 0..<leadingZeros {
            outputStart -= 1
            encoded[outputStart] = alphabet[0]
        }
        return String(encoded[outputStart..<encoded.count])
    }

    static func decode(_ input: String) -> Data? {
        if input.isEmpty { return Data() }
        var input58 = [UInt8]()
        input58.reserveCapacity(input.count)
        for c in input {
            guard let ascii = c.asciiValue, ascii < 128 else { return nil }
            let digit = indexes[Int(ascii)]
            guard digit >= 0 else { return nil }
            input58.append(UInt8(digit))
        }
        var leadingZeros = 0
        while leadingZeros < input58.count && input58[leadingZeros] == 0 { leadingZeros += 1 }

        var decoded = [UInt8](repeating: 0, count: input.count)
        var outputStart = decoded.count
        var startAt = leadingZeros
        while startAt < input58.count {
            let mod = divmod(&input58, firstDigit: startAt, base: 58, divisor: 256)
            if input58[startAt] == 0 { startAt += 1 }
            outputStart -= 1
            decoded[outputStart] = mod
        }
        while outputStart < decoded.count && decoded[outputStart] == 0 { outputStart += 1 }
        outputStart -= leadingZeros
        if outputStart < 0 { outputStart = 0 }
        return Data(decoded[outputStart..<decoded.count])
    }

    private static func divmod(_ number: inout [UInt8], firstDigit: Int, base: Int, divisor: Int) -> UInt8 {
        var remainder = 0
        for i in firstDigit..<number.count {
            let digit = Int(number[i])
            let temp = remainder * base + digit
            number[i] = UInt8(temp / divisor)
            remainder = temp % divisor
        }
        return UInt8(remainder)
    }
}
