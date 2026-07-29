import Capacitor
import EventKit
import EventKitUI
import Foundation

/// Capacitor bridge for the Calendar plugin. Parses calls, ensures the
/// needed access level, and delegates to `Calendar`.
@objc(CalendarPlugin)
public class CalendarPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "CalendarPlugin"
    public let jsName = "Calendar"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "checkPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "createEvent", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "createEventInteractively", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "modifyEvent", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "findEvents", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "deleteEvent", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "listCalendars", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "createCalendar", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "deleteCalendar", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openCalendar", returnType: CAPPluginReturnPromise)
    ]

    private let implementation = Calendar()

    /// Call awaiting the interactive event editor.
    private var editCall: CAPPluginCall?
    private var editDelegate: EditDelegate?

    // MARK: - Permissions

    @objc override public func checkPermissions(_ call: CAPPluginCall) {
        let states = implementation.permissionStates()
        call.resolve(["readCalendar": states.read, "writeCalendar": states.write])
    }

    @objc override public func requestPermissions(_ call: CAPPluginCall) {
        let requested = call.getArray("permissions", String.self) ?? ["readCalendar", "writeCalendar"]
        implementation.requestAccess(read: requested.contains("readCalendar")) { [weak self] in
            self?.checkPermissions(call)
        }
    }

    // MARK: - Events

    @objc func createEvent(_ call: CAPPluginCall) {
        withAccess(call, read: false) {
            do {
                let id = try self.implementation.createEvent(self.options(of: call))
                call.resolve(["id": id])
            } catch {
                self.reject(call, error)
            }
        }
    }

    @objc func createEventInteractively(_ call: CAPPluginCall) {
        guard editCall == nil else {
            reject(call, CalendarError.pendingOperation)
            return
        }
        // iOS 17+ shows the editor without calendar permission; earlier
        // versions need access before the store can save from the editor.
        if #available(iOS 17.0, *) {
            presentEditor(call)
        } else {
            withAccess(call, read: false) {
                self.presentEditor(call)
            }
        }
    }

    @objc func modifyEvent(_ call: CAPPluginCall) {
        guard let filter = call.getObject("filter"), let newEvent = call.getObject("newEvent") else {
            reject(call, CalendarError.invalidArgument)
            return
        }
        withAccess(call, read: true) {
            do {
                try self.implementation.modifyEvent(filter: filter, newEvent: newEvent)
                call.resolve()
            } catch {
                self.reject(call, error)
            }
        }
    }

    @objc func findEvents(_ call: CAPPluginCall) {
        withAccess(call, read: true) {
            do {
                let events = try self.implementation.findEvents(self.options(of: call))
                call.resolve(["events": events])
            } catch {
                self.reject(call, error)
            }
        }
    }

    @objc func deleteEvent(_ call: CAPPluginCall) {
        let hasId = (call.getString("id")?.isEmpty == false)
        let hasFilter = ["title", "location", "notes", "startDate", "endDate", "calendarName"]
            .contains { call.options?[$0] != nil }
        guard hasId || hasFilter else {
            reject(call, CalendarError.invalidArgument)
            return
        }
        withAccess(call, read: true) {
            do {
                try self.implementation.deleteEvent(self.options(of: call))
                call.resolve()
            } catch {
                self.reject(call, error)
            }
        }
    }

    // MARK: - Calendars

    @objc func listCalendars(_ call: CAPPluginCall) {
        withAccess(call, read: true) {
            call.resolve(["calendars": self.implementation.listCalendars()])
        }
    }

    @objc func createCalendar(_ call: CAPPluginCall) {
        guard let name = call.getString("name"), !name.isEmpty else {
            reject(call, CalendarError.invalidArgument)
            return
        }
        withAccess(call, read: false) {
            do {
                let id = try self.implementation.createCalendar(name: name, color: call.getString("color"))
                call.resolve(["id": id])
            } catch {
                self.reject(call, error)
            }
        }
    }

    @objc func deleteCalendar(_ call: CAPPluginCall) {
        guard let name = call.getString("name"), !name.isEmpty else {
            reject(call, CalendarError.invalidArgument)
            return
        }
        withAccess(call, read: true) {
            do {
                try self.implementation.deleteCalendar(name: name)
                call.resolve()
            } catch {
                self.reject(call, error)
            }
        }
    }

    @objc func openCalendar(_ call: CAPPluginCall) {
        let ms = call.getDouble("date") ?? Date().timeIntervalSince1970 * 1000.0
        let date = Date(timeIntervalSince1970: ms / 1000.0)
        let seconds = Int(date.timeIntervalSinceReferenceDate)
        DispatchQueue.main.async {
            guard let url = URL(string: "calshow:\(seconds)") else {
                self.reject(call, CalendarError.unknown)
                return
            }
            UIApplication.shared.open(url, options: [:]) { opened in
                if opened {
                    call.resolve()
                } else {
                    self.reject(call, CalendarError.notSupported)
                }
            }
        }
    }

    // MARK: - Helpers

    /// Runs `body` on a background queue once the needed access level is
    /// granted, requesting it first when not yet determined.
    private func withAccess(_ call: CAPPluginCall, read: Bool, _ body: @escaping () -> Void) {
        let granted = read ? implementation.hasReadAccess() : implementation.hasWriteAccess()
        if granted {
            DispatchQueue.global(qos: .userInitiated).async(execute: body)
            return
        }
        implementation.requestAccess(read: read) { [weak self] in
            guard let self = self else { return }
            let granted = read ? self.implementation.hasReadAccess() : self.implementation.hasWriteAccess()
            if granted {
                DispatchQueue.global(qos: .userInitiated).async(execute: body)
            } else {
                self.reject(call, CalendarError.permissionDenied)
            }
        }
    }

    private func presentEditor(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            let event = EKEvent(eventStore: self.implementation.store)
            do {
                try self.implementation.apply(self.options(of: call), to: event, requireDates: true)
            } catch {
                self.reject(call, error)
                return
            }

            guard var presenter = self.bridge?.viewController else {
                self.reject(call, CalendarError.unknown)
                return
            }
            while let presented = presenter.presentedViewController {
                presenter = presented
            }

            self.editCall = call
            let delegate = EditDelegate { [weak self] action, savedId in
                guard let self = self, let call = self.editCall else { return }
                self.editCall = nil
                self.editDelegate = nil
                if action == .saved {
                    var result: [String: Any] = [:]
                    if let savedId = savedId {
                        result["id"] = savedId
                    }
                    call.resolve(result)
                } else {
                    self.reject(call, CalendarError.operationCancelled)
                }
            }
            self.editDelegate = delegate

            let controller = EKEventEditViewController()
            controller.eventStore = self.implementation.store
            controller.event = event
            controller.editViewDelegate = delegate
            presenter.present(controller, animated: true)
        }
    }

    private func options(of call: CAPPluginCall) -> [String: Any] {
        return (call.options as? [String: Any]) ?? [:]
    }

    private func reject(_ call: CAPPluginCall, _ error: Error) {
        let calendarError = error as? CalendarError ?? .unknown
        call.reject(calendarError.message, calendarError.code)
    }
}

/// `EKEventEditViewDelegate` wrapper reporting the edit outcome and, on save,
/// the created event's id.
private class EditDelegate: NSObject, EKEventEditViewDelegate {
    private let completion: (EKEventEditViewAction, String?) -> Void

    init(completion: @escaping (EKEventEditViewAction, String?) -> Void) {
        self.completion = completion
    }

    func eventEditViewController(_ controller: EKEventEditViewController, didCompleteWith action: EKEventEditViewAction) {
        let savedId = action == .saved ? controller.event?.calendarItemIdentifier : nil
        controller.dismiss(animated: true) {
            self.completion(action, savedId)
        }
    }
}
