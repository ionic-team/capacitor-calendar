import Foundation

/// Unified error set shared with the Cordova plugin: identical codes and
/// messages on both platforms.
public enum CalendarError: Error {
    case unknown
    case invalidArgument
    case pendingOperation
    case ioError
    case notSupported
    case operationCancelled
    case permissionDenied

    public var code: String {
        switch self {
        case .unknown: return "OS-PLUG-CLDR-0000"
        case .invalidArgument: return "OS-PLUG-CLDR-0001"
        case .pendingOperation: return "OS-PLUG-CLDR-0003"
        case .ioError: return "OS-PLUG-CLDR-0004"
        case .notSupported: return "OS-PLUG-CLDR-0005"
        case .operationCancelled: return "OS-PLUG-CLDR-0006"
        case .permissionDenied: return "OS-PLUG-CLDR-0020"
        }
    }

    public var message: String {
        switch self {
        case .unknown: return "An unknown error occurred."
        case .invalidArgument: return "Invalid arguments were provided."
        case .pendingOperation: return "A pending operation is already in progress."
        case .ioError: return "An I/O error occurred while accessing the calendar."
        case .notSupported: return "The operation is not supported on this device."
        case .operationCancelled: return "The operation was cancelled."
        case .permissionDenied: return "Calendar permission was denied."
        }
    }
}
