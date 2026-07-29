package com.capacitorjs.plugins.calendar

/**
 * Unified error set shared with the Cordova plugin: identical codes and
 * messages on both platforms.
 */
enum class CalendarError(val code: String, val message: String) {
    UNKNOWN("OS-PLUG-CLDR-0000", "An unknown error occurred."),
    INVALID_ARGUMENT("OS-PLUG-CLDR-0001", "Invalid arguments were provided."),
    PENDING_OPERATION("OS-PLUG-CLDR-0003", "A pending operation is already in progress."),
    IO_ERROR("OS-PLUG-CLDR-0004", "An I/O error occurred while accessing the calendar."),
    NOT_SUPPORTED("OS-PLUG-CLDR-0005", "The operation is not supported on this device."),
    OPERATION_CANCELLED("OS-PLUG-CLDR-0006", "The operation was cancelled."),
    PERMISSION_DENIED("OS-PLUG-CLDR-0020", "Calendar permission was denied.")
}

/** Enums cannot be thrown in Kotlin, so implementation code throws this wrapper. */
class CalendarException(val error: CalendarError) : Exception(error.message)
