import Foundation

/// A saved timing to reuse when creating groups (`/me/templates`).
struct RequirementTemplate: Codable, Sendable, Identifiable, Equatable {
    let id: String
    var name: String
    var requirement: Requirement
    let createdAt: Date

    /// The template's timing, ready for a new group: same rules, but the search range starts
    /// today (keeping its length), specific dates already over are dropped, and times are read
    /// in your time zone. Mirrors `requirementFromTemplate` in web/src/lib/client/templates.ts.
    func requirementForNewGroup(today: CalendarDay = .today(), timezone: String, calendar: Calendar = .current) -> Requirement {
        var r = requirement
        let from = r.range.from.startDate(calendar: calendar)
        let to = r.range.to.startDate(calendar: calendar)
        let length = max(0, calendar.dateComponents([.day], from: from, to: to).day ?? 0)
        r.range = DateRange(from: today, to: today.adding(days: length, calendar: calendar))
        r.dates = r.dates.filter { $0.to >= today }
        r.customSchoolHolidays = r.customSchoolHolidays.filter { $0.to >= today }
        r.timezone = timezone
        return r
    }
}

extension Requirement {
    /// True when this is the same timing as `other`, ignoring when the search range starts (its
    /// length still counts) and the time zone: a template's range moves to "today" when it's used.
    /// Mirrors `sameTiming` in web/src/lib/client/templates.ts.
    func isSameTiming(as other: Requirement, calendar: Calendar = .current) -> Bool {
        func normalized(_ r: Requirement) -> Requirement {
            var r = r
            let length = calendar.dateComponents([.day], from: r.range.from.startDate(calendar: calendar), to: r.range.to.startDate(calendar: calendar)).day ?? 0
            let anchor = CalendarDay(year: 2000, month: 1, day: 1)
            r.range = DateRange(from: anchor, to: anchor.adding(days: length, calendar: calendar))
            r.days = Array(Set(r.days)).sorted()
            r.timezone = ""
            return r
        }
        return normalized(self) == normalized(other)
    }
}

extension Array where Element == RequirementTemplate {
    /// The template with the same timing as `requirement`, if one was saved.
    func matching(_ requirement: Requirement) -> RequirementTemplate? {
        first { $0.requirement.isSameTiming(as: requirement) }
    }
}

struct TemplatesResponse: Decodable, Sendable { let templates: [RequirementTemplate] }

struct CreateTemplateBody: Encodable, Sendable {
    let name: String
    let requirement: Requirement
}

struct RenameTemplateBody: Encodable, Sendable { let name: String }

extension Requirement {
    /// "A weekend · Fri 18:00 → Sun · school holidays", for template lists.
    var templateSummary: String {
        var parts = [draftSummary]
        if case .span = length {} else { parts.append(Self.describeDays(days)) }
        if schoolHolidaysOnly { parts.append("school holidays") }
        if !dates.isEmpty { parts.append(Formatting.plural(dates.count, "date range")) }
        return parts.joined(separator: " · ")
    }

    /// "any day", "weekdays", "weekends", "Fri–Sun", "Mon, Wed, Fri".
    static func describeDays(_ days: [Int]) -> String {
        let sorted = Array(Set(days)).sorted()
        if sorted.isEmpty || sorted.count == 7 { return "any day" }
        if sorted == [0, 1, 2, 3, 4] { return "weekdays" }
        if sorted == [5, 6] { return "weekends" }
        let contiguous = zip(sorted, sorted.dropFirst()).allSatisfy { $1 == $0 + 1 }
        if contiguous && sorted.count > 2 { return "\(Weekday.short(sorted[0]))–\(Weekday.short(sorted[sorted.count - 1]))" }
        return sorted.map(Weekday.short).joined(separator: ", ")
    }
}
