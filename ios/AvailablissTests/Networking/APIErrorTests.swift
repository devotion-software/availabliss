import Foundation
import Testing
@testable import Availabliss

/// Ported from Android `ApiErrorTest`, `PlanLabelsTest.groupLimitIsUpgradeRequired` and `LinkAccountTest`.
struct APIErrorTests {
    private func apiError(_ status: Int, _ json: String?) throws -> APIError {
        APIError(status: status, body: try json.map { try Fixtures.decode(APIErrorBody.self, $0) })
    }

    @Test func validationErrorsCarryTheirIssues() throws {
        let error = try apiError(400, #"{"message":"Invalid","code":"validation_error","issues":[{"path":"name","message":"Required"}]}"#)
        #expect(error == .validation("Invalid", issues: [APIErrorBody.Issue(path: "name", message: "Required")]))
        // The issues are what the user reads.
        #expect(error.errorDescription == "Required")
        #expect(try apiError(400, #"{"message":"Invalid","code":"validation_error"}"#).errorDescription == "Invalid")
    }

    @Test func fallsBackToTheStatusCodeWithoutABody() {
        #expect(APIError(status: 401, body: nil).isUnauthorized)
        guard case .server = APIError(status: 503, body: nil) else { Issue.record("503 should be a server error"); return }
        guard case .rateLimited = APIError(status: 429, body: nil) else { Issue.record("429 should be rate limited"); return }
        guard case .notFound = APIError(status: 404, body: nil) else { Issue.record("404 should be not found"); return }
        guard case .http(418, _) = APIError(status: 418, body: nil) else { Issue.record("418 should be a plain HTTP error"); return }
    }

    @Test func theCodeWinsOverTheStatus() throws {
        #expect(try apiError(400, #"{"message":"Nope","code":"forbidden"}"#) == .forbidden("Nope"))
        #expect(try apiError(200, #"{"message":"Gone","code":"not_found"}"#) == .notFound("Gone"))
    }

    @Test func theGroupLimitIsUpgradeRequired() throws {
        let error = try apiError(402, #"{"message":"Free accounts can create up to 3 groups.","code":"upgrade_required"}"#)
        #expect(error == .upgradeRequired("Free accounts can create up to 3 groups."))
        #expect(error.userMessage == "Free accounts can create up to 3 groups.")
    }

    @Test func aConflictWithAnAccountAsksToMerge() throws {
        let merge = try apiError(409, #"{"message":"Already linked","code":"conflict","account":{"name":"Sam","email":"sam@example.com","groups":2}}"#)
        #expect(merge == .mergeCandidate(MergeCandidate(name: "Sam", email: "sam@example.com", groups: 2), message: "Already linked"))
        #expect(try apiError(409, #"{"message":"Taken","code":"conflict"}"#) == .conflict("Taken"))
    }

    @Test func decodingAndCancellationMessages() {
        #expect(APIError.decoding("keyNotFound").errorDescription == "Something unexpected came back from the server.")
        #expect(APIError.cancelled.isCancellation)
        #expect(CancellationError().isCancellation)
        #expect(URLError(.cancelled).isCancellation)
        #expect(!URLError(.timedOut).isCancellation)
        #expect(!APIError.server("x").isCancellation)
    }
}
