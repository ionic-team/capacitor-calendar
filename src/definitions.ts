import type { PermissionState } from '@capacitor/core';

export interface CalendarPlugin {
  /**
   * Returns the current calendar permission state without prompting.
   *
   * On iOS 17+, `readCalendar` reflects full access; `writeCalendar` is also
   * granted by write-only access ("Add Events Only").
   *
   * @since 1.0.0
   */
  checkPermissions(): Promise<CalendarPermissionStatus>;

  /**
   * Prompts for the given calendar permissions (both when omitted).
   *
   * On iOS, requesting `readCalendar` prompts for full access; requesting only
   * `writeCalendar` prompts for write-only access on iOS 17+.
   *
   * @since 1.0.0
   */
  requestPermissions(options?: RequestPermissionsOptions): Promise<CalendarPermissionStatus>;

  /**
   * Creates a calendar event silently and resolves with its id.
   *
   * Requires write permission; requests it when not yet determined.
   *
   * @since 1.0.0
   */
  createEvent(options: CreateEventOptions): Promise<CreateEventResult>;

  /**
   * Opens the system event-editing UI prefilled with the given values.
   * Resolves when the user saves (with the new event's id where the platform
   * provides one; Android does not) and fails with `OS-PLUG-CLDR-0006` when
   * the user cancels.
   *
   * On iOS 17+ the editor needs no calendar permission. On Android and older
   * iOS versions, write permission is requested first.
   *
   * @since 1.0.0
   */
  createEventInteractively(options: CreateEventOptions): Promise<CreateEventResult>;

  /**
   * Updates the first event matching `filter` with the values in `newEvent`.
   * Only the fields present in `newEvent` are changed. Fails with
   * `OS-PLUG-CLDR-0001` when no event matches.
   *
   * Requires read and write permission.
   *
   * @since 1.0.0
   */
  modifyEvent(options: ModifyEventOptions): Promise<void>;

  /**
   * Returns events matching the filter fields within the date range.
   * `title`, `location` and `notes` match case-insensitive substrings;
   * `calendarName` restricts the search to that calendar. A recurring
   * event is returned once per occurrence in the range, each with its
   * own dates.
   *
   * Requires read permission.
   *
   * @since 1.0.0
   */
  findEvents(options: FindEventsOptions): Promise<FindEventsResult>;

  /**
   * Deletes events: by `id` when given, otherwise every event matching the
   * filter fields. Fails with `OS-PLUG-CLDR-0001` when nothing matches.
   * Deleting a recurring event removes the entire series.
   *
   * Requires read and write permission.
   *
   * @since 1.0.0
   */
  deleteEvent(options: DeleteEventOptions): Promise<void>;

  /**
   * Returns the calendars available on the device.
   *
   * Requires read permission.
   *
   * @since 1.0.0
   */
  listCalendars(): Promise<ListCalendarsResult>;

  /**
   * Creates a calendar and resolves with its id.
   *
   * Requires write permission.
   *
   * @since 1.0.0
   */
  createCalendar(options: CreateCalendarOptions): Promise<CreateCalendarResult>;

  /**
   * Deletes the calendar with the given name. Fails with
   * `OS-PLUG-CLDR-0001` when no calendar has that name.
   *
   * Requires read and write permission.
   *
   * @since 1.0.0
   */
  deleteCalendar(options: DeleteCalendarOptions): Promise<void>;

  /**
   * Opens the system calendar app at the given date (today when omitted).
   * Needs no calendar permission.
   *
   * @since 1.0.0
   */
  openCalendar(options?: OpenCalendarOptions): Promise<void>;
}

/**
 * The individually requestable calendar permissions.
 *
 * @since 1.0.0
 */
export type CalendarPermissionType = 'readCalendar' | 'writeCalendar';

/**
 * Permission state per calendar permission.
 *
 * @since 1.0.0
 */
export interface CalendarPermissionStatus {
  /**
   * Permission to read calendar events.
   *
   * @since 1.0.0
   */
  readCalendar: PermissionState;

  /**
   * Permission to add events to the calendar.
   *
   * @since 1.0.0
   */
  writeCalendar: PermissionState;
}

/**
 * Options accepted by {@link CalendarPlugin.requestPermissions}.
 *
 * @since 1.0.0
 */
export interface RequestPermissionsOptions {
  /**
   * The permissions to request. Both are requested when omitted.
   *
   * @since 1.0.0
   */
  permissions?: CalendarPermissionType[];
}

/**
 * How often a recurring event repeats.
 *
 * @since 1.0.0
 */
export type RecurrenceFrequency = 'daily' | 'weekly' | 'monthly' | 'yearly';

/**
 * Recurrence rule applied to a created event.
 *
 * @since 1.0.0
 */
export interface EventRecurrence {
  /**
   * How often the event repeats.
   *
   * @since 1.0.0
   */
  frequency: RecurrenceFrequency;

  /**
   * Repeat every `interval` periods of `frequency` (default 1).
   *
   * @since 1.0.0
   */
  interval?: number;

  /**
   * Last possible date of a repetition, as epoch milliseconds. Mutually
   * exclusive with `count`; `endDate` wins when both are set.
   *
   * @since 1.0.0
   */
  endDate?: number;

  /**
   * Total number of repetitions.
   *
   * @since 1.0.0
   */
  count?: number;
}

/**
 * Options accepted by {@link CalendarPlugin.createEvent} and
 * {@link CalendarPlugin.createEventInteractively}, and the new values of
 * {@link CalendarPlugin.modifyEvent}.
 *
 * @since 1.0.0
 */
export interface CreateEventOptions {
  /**
   * The event title.
   *
   * @since 1.0.0
   */
  title: string;

  /**
   * The event location.
   *
   * @since 1.0.0
   */
  location?: string;

  /**
   * Free-form event notes.
   *
   * @since 1.0.0
   */
  notes?: string;

  /**
   * Event start as epoch milliseconds.
   *
   * @since 1.0.0
   */
  startDate: number;

  /**
   * Event end as epoch milliseconds.
   *
   * @since 1.0.0
   */
  endDate: number;

  /**
   * Whether the event lasts all day.
   *
   * @since 1.0.0
   */
  isAllDay?: boolean;

  /**
   * Id of the calendar to create the event in. Takes precedence over
   * `calendarName`; the default calendar is used when neither is set.
   *
   * @since 1.0.0
   */
  calendarId?: string;

  /**
   * Name of the calendar to create the event in.
   *
   * @since 1.0.0
   */
  calendarName?: string;

  /**
   * URL attached to the event.
   *
   * **Android:** the platform's event model has no URL field; the value is
   * ignored.
   *
   * @since 1.0.0
   */
  url?: string;

  /**
   * Minutes before the event for the first reminder.
   *
   * @since 1.0.0
   */
  firstReminderMinutes?: number;

  /**
   * Minutes before the event for the second reminder.
   *
   * @since 1.0.0
   */
  secondReminderMinutes?: number;

  /**
   * Recurrence rule for a repeating event.
   *
   * @since 1.0.0
   */
  recurrence?: EventRecurrence;
}

/**
 * Result of {@link CalendarPlugin.createEvent} and
 * {@link CalendarPlugin.createEventInteractively}.
 *
 * @since 1.0.0
 */
export interface CreateEventResult {
  /**
   * The created event's id. Absent when the platform does not report it
   * (Android's interactive editor).
   *
   * @since 1.0.0
   */
  id?: string;
}

/**
 * Fields used to locate the event to change in
 * {@link CalendarPlugin.modifyEvent}. Set fields must all match.
 *
 * @since 1.0.0
 */
export interface EventFilter {
  /**
   * Title substring to match (case-insensitive).
   *
   * @since 1.0.0
   */
  title?: string;

  /**
   * Location substring to match (case-insensitive).
   *
   * @since 1.0.0
   */
  location?: string;

  /**
   * Notes substring to match (case-insensitive).
   *
   * @since 1.0.0
   */
  notes?: string;

  /**
   * Start of the date range as epoch milliseconds.
   *
   * @since 1.0.0
   */
  startDate?: number;

  /**
   * End of the date range as epoch milliseconds.
   *
   * @since 1.0.0
   */
  endDate?: number;

  /**
   * Restrict the match to the calendar with this name.
   *
   * @since 1.0.0
   */
  calendarName?: string;
}

/**
 * Options accepted by {@link CalendarPlugin.modifyEvent}.
 *
 * @since 1.0.0
 */
export interface ModifyEventOptions {
  /**
   * Fields identifying the event to change.
   *
   * @since 1.0.0
   */
  filter: EventFilter;

  /**
   * New values to apply. Only the fields present are changed.
   *
   * @since 1.0.0
   */
  newEvent: Partial<CreateEventOptions>;
}

/**
 * Options accepted by {@link CalendarPlugin.findEvents}.
 *
 * @since 1.0.0
 */
export interface FindEventsOptions {
  /**
   * Title substring to match (case-insensitive).
   *
   * @since 1.0.0
   */
  title?: string;

  /**
   * Location substring to match (case-insensitive).
   *
   * @since 1.0.0
   */
  location?: string;

  /**
   * Notes substring to match (case-insensitive).
   *
   * @since 1.0.0
   */
  notes?: string;

  /**
   * Start of the search range as epoch milliseconds. Defaults to the current
   * time minus six months.
   *
   * @since 1.0.0
   */
  startDate?: number;

  /**
   * End of the search range as epoch milliseconds. Defaults to the current
   * time plus two years.
   *
   * @since 1.0.0
   */
  endDate?: number;

  /**
   * Restrict the search to the calendar with this name.
   *
   * @since 1.0.0
   */
  calendarName?: string;
}

/**
 * A calendar event.
 *
 * @since 1.0.0
 */
export interface CalendarEvent {
  /**
   * Platform-assigned event id.
   *
   * @since 1.0.0
   */
  id: string;

  /**
   * The event title.
   *
   * @since 1.0.0
   */
  title?: string;

  /**
   * The event location.
   *
   * @since 1.0.0
   */
  location?: string;

  /**
   * Free-form event notes.
   *
   * @since 1.0.0
   */
  notes?: string;

  /**
   * Event start as epoch milliseconds.
   *
   * @since 1.0.0
   */
  startDate: number;

  /**
   * Event end as epoch milliseconds.
   *
   * @since 1.0.0
   */
  endDate: number;

  /**
   * Whether the event lasts all day.
   *
   * @since 1.0.0
   */
  isAllDay?: boolean;

  /**
   * Id of the calendar containing the event.
   *
   * @since 1.0.0
   */
  calendarId?: string;

  /**
   * Name of the calendar containing the event.
   *
   * @since 1.0.0
   */
  calendarName?: string;

  /**
   * The event's attendees. Absent when the event has none.
   *
   * @since 1.0.0
   */
  attendees?: EventAttendee[];
}

/**
 * An attendee of a {@link CalendarEvent}.
 *
 * @since 1.0.0
 */
export interface EventAttendee {
  /**
   * The attendee's display name.
   *
   * @since 1.0.0
   */
  name?: string;

  /**
   * The attendee's email address.
   *
   * @since 1.0.0
   */
  email?: string;

  /**
   * The attendee's participation status.
   *
   * @since 1.0.0
   */
  status: 'unknown' | 'pending' | 'accepted' | 'declined' | 'tentative' | 'delegated' | 'completed' | 'in-process';
}

/**
 * Result of {@link CalendarPlugin.findEvents}.
 *
 * @since 1.0.0
 */
export interface FindEventsResult {
  /**
   * The events matching the search.
   *
   * @since 1.0.0
   */
  events: CalendarEvent[];
}

/**
 * Options accepted by {@link CalendarPlugin.deleteEvent}.
 *
 * @since 1.0.0
 */
export interface DeleteEventOptions {
  /**
   * Id of the event to delete. When set, the filter fields are ignored.
   *
   * @since 1.0.0
   */
  id?: string;

  /**
   * Only with `id`, for a recurring event: keeps occurrences before this
   * date (epoch milliseconds) and removes the rest of the series.
   *
   * @since 1.0.0
   */
  fromDate?: number;

  /**
   * Title substring to match (case-insensitive).
   *
   * @since 1.0.0
   */
  title?: string;

  /**
   * Location substring to match (case-insensitive).
   *
   * @since 1.0.0
   */
  location?: string;

  /**
   * Notes substring to match (case-insensitive).
   *
   * @since 1.0.0
   */
  notes?: string;

  /**
   * Start of the date range as epoch milliseconds.
   *
   * @since 1.0.0
   */
  startDate?: number;

  /**
   * End of the date range as epoch milliseconds.
   *
   * @since 1.0.0
   */
  endDate?: number;

  /**
   * Restrict the match to the calendar with this name.
   *
   * @since 1.0.0
   */
  calendarName?: string;
}

/**
 * A calendar available on the device.
 *
 * @since 1.0.0
 */
export interface DeviceCalendar {
  /**
   * Platform-assigned calendar id.
   *
   * @since 1.0.0
   */
  id: string;

  /**
   * The calendar name.
   *
   * @since 1.0.0
   */
  name: string;

  /**
   * Name shown to the user, when the platform distinguishes it from `name`.
   *
   * @since 1.0.0
   */
  displayName?: string;

  /**
   * Whether this is the default calendar for new events.
   *
   * @since 1.0.0
   */
  isPrimary?: boolean;
}

/**
 * Result of {@link CalendarPlugin.listCalendars}.
 *
 * @since 1.0.0
 */
export interface ListCalendarsResult {
  /**
   * The calendars on the device.
   *
   * @since 1.0.0
   */
  calendars: DeviceCalendar[];
}

/**
 * Options accepted by {@link CalendarPlugin.createCalendar}.
 *
 * @since 1.0.0
 */
export interface CreateCalendarOptions {
  /**
   * The calendar name.
   *
   * @since 1.0.0
   */
  name: string;

  /**
   * Calendar color as a `#RRGGBB` hex string. The platform picks one when
   * omitted.
   *
   * @since 1.0.0
   */
  color?: string;
}

/**
 * Result of {@link CalendarPlugin.createCalendar}.
 *
 * @since 1.0.0
 */
export interface CreateCalendarResult {
  /**
   * The created calendar's id.
   *
   * @since 1.0.0
   */
  id?: string;
}

/**
 * Options accepted by {@link CalendarPlugin.deleteCalendar}.
 *
 * @since 1.0.0
 */
export interface DeleteCalendarOptions {
  /**
   * Name of the calendar to delete.
   *
   * @since 1.0.0
   */
  name: string;
}

/**
 * Options accepted by {@link CalendarPlugin.openCalendar}.
 *
 * @since 1.0.0
 */
export interface OpenCalendarOptions {
  /**
   * Date to show, as epoch milliseconds. Today when omitted.
   *
   * @since 1.0.0
   */
  date?: number;
}
