import Foundation

enum When {

    private static var calendar: Calendar { .current }

    static func relative(_ date: Date, now: Date = Date()) -> String {
        let seconds = now.timeIntervalSince(date)
        if seconds < 60 { return String(localized: "now") }
        if seconds < 3600 { return String(localized: "\(Int(seconds / 60)) min") }
        if calendar.isDate(date, inSameDayAs: now) { return String(localized: "\(Int(seconds / 3600)) h") }
        if calendar.isDateInYesterday(date) { return String(localized: "yesterday") }
        return date.formatted(date: .numeric, time: .omitted)
    }

    static func seenAgo(_ date: Date?, now: Date = Date()) -> String {
        guard let date, date.timeIntervalSince1970 > 0 else { return String(localized: "not seen yet") }
        let seconds = now.timeIntervalSince(date)
        if seconds < 60 { return String(localized: "seen just now") }
        if seconds < 3600 { return String(localized: "seen \(Int(seconds / 60)) min ago") }
        if seconds < 86400 { return String(localized: "seen \(Int(seconds / 3600)) h ago") }
        if calendar.isDateInYesterday(date) { return String(localized: "seen yesterday") }
        return String(localized: "seen \(date.formatted(.dateTime.day().month(.abbreviated)))")
    }

    static func contact(_ date: Date) -> String {
        let time = date.formatted(date: .omitted, time: .shortened)
        if calendar.isDateInToday(date) { return String(localized: "today, \(time)") }
        if calendar.isDateInYesterday(date) { return String(localized: "yesterday, \(time)") }
        return date.formatted(date: .numeric, time: .shortened)
    }

    static func day(_ date: Date, now: Date = Date()) -> String {
        if calendar.isDateInToday(date) { return String(localized: "Today") }
        if calendar.isDateInYesterday(date) { return String(localized: "Yesterday") }
        if calendar.isDate(date, equalTo: now, toGranularity: .year) { return date.formatted(.dateTime.day().month(.wide)) }
        return date.formatted(.dateTime.day().month(.wide).year())
    }

    static func clock(_ date: Date) -> String { date.formatted(date: .omitted, time: .shortened) }

    static func exact(_ date: Date) -> String { date.formatted(.dateTime.day().month(.wide).year().hour().minute().second()) }

    static func long(_ date: Date) -> String { date.formatted(.dateTime.day().month(.wide).year()) }
}
