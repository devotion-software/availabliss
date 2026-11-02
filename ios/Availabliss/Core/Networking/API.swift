import Foundation

/// Every endpoint in docs/API.md, grouped as in the doc. Nothing else should build paths.
enum API {
    private static func dayString(_ day: CalendarDay?) -> String? { day?.description }
    private static func iso(_ date: Date?) -> String? { date.map(JSONCoding.formatDate) }

    // MARK: Me
    enum Me {
        static func get() -> Endpoint<User> { .init(.get, "/me") }
        static func update(_ patch: UserPatch) -> Endpoint<User> { .init(.patch, "/me", body: patch) }
        static func busy(from: Date, to: Date) -> Endpoint<BusyIntervalsResponse> {
            .init(.get, "/me/busy", query: ["from": iso(from), "to": iso(to)])
        }
        static func delete() -> Endpoint<EmptyResponse> { .init(.delete, "/me") }
        static func upcoming() -> Endpoint<EventsResponse> { .init(.get, "/me/upcoming") }
    }

    // MARK: Regions & holidays
    enum Regions {
        static func countries() -> Endpoint<CountriesResponse> { .init(.get, "/regions/countries") }
        static func subdivisions(country: String) -> Endpoint<SubdivisionsResponse> {
            .init(.get, "/regions/subdivisions", query: ["country": country])
        }
        static func schoolAuthorities(country: String) -> Endpoint<AuthoritiesResponse> {
            .init(.get, "/regions/school-authorities", query: ["country": country])
        }
        static func detect() -> Endpoint<DetectedRegion> { .init(.get, "/regions/detect") }
        static func holidays(country: String, subdivision: String?, from: CalendarDay, to: CalendarDay) -> Endpoint<HolidaysResponse> {
            .init(.get, "/holidays", query: ["country": country, "subdivision": subdivision, "from": dayString(from), "to": dayString(to)])
        }
    }

    // MARK: Calendars
    enum Calendars {
        static func list() -> Endpoint<ConnectionsResponse> { .init(.get, "/calendars") }
        static func connectCalDAV(_ body: CalDAVBody) -> Endpoint<CalendarConnection> { .init(.post, "/calendars/caldav", body: body) }
        static func uploadDevice(_ sync: DeviceSync) -> Endpoint<CalendarConnection> { .init(.put, "/calendars/device", body: sync) }
        static func setBusy(connectionId: String, calendarId: String, busy: Bool) -> Endpoint<CalendarConnection> {
            .init(.patch, "/calendars/\(connectionId.pathEncoded)/calendars/\(calendarId.pathEncoded)", body: CalendarBusyBody(busy: busy))
        }
        static func sync(connectionId: String) -> Endpoint<CalendarConnection> { .init(.post, "/calendars/\(connectionId.pathEncoded)/sync") }
        static func delete(connectionId: String) -> Endpoint<EmptyResponse> { .init(.delete, "/calendars/\(connectionId.pathEncoded)") }
        /// Consent URL (with signed state) to open in `ASWebAuthenticationSession`.
        static func oauthURL(provider: CalendarProvider, redirect: String) -> Endpoint<OAuthURLResponse> {
            .init(.post, "/calendars/oauth/\(provider.rawValue)/url", body: OAuthURLBody(redirect: redirect))
        }
    }

    // MARK: Manual blocks
    enum Blocks {
        static func list() -> Endpoint<BlocksResponse> { .init(.get, "/blocks") }
        static func create(_ block: NewManualBlock) -> Endpoint<ManualBlock> { .init(.post, "/blocks", body: block) }
        static func update(id: String, _ block: NewManualBlock) -> Endpoint<ManualBlock> { .init(.patch, "/blocks/\(id.pathEncoded)", body: block) }
        static func delete(id: String) -> Endpoint<EmptyResponse> { .init(.delete, "/blocks/\(id.pathEncoded)") }
    }

    // MARK: Groups
    enum Groups {
        static func list() -> Endpoint<GroupsResponse> { .init(.get, "/groups") }
        static func create(_ body: CreateGroupBody) -> Endpoint<Group> { .init(.post, "/groups", body: body) }
        static func get(id: String) -> Endpoint<Group> { .init(.get, "/groups/\(id.pathEncoded)") }
        static func update(id: String, _ body: UpdateGroupBody) -> Endpoint<Group> { .init(.patch, "/groups/\(id.pathEncoded)", body: body) }
        static func delete(id: String) -> Endpoint<EmptyResponse> { .init(.delete, "/groups/\(id.pathEncoded)") }
        static func setRole(groupId: String, userId: String, role: Role) -> Endpoint<Member> {
            .init(.patch, "/groups/\(groupId.pathEncoded)/members/\(userId.pathEncoded)", body: RoleBody(role: role))
        }
        /// Removes a member, or leaves the group when `userId` is yourself.
        static func removeMember(groupId: String, userId: String) -> Endpoint<EmptyResponse> {
            .init(.delete, "/groups/\(groupId.pathEncoded)/members/\(userId.pathEncoded)")
        }
        /// Members whose calendars or blocked time clash with `start`–`end`.
        static func conflicts(groupId: String, start: Date, end: Date) -> Endpoint<ConflictsResponse> {
            .init(.get, "/groups/\(groupId.pathEncoded)/conflicts", query: ["start": iso(start), "end": iso(end)])
        }
        static func nudge(groupId: String) -> Endpoint<NudgeResponse> { .init(.post, "/groups/\(groupId.pathEncoded)/nudge") }
        static func createInvite(groupId: String, email: String? = nil) -> Endpoint<InviteLink> {
            .init(.post, "/groups/\(groupId.pathEncoded)/invites", body: CreateInviteBody(email: email))
        }
    }

    // MARK: Invites
    enum Invites {
        static func preview(token: String) -> Endpoint<InvitePreview> { .init(.get, "/invites/\(token.pathEncoded)") }
        static func accept(token: String) -> Endpoint<Group> { .init(.post, "/invites/\(token.pathEncoded)/accept") }
    }

    // MARK: Availability
    enum Requirements {
        static func preview(_ requirement: Requirement) -> Endpoint<RequirementPreview> {
            .init(.post, "/requirements/preview", body: RequirementPreviewBody(requirement: requirement))
        }
    }

    enum GroupAvailability {
        static func range(groupId: String, from: CalendarDay, to: CalendarDay) -> Endpoint<Availability> {
            .init(.get, "/groups/\(groupId.pathEncoded)/availability", query: ["from": dayString(from), "to": dayString(to)])
        }
        static func day(groupId: String, date: CalendarDay) -> Endpoint<DayAvailability> {
            .init(.get, "/groups/\(groupId.pathEncoded)/availability/day", query: ["date": date.description])
        }
    }

    // MARK: Proposals
    enum Proposals {
        static func list(groupId: String, status: Proposal.Status? = nil) -> Endpoint<ProposalsResponse> {
            .init(.get, "/groups/\(groupId.pathEncoded)/proposals", query: ["status": status?.rawValue])
        }
        static func create(groupId: String, _ body: CreateProposalBody) -> Endpoint<Proposal> {
            .init(.post, "/groups/\(groupId.pathEncoded)/proposals", body: body)
        }
        static func get(id: String) -> Endpoint<Proposal> { .init(.get, "/proposals/\(id.pathEncoded)") }
        static func respond(id: String, _ response: ResponseKind) -> Endpoint<Proposal> {
            .init(.post, "/proposals/\(id.pathEncoded)/respond", body: RespondBody(response: response))
        }
        static func override(id: String, userId: String, response: ResponseKind) -> Endpoint<Proposal> {
            .init(.post, "/proposals/\(id.pathEncoded)/override", body: OverrideBody(userId: userId, response: response))
        }
        static func cancel(id: String) -> Endpoint<EmptyResponse> { .init(.delete, "/proposals/\(id.pathEncoded)") }
    }

    // MARK: Events
    enum Events {
        static func list(groupId: String, from: Date? = nil, to: Date? = nil) -> Endpoint<EventsResponse> {
            .init(.get, "/groups/\(groupId.pathEncoded)/events", query: ["from": iso(from), "to": iso(to)])
        }
        static func create(groupId: String, _ body: EventBody) -> Endpoint<GroupEvent> {
            .init(.post, "/groups/\(groupId.pathEncoded)/events", body: body)
        }
        static func get(id: String) -> Endpoint<GroupEvent> { .init(.get, "/events/\(id.pathEncoded)") }
        static func update(id: String, _ body: EventBody) -> Endpoint<GroupEvent> { .init(.patch, "/events/\(id.pathEncoded)", body: body) }
        static func delete(id: String) -> Endpoint<EmptyResponse> { .init(.delete, "/events/\(id.pathEncoded)") }
    }

    // MARK: Chat
    enum Chat {
        static func messages(groupId: String, before: String? = nil, limit: Int = 50) -> Endpoint<MessagesPage> {
            .init(.get, "/groups/\(groupId.pathEncoded)/messages", query: ["before": before, "limit": String(limit)])
        }
        static func send(groupId: String, _ body: SendMessageBody) -> Endpoint<Message> {
            .init(.post, "/groups/\(groupId.pathEncoded)/messages", body: body)
        }
        static func receipts(groupId: String, upTo: String, state: ReceiptBody.State) -> Endpoint<EmptyResponse> {
            .init(.post, "/groups/\(groupId.pathEncoded)/messages/receipts", body: ReceiptBody(upTo: upTo, state: state))
        }
        static func info(messageId: String) -> Endpoint<MessageInfo> { .init(.get, "/messages/\(messageId.pathEncoded)/info") }
        static func vote(pollId: String, optionIds: [String]) -> Endpoint<Poll> {
            .init(.post, "/polls/\(pollId.pathEncoded)/vote", body: VoteBody(optionIds: optionIds))
        }
        static func closePoll(pollId: String) -> Endpoint<Poll> { .init(.post, "/polls/\(pollId.pathEncoded)/close") }
    }

    // MARK: Notifications & devices
    enum Notifications {
        static func list() -> Endpoint<NotificationsResponse> { .init(.get, "/notifications") }
        static func markRead(ids: [String]? = nil) -> Endpoint<EmptyResponse> { .init(.post, "/notifications/read", body: MarkReadBody(ids: ids)) }
        static func registerDevice(token: String, name: String?) -> Endpoint<EmptyResponse> {
            .init(.post, "/devices", body: RegisterDeviceBody(platform: "ios", token: token, name: name))
        }
        static func unregisterDevice(token: String) -> Endpoint<EmptyResponse> { .init(.delete, "/devices/\(token.pathEncoded)") }
    }

    // MARK: Better Auth (/api/auth)
    enum Auth {
        static func sendOTP(email: String) -> Endpoint<EmptyResponse> {
            .init(.post, "/email-otp/send-verification-otp", mount: .auth, body: SendOTPBody(email: email, type: "sign-in"))
        }
        static func signInOTP(email: String, otp: String) -> Endpoint<EmptyResponse> {
            .init(.post, "/sign-in/email-otp", mount: .auth, body: OTPSignInBody(email: email, otp: otp))
        }
        static func signInSocial(provider: String, idToken: String, nonce: String?) -> Endpoint<EmptyResponse> {
            .init(.post, "/sign-in/social", mount: .auth, body: SocialSignInBody(provider: provider, idToken: .init(token: idToken, nonce: nonce)))
        }
        static func passkeyAuthenticateOptions() -> Endpoint<PasskeyRequestOptions> {
            .init(.get, "/passkey/generate-authenticate-options", mount: .auth)
        }
        static func passkeyVerifyAuthentication(_ response: PasskeyAssertionJSON) -> Endpoint<EmptyResponse> {
            .init(.post, "/passkey/verify-authentication", mount: .auth, body: PasskeyVerifyBody(response: response, name: nil))
        }
        static func passkeyRegisterOptions() -> Endpoint<PasskeyCreationOptions> {
            .init(.get, "/passkey/generate-register-options", mount: .auth)
        }
        static func passkeyVerifyRegistration(_ response: PasskeyRegistrationJSON, name: String?) -> Endpoint<EmptyResponse> {
            .init(.post, "/passkey/verify-registration", mount: .auth, body: PasskeyVerifyBody(response: response, name: name))
        }
        static func signOut() -> Endpoint<EmptyResponse> { .init(.post, "/sign-out", mount: .auth) }
    }
}

// MARK: - Auth bodies

struct SendOTPBody: Encodable, Sendable {
    let email: String
    let type: String
}

struct OTPSignInBody: Encodable, Sendable {
    let email: String
    let otp: String
}

struct SocialSignInBody: Encodable, Sendable {
    struct IDToken: Encodable, Sendable {
        let token: String
        let nonce: String?
    }
    let provider: String
    let idToken: IDToken
}

struct PasskeyVerifyBody<Response: Encodable & Sendable>: Encodable, Sendable {
    let response: Response
    let name: String?
}
