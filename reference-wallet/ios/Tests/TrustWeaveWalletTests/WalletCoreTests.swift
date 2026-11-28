// WalletCoreTests.swift — XCTest suite covering iOS crypto, SD-JWT, fail-closed issuer trust,
// credential verification, unified QR/NFC/URL routing, and consumer display helpers.

import CryptoKit
import Foundation
import XCTest

#if canImport(TrustWeaveWalletCore)
@testable import TrustWeaveWalletCore
#elseif canImport(TrustWeaveWallet)
@testable import TrustWeaveWallet
#endif

final class WalletCoreTests: XCTestCase {

    // MARK: - Crypto & Base58

    func testBase58RoundTripAndLeadingZeros() {
        let raw = Data([0x00, 0x00, 0xED, 0x01, 0x11, 0x22, 0x33, 0x44])
        let encoded = Base58.encode(raw)
        XCTAssertTrue(encoded.hasPrefix("11"))
        XCTAssertEqual(Base58.decode(encoded), raw)
        XCTAssertNil(Base58.decode("0OIl"))
    }

    func testDidKeyRoundTripAndJwsSignVerify() throws {
        let priv = Curve25519.Signing.PrivateKey()
        let pub = priv.publicKey.rawRepresentation
        let did = Crypto.publicKeyToDidKey(pub)
        XCTAssertTrue(did.hasPrefix("did:key:z"))
        XCTAssertEqual(Crypto.didKeyToPublicKey(did), pub)

        let payloadJson = "{\"iss\":\"\(did)\",\"hello\":\"world\"}"
        let jws = try Crypto.signCompactJws(
            jsonPayload: payloadJson,
            kid: "\(did)#\(did.dropFirst("did:key:".count))",
            signer: { try priv.signature(for: $0) }
        )
        let verified = Crypto.verifyCompactJws(jws, publicKey: pub)
        XCTAssertNotNil(verified)
        XCTAssertEqual(String(data: verified!, encoding: .utf8), payloadJson)

        let otherKey = Curve25519.Signing.PrivateKey().publicKey.rawRepresentation
        XCTAssertNil(Crypto.verifyCompactJws(jws, publicKey: otherKey))
    }

    // MARK: - Fail-closed issuer trust & verification

    func testIssuerTrustFailsClosedAndFingerprintsCleanly() throws {
        let issuer = "did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSwuBV8xRoAnwWsdvktH"
        XCTAssertFalse(IssuerTrustPolicy.none.isTrusted(issuer))
        XCTAssertEqual(IssuerTrust.shortDidFingerprint(issuer), "z6MkpT…vktH")

        let accepted = try IssuerTrust.withAccepted([], did: issuer)
        XCTAssertEqual(accepted, [issuer])
        let policy = IssuerTrust.policy(configured: [], backend: [], accepted: accepted)
        XCTAssertTrue(policy.isTrusted(issuer))
    }

    func testCredentialVerificationRejectsUntrustedIssuerThenAcceptsWhenConfirmed() throws {
        let issuerPriv = Curve25519.Signing.PrivateKey()
        let issuerDid = Crypto.publicKeyToDidKey(issuerPriv.publicKey.rawRepresentation)
        let holderPriv = Curve25519.Signing.PrivateKey()
        let holderDid = Crypto.publicKeyToDidKey(holderPriv.publicKey.rawRepresentation)

        let disclosure = try makeDisclosure(name: "fullName", value: "Alice Johnson")
        let now: Int64 = 1_700_000_000
        let payload: [String: Any] = [
            "iss": issuerDid,
            "sub": holderDid,
            "iat": now - 10,
            "exp": now + 365 * 24 * 3600,
            "vct": "BachelorOfScience",
            "cnf": ["kid": holderDid],
            "_sd_alg": "sha-256",
            "_sd": [disclosure.hash],
        ]
        let payloadData = try JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys])
        let payloadStr = String(data: payloadData, encoding: .utf8)!
        let jwt = try Crypto.signCompactJws(
            jsonPayload: payloadStr,
            kid: "\(issuerDid)#\(issuerDid.dropFirst("did:key:".count))",
            typ: "vc+sd-jwt",
            signer: { try issuerPriv.signature(for: $0) }
        )
        let compact = "\(jwt)~\(disclosure.raw)~"

        XCTAssertThrowsError(
            try CredentialVerification.verifyImportedCredential(
                compact: compact,
                format: "vc+sd-jwt",
                holderDid: holderDid,
                nowEpochSeconds: now,
                issuerPolicy: .none
            )
        ) { err in
            XCTAssertTrue(err is UntrustedIssuerError)
        }

        XCTAssertNoThrow(
            try CredentialVerification.verifyImportedCredential(
                compact: compact,
                format: "vc+sd-jwt",
                holderDid: holderDid,
                nowEpochSeconds: now,
                issuerPolicy: .allowList([issuerDid])
            )
        )

        // Selective disclosure presentation includes only chosen claim + KB-JWT
        let presented = try SdJwt.present(
            sdJwtVc: compact,
            selectDisclose: ["fullName"],
            holderSigner: { try holderPriv.signature(for: $0) },
            holderDid: holderDid,
            audience: "https://verifier.example",
            nonce: "nonce-123",
            nowEpochSeconds: now
        )
        let decodedPres = try SdJwt.decode(presented)
        XCTAssertEqual(decodedPres.disclosures.map { $0.name }, ["fullName"])
        XCTAssertNotNil(decodedPres.kbJwt)
    }

    // MARK: - Unified PayloadRouter

    func testPayloadRouterClassifiesOffersVerifiersPublicVerifyAndDeepLinks() {
        let oid4vci = "openid-credential-offer://?credential_offer=%7B%22credential_issuer%22%3A%22https%3A%2F%2Fissuer.example%22%2C%22credential_configuration_ids%22%3A%5B%22UniversityDegree%22%5D%2C%22grants%22%3A%7B%22urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code%22%3A%7B%22pre-authorized_code%22%3A%22abc123%22%7D%7D%7D"
        if case .offer(.oid4VciPreAuthorized(let base, let code, _), _) = PayloadRouter.classify(oid4vci) {
            XCTAssertEqual(base, "https://issuer.example")
            XCTAssertEqual(code, "abc123")
        } else {
            XCTFail("Expected OID4VCI offer classification")
        }

        let oid4vp = "openid4vp://?request_uri=https%3A%2F%2Fverifier.example%2Fapi%2Fdemo-verifier%2Frequest%3Fid%3D42"
        if case .presentation(let req, _) = PayloadRouter.classify(oid4vp) {
            XCTAssertEqual(req.verifierUrl, "https://verifier.example")
            XCTAssertTrue(req.requestEndpoint.contains("/api/demo-verifier/request"))
        } else {
            XCTFail("Expected OID4VP presentation classification")
        }

        if case .publicVerify(let url, _) = PayloadRouter.classify("https://demo.example/verify/abc") {
            XCTAssertEqual(url.absoluteString, "https://demo.example/verify/abc")
        } else {
            XCTFail("Expected public verify URL classification")
        }

        let wrapped = "trustweavewallet://scan?url=https%3A%2F%2Fverifier.example%2Fverifier"
        if case .presentation(let req, _) = PayloadRouter.classify(wrapped) {
            XCTAssertEqual(req.verifierUrl, "https://verifier.example")
        } else {
            XCTFail("Expected wrapped deep link to unwrap to presentation request")
        }
    }

    // MARK: - Consumer display & honest badges

    func testCredentialDisplayHonestLabelsAndClaimValues() throws {
        let issuerDid = "did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSwuBV8xRoAnwWsdvktH"
        let label = CredentialDisplay.formatIssuerLabel(issuerDid)
        XCTAssertFalse(label.localizedCaseInsensitiveContains("verified"))
        XCTAssertEqual(label, "Issuer · z6MkpT…vktH")
        XCTAssertEqual(CredentialDisplay.humanClaimName("graduationYear"), "Graduation Year")

        let now: Int64 = 1_700_000_000
        let dName = try makeDisclosure(name: "fullName", value: "Alice Johnson")
        let header = Crypto.base64UrlEncodeString("{\"alg\":\"EdDSA\",\"typ\":\"vc+sd-jwt\"}")
        let payload = Crypto.base64UrlEncodeString("{\"iss\":\"\(issuerDid)\",\"sub\":\"did:key:z6MkHolder\",\"vct\":\"BachelorOfScience\",\"exp\":\(now + 365 * 24 * 3600),\"_sd\":[\"\(dName.hash)\"]}")
        let cred = StoredCredential(
            id: "c1",
            format: "vc+sd-jwt",
            credential: "\(header).\(payload).sig~\(dName.raw)~",
            receivedAt: "2026-01-01T00:00:00Z",
            issuerDid: issuerDid,
            subjectDid: "did:key:z6MkHolder",
            type: ["BachelorOfScience"],
            previewTitle: "Bachelor of Science",
            previewSubtitle: "Alice Johnson",
            selectivelyDisclosable: ["fullName"],
            trustSource: "backend"
        )
        let shareable = CredentialDisplay.extractShareableClaimDetails(cred)
        XCTAssertEqual(shareable.count, 1)
        XCTAssertEqual(shareable[0].label, "Full Name")
        XCTAssertEqual(shareable[0].value, "Alice Johnson")
        XCTAssertEqual(CredentialDisplay.statusBadge(for: cred, nowEpochSeconds: now).label, "Trusted issuer")
        XCTAssertEqual(CredentialDisplay.statusBadge(for: cred, nowEpochSeconds: now + 360 * 24 * 3600).label, "Expiring soon")
        XCTAssertEqual(CredentialDisplay.statusBadge(for: cred, nowEpochSeconds: now + 400 * 24 * 3600).label, "Expired")
    }

    // MARK: - Helper

    private func makeDisclosure(name: String, value: Any) throws -> Disclosure {
        let salt = SdJwt.randomSalt()
        let arr: [Any] = [salt, name, value]
        let data = try JSONSerialization.data(withJSONObject: arr)
        let raw = Crypto.base64UrlEncode(data)
        return try SdJwt.parseDisclosure(raw)
    }
}
