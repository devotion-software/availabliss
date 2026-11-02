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
