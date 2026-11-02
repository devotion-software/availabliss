/**
 * Plans and web purchases. The RevenueCat web SDK (Web Billing, backed by
 * Stripe) is only loaded when someone opens the upgrade sheet.
 */
import { api, type Fetch } from './api';
import type { Plan } from '#lib/types';

export const getPlan = (fetch?: Fetch) => api.get<Plan>('/me/plan', { fetch });
/** Re-reads the plan from RevenueCat right after a purchase (the webhook may be a moment behind). */
export const refreshPlan = () => api.post<Plan>('/me/plan/refresh', {});

export interface WebPackage {
	id: string;
	/** "Monthly", "Yearly", "Lifetime" */
	label: string;
	price: string;
	/** "/month", "/year", "" */
	per: string;
	/** The SDK's package object, handed back to `buyOnWeb`. */
	raw: unknown;
}

type PurchasesModule = typeof import('@revenuecat/purchases-js');
type Purchases = InstanceType<PurchasesModule['Purchases']>;

let sdk: { module: PurchasesModule; purchases: Purchases; userId: string } | null = null;

async function purchasesFor(apiKey: string, userId: string) {
	if (sdk?.userId === userId) return sdk;
	const module = await import('@revenuecat/purchases-js');
	// Our user id is the RevenueCat app user id on every platform, so purchases follow the account.
	const purchases = module.Purchases.configure({ apiKey, appUserId: userId });
	sdk = { module, purchases, userId };
	return sdk;
}

const LABELS: Record<string, [string, string]> = {
	$rc_monthly: ['Monthly', '/month'],
	$rc_annual: ['Yearly', '/year'],
	$rc_six_month: ['6 months', '/6 months'],
	$rc_three_month: ['3 months', '/3 months'],
	$rc_weekly: ['Weekly', '/week'],
	$rc_lifetime: ['Lifetime', ' once']
};

/** The current offering's packages, or [] when there's nothing to sell yet. */
export async function webPackages(apiKey: string, userId: string): Promise<WebPackage[]> {
	const { purchases } = await purchasesFor(apiKey, userId);
	const offering = (await purchases.getOfferings()).current;
	return (offering?.availablePackages ?? []).map((pkg) => {
		const [label, per] = LABELS[pkg.identifier] ?? [pkg.webBillingProduct.title, ''];
		return { id: pkg.identifier, label, per, price: pkg.webBillingProduct.price.formattedPrice, raw: pkg };
	});
}

/** Opens RevenueCat's checkout. Resolves 'cancelled' when the person closes it. */
export async function buyOnWeb(apiKey: string, userId: string, pkg: WebPackage, email?: string): Promise<'purchased' | 'cancelled'> {
	const { module, purchases } = await purchasesFor(apiKey, userId);
	try {
		await purchases.purchase({ rcPackage: pkg.raw as Parameters<Purchases['purchase']>[0]['rcPackage'], customerEmail: email });
		return 'purchased';
	} catch (err) {
		if (err instanceof module.PurchasesError && err.errorCode === module.ErrorCode.UserCancelledError) return 'cancelled';
		throw err;
	}
}

/** Where a web subscriber manages or cancels (RevenueCat's customer portal). */
export async function webManagementUrl(apiKey: string, userId: string): Promise<string | null> {
	const { purchases } = await purchasesFor(apiKey, userId);
	return (await purchases.getCustomerInfo()).managementURL;
}

/** "the App Store" / "Google Play" for subscriptions managed outside the web. */
export function storeName(store: string | null): string | null {
	if (store === 'app_store' || store === 'mac_app_store') return 'the App Store';
	if (store === 'play_store') return 'Google Play';
	return null;
}
