import Foundation
import Observation

/// Country / region / school-authority selection, used in onboarding and settings.
@MainActor
@Observable
final class RegionViewModel {
    private(set) var countries: [Country] = []
    private(set) var subdivisions: [Subdivision] = []
    private(set) var authorities: [SchoolAuthority] = []
    var country: String?
    var subdivision: String?
    var schoolAuthority: String?
    var timezone = TimeZone.current.identifier
    /// Asked for during onboarding (email sign-up creates accounts without one).
    var name = ""
    /// Also onboarding: prefilled with the username the account was given.
    let username: UsernameFieldModel
    private(set) var isLoading = false
    private(set) var isSaving = false
    var error: String?

    private let api: APIClient

    init(api: APIClient) {
        self.api = api
        self.username = UsernameFieldModel(api: api)
    }

    var countryName: String? { countries.first { $0.code == country }?.name }
    var subdivisionName: String? { subdivisions.first { $0.code == subdivision }?.name }
    var authorityName: String? { authorities.first { $0.id == schoolAuthority }?.name }

    /// "United Kingdom · England"
    var summary: String {
        [countryName, subdivisionName].compactMap { $0 }.joined(separator: " · ")
    }

    /// Loads lists, prefilled from the user's saved region or `GET /regions/detect`.
    func load(user: User?) async {
        isLoading = true
        defer { isLoading = false }
        // An account without a name yet starts from the one typed on the intro.
        if name.isEmpty {
            let accountName = user?.name ?? ""
            name = accountName.isEmpty ? (IntroName.saved ?? "") : accountName
        }
        username.prefill(user?.username)
        // Shared with sign-in, which starts these early, so they're usually here already.
        let cache = RegionCache.shared
        let countriesRequest = cache.countries(api: api)
        if let user, user.country != nil {
            country = user.country
            subdivision = user.subdivision
            schoolAuthority = user.schoolAuthority
            timezone = user.timezone
        } else if let detected = await cache.detect(api: api) {
            country = detected.country
            subdivision = detected.subdivision
            timezone = detected.timezone ?? timezone
        }
        // The country's regions and authorities don't wait for the list of countries.
        async let details: Void = loadCountryDetails()
        do {
            countries = try await countriesRequest.value
        } catch {
            self.error = error.userMessage
        }
        await details
    }

    func selectCountry(_ code: String) async {
        guard code != country else { return }
        country = code
        subdivision = nil
        schoolAuthority = nil
        await loadCountryDetails()
    }

    var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }

    /// Saves the region, and the name and username too when `includingName`.
    func save(session: SessionStore, includingName: Bool = false) async -> Bool {
        isSaving = true
        defer { isSaving = false }
        do {
            var patch = UserPatch.region(country: country, subdivision: subdivision, schoolAuthority: schoolAuthority, timezone: timezone)
            if includingName {
                patch.name = trimmedName
                patch.username = username.patchValue
            }
            let user = try await session.update(patch)
            if includingName { username.saved(user.username) }
            return true
        } catch {
            if !(includingName && username.handleSaveError(error)) { self.error = error.userMessage }
            return false
        }
    }

    private func loadCountryDetails() async {
        guard let country else {
            subdivisions = []
            authorities = []
            return
        }
        let cache = RegionCache.shared
        cache.prefetchDetails(country: country, api: api)
        async let subs = cache.subdivisions(country: country, api: api)
        async let auths = cache.authorities(country: country, api: api)
        subdivisions = await subs
        authorities = await auths
    }
}
