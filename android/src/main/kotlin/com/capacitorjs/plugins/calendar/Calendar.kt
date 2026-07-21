package com.capacitorjs.plugins.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray

/**
 * Business logic for reading, creating, modifying and removing calendar
 * events and calendars via [CalendarContract]. Holds no Capacitor bridge
 * types beyond the JSON containers; the bridge plugin parses calls and
 * delegates here.
 */
class Calendar(private val context: Context) {

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    fun createEvent(options: JSONObject): String {
        val title = options.optString("title")
        val startMs = optLong(options, "startDate")
        val endMs = optLong(options, "endDate")
        if (title.isNullOrEmpty() || startMs == null || endMs == null || endMs < startMs) {
            throw CalendarException(CalendarError.INVALID_ARGUMENT)
        }

        val values = ContentValues()
        values.put(Events.CALENDAR_ID, resolveCalendarId(options))
        values.put(Events.TITLE, title)
        values.put(Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        values.put(Events.DTSTART, startMs)
        optString(options, "location")?.let { values.put(Events.EVENT_LOCATION, it) }
        optString(options, "notes")?.let { values.put(Events.DESCRIPTION, it) }
        values.put(Events.ALL_DAY, if (options.optBoolean("isAllDay", false)) 1 else 0)

        val rrule = recurrenceRule(options.optJSONObject("recurrence"))
        if (rrule != null) {
            // The provider requires recurring events to carry a DURATION
            // instead of DTEND.
            values.put(Events.RRULE, rrule)
            values.put(Events.DURATION, "P" + (endMs - startMs) / 1000 + "S")
        } else {
            values.put(Events.DTEND, endMs)
        }

        val uri = context.contentResolver.insert(Events.CONTENT_URI, values)
            ?: throw CalendarException(CalendarError.IO_ERROR)
        val eventId = ContentUris.parseId(uri)

        addReminders(eventId, options)
        return eventId.toString()
    }

    fun modifyEvent(filter: JSONObject, newEvent: JSONObject) {
        val eventId = findMatchingEventIds(filter).firstOrNull()
            ?: throw CalendarException(CalendarError.INVALID_ARGUMENT)

        val values = ContentValues()
        optString(newEvent, "title")?.let { values.put(Events.TITLE, it) }
        optString(newEvent, "location")?.let { values.put(Events.EVENT_LOCATION, it) }
        optString(newEvent, "notes")?.let { values.put(Events.DESCRIPTION, it) }
        optLong(newEvent, "startDate")?.let { values.put(Events.DTSTART, it) }
        optLong(newEvent, "endDate")?.let { values.put(Events.DTEND, it) }
        if (newEvent.has("isAllDay")) {
            values.put(Events.ALL_DAY, if (newEvent.optBoolean("isAllDay", false)) 1 else 0)
        }
        newEvent.optJSONObject("recurrence")?.let { recurrence ->
            values.put(Events.RRULE, recurrenceRule(recurrence))
            // The provider requires recurring events to carry a DURATION
            // instead of DTEND.
            val start = optLong(newEvent, "startDate")
            val end = optLong(newEvent, "endDate")
            if (start != null && end != null) {
                values.put(Events.DURATION, "P" + (end - start) / 1000 + "S")
                values.putNull(Events.DTEND)
            }
        }
        if (newEvent.has("calendarId") || newEvent.has("calendarName")) {
            values.put(Events.CALENDAR_ID, resolveCalendarId(newEvent))
        }

        if (values.size() > 0) {
            val updated = context.contentResolver.update(
                ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
                values,
                null,
                null
            )
            if (updated == 0) {
                throw CalendarException(CalendarError.IO_ERROR)
            }
        }

        if (newEvent.has("firstReminderMinutes") || newEvent.has("secondReminderMinutes")) {
            context.contentResolver.delete(
                Reminders.CONTENT_URI,
                "${Reminders.EVENT_ID} = ?",
                arrayOf(eventId.toString())
            )
            addReminders(eventId, newEvent)
        }
    }

    fun findEvents(filter: JSONObject): JSONArray {
        val results = JSONArray()
        val eventIds = mutableSetOf<Long>()
        queryEvents(filter) { cursor ->
            val event = JSONObject()
            val eventId = cursor.getLong(0)
            eventIds.add(eventId)
            event.put("id", eventId.toString())
            cursor.getString(1)?.let { event.put("title", it) }
            cursor.getString(2)?.let { event.put("location", it) }
            cursor.getString(3)?.let { event.put("notes", it) }
            val start = cursor.getLong(4)
            event.put("startDate", start)
            event.put("endDate", if (cursor.isNull(5)) start else cursor.getLong(5))
            event.put("isAllDay", cursor.getInt(6) == 1)
            event.put("calendarId", cursor.getLong(7).toString())
            cursor.getString(8)?.let { event.put("calendarName", it) }
            results.put(event)
        }
        val attendees = fetchAttendees(eventIds)
        for (i in 0 until results.length()) {
            val event = results.getJSONObject(i)
            attendees[event.getString("id").toLong()]?.let { event.put("attendees", it) }
        }
        return results
    }

    fun deleteEvent(options: JSONObject) {
        val id = optString(options, "id")
        if (id != null) {
            val eventId = id.toLongOrNull() ?: throw CalendarException(CalendarError.INVALID_ARGUMENT)
            val fromMs = optLong(options, "fromDate")
            if (fromMs != null && truncateSeries(eventId, fromMs)) {
                return
            }
            val deleted = context.contentResolver.delete(
                ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
                null,
                null
            )
            if (deleted == 0) {
                throw CalendarException(CalendarError.INVALID_ARGUMENT)
            }
            return
        }

        val matches = findMatchingEventIds(options)
        if (matches.isEmpty()) {
            throw CalendarException(CalendarError.INVALID_ARGUMENT)
        }
        for (eventId in matches) {
            context.contentResolver.delete(
                ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
                null,
                null
            )
        }
    }

    // ------------------------------------------------------------------
    // Calendars
    // ------------------------------------------------------------------

    fun listCalendars(): JSONArray {
        val results = JSONArray()
        val projection = arrayOf(
            Calendars._ID,
            Calendars.NAME,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.IS_PRIMARY
        )
        context.contentResolver.query(Calendars.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val entry = JSONObject()
                entry.put("id", cursor.getLong(0).toString())
                val displayName = cursor.getString(2)
                entry.put("name", cursor.getString(1) ?: displayName ?: "")
                displayName?.let { entry.put("displayName", it) }
                entry.put("isPrimary", cursor.getInt(3) == 1)
                results.put(entry)
            }
        }
        return results
    }

    fun createCalendar(name: String, color: String?): String {
        val values = ContentValues()
        values.put(Calendars.ACCOUNT_NAME, name)
        values.put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        values.put(Calendars.NAME, name)
        values.put(Calendars.CALENDAR_DISPLAY_NAME, name)
        values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
        values.put(Calendars.OWNER_ACCOUNT, name)
        values.put(Calendars.VISIBLE, 1)
        values.put(Calendars.SYNC_EVENTS, 1)
        parseColor(color)?.let { values.put(Calendars.CALENDAR_COLOR, it) }

        // Calendar rows can only be inserted through the sync-adapter URI.
        val uri = Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, name)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val inserted = context.contentResolver.insert(uri, values)
            ?: throw CalendarException(CalendarError.IO_ERROR)
        return ContentUris.parseId(inserted).toString()
    }

    fun deleteCalendar(name: String) {
        val calendarId = findCalendarIdByName(name)
            ?: throw CalendarException(CalendarError.INVALID_ARGUMENT)
        val deleted = context.contentResolver.delete(
            ContentUris.withAppendedId(Calendars.CONTENT_URI, calendarId),
            null,
            null
        )
        if (deleted == 0) {
            throw CalendarException(CalendarError.IO_ERROR)
        }
    }

    // ------------------------------------------------------------------
    // Matching
    // ------------------------------------------------------------------

    private fun findMatchingEventIds(filter: JSONObject): List<Long> {
        // Recurring events surface once per occurrence; act on each event once.
        val ids = mutableListOf<Long>()
        queryEvents(filter) { cursor -> ids.add(cursor.getLong(0)) }
        return ids.distinct()
    }

    /**
     * Queries event occurrences within the filter's date range whose title,
     * location and notes contain the respective filter values
     * (case-insensitive). A `calendarName` restricts the search to that
     * calendar. The Instances table expands recurring events, so each
     * occurrence is reported with its own start and end (as EventKit does
     * on iOS).
     */
    private fun queryEvents(filter: JSONObject, onRow: (android.database.Cursor) -> Unit) {
        val now = System.currentTimeMillis()
        val start = optLong(filter, "startDate") ?: (now - 182L * 24 * 60 * 60 * 1000)
        val end = optLong(filter, "endDate") ?: (now + 2L * 365 * 24 * 60 * 60 * 1000)

        val selection = StringBuilder("1 = 1")
        val args = mutableListOf<String>()

        for ((key, column) in listOf(
            "title" to Instances.TITLE,
            "location" to Instances.EVENT_LOCATION,
            "notes" to Instances.DESCRIPTION
        )) {
            val needle = optString(filter, key)
            if (!needle.isNullOrEmpty()) {
                selection.append(" AND $column LIKE ? ESCAPE '\\'")
                args.add("%${escapeLike(needle)}%")
            }
        }

        val calendarName = optString(filter, "calendarName")
        if (!calendarName.isNullOrEmpty()) {
            if (findCalendarIdByName(calendarName) == null) {
                throw CalendarException(CalendarError.INVALID_ARGUMENT)
            }
            selection.append(" AND ${Instances.CALENDAR_DISPLAY_NAME} = ?")
            args.add(calendarName)
        }

        val projection = arrayOf(
            Instances.EVENT_ID,
            Instances.TITLE,
            Instances.EVENT_LOCATION,
            Instances.DESCRIPTION,
            Instances.BEGIN,
            Instances.END,
            Instances.ALL_DAY,
            Instances.CALENDAR_ID,
            Instances.CALENDAR_DISPLAY_NAME
        )
        val uri = Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uri, start)
        ContentUris.appendId(uri, end)
        context.contentResolver.query(
            uri.build(),
            projection,
            selection.toString(),
            args.toTypedArray(),
            "${Instances.BEGIN} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                onRow(cursor)
            }
        }
    }

    /**
     * Ends a recurring series just before `fromMs`, keeping earlier
     * occurrences. Returns false when the whole event should be deleted
     * instead (it starts at or after `fromMs`, or does not recur); returns
     * true when the series was truncated or no occurrence remained to
     * remove.
     */
    private fun truncateSeries(eventId: Long, fromMs: Long): Boolean {
        var dtStart: Long? = null
        var rrule: String? = null
        context.contentResolver.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
            arrayOf(Events.DTSTART, Events.RRULE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToNext()) {
                dtStart = cursor.getLong(0)
                rrule = cursor.getString(1)
            }
        }
        val start = dtStart ?: return false
        val rule = rrule
        if (start >= fromMs || rule.isNullOrEmpty()) {
            return false
        }

        // Look for a remaining occurrence in the year after fromMs. Wider
        // Instances scans can corrupt the calendar storage state
        // (https://issuetracker.google.com/issues/36980229).
        var hasOccurrence = false
        val uri = Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uri, fromMs)
        ContentUris.appendId(uri, fromMs + 367L * 24 * 60 * 60 * 1000)
        context.contentResolver.query(
            uri.build(),
            arrayOf(Instances.EVENT_ID),
            "${Instances.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null
        )?.use { cursor -> hasOccurrence = cursor.moveToNext() }
        if (!hasOccurrence) {
            return true
        }

        val kept = rule.split(";").filterNot { it.startsWith("COUNT=") || it.startsWith("UNTIL=") }
        val format = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        val until = format.format(java.util.Date(fromMs - 1000))
        val values = ContentValues()
        values.put(Events.RRULE, (kept + "UNTIL=$until").joinToString(";"))
        val updated = context.contentResolver.update(
            ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
            values,
            null,
            null
        )
        if (updated == 0) {
            throw CalendarException(CalendarError.IO_ERROR)
        }
        return true
    }

    /** The attendees of each given event, keyed by event id. */
    private fun fetchAttendees(eventIds: Set<Long>): Map<Long, JSONArray> {
        if (eventIds.isEmpty()) {
            return emptyMap()
        }
        val results = mutableMapOf<Long, JSONArray>()
        val placeholders = eventIds.joinToString(",") { "?" }
        context.contentResolver.query(
            Attendees.CONTENT_URI,
            arrayOf(
                Attendees.EVENT_ID,
                Attendees.ATTENDEE_NAME,
                Attendees.ATTENDEE_EMAIL,
                Attendees.ATTENDEE_STATUS
            ),
            "${Attendees.EVENT_ID} IN ($placeholders)",
            eventIds.map { it.toString() }.toTypedArray(),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val attendee = JSONObject()
                cursor.getString(1)?.takeIf { it.isNotEmpty() }?.let { attendee.put("name", it) }
                cursor.getString(2)?.takeIf { it.isNotEmpty() }?.let { attendee.put("email", it) }
                attendee.put(
                    "status",
                    when (cursor.getInt(3)) {
                        Attendees.ATTENDEE_STATUS_ACCEPTED -> "accepted"
                        Attendees.ATTENDEE_STATUS_DECLINED -> "declined"
                        Attendees.ATTENDEE_STATUS_INVITED -> "pending"
                        Attendees.ATTENDEE_STATUS_TENTATIVE -> "tentative"
                        else -> "unknown"
                    }
                )
                results.getOrPut(cursor.getLong(0)) { JSONArray() }.put(attendee)
            }
        }
        return results
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun resolveCalendarId(options: JSONObject): Long {
        optString(options, "calendarId")?.let { id ->
            return id.toLongOrNull() ?: throw CalendarException(CalendarError.INVALID_ARGUMENT)
        }
        optString(options, "calendarName")?.let { name ->
            return findCalendarIdByName(name)
                ?: throw CalendarException(CalendarError.INVALID_ARGUMENT)
        }
        return defaultCalendarId() ?: throw CalendarException(CalendarError.IO_ERROR)
    }

    private fun defaultCalendarId(): Long? {
        var fallback: Long? = null
        context.contentResolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID, Calendars.IS_PRIMARY),
            "${Calendars.VISIBLE} = 1",
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getInt(1) == 1) {
                    return cursor.getLong(0)
                }
                if (fallback == null) {
                    fallback = cursor.getLong(0)
                }
            }
        }
        return fallback
    }

    private fun findCalendarIdByName(name: String): Long? {
        context.contentResolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID),
            "${Calendars.CALENDAR_DISPLAY_NAME} = ?",
            arrayOf(name),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return null
    }

    private fun addReminders(eventId: Long, options: JSONObject) {
        for (key in listOf("firstReminderMinutes", "secondReminderMinutes")) {
            val minutes = optLong(options, key) ?: continue
            val values = ContentValues()
            values.put(Reminders.EVENT_ID, eventId)
            values.put(Reminders.MINUTES, minutes)
            values.put(Reminders.METHOD, Reminders.METHOD_ALERT)
            context.contentResolver.insert(Reminders.CONTENT_URI, values)
        }
    }

    private fun recurrenceRule(recurrence: JSONObject?): String? {
        recurrence ?: return null
        val frequency = when (recurrence.optString("frequency")) {
            "daily" -> "DAILY"
            "weekly" -> "WEEKLY"
            "monthly" -> "MONTHLY"
            "yearly" -> "YEARLY"
            else -> throw CalendarException(CalendarError.INVALID_ARGUMENT)
        }
        val rule = StringBuilder("FREQ=$frequency")
        optLong(recurrence, "interval")?.takeIf { it > 1 }?.let { rule.append(";INTERVAL=$it") }
        val endMs = optLong(recurrence, "endDate")
        if (endMs != null) {
            val formatter = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
            formatter.timeZone = TimeZone.getTimeZone("UTC")
            rule.append(";UNTIL=${formatter.format(java.util.Date(endMs))}")
        } else {
            optLong(recurrence, "count")?.takeIf { it > 0 }?.let { rule.append(";COUNT=$it") }
        }
        return rule.toString()
    }

    /** The string at `key`, or null when absent or empty. */
    private fun optString(source: JSONObject, key: String): String? =
        source.optString(key, "").takeIf { it.isNotEmpty() }

    private fun optLong(source: JSONObject, key: String): Long? {
        if (!source.has(key) || source.isNull(key)) return null
        return try {
            source.getLong(key)
        } catch (e: Exception) {
            null
        }
    }

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun parseColor(color: String?): Int? {
        val value = color?.trim()?.removePrefix("#") ?: return null
        if (value.length != 6) return null
        return value.toIntOrNull(16)?.or(0xFF000000.toInt())
    }
}
