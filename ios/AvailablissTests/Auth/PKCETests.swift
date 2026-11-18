import CryptoKit
import Foundation
import Testing

@testable import Availabliss

/// Google sign-in's PKCE pieces (`GoogleSignIn` builds its challenge from these). Ported from Android `PkceTest`.
struct PKCETests {
    /// What `SocialSignIn` sends as `code_challenge` for a verifier.
    private func challenge(_ verifier: String) -> String {
        Base64URL.encode(Data(SHA256.hash(data: Data(verifier.utf8))))
    }

    @Test func challengeIsBase64URLSHA256MatchingTheServer() {
        // Same vector as web/src/lib/server/native-auth.test.ts.
        #expect(challenge("dBjftJeZ4CVP-mJ92K9gYqF5yI1gG8ZoMAvHOn0vR3U") == "V3SuLHIK7yk4RyrjZ3sQ8mzmOovJLsWYWwmh1P1rX4M")
    }

    @Test func verifiersAreRandomURLSafeStrings() {
        let a = Nonce.random(length: 64)
        let b = Nonce.random(length: 64)
        #expect(a != b)
        #expect(a.count == 64)
        // RFC 7636 unreserved characters only.
        #expect(a.wholeMatch(of: /[A-Za-z0-9\-._~]+/) != nil)
        #expect(challenge(a).wholeMatch(of: /[A-Za-z0-9_-]{43}/) != nil)
        #expect(Nonce.random().count == 32)
    }

    @Test func base64URLHasNoPaddingAndRoundTrips() {
        let bytes = Data([0xfb, 0xff, 0xfe, 0x00, 0x10])
        let encoded = Base64URL.encode(bytes)
        #expect(encoded == "-__-ABA")
        #expect(Base64URL.decode(encoded) == bytes)
        #expect(Base64URL.decode("YQ") == Data("a".utf8))
    }

    @Test func appleNonceIsHexSHA256() {
        #expect(Nonce.sha256("abc") == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }
}
