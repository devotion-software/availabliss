import Foundation
import RevenueCat

/// Pro purchases through RevenueCat (docs/BILLING.md). Configured lazily, the first time
/// someone opens the upgrade sheet, with the public key from `Plan.purchase.ios` and the
/// signed-in user's id as the app user id, so Pro follows the account to every platform.
/// The server stays the source of truth: after a purchase or restore, call `POST /me/plan/refresh`.
@MainActor
final class Billing {
    /// A package from the current offering, ready to show.
    struct ProPackage: Identifiable {
        let id: String
        /// "Yearly", "Monthly", "Lifetime".
        let title: String
        /// Localised, e.g. "£19.99".
        let price: String
        /// "/year", "/month", or "" for one-off.
        let period: String
        let isAnnual: Bool
        fileprivate let package: Package
    }

    /// Sets RevenueCat up for `userId` (or switches to them if someone else used it before).
    func prepare(apiKey: String, userId: String) async throws {
        if !Purchases.isConfigured {
            #if DEBUG
            Purchases.logLevel = .warn
            #endif
            Purchases.configure(withAPIKey: apiKey, appUserID: userId)
        } else if Purchases.shared.appUserID != userId {
            _ = try await Purchases.shared.logIn(userId)
        }
    }

    /// The current offering's packages, annual first.
    func packages() async throws -> [ProPackage] {
        let offerings = try await Purchases.shared.offerings()
        let packages = offerings.current?.availablePackages ?? []
        return packages.map(Self.describe).sorted { $0.isAnnual && !$1.isAnnual }
    }

    /// False when the person cancelled the App Store sheet.
    func purchase(_ package: ProPackage) async throws -> Bool {
        let result = try await Purchases.shared.purchase(package: package.package)
        return !result.userCancelled
    }

    func restore() async throws {
        _ = try await Purchases.shared.restorePurchases()
    }

    /// The App Store's own subscription management sheet.
    func manageSubscriptions() async throws {
        try await Purchases.shared.showManageSubscriptions()
    }

    /// Forget the signed-in user so the next account starts clean.
    func signOut() async {
        guard Purchases.isConfigured, !Purchases.shared.isAnonymous else { return }
        _ = try? await Purchases.shared.logOut()
    }

    private static func describe(_ package: Package) -> ProPackage {
        let (title, period): (String, String) = switch package.packageType {
        case .annual: ("Yearly", "/year")
        case .sixMonth: ("6 months", "/6 months")
        case .threeMonth: ("3 months", "/3 months")
        case .twoMonth: ("2 months", "/2 months")
        case .monthly: ("Monthly", "/month")
        case .weekly: ("Weekly", "/week")
        case .lifetime: ("Lifetime", " once")
        default: (package.storeProduct.localizedTitle, "")
        }
        return ProPackage(
            id: package.identifier,
            title: title,
            price: package.storeProduct.localizedPriceString,
            period: period,
            isAnnual: package.packageType == .annual,
            package: package
        )
    }
}
