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
    private(set) var isLoading = false
    private(set) var isSaving = false
    var error: String?

    private let api: APIClient

    init(api: APIClient) {
        self.api = api
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
        do {
            async let countriesRequest = api.send(API.Regions.countries())
            if let user, user.country != nil {
                country = user.country
                subdivision = user.subdivision
                schoolAuthority = user.schoolAuthority
                timezone = user.timezone
            } else if let detected = try? await api.send(API.Regions.detect()) {
                country = detected.country
                subdivision = detected.subdivision
                timezone = detected.timezone ?? timezone
            }
            countries = try await countriesRequest.countries
            await loadCountryDetails()
        } catch {
            self.error = error.userMessage
        }
    }

    func selectCountry(_ code: String) async {
        guard code != country else { return }
        country = code
        subdivision = nil
        schoolAuthority = nil
        await loadCountryDetails()
    }

    func save(session: SessionStore) async -> Bool {
        isSaving = true
        defer { isSaving = false }
        do {
            try await session.update(UserPatch(country: country, subdivision: subdivision, schoolAuthority: schoolAuthority, timezone: timezone))
            return true
        } catch {
            self.error = error.userMessage
            return false
        }
    }

    private func loadCountryDetails() async {
        guard let country else {
            subdivisions = []
            authorities = []
            return
        }
        async let subs = try? api.send(API.Regions.subdivisions(country: country))
        async let auths = try? api.send(API.Regions.schoolAuthorities(country: country))
        subdivisions = await subs?.subdivisions ?? []
        authorities = await auths?.authorities ?? []
    }
}
