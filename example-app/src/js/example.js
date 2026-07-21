import { Calendar } from '@capacitor/calendar';

let lastId = null;

function show(label, data) {
  document.getElementById('out').textContent = `${label}\n${JSON.stringify(data, null, 2)}`;
}

async function run(label, fn) {
  document.getElementById('out').textContent = `${label} …`;
  try {
    const result = await fn();
    show(`✅ ${label}`, result ?? 'OK (void)');
    return result;
  } catch (e) {
    show(`❌ ${label}`, { code: e.code, message: e.message });
    return undefined;
  }
}

function sampleEvent() {
  const start = Date.now() + 60 * 60 * 1000;
  return {
    title: document.getElementById('titleInput').value || 'Example Event',
    location: 'Room 4',
    notes: 'Created by the example app',
    startDate: start,
    endDate: start + 60 * 60 * 1000,
    firstReminderMinutes: 15,
  };
}

window.testCheckPermissions = async () => {
  await run('checkPermissions', () => Calendar.checkPermissions());
};

window.testRequestPermissions = async () => {
  await run('requestPermissions', () => Calendar.requestPermissions());
};

window.testCreateEvent = async () => {
  const created = await run('createEvent', () => Calendar.createEvent(sampleEvent()));
  if (created?.id) lastId = created.id;
};

window.testCreateInteractively = async () => {
  const created = await run('createEventInteractively', () => Calendar.createEventInteractively(sampleEvent()));
  if (created?.id) lastId = created.id;
};

window.testFindEvents = async () => {
  const title = document.getElementById('titleInput').value || 'Example Event';
  await run('findEvents', () => Calendar.findEvents({ title }));
};

window.testModifyEvent = async () => {
  const title = document.getElementById('titleInput').value || 'Example Event';
  await run('modifyEvent', () =>
    Calendar.modifyEvent({ filter: { title }, newEvent: { title: `${title} (edited)` } }),
  );
};

window.testDeleteEvent = async () => {
  if (!lastId) {
    show('❌ deleteEvent', 'No event id captured yet; create an event first.');
    return;
  }
  await run('deleteEvent', () => Calendar.deleteEvent({ id: lastId }));
  lastId = null;
};

window.testListCalendars = async () => {
  await run('listCalendars', () => Calendar.listCalendars());
};

window.testCreateCalendar = async () => {
  await run('createCalendar', () => Calendar.createCalendar({ name: 'ExampleCal', color: '#FF8800' }));
};

window.testDeleteCalendar = async () => {
  await run('deleteCalendar', () => Calendar.deleteCalendar({ name: 'ExampleCal' }));
};

window.testOpenCalendar = async () => {
  await run('openCalendar', () => Calendar.openCalendar());
};
