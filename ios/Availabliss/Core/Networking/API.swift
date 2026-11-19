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
        /// Sets your profile picture (JPEG bytes; the server shrinks it to fit 512 px).
        static func setAvatar(jpeg: Data) -> Endpoint<User> {
            var endpoint = Endpoint<User>(.put, "/me/avatar", timeout: 60)
            endpoint.rawBody = (jpeg, "image/jpeg")
            return endpoint
        }
        static func removeAvatar() -> Endpoint<User> { .init(.delete, "/me/avatar") }
        static func ignoreBusy(_ body: BusyIgnoreBody) -> Endpoint<BusyIgnoreResponse> { .init(.post, "/me/busy/ignores", body: body) }
        static func unignoreBusy(id: String) -> Endpoint<EmptyResponse> { .init(.delete, "/me/busy/ignores/\(id.pathEncoded)") }
        static func delete() -> Endpoint<EmptyResponse> { .init(.delete, "/me") }
        static func upcoming() -> Endpoint<EventsResponse> { .init(.get, "/me/upcoming") }
        /// Every plan across your groups: coming up, can't make it, and past.
        static func plans() -> Endpoint<MyPlansResponse> { .init(.get, "/me/plans") }
        static func plan() -> Endpoint<Plan> { .init(.get, "/me/plan") }
        /// Apple / Google sign-ins on the account, and which ones the server can link.
        static func accounts() -> Endpoint<LinkedAccounts> { .init(.get, "/me/accounts") }
        /// Links a sign-in; a 409 `.mergeCandidate` means it has its own account (send again with `merge`).
        static func linkAccount(_ body: LinkAccountBody) -> Endpoint<LinkAccountResponse> { .init(.post, "/me/accounts/link", body: body) }
        /// Re-reads RevenueCat after a purchase or restore.
        static func refreshPlan() -> Endpoint<Plan> { .init(.post, "/me/plan/refresh") }
        static func templates() -> Endpoint<TemplatesResponse> { .init(.get, "/me/templates") }
        static func createTemplate(name: String, requirement: Requirement) -> Endpoint<RequirementTemplate> {
            .init(.post, "/me/templates", body: CreateTemplateBody(name: name, requirement: requirement))
        }
        static func renameTemplate(id: String, name: String) -> Endpoint<RequirementTemplate> {
            .init(.patch, "/me/templates/\(id.pathEncoded)", body: RenameTemplateBody(name: name))
        }
        static func deleteTemplate(id: String) -> Endpoint<EmptyResponse> { .init(.delete, "/me/templates/\(id.pathEncoded)") }
        /// Your secret calendar feed link of every group's plans (made the first time it's asked for).
        static func calendarFeed() -> Endpoint<CalendarFeed> { .init(.get, "/me/calendar-feed") }
        /// Swaps the feed link for a new one; calendars subscribed with the old one stop updating.
        static func resetCalendarFeed() -> Endpoint<CalendarFeed> { .init(.post, "/me/calendar-feed/reset") }
        /// Which pushes you get: categories turned off, quiet hours and muted groups.
        static func notificationSettings() -> Endpoint<NotificationSettings> { .init(.get, "/me/notification-settings") }
        /// Changes only the fields set in `patch` (a `nil` quiet hours inside it turns them off).
        static func updateNotificationSettings(_ patch: NotificationSettingsPatch) -> Endpoint<NotificationSettings> {
            .init(.patch, "/me/notification-settings", body: patch)
        }
    }

    // MARK: Usernames
    enum Usernames {
        /// Whether it can be yours, and who has it (to invite them). Rate limited: debounce typing.
        static func check(_ username: String) -> Endpoint<UsernameCheck> { .init(.get, "/usernames/\(username.pathEncoded)") }
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
        /// Finishes a connection with the one-time code from `availabliss://calendars?complete=`.
        static func completeOAuth(provider: CalendarProvider, code: String) -> Endpoint<CalendarConnection> {
            .init(.post, "/calendars/oauth/\(provider.rawValue)/complete", body: OAuthCompleteBody(code: code))
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
        /// Sets the group's photo (admins; JPEG bytes, shrunk to fit 512 px).
        static func setImage(id: String, jpeg: Data) -> Endpoint<Group> {
            var endpoint = Endpoint<Group>(.put, "/groups/\(id.pathEncoded)/image", timeout: 60)
            endpoint.rawBody = (jpeg, "image/jpeg")
            return endpoint
        }
        static func removeImage(id: String) -> Endpoint<Group> { .init(.delete, "/groups/\(id.pathEncoded)/image") }
        static func setRole(groupId: String, userId: String, role: Role) -> Endpoint<Member> {
            .init(.patch, "/groups/\(groupId.pathEncoded)/members/\(userId.pathEncoded)", body: RoleBody(role: role))
        }
        /// Sets (or with nil, clears) the name you go by in the group.
        static func setMyName(groupId: String, displayName: String?) -> Endpoint<Member> {
            .init(.patch, "/groups/\(groupId.pathEncoded)/members/me", body: MemberNameBody(displayName: displayName))
        }
        /// Sets (or with nil, clears) the name someone goes by in the group: yours, or as an admin anyone's.
        static func setName(groupId: String, userId: String, displayName: String?) -> Endpoint<Member> {
            .init(.patch, "/groups/\(groupId.pathEncoded)/members/\(userId.pathEncoded)", body: MemberNameBody(displayName: displayName))
        }
        /// Removes a member (an admin removing someone else).
        static func removeMember(groupId: String, userId: String) -> Endpoint<EmptyResponse> {
            .init(.delete, "/groups/\(groupId.pathEncoded)/members/\(userId.pathEncoded)")
        }
        /// Leaves the group. The owner names the admin who becomes the owner.
        static func leave(groupId: String, newOwnerId: String?) -> Endpoint<EmptyResponse> {
            .init(.post, "/groups/\(groupId.pathEncoded)/leave", body: LeaveGroupBody(newOwnerId: newOwnerId))
        }
        /// The optional AI planner (Pro). Can take up to a minute.
        static func planner(groupId: String, notes: String?) -> Endpoint<PlannerResult> {
            .init(.post, "/groups/\(groupId.pathEncoded)/planner", body: PlannerBody(notes: notes), timeout: 90)
        }
        /// Pro: an AI-drafted title and message for a proposal at `start`–`end` (404 when AI isn't set up).
        static func draftProposal(groupId: String, start: Date, end: Date, notes: String? = nil) -> Endpoint<ProposalDraftText> {
            .init(.post, "/groups/\(groupId.pathEncoded)/planner/draft", body: ProposalDraftBody(start: start, end: end, notes: notes), timeout: 60)
        }
        /// Members whose calendars or blocked time clash with `start`–`end`.
        static func conflicts(groupId: String, start: Date, end: Date) -> Endpoint<ConflictsResponse> {
            .init(.get, "/groups/\(groupId.pathEncoded)/conflicts", query: ["start": iso(start), "end": iso(end)])
        }
        /// With a rhythm: the free time nearest the group's next meet-up.
        static func next(groupId: String) -> Endpoint<NextMeetResponse> { .init(.get, "/groups/\(groupId.pathEncoded)/next") }
        static func nudge(groupId: String) -> Endpoint<NudgeResponse> { .init(.post, "/groups/\(groupId.pathEncoded)/nudge") }
        /// No pushes from the group until `until` (nil: until it's unmuted). Returns your whole notification settings.
        static func mute(groupId: String, until: Date?) -> Endpoint<NotificationSettings> {
            .init(.put, "/groups/\(groupId.pathEncoded)/mute", body: MuteGroupBody(until: until))
        }
        /// Pushes from the group again. Returns your whole notification settings.
        static func unmute(groupId: String) -> Endpoint<NotificationSettings> { .init(.delete, "/groups/\(groupId.pathEncoded)/mute") }
        /// No one: a link anyone can use, by `maxUses` people (left out: one). Emails / usernames: one invite each, only for that person
        /// (emailed, or sent to their Activity). 404 for a username nobody has, 409 if they're in already.
        /// User ids: likewise sent to their Activity, for people you're already in a group with.
        static func createInvite(groupId: String, emails: [String] = [], usernames: [String] = [], userIds: [String] = [], maxUses: LinkLimit? = nil) -> Endpoint<
            InviteLink
        > {
            .init(
                .post, "/groups/\(groupId.pathEncoded)/invites",
                body: CreateInviteBody(
                    emails: emails.isEmpty ? nil : emails,
                    usernames: usernames.isEmpty ? nil : usernames,
                    userIds: userIds.isEmpty ? nil : userIds,
                    maxUses: maxUses
                ))
        }
        static func preferences(groupId: String) -> Endpoint<GroupPreferencesResponse> { .init(.get, "/groups/\(groupId.pathEncoded)/preferences") }
        static func addPreference(groupId: String, _ body: NewGroupPreference) -> Endpoint<GroupPreference> {
            .init(.post, "/groups/\(groupId.pathEncoded)/preferences", body: body)
        }
        /// Changes one of your preferred times in place (same body as adding one).
        static func updatePreference(groupId: String, id: String, _ body: NewGroupPreference) -> Endpoint<GroupPreference> {
            .init(.put, "/groups/\(groupId.pathEncoded)/preferences/\(id.pathEncoded)", body: body)
        }
        static func removePreference(groupId: String, id: String) -> Endpoint<EmptyResponse> {
            .init(.delete, "/groups/\(groupId.pathEncoded)/preferences/\(id.pathEncoded)")
        }
        static func invites(groupId: String) -> Endpoint<GroupInvitesResponse> { .init(.get, "/groups/\(groupId.pathEncoded)/invites") }
        static func revokeInvite(groupId: String, inviteId: String) -> Endpoint<EmptyResponse> {
            .init(.delete, "/groups/\(groupId.pathEncoded)/invites/\(inviteId.pathEncoded)")
        }
    }

    // MARK: Invites
    enum Invites {
        static func preview(token: String) -> Endpoint<InvitePreview> { .init(.get, "/invites/\(token.pathEncoded)") }
        static func accept(token: String) -> Endpoint<JoinedGroup> { .init(.post, "/invites/\(token.pathEncoded)/accept") }
        /// Turns down an invite sent to you (one with `personal`): it and its Activity item go.
        static func decline(token: String) -> Endpoint<EmptyResponse> { .init(.post, "/invites/\(token.pathEncoded)/decline") }
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
        /// "Confirm anyway": the proposer or an admin confirms before everyone has accepted.
        static func confirm(id: String) -> Endpoint<Proposal> { .init(.post, "/proposals/\(id.pathEncoded)/confirm") }
        static func override(id: String, userId: String, response: ResponseKind) -> Endpoint<Proposal> {
            .init(.post, "/proposals/\(id.pathEncoded)/override", body: OverrideBody(userId: userId, response: response))
        }
        /// Withdraws an open proposal, or cancels a confirmed plan (deleting its event).
        static func cancel(id: String) -> Endpoint<EmptyResponse> { .init(.delete, "/proposals/\(id.pathEncoded)") }
        /// "Keep the plan" after someone can't make it any more: clears `needsReview`.
        static func keep(id: String) -> Endpoint<Proposal> { .init(.post, "/proposals/\(id.pathEncoded)/keep") }
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
        /// Sets the cover photo (creator or admin; JPEG bytes, shrunk to fit 1600 px).
        static func setImage(id: String, jpeg: Data) -> Endpoint<GroupEvent> {
            var endpoint = Endpoint<GroupEvent>(.put, "/events/\(id.pathEncoded)/image", timeout: 60)
            endpoint.rawBody = (jpeg, "image/jpeg")
            return endpoint
        }
        static func removeImage(id: String) -> Endpoint<GroupEvent> { .init(.delete, "/events/\(id.pathEncoded)/image") }
    }

    // MARK: Chat
    enum Chat {
        static func messages(groupId: String, before: String? = nil, limit: Int = 50) -> Endpoint<MessagesPage> {
            .init(.get, "/groups/\(groupId.pathEncoded)/messages", query: ["before": before, "limit": String(limit)])
        }
        /// Every group chat you're in, latest message first (Activity's Chats tab).
        static func chats() -> Endpoint<ChatList> { .init(.get, "/chats") }
        /// A chat's media page: photos and GIFs (`media`) or files and passes (`files`), newest first;
        /// `before` is the last attachment's id, for the next page.
        static func media(groupId: String, kind: String, before: String? = nil) -> Endpoint<GroupMediaPage> {
            .init(.get, "/groups/\(groupId.pathEncoded)/attachments", query: ["kind": kind, "before": before, "limit": "60"])
        }
        /// Messages containing `query` (2–100 characters, any case), newest first.
        static func search(groupId: String, query: String, limit: Int = 50) -> Endpoint<MessagesPage> {
            .init(.get, "/groups/\(groupId.pathEncoded)/messages/search", query: ["q": query, "limit": String(limit)])
        }
        static func send(groupId: String, _ body: SendMessageBody) -> Endpoint<Message> {
            .init(.post, "/groups/\(groupId.pathEncoded)/messages", body: body)
        }
        static func receipts(groupId: String, upTo: String, state: ReceiptBody.State) -> Endpoint<EmptyResponse> {
            .init(.post, "/groups/\(groupId.pathEncoded)/messages/receipts", body: ReceiptBody(upTo: upTo, state: state))
        }
        static func info(messageId: String) -> Endpoint<MessageInfo> { .init(.get, "/messages/\(messageId.pathEncoded)/info") }
        static func thread(messageId: String) -> Endpoint<MessageThread> { .init(.get, "/messages/\(messageId.pathEncoded)/thread") }
        static func edit(messageId: String, body: String) -> Endpoint<Message> {
            .init(.patch, "/messages/\(messageId.pathEncoded)", body: EditMessageBody(body: body))
        }
        static func delete(messageId: String) -> Endpoint<EmptyResponse> { .init(.delete, "/messages/\(messageId.pathEncoded)") }
        static func vote(pollId: String, optionIds: [String]) -> Endpoint<Poll> {
            .init(.post, "/polls/\(pollId.pathEncoded)/vote", body: VoteBody(optionIds: optionIds))
        }
        static func closePoll(pollId: String) -> Endpoint<Poll> { .init(.post, "/polls/\(pollId.pathEncoded)/close") }
        /// Edits your poll: question, pick-one / pick-any, and a text poll's options (an id keeps one and its votes).
        static func updatePoll(pollId: String, _ body: UpdatePollBody) -> Endpoint<Poll> { .init(.patch, "/polls/\(pollId.pathEncoded)", body: body) }
        /// Uploads one photo, GIF or file for the chat (multipart, field `file`); send its id with a message.
        /// A video goes with `poster` (a JPEG frame from it) for its preview.
        static func upload(groupId: String, data: Data, name: String, mime: String, poster: Data? = nil) -> Endpoint<Attachment> {
            // Videos can be up to 300 MB.
            var endpoint = Endpoint<Attachment>(.post, "/groups/\(groupId.pathEncoded)/attachments", timeout: poster != nil || mime.hasPrefix("video/") ? 900 : 120)
            let form = MultipartForm(file: data, field: "file", name: name, mime: mime, poster: poster)
            endpoint.rawBody = (form.body, form.contentType)
            return endpoint
        }
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
        /// Passkeys on this account (Better Auth's own list, as web shows it).
        static func listPasskeys() -> Endpoint<[PasskeySummary]> { .init(.get, "/passkey/list-user-passkeys", mount: .auth) }
        static func deletePasskey(id: String) -> Endpoint<EmptyResponse> {
            .init(.post, "/passkey/delete-passkey", mount: .auth, body: PasskeyIdBody(id: id))
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

/// A passkey on the account: its name (often the device it was made on) and when it was added.
struct PasskeySummary: Decodable, Sendable, Identifiable, Equatable {
    let id: String
    var name: String?
    let createdAt: Date
}

struct PasskeyIdBody: Encodable, Sendable { let id: String }

struct UpdatePollBody: Encodable, Sendable {
    struct Option: Encodable, Sendable {
        var id: String?
        let label: String
    }
    let question: String
    let multi: Bool
    /// Left out for a date poll (its times can't change).
    var options: [Option]?
}
