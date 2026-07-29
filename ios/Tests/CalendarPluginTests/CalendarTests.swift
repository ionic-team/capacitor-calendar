import EventKit
import XCTest

@testable import CalendarPlugin

class CalendarTests: XCTestCase {

    func testErrorCodesFollowUnifiedFormat() {
        XCTAssertEqual(CalendarError.unknown.code, "OS-PLUG-CLDR-0000")
        XCTAssertEqual(CalendarError.invalidArgument.code, "OS-PLUG-CLDR-0001")
        XCTAssertEqual(CalendarError.pendingOperation.code, "OS-PLUG-CLDR-0003")
        XCTAssertEqual(CalendarError.ioError.code, "OS-PLUG-CLDR-0004")
        XCTAssertEqual(CalendarError.notSupported.code, "OS-PLUG-CLDR-0005")
        XCTAssertEqual(CalendarError.operationCancelled.code, "OS-PLUG-CLDR-0006")
        XCTAssertEqual(CalendarError.permissionDenied.code, "OS-PLUG-CLDR-0020")
    }

    func testEventToDictionaryMapsWireFormat() {
        let implementation = Calendar()
        let event = EKEvent(eventStore: implementation.store)
        event.title = "Team sync"
        event.location = "Room 4"
        event.notes = "Weekly"
        event.startDate = Date(timeIntervalSince1970: 1_700_000_000)
        event.endDate = Date(timeIntervalSince1970: 1_700_003_600)
        event.isAllDay = false

        let dict = implementation.eventToDictionary(event)

        XCTAssertEqual(dict["title"] as? String, "Team sync")
        XCTAssertEqual(dict["location"] as? String, "Room 4")
        XCTAssertEqual(dict["notes"] as? String, "Weekly")
        XCTAssertEqual(dict["startDate"] as? Double, 1_700_000_000_000)
        XCTAssertEqual(dict["endDate"] as? Double, 1_700_003_600_000)
        XCTAssertEqual(dict["isAllDay"] as? Bool, false)
    }

    func testApplyRejectsInvalidDateRange() {
        let implementation = Calendar()
        let event = EKEvent(eventStore: implementation.store)
        let options: [String: Any] = [
            "title": "Broken",
            "startDate": 2_000,
            "endDate": 1_000
        ]
        XCTAssertThrowsError(try implementation.apply(options, to: event, requireDates: true)) { error in
            XCTAssertEqual((error as? CalendarError)?.code, "OS-PLUG-CLDR-0001")
        }
    }
}
