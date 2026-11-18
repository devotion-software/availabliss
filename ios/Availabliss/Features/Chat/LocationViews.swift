import CoreLocation
import MapKit
import SwiftUI

// MARK: - Links

/// Where a shared place can be opened: Apple Maps, Google Maps (the app if it's installed) and what3words.
enum LocationLinks {
    /// Coordinates as sent in links: 6 decimal places is about 10 cm.
    private static func coordinate(_ value: Double) -> String { String(format: "%.6f", value) }
    private static func pair(_ location: MessageLocation) -> String { "\(coordinate(location.lat)),\(coordinate(location.lng))" }

    /// maps.apple.com opens in Maps.
    static func appleMaps(_ location: MessageLocation) -> URL {
        var components = URLComponents(string: "https://maps.apple.com/")!
        components.queryItems = [
            URLQueryItem(name: "ll", value: pair(location)),
            URLQueryItem(name: "q", value: location.name ?? "Shared location"),
        ]
        return components.url!
    }

    /// The Google Maps app when it's installed (needs `comgooglemaps` in LSApplicationQueriesSchemes), else the website.
    @MainActor static func googleMaps(_ location: MessageLocation) -> URL {
        if let app = URL(string: "comgooglemaps://?q=\(pair(location))&center=\(pair(location))&zoom=16"), UIApplication.shared.canOpenURL(app) {
            return app
        }
        return URL(string: "https://www.google.com/maps/search/?api=1&query=\(pair(location))")!
    }

    /// what3words turns coordinates in the link into the square's three words (the app opens it if installed).
    static func what3words(_ location: MessageLocation) -> URL {
        URL(string: "https://what3words.com/\(pair(location))")!
    }
}

// MARK: - Card in a message

/// A shared place in a bubble: a small map with a pin, its name and address. Tapping asks where to open it.
struct LocationCard: View {
    let location: MessageLocation
    let isMine: Bool
    @Environment(\.theme) private var theme
    @Environment(\.openURL) private var openURL
    @Environment(\.colorScheme) private var colorScheme
    @State private var choosing = false

    static let width: CGFloat = 240
    private static let mapHeight: CGFloat = 130

    private var title: String { location.name ?? "Location" }
    private var detail: String? {
        if let address = location.address, !address.isEmpty { return address }
        if location.name == nil, let accuracy = location.accuracy { return "Accurate to \(Self.distance(accuracy))" }
        return nil
    }

    var body: some View {
        Button {
            choosing = true
        } label: {
            VStack(alignment: .leading, spacing: 0) {
                LocationSnapshot(location: location, scheme: colorScheme)
                    .frame(width: Self.width, height: Self.mapHeight)
                    .clipped()
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title)
                            .bodyFont(14, .semibold)
                            .foregroundStyle(isMine ? theme.onAccent : theme.ink)
                            .lineLimit(2)
                        if let detail {
                            Text(detail)
                                .bodyFont(12, relativeTo: .caption)
                                .foregroundStyle(isMine ? theme.onAccent.opacity(0.8) : theme.muted)
                                .lineLimit(2)
                        }
                    }
                    .multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                    Image(systemName: "arrow.up.right.square")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(isMine ? theme.onAccent.opacity(0.85) : theme.accentInk)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 8)
            }
            .frame(width: Self.width, alignment: .leading)
            .background(isMine ? theme.onAccent.opacity(0.12) : theme.surface)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(["Location: \(title)", detail].compactMap { $0 }.joined(separator: ", "))
        .accessibilityHint("Opens it in Maps, Google Maps or what3words")
        .confirmSheet("Open in", isPresented: $choosing, message: location.name ?? "This location") {
            [
                ConfirmOption("Apple Maps", systemImage: "map") { openURL(LocationLinks.appleMaps(location)) },
                ConfirmOption("Google Maps", systemImage: "globe", kind: .secondary) { openURL(LocationLinks.googleMaps(location)) },
                ConfirmOption("what3words", systemImage: "square.grid.3x3", kind: .secondary) { openURL(LocationLinks.what3words(location)) },
            ]
        }
    }

    /// "12 m", "1.2 km".
    static func distance(_ metres: Double) -> String {
        let formatter = MeasurementFormatter()
        formatter.unitOptions = .naturalScale
        formatter.numberFormatter.maximumFractionDigits = metres < 1000 ? 0 : 1
        return formatter.string(from: Measurement(value: metres, unit: UnitLength.meters))
    }
}

/// A still map of the place (MKMapSnapshotter), with the pin drawn on top. Kept in memory once made.
private struct LocationSnapshot: View {
    let location: MessageLocation
    let scheme: ColorScheme
    @Environment(\.theme) private var theme
    @State private var image: UIImage?

    private var key: String { "\(location.lat),\(location.lng),\(scheme == .dark ? "d" : "l")" }

    var body: some View {
        ZStack {
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                theme.accentSoft
                Image(systemName: "map").font(.system(size: 28, weight: .light)).foregroundStyle(theme.accentInk.opacity(0.5))
            }
            LocationPin()
        }
        .task(id: key) { image = await SnapshotCache.image(for: location, key: key, scheme: scheme) }
    }
}

/// The pin on a map card: a filled circle with a map pin, like the one in Maps.
struct LocationPin: View {
    @Environment(\.theme) private var theme
    var body: some View {
        Image(systemName: "mappin.circle.fill")
            .font(.system(size: 30))
            .symbolRenderingMode(.palette)
            .foregroundStyle(.white, theme.accent)
            .shadow(color: .black.opacity(0.25), radius: 3, y: 1)
            .accessibilityHidden(true)
    }
}

@MainActor
private enum SnapshotCache {
    private static var images: [String: UIImage] = [:]

    static func image(for location: MessageLocation, key: String, scheme: ColorScheme) async -> UIImage? {
        if let cached = images[key] { return cached }
        let options = MKMapSnapshotter.Options()
        options.region = MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: location.lat, longitude: location.lng),
            latitudinalMeters: 600,
            longitudinalMeters: 600
        )
        options.size = CGSize(width: LocationCard.width, height: 130)
        options.traitCollection = UITraitCollection(userInterfaceStyle: scheme == .dark ? .dark : .light)
        options.pointOfInterestFilter = .includingAll
        guard let snapshot = try? await MKMapSnapshotter(options: options).start() else { return nil }
        images[key] = snapshot.image
        return snapshot.image
    }
}

// MARK: - Picking a place

/// The device's position while the location sheet is open (When In Use permission, asked when it opens).
@MainActor @Observable
final class CurrentLocation: NSObject, CLLocationManagerDelegate {
    private(set) var location: CLLocation?
    private(set) var status: CLAuthorizationStatus
    private let manager = CLLocationManager()

    override init() {
        status = manager.authorizationStatus
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
    }

    var isAllowed: Bool { status == .authorizedWhenInUse || status == .authorizedAlways }
    var isDenied: Bool { status == .denied || status == .restricted }

    func start() {
        if status == .notDetermined { manager.requestWhenInUseAuthorization() } else if isAllowed { manager.startUpdatingLocation() }
    }

    func stop() { manager.stopUpdatingLocation() }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            self.status = status
            if self.isAllowed { self.manager.startUpdatingLocation() }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let latest = locations.last else { return }
        Task { @MainActor in self.location = latest }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {}
}

/// A place found by searching.
struct FoundPlace: Identifiable, Equatable {
    let id = UUID()
    let name: String
    let address: String?
    let coordinate: CLLocationCoordinate2D

    static func == (a: FoundPlace, b: FoundPlace) -> Bool { a.id == b.id }

    var location: MessageLocation {
        MessageLocation(lat: coordinate.latitude, lng: coordinate.longitude, name: name, address: address, accuracy: nil)
    }
}

/// "Location" from the + menu: a map of where you are, Send my current location, or search for a place and send that.
struct LocationPickerSheet: View {
    let send: (MessageLocation) async -> Bool
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @Environment(\.openURL) private var openURL
    @State private var current = CurrentLocation()
    @State private var position: MapCameraPosition = .userLocation(fallback: .automatic)
    @State private var query = ""
    @State private var results: [FoundPlace] = []
    @State private var isSearching = false
    @State private var searchFailed = false
    @State private var picked: FoundPlace?
    @State private var isSending = false

    private var myLocation: MessageLocation? {
        guard let location = current.location else { return nil }
        return MessageLocation(
            lat: location.coordinate.latitude,
            lng: location.coordinate.longitude,
            accuracy: location.horizontalAccuracy >= 0 ? location.horizontalAccuracy.rounded() : nil
        )
    }

    var body: some View {
        BottomSheet(title: "Share a location") {
            searchField
            if !results.isEmpty, picked == nil {
                resultsList
            } else if searchFailed {
                Text("Nothing found for “\(query)”.").bodyFont(14).foregroundStyle(theme.muted)
            }
            map
            if current.isDenied {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Location access is off for Availabliss. Turn it on in Settings to send where you are, or search for a place.")
                        .bodyFont(14)
                        .foregroundStyle(theme.muted)
                        .fixedSize(horizontal: false, vertical: true)
                    Button("Open Settings") {
                        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                    }
                    .buttonStyle(.textAction)
                }
            }
            if let picked {
                Button {
                    Task { await share(picked.location) }
                } label: {
                    LoadingLabel(title: "Send \(picked.name)", systemImage: "mappin.and.ellipse", isLoading: isSending)
                }
                .buttonStyle(.accent)
                .disabled(isSending)
            }
            Button {
                if let myLocation { Task { await share(myLocation) } }
            } label: {
                LoadingLabel(title: "Send my current location", systemImage: "location.fill", isLoading: isSending && picked == nil)
            }
            .buttonStyle(picked == nil ? .filled(.primary) : .filled(.secondary))
            .disabled(myLocation == nil || isSending)
            if let accuracy = myLocation?.accuracy, !current.isDenied {
                Text("Accurate to \(LocationCard.distance(accuracy))")
                    .bodyFont(12, relativeTo: .caption)
                    .foregroundStyle(theme.muted)
                    .frame(maxWidth: .infinity)
            } else if current.isAllowed {
                Text("Finding where you are…")
                    .bodyFont(12, relativeTo: .caption)
                    .foregroundStyle(theme.muted)
                    .frame(maxWidth: .infinity)
            }
        }
        .onAppear { current.start() }
        .onDisappear { current.stop() }
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(theme.muted)
            TextField("", text: $query, prompt: Text("Search for a place").foregroundStyle(theme.muted))
                .bodyFont(16)
                .submitLabel(.search)
                .autocorrectionDisabled()
                .onSubmit { Task { await search() } }
            if isSearching {
                ProgressView()
            } else if !query.isEmpty {
                Button {
                    clearSearch()
                } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(theme.muted)
                }
                .accessibilityLabel("Clear search")
            }
        }
        .padding(.horizontal, 14)
        .frame(height: 46)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(theme.surface))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(theme.line, lineWidth: 1))
    }

    private var resultsList: some View {
        VStack(spacing: 0) {
            ForEach(results) { place in
                Button {
                    pick(place)
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "mappin.circle.fill")
                            .font(.system(size: 22))
                            .foregroundStyle(theme.accentInk)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(place.name).bodyFont(15, .semibold).foregroundStyle(theme.ink)
                            if let address = place.address {
                                Text(address).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted).lineLimit(1)
                            }
                        }
                        Spacer(minLength: 0)
                    }
                    .frame(minHeight: 52)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .overlay(alignment: .top) {
                    if place != results.first { Rectangle().fill(theme.line).frame(height: 1) }
                }
            }
        }
        .padding(.horizontal, 14)
        .surfaceBackground(cornerRadius: 18)
    }

    private var map: some View {
        Map(position: $position) {
            UserAnnotation()
            if let picked {
                Marker(picked.name, coordinate: picked.coordinate).tint(theme.accent)
            }
        }
        .mapControls { MapUserLocationButton() }
        .frame(height: 240)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .accessibilityLabel(picked.map { "Map showing \($0.name)" } ?? "Map showing where you are")
    }

    private func clearSearch() {
        query = ""
        results = []
        searchFailed = false
        picked = nil
        withAnimation { position = .userLocation(fallback: .automatic) }
    }

    private func pick(_ place: FoundPlace) {
        picked = place
        withAnimation {
            position = .region(MKCoordinateRegion(center: place.coordinate, latitudinalMeters: 700, longitudinalMeters: 700))
        }
    }

    private func search() async {
        let text = query.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return }
        isSearching = true
        defer { isSearching = false }
        picked = nil
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = text
        if let here = current.location {
            request.region = MKCoordinateRegion(center: here.coordinate, latitudinalMeters: 50_000, longitudinalMeters: 50_000)
        }
        let found = (try? await MKLocalSearch(request: request).start())?.mapItems ?? []
        results = found.prefix(6).map { item in
            let mark = item.placemark
            let street = [mark.subThoroughfare, mark.thoroughfare].compactMap { $0 }.joined(separator: " ")
            let address = [street, mark.locality, mark.postalCode].filter { !($0 ?? "").isEmpty }.compactMap { $0 }.joined(separator: ", ")
            return FoundPlace(name: item.name ?? text, address: address.isEmpty ? nil : address, coordinate: mark.coordinate)
        }
        searchFailed = results.isEmpty
        if results.count == 1, let only = results.first { pick(only) }
    }

    private func share(_ location: MessageLocation) async {
        isSending = true
        defer { isSending = false }
        if await send(location) { dismiss() }
    }
}
