/**
 * Imports school holiday dates for local authorities from a CSV file.
 *
 *   bun scripts/import-school-holidays.ts path/to/holidays.csv
 *
 * CSV columns (header row required):
 *   country,authority,authority_name,name,start,end
 *   GB,bristol,Bristol City Council,Christmas holidays,2026-12-19,2027-01-03
 *
 * Rows for each (country, authority) in the file replace that authority's
 * existing rows, so re-importing an updated file is safe.
 */
import 'dotenv/config';
import { and, eq } from 'drizzle-orm';
import { db } from '../src/lib/server/db';
import { schoolHolidays } from '../src/lib/server/db/schema';
import { newId } from '../src/lib/server/ids';

const DATE = /^\d{4}-\d{2}-\d{2}$/;

const path = process.argv[2];
if (!path) {
	console.error('Usage: bun scripts/import-school-holidays.ts <file.csv>');
	process.exit(1);
}

const [header, ...lines] = (await Bun.file(path).text()).trim().split(/\r?\n/);
const columns = header.split(',').map((c) => c.trim());
const expected = ['country', 'authority', 'authority_name', 'name', 'start', 'end'];
if (expected.some((c, i) => columns[i] !== c)) {
	console.error(`Expected header: ${expected.join(',')}`);
	process.exit(1);
}

const rows = lines.map((line, i) => {
	const [country, authority, authorityName, name, start, end] = line.split(',').map((v) => v.trim());
	if (!DATE.test(start) || !DATE.test(end) || start > end || country.length !== 2) {
		throw new Error(`Line ${i + 2} is invalid: ${line}`);
	}
	return { id: newId(), country: country.toUpperCase(), authority, authorityName, name, start, end };
});

const authorities = new Map(rows.map((r) => [`${r.country}:${r.authority}`, r]));
await db.transaction(async (tx) => {
	for (const { country, authority } of authorities.values()) {
		await tx.delete(schoolHolidays).where(and(eq(schoolHolidays.country, country), eq(schoolHolidays.authority, authority)));
	}
	if (rows.length) await tx.insert(schoolHolidays).values(rows);
});

console.log(`Imported ${rows.length} holiday ranges for ${authorities.size} authorities.`);
process.exit(0);
