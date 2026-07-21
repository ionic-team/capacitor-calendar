package com.capacitorjs.plugins.calendar

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import androidx.activity.result.ActivityResult
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Capacitor bridge for the Calendar plugin. Parses calls, ensures the needed
 * permissions, and delegates to [Calendar]. Provider work runs
 * on a single-thread executor; the interactive editor and the calendar app
 * run through activity intents.
 */
@CapacitorPlugin(
    name = "Calendar",
    permissions = [
        Permission(strings = [Manifest.permission.READ_CALENDAR], alias = CalendarPlugin.READ),
        Permission(strings = [Manifest.permission.WRITE_CALENDAR], alias = CalendarPlugin.WRITE)
    ]
)
class CalendarPlugin : Plugin() {

    companion object {
        const val READ = "readCalendar"
        const val WRITE = "writeCalendar"
    }

    private val implementation by lazy { Calendar(context) }
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    /** `true` while the interactive event editor is open. */
    @Volatile
    private var editInProgress = false

    override fun handleOnDestroy() {
        executor.shutdown()
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    @PluginMethod
    fun createEvent(call: PluginCall) {
        withPermissions(call, "createEventPermissionCallback", WRITE) {
            run(call) { call.resolve(JSObject().put("id", implementation.createEvent(call.data))) }
        }
    }

    @PermissionCallback
    private fun createEventPermissionCallback(call: PluginCall) {
        continueWithPermissions(call, WRITE) {
            run(call) { call.resolve(JSObject().put("id", implementation.createEvent(call.data))) }
        }
    }

    @PluginMethod
    fun createEventInteractively(call: PluginCall) {
        if (editInProgress) {
            reject(call, CalendarError.PENDING_OPERATION)
            return
        }
        val title = call.getString("title")
        val startMs = call.getLong("startDate")
        val endMs = call.getLong("endDate")
        if (title.isNullOrEmpty() || startMs == null || endMs == null || endMs < startMs) {
            reject(call, CalendarError.INVALID_ARGUMENT)
            return
        }

        val intent = Intent(Intent.ACTION_INSERT)
            .setData(Events.CONTENT_URI)
            .putExtra(Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, call.getBoolean("isAllDay") == true)
        call.getString("location")?.let { intent.putExtra(Events.EVENT_LOCATION, it) }
        call.getString("notes")?.let { intent.putExtra(Events.DESCRIPTION, it) }

        editInProgress = true
        try {
            startActivityForResult(call, intent, "editEventResult")
        } catch (e: ActivityNotFoundException) {
            editInProgress = false
            reject(call, CalendarError.NOT_SUPPORTED)
        }
    }

    @ActivityCallback
    private fun editEventResult(call: PluginCall?, result: ActivityResult) {
        editInProgress = false
        // The calendar app reports no result data and no reliable result
        // code, so a saved event's id (and a cancel) cannot be detected.
        call?.resolve(JSObject())
    }

    @PluginMethod
    fun modifyEvent(call: PluginCall) {
        val filter = call.getObject("filter")
        val newEvent = call.getObject("newEvent")
        if (filter == null || newEvent == null) {
            reject(call, CalendarError.INVALID_ARGUMENT)
            return
        }
        withPermissions(call, "modifyEventPermissionCallback", READ, WRITE) {
            run(call) {
                implementation.modifyEvent(filter, newEvent)
                call.resolve()
            }
        }
    }

    @PermissionCallback
    private fun modifyEventPermissionCallback(call: PluginCall) {
        continueWithPermissions(call, READ, WRITE) {
            run(call) {
                implementation.modifyEvent(call.getObject("filter")!!, call.getObject("newEvent")!!)
                call.resolve()
            }
        }
    }

    @PluginMethod
    fun findEvents(call: PluginCall) {
        withPermissions(call, "findEventsPermissionCallback", READ) {
            run(call) { call.resolve(JSObject().put("events", implementation.findEvents(call.data))) }
        }
    }

    @PermissionCallback
    private fun findEventsPermissionCallback(call: PluginCall) {
        continueWithPermissions(call, READ) {
            run(call) { call.resolve(JSObject().put("events", implementation.findEvents(call.data))) }
        }
    }

    @PluginMethod
    fun deleteEvent(call: PluginCall) {
        val hasId = !call.getString("id").isNullOrEmpty()
        val hasFilter = listOf("title", "location", "notes", "startDate", "endDate", "calendarName")
            .any { call.data.has(it) }
        if (!hasId && !hasFilter) {
            reject(call, CalendarError.INVALID_ARGUMENT)
            return
        }
        withPermissions(call, "deleteEventPermissionCallback", READ, WRITE) {
            run(call) {
                implementation.deleteEvent(call.data)
                call.resolve()
            }
        }
    }

    @PermissionCallback
    private fun deleteEventPermissionCallback(call: PluginCall) {
        continueWithPermissions(call, READ, WRITE) {
            run(call) {
                implementation.deleteEvent(call.data)
                call.resolve()
            }
        }
    }

    // ------------------------------------------------------------------
    // Calendars
    // ------------------------------------------------------------------

    @PluginMethod
    fun listCalendars(call: PluginCall) {
        withPermissions(call, "listCalendarsPermissionCallback", READ) {
            run(call) { call.resolve(JSObject().put("calendars", implementation.listCalendars())) }
        }
    }

    @PermissionCallback
    private fun listCalendarsPermissionCallback(call: PluginCall) {
        continueWithPermissions(call, READ) {
            run(call) { call.resolve(JSObject().put("calendars", implementation.listCalendars())) }
        }
    }

    @PluginMethod
    fun createCalendar(call: PluginCall) {
        val name = call.getString("name")
        if (name.isNullOrEmpty()) {
            reject(call, CalendarError.INVALID_ARGUMENT)
            return
        }
        withPermissions(call, "createCalendarPermissionCallback", WRITE) {
            run(call) {
                call.resolve(JSObject().put("id", implementation.createCalendar(name, call.getString("color"))))
            }
        }
    }

    @PermissionCallback
    private fun createCalendarPermissionCallback(call: PluginCall) {
        continueWithPermissions(call, WRITE) {
            run(call) {
                call.resolve(
                    JSObject().put("id", implementation.createCalendar(call.getString("name")!!, call.getString("color")))
                )
            }
        }
    }

    @PluginMethod
    fun deleteCalendar(call: PluginCall) {
        val name = call.getString("name")
        if (name.isNullOrEmpty()) {
            reject(call, CalendarError.INVALID_ARGUMENT)
            return
        }
        withPermissions(call, "deleteCalendarPermissionCallback", READ, WRITE) {
            run(call) {
                implementation.deleteCalendar(name)
                call.resolve()
            }
        }
    }

    @PermissionCallback
    private fun deleteCalendarPermissionCallback(call: PluginCall) {
        continueWithPermissions(call, READ, WRITE) {
            run(call) {
                implementation.deleteCalendar(call.getString("name")!!)
                call.resolve()
            }
        }
    }

    @PluginMethod
    fun openCalendar(call: PluginCall) {
        val ms = call.getLong("date") ?: System.currentTimeMillis()
        val uri = ContentUris.appendId(
            CalendarContract.CONTENT_URI.buildUpon().appendPath("time"),
            ms
        ).build()
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW).setData(uri))
            call.resolve()
        } catch (e: ActivityNotFoundException) {
            reject(call, CalendarError.NOT_SUPPORTED)
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun hasPermissions(vararg aliases: String): Boolean =
        aliases.all { getPermissionState(it) == com.getcapacitor.PermissionState.GRANTED }

    /**
     * Runs `body` when the aliases are granted, otherwise requests them and
     * resumes in the named [PermissionCallback].
     */
    private fun withPermissions(call: PluginCall, callbackName: String, vararg aliases: String, body: () -> Unit) {
        if (hasPermissions(*aliases)) {
            body()
        } else {
            requestPermissionForAliases(aliases, call, callbackName)
        }
    }

    private fun continueWithPermissions(call: PluginCall, vararg aliases: String, body: () -> Unit) {
        if (hasPermissions(*aliases)) {
            body()
        } else {
            reject(call, CalendarError.PERMISSION_DENIED)
        }
    }

    /** Runs `body` on the provider executor, mapping thrown errors to rejections. */
    private fun run(call: PluginCall, body: () -> Unit) {
        executor.execute {
            try {
                body()
            } catch (e: CalendarException) {
                reject(call, e.error)
            } catch (e: SecurityException) {
                reject(call, CalendarError.PERMISSION_DENIED)
            } catch (e: Exception) {
                reject(call, CalendarError.UNKNOWN)
            }
        }
    }

    private fun reject(call: PluginCall, error: CalendarError) {
        call.reject(error.message, error.code)
    }
}
