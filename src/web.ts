import { WebPlugin } from '@capacitor/core';

import type {
  CalendarPermissionStatus,
  CalendarPlugin,
  CreateCalendarOptions,
  CreateCalendarResult,
  CreateEventOptions,
  CreateEventResult,
  DeleteCalendarOptions,
  DeleteEventOptions,
  FindEventsOptions,
  FindEventsResult,
  ListCalendarsResult,
  ModifyEventOptions,
  OpenCalendarOptions,
  RequestPermissionsOptions,
} from './definitions';

export class CalendarWeb extends WebPlugin implements CalendarPlugin {
  async checkPermissions(): Promise<CalendarPermissionStatus> {
    throw this.unimplemented('Not implemented on web.');
  }

  async requestPermissions(_options?: RequestPermissionsOptions): Promise<CalendarPermissionStatus> {
    throw this.unimplemented('Not implemented on web.');
  }

  async createEvent(_options: CreateEventOptions): Promise<CreateEventResult> {
    throw this.unimplemented('Not implemented on web.');
  }

  async createEventInteractively(_options: CreateEventOptions): Promise<CreateEventResult> {
    throw this.unimplemented('Not implemented on web.');
  }

  async modifyEvent(_options: ModifyEventOptions): Promise<void> {
    throw this.unimplemented('Not implemented on web.');
  }

  async findEvents(_options: FindEventsOptions): Promise<FindEventsResult> {
    throw this.unimplemented('Not implemented on web.');
  }

  async deleteEvent(_options: DeleteEventOptions): Promise<void> {
    throw this.unimplemented('Not implemented on web.');
  }

  async listCalendars(): Promise<ListCalendarsResult> {
    throw this.unimplemented('Not implemented on web.');
  }

  async createCalendar(_options: CreateCalendarOptions): Promise<CreateCalendarResult> {
    throw this.unimplemented('Not implemented on web.');
  }

  async deleteCalendar(_options: DeleteCalendarOptions): Promise<void> {
    throw this.unimplemented('Not implemented on web.');
  }

  async openCalendar(_options?: OpenCalendarOptions): Promise<void> {
    throw this.unimplemented('Not implemented on web.');
  }
}
