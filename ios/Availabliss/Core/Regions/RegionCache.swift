import Foundation

/// The region lists (`/regions/*`, which need no sign-in), fetched once and shared, so the sign-up
/// wizard's first step and the first group's timing don't wait for them. Sign-in starts them early
/// (`prefetch`); asking again while a request is in flight joins it rather than sending another.
/// Countries are also kept on disk, as they hardly ever change.
@MainActor
final class RegionCache {
    static let shared = RegionCache()

    private var detected: Task<DetectedRegion?, Never>?
    private var countries: Task<[Country], Error>?
    private var subdivisions: [String: Task<[Subdivision], Never>] = [:]
    private var authorities: [String: Task<[SchoolAuthority], Never>] = [:]
    private static let countriesKey = "regions.countries"

    /// Starts everything the wizard's first step needs: where you seem to be, the countries, and
    /// that country's regions and school authorities.
    func prefetch(api: APIClient) {
        _ = countries(api: api)
        Task {
            if let country = await detect(api: api)?.country { prefetchDetails(country: country, api: api) }
        }
    }

    func prefetchDetails(country: String, api: APIClient) {
        _ = subdivisionsTask(country: country, api: api)
        _ = authoritiesTask(country: country, api: api)
    }

    func detect(api: APIClient) async -> DetectedRegion? {
        if let detected { return await detected.value }
        let task = Task { try? await api.send(API.Regions.detect()) }
        detected = task
        let value = await task.value
        if value == nil, detected == task { detected = nil }
        return value
    }

    /// The saved list straight away when there is one (refreshed in the background), else the server's.
    func countries(api: APIClient) -> Task<[Country], Error> {
        if let countries { return countries }
        let saved = UserDefaults.standard.data(forKey: Self.countriesKey).flatMap { try? JSONDecoder().decode([Country].self, from: $0) }
        let fetch = Task<[Country], Error> {
            let list = try await api.send(API.Regions.countries()).countries
            if let data = try? JSONEncoder().encode(list) { UserDefaults.standard.set(data, forKey: Self.countriesKey) }
            return list
        }
        let task: Task<[Country], Error>
        if let saved, !saved.isEmpty {
            task = Task { saved }
        } else {
            task = fetch
        }
        // A failed fetch is tried again next time.
        countries = task
        Task {
            if (try? await fetch.value) == nil, countries == fetch { countries = nil }
        }
        return task
    }

    func subdivisions(country: String, api: APIClient) async -> [Subdivision] {
        await subdivisionsTask(country: country, api: api).value
    }

    func authorities(country: String, api: APIClient) async -> [SchoolAuthority] {
        await authoritiesTask(country: country, api: api).value
    }

    private func subdivisionsTask(country: String, api: APIClient) -> Task<[Subdivision], Never> {
        if let task = subdivisions[country] { return task }
        let task = Task { (try? await api.send(API.Regions.subdivisions(country: country)).subdivisions) ?? [] }
        subdivisions[country] = task
        Task { if await task.value.isEmpty, subdivisions[country] == task { subdivisions[country] = nil } }
        return task
    }

    private func authoritiesTask(country: String, api: APIClient) -> Task<[SchoolAuthority], Never> {
        if let task = authorities[country] { return task }
        let task = Task { (try? await api.send(API.Regions.schoolAuthorities(country: country)).authorities) ?? [] }
        authorities[country] = task
        Task { if await task.value.isEmpty, authorities[country] == task { authorities[country] = nil } }
        return task
    }
}
