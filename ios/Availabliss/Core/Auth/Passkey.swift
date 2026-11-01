import AuthenticationServices
import Foundation

// WebAuthn JSON shapes exchanged with Better Auth's passkey plugin
// (SimpleWebAuthn `PublicKeyCredential*OptionsJSON` / `*ResponseJSON`).

struct PasskeyRequestOptions: Decodable, Sendable {
    let challenge: String
    let rpId: String?
    let userVerification: String?
}

struct PasskeyCreationOptions: Decodable, Sendable {
    struct RelyingParty: Decodable, Sendable {
        let id: String?
        let name: String
    }
    struct UserEntity: Decodable, Sendable {
        let id: String
        let name: String
        let displayName: String?
    }
    let challenge: String
    let rp: RelyingParty
    let user: UserEntity
}

struct PasskeyAssertionJSON: Encodable, Sendable {
    struct Response: Encodable, Sendable {
        let clientDataJSON: String
        let authenticatorData: String
        let signature: String
        let userHandle: String?
    }
    let id: String
    let rawId: String
    let type = "public-key"
    let response: Response
    let clientExtensionResults: [String: String] = [:]
    let authenticatorAttachment = "platform"
}

struct PasskeyRegistrationJSON: Encodable, Sendable {
    struct Response: Encodable, Sendable {
        let clientDataJSON: String
        let attestationObject: String
        let transports: [String]
    }
    let id: String
    let rawId: String
    let type = "public-key"
    let response: Response
    let clientExtensionResults: [String: String] = [:]
    let authenticatorAttachment = "platform"
}

enum Base64URL {
    static func encode(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    static func decode(_ string: String) -> Data? {
        var base64 = string
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        base64 += String(repeating: "=", count: (4 - base64.count % 4) % 4)
        return Data(base64Encoded: base64)
    }
}

/// Builds passkey requests and turns platform results into WebAuthn JSON.
@MainActor
enum PasskeyCoordinator {
    static func assertion(options: PasskeyRequestOptions, fallbackRelyingParty: String) throws -> ASAuthorizationRequest {
        guard let challenge = Base64URL.decode(options.challenge) else { throw AuthFlowError.invalidServerResponse }
        let provider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: options.rpId ?? fallbackRelyingParty)
        return provider.createCredentialAssertionRequest(challenge: challenge)
    }

    static func registration(options: PasskeyCreationOptions, fallbackRelyingParty: String) throws -> ASAuthorizationRequest {
        guard let challenge = Base64URL.decode(options.challenge),
              let userID = Base64URL.decode(options.user.id) ?? options.user.id.data(using: .utf8)
        else { throw AuthFlowError.invalidServerResponse }
        let provider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: options.rp.id ?? fallbackRelyingParty)
        return provider.createCredentialRegistrationRequest(challenge: challenge, name: options.user.name, userID: userID)
    }

    static func assertionJSON(from credential: ASAuthorizationCredential) throws -> PasskeyAssertionJSON {
        guard let assertion = credential as? ASAuthorizationPlatformPublicKeyCredentialAssertion else {
            throw AuthFlowError.invalidServerResponse
        }
        let id = Base64URL.encode(assertion.credentialID)
        return PasskeyAssertionJSON(
            id: id,
            rawId: id,
            response: .init(
                clientDataJSON: Base64URL.encode(assertion.rawClientDataJSON),
                authenticatorData: Base64URL.encode(assertion.rawAuthenticatorData),
                signature: Base64URL.encode(assertion.signature),
                userHandle: assertion.userID.isEmpty ? nil : Base64URL.encode(assertion.userID)
            )
        )
    }

    static func registrationJSON(from credential: ASAuthorizationCredential) throws -> PasskeyRegistrationJSON {
        guard let registration = credential as? ASAuthorizationPlatformPublicKeyCredentialRegistration,
              let attestation = registration.rawAttestationObject
        else { throw AuthFlowError.invalidServerResponse }
        let id = Base64URL.encode(registration.credentialID)
        return PasskeyRegistrationJSON(
            id: id,
            rawId: id,
            response: .init(
                clientDataJSON: Base64URL.encode(registration.rawClientDataJSON),
                attestationObject: Base64URL.encode(attestation),
                transports: ["internal", "hybrid"]
            )
        )
    }
}
