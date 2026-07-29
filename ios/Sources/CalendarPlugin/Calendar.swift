import EventKit
import Foundation

/// Business logic for reading, creating, modifying and removing calendar
/// events and calendars via EventKit (`EKEventStore`). Holds no
/// bridge-framework types; the bridge plugin parses calls and delegates here.
@objc public class Calendar: NSObject {

    let store = EKEventStore()

    // MARK: - Authorization

    /// `true` when events can be read: full access, or plain authorization on
    /// iOS versions before access levels were split.
    @objc public func hasReadAccess() -> Bool {
        let status = EKEventStore.authorizationStatus(for: .event)
        if #available(iOS 17.0, *) {
            return status == .fullAccess
        }
        return status == .authorized
    }

    /// `true` when events can be added: full access, or iOS 17+ write-only
    /// access ("Add Events Only").
    @objc public func hasWriteAccess() -> Bool {
        let status = EKEventStore.authorizationStatus(for: .event)
        if #available(iOS 17.0, *) {
            return status == .fullAccess || status == .writeOnly
        }
        return status == .authorized
    }

    public func permissionStates() -> (read: String, write: String) {
        let status = EKEventStore.authorizationStatus(for: .event)
        switch status {
        case .notDetermined:
            return ("prompt", "prompt")
        case .denied, .restricted:
            return ("denied", "denied")
        default:
            return (hasReadAccess() ? "granted" : "denied", hasWriteAccess() ? "granted" : "denied")
        }
    }

    /// Requests full access when reading is needed, or write-only access when
    /// only writing is (iOS 17+; earlier versions have a single access level).
    public func requestAccess(read: Bool, completion: @escaping () -> Void) {
        let handler: (Bool, Error?) -> Void = { _, _ in completion() }
        if #available(iOS 17.0, *) {
            if read {
                store.requestFullAccessToEvents(completion: handler)
            } else {
                store.requestWriteOnlyAccessToEvents(completion: handler)
            }
        } else {
            store.requestAccess(to: .event, completion: handler)
        }
    }

    // MARK: - Events

    public func createEvent(_ options: [String: Any]) throws -> String {
        let event = EKEvent(eventStore: store)
        try apply(options, to: event, requireDates: true)
        do {
            try store.save(event, span: .thisEvent, commit: true)
        } catch {
            throw CalendarError.ioError
        }
        return event.calendarItemIdentifier
    }

    public func modifyEvent(filter: [String: Any], newEvent: [String: Any]) throws {
        guard let event = try findMatchingEvents(filter).first else {
            throw CalendarError.invalidArgument
        }
        try apply(newEvent, to: event, requireDates: false)
        do {
            try store.save(event, span: .thisEvent, commit: true)
        } catch {
            throw CalendarError.ioError
        }
    }

    public func findEvents(_ filter: [String: Any]) throws -> [[String: Any]] {
        return try findMatchingEvents(filter).map(eventToDictionary)
    }

    public func deleteEvent(_ options: [String: Any]) throws {
        if let id = options["id"] as? String, !id.isEmpty {
            guard let event = store.calendarItem(withIdentifier: id) as? EKEvent else {
                throw CalendarError.invalidArgument
            }
            // With fromDate, keep occurrences before it and remove the rest
            // of the series from the first occurrence at or after it.
            var target = event
            if let fromMs = (options["fromDate"] as? NSNumber)?.doubleValue {
                let fromDate = Date(timeIntervalSince1970: fromMs / 1000)
                let predicate = store.predicateForEvents(
                    withStart: fromDate,
                    end: Date.distantFuture,
                    calendars: [event.calendar]
                )
                let occurrences = store.events(matching: predicate)
                    .filter { $0.calendarItemIdentifier == id }
                    .sorted { $0.startDate < $1.startDate }
                guard let first = occurrences.first else {
                    return
                }
                target = first
            }
            do {
                try store.remove(target, span: .futureEvents, commit: true)
            } catch {
                throw CalendarError.ioError
            }
            return
        }
        let matches = try findMatchingEvents(options)
        guard !matches.isEmpty else {
            throw CalendarError.invalidArgument
        }
        do {
            for event in matches {
                try store.remove(event, span: .thisEvent, commit: false)
            }
            try store.commit()
        } catch {
            throw CalendarError.ioError
        }
    }

    // MARK: - Calendars

    public func listCalendars() -> [[String: Any]] {
        let defaultCalendar = store.defaultCalendarForNewEvents
        return store.calendars(for: .event).map { calendar in
            var entry: [String: Any] = [
                "id": calendar.calendarIdentifier,
                "name": calendar.title,
                "displayName": calendar.title
            ]
            if let defaultCalendar = defaultCalendar {
                entry["isPrimary"] = calendar.calendarIdentifier == defaultCalendar.calendarIdentifier
            }
            return entry
        }
    }

    public func createCalendar(name: String, color: String?) throws -> String {
        let calendar = EKCalendar(for: .event, eventStore: store)
        calendar.title = name
        if let color = color, let cgColor = Calendar.cgColor(fromHex: color) {
            calendar.cgColor = cgColor
        }
        guard let source = store.defaultCalendarForNewEvents?.source
            ?? store.sources.first(where: { $0.sourceType == .local }) else {
            throw CalendarError.ioError
        }
        calendar.source = source
        do {
            try store.saveCalendar(calendar, commit: true)
        } catch {
            throw CalendarError.ioError
        }
        return calendar.calendarIdentifier
    }

    public func deleteCalendar(name: String) throws {
        guard let calendar = store.calendars(for: .event).first(where: { $0.title == name }) else {
            throw CalendarError.invalidArgument
        }
        do {
            try store.removeCalendar(calendar, commit: true)
        } catch {
            throw CalendarError.ioError
        }
    }

    // MARK: - Matching

    /// Events within the filter's date range whose title, location and notes
    /// contain the respective filter values (case-insensitive). A
    /// `calendarName` restricts the search to that calendar.
    private func findMatchingEvents(_ filter: [String: Any]) throws -> [EKEvent] {
        let now = Date()
        let start = (filter["startDate"] as? NSNumber).map { Date(timeIntervalSince1970: $0.doubleValue / 1000.0) }
            ?? now.addingTimeInterval(-182 * 24 * 60 * 60)
        let end = (filter["endDate"] as? NSNumber).map { Date(timeIntervalSince1970: $0.doubleValue / 1000.0) }
            ?? now.addingTimeInterval(2 * 365 * 24 * 60 * 60)

        var calendars: [EKCalendar]?
        if let calendarName = filter["calendarName"] as? String, !calendarName.isEmpty {
            let matching = store.calendars(for: .event).filter { $0.title == calendarName }
            guard !matching.isEmpty else {
                throw CalendarError.invalidArgument
            }
            calendars = matching
        }

        let predicate = store.predicateForEvents(withStart: start, end: end, calendars: calendars)
        var events = store.events(matching: predicate)

        for key in ["title", "location", "notes"] {
            guard let needle = filter[key] as? String, !needle.isEmpty else { continue }
            events = events.filter { event in
                let haystack: String?
                switch key {
                case "title": haystack = event.title
                case "location": haystack = event.location
                default: haystack = event.notes
                }
                return haystack?.range(of: needle, options: .caseInsensitive) != nil
            }
        }
        return events
    }

    // MARK: - Writing fields

    /// Applies the option fields present in `options` to `event`. With
    /// `requireDates`, `title`, `startDate` and `endDate` must be present and
    /// the range valid.
    func apply(_ options: [String: Any], to event: EKEvent, requireDates: Bool) throws {
        let startMs = (options["startDate"] as? NSNumber)?.doubleValue
        let endMs = (options["endDate"] as? NSNumber)?.doubleValue
        if requireDates {
            guard let title = options["title"] as? String, !title.isEmpty,
                  let startMs = startMs, let endMs = endMs, endMs >= startMs else {
                throw CalendarError.invalidArgument
            }
            event.title = title
        } else if let title = options["title"] as? String {
            event.title = title
        }

        if let startMs = startMs {
            event.startDate = Date(timeIntervalSince1970: startMs / 1000.0)
        }
        if let endMs = endMs {
            event.endDate = Date(timeIntervalSince1970: endMs / 1000.0)
        }
        if let location = options["location"] as? String {
            event.location = location
        }
        if let notes = options["notes"] as? String {
            event.notes = notes
        }
        if let isAllDay = options["isAllDay"] as? Bool {
            event.isAllDay = isAllDay
        }
        if let url = options["url"] as? String, let parsed = URL(string: url) {
            event.url = parsed
        }

        if event.calendar == nil || options["calendarId"] != nil || options["calendarName"] != nil {
            event.calendar = try resolveCalendar(options)
        }

        for key in ["firstReminderMinutes", "secondReminderMinutes"] {
            if let minutes = (options[key] as? NSNumber)?.doubleValue {
                event.addAlarm(EKAlarm(relativeOffset: -minutes * 60))
            }
        }

        if let recurrence = options["recurrence"] as? [String: Any] {
            event.recurrenceRules = [try recurrenceRule(from: recurrence)]
        }
    }

    private func resolveCalendar(_ options: [String: Any]) throws -> EKCalendar {
        if let id = options["calendarId"] as? String, !id.isEmpty {
            guard let calendar = store.calendar(withIdentifier: id) else {
                throw CalendarError.invalidArgument
            }
            return calendar
        }
        if let name = options["calendarName"] as? String, !name.isEmpty {
            guard let calendar = store.calendars(for: .event).first(where: { $0.title == name }) else {
                throw CalendarError.invalidArgument
            }
            return calendar
        }
        guard let calendar = store.defaultCalendarForNewEvents else {
            throw CalendarError.ioError
        }
        return calendar
    }

    private func recurrenceRule(from options: [String: Any]) throws -> EKRecurrenceRule {
        let frequency: EKRecurrenceFrequency
        switch options["frequency"] as? String {
        case "daily": frequency = .daily
        case "weekly": frequency = .weekly
        case "monthly": frequency = .monthly
        case "yearly": frequency = .yearly
        default: throw CalendarError.invalidArgument
        }
        let interval = max((options["interval"] as? NSNumber)?.intValue ?? 1, 1)
        var end: EKRecurrenceEnd?
        if let endMs = (options["endDate"] as? NSNumber)?.doubleValue {
            end = EKRecurrenceEnd(end: Date(timeIntervalSince1970: endMs / 1000.0))
        } else if let count = (options["count"] as? NSNumber)?.intValue, count > 0 {
            end = EKRecurrenceEnd(occurrenceCount: count)
        }
        return EKRecurrenceRule(recurrenceWith: frequency, interval: interval, end: end)
    }

    // MARK: - Reading fields

    func eventToDictionary(_ event: EKEvent) -> [String: Any] {
        var entry: [String: Any] = [
            "id": event.calendarItemIdentifier,
            "startDate": event.startDate.timeIntervalSince1970 * 1000.0,
            "endDate": event.endDate.timeIntervalSince1970 * 1000.0,
            "isAllDay": event.isAllDay
        ]
        if let title = event.title {
            entry["title"] = title
        }
        if let location = event.location {
            entry["location"] = location
        }
        if let notes = event.notes {
            entry["notes"] = notes
        }
        if let calendar = event.calendar {
            entry["calendarId"] = calendar.calendarIdentifier
            entry["calendarName"] = calendar.title
        }
        if let attendees = event.attendees, !attendees.isEmpty {
            entry["attendees"] = attendees.map(attendeeToDictionary)
        }
        return entry
    }

    private func attendeeToDictionary(_ participant: EKParticipant) -> [String: Any] {
        var entry: [String: Any] = [
            "status": Self.attendeeStatus(participant.participantStatus)
        ]
        if let name = participant.name {
            entry["name"] = name
        }
        let url = participant.url.absoluteString
        if url.hasPrefix("mailto:") {
            entry["email"] = String(url.dropFirst("mailto:".count))
        }
        return entry
    }

    private static func attendeeStatus(_ status: EKParticipantStatus) -> String {
        switch status {
        case .pending: return "pending"
        case .accepted: return "accepted"
        case .declined: return "declined"
        case .tentative: return "tentative"
        case .delegated: return "delegated"
        case .completed: return "completed"
        case .inProcess: return "in-process"
        default: return "unknown"
        }
    }

    private static func cgColor(fromHex hex: String) -> CGColor? {
        var value = hex.trimmingCharacters(in: .whitespaces)
        if value.hasPrefix("#") {
            value.removeFirst()
        }
        guard value.count == 6, let rgb = UInt32(value, radix: 16) else {
            return nil
        }
        return CGColor(
            red: CGFloat((rgb >> 16) & 0xFF) / 255.0,
            green: CGFloat((rgb >> 8) & 0xFF) / 255.0,
            blue: CGFloat(rgb & 0xFF) / 255.0,
            alpha: 1.0
        )
    }
}
