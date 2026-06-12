// gcal.jsh — Google Calendar CLI for SLICC agents
// Uses GWS_* env vars (OAuth client credentials + refresh token) to obtain
// access tokens via the Google OAuth2 token endpoint. No browser needed.
// Requires GWS_REFRESH_TOKEN to include the calendar scope.
//
// Usage: gcal <command> [args] [--flags]
//
// Commands:
//   list        List upcoming events
//   view        View a single event (full details)
//   create      Create a new event
//   update      Update an existing event
//   delete      Delete an event
//   freebusy    Query free/busy availability
//   calendars   List all accessible calendars

const CAL_BASE = 'https://www.googleapis.com/calendar/v3';

// ─── Argument Parsing ────────────────────────────────────────────────────────

const args = process.argv.slice(2);
const subcommand = args[0] || '';
const positional = [];
const flags = {};

for (let i = 1; i < args.length; i++) {
  const arg = args[i];
  if (arg.startsWith('--')) {
    const eq = arg.indexOf('=');
    if (eq !== -1) {
      flags[arg.slice(2, eq)] = arg.slice(eq + 1);
    } else {
      const key = arg.slice(2);
      if (i + 1 < args.length && !args[i + 1].startsWith('--')) {
        flags[key] = args[++i];
      } else {
        flags[key] = true;
      }
    }
  } else {
    positional.push(arg);
  }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

function die(msg) {
  process.stderr.write(msg + '\n');
  process.exit(1);
}

function trunc(s, n) {
  s = String(s == null ? '' : s);
  return s.length > n ? s.slice(0, n - 1) + '…' : s;
}

function formatDateTime(dtObj) {
  if (!dtObj) return '';
  const raw = dtObj.dateTime || dtObj.date || '';
  if (!raw) return '';
  if (dtObj.date) return dtObj.date; // all-day
  const d = new Date(raw);
  if (isNaN(d.getTime())) return raw;
  return d.toISOString().replace('T', ' ').replace(/\.\d+Z$/, ' UTC');
}

function durationToDate(dur, base) {
  if (!dur) return null;
  const match = dur.match(/^(\d+)(h|d|w|m)$/);
  if (!match) return null;
  const n = parseInt(match[1], 10);
  const unit = match[2];
  const ms = { h: 3600000, d: 86400000, w: 604800000, m: 2592000000 };
  return new Date((base || Date.now()) + ms[unit] * n);
}

function toRFC3339(str, tz) {
  // If already has timezone info, pass through
  if (/Z$|[+-]\d{2}:\d{2}$/.test(str)) return str;
  // If no seconds, add them
  const normalized = /T\d{2}:\d{2}$/.test(str) ? str + ':00' : str;
  if (!tz || tz === 'UTC') return normalized + 'Z';
  // Append the timezone offset notation — Calendar API accepts IANA tz separately
  return normalized;
}

function isoNow() {
  return new Date().toISOString();
}

// ─── ANSI Colors ─────────────────────────────────────────────────────────────

const C = {
  green:  s => `\x1b[32m${s}\x1b[0m`,
  red:    s => `\x1b[31m${s}\x1b[0m`,
  yellow: s => `\x1b[33m${s}\x1b[0m`,
  gray:   s => `\x1b[90m${s}\x1b[0m`,
  bold:   s => `\x1b[1m${s}\x1b[0m`,
  cyan:   s => `\x1b[36m${s}\x1b[0m`,
  blue:   s => `\x1b[34m${s}\x1b[0m`,
};

// ─── Auth ────────────────────────────────────────────────────────────────────

let _accessToken = null;

async function getAccessToken() {
  if (_accessToken) return _accessToken;

  const clientId = process.env.GWS_CLIENT_ID;
  const clientSecret = process.env.GWS_CLIENT_SECRET;
  const refreshToken = process.env.GWS_REFRESH_TOKEN;

  if (!clientId || !clientSecret || !refreshToken) {
    die('gcal: missing GWS_CLIENT_ID, GWS_CLIENT_SECRET, or GWS_REFRESH_TOKEN env vars.');
  }

  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: clientId,
      client_secret: clientSecret,
      refresh_token: refreshToken,
      grant_type: 'refresh_token',
    }).toString(),
  });

  const data = await res.json();
  if (!data.access_token) {
    die(`gcal: token refresh failed: ${JSON.stringify(data)}`);
  }

  _accessToken = data.access_token;
  return _accessToken;
}

async function calFetch(path, opts = {}) {
  const token = await getAccessToken();
  const url = path.startsWith('http') ? path : `${CAL_BASE}${path}`;
  const res = await fetch(url, {
    ...opts,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      ...(opts.headers || {}),
    },
  });

  if (res.status === 204) return null; // DELETE success
  const data = await res.json();

  if (!res.ok) {
    const msg = data?.error?.message || JSON.stringify(data);
    die(`gcal: API error ${res.status}: ${msg}`);
  }

  return data;
}

// ─── Commands ────────────────────────────────────────────────────────────────

async function cmdList() {
  const calendarId = encodeURIComponent(flags.calendar || 'primary');
  const limit = parseInt(flags.limit || '20', 10);
  const tz = flags.tz || 'UTC';
  const now = new Date().toISOString();

  let timeMax;
  if (flags.date) {
    const end = durationToDate(flags.date);
    if (!end) die(`gcal: invalid --date value "${flags.date}". Use e.g. 7d, 2w, 1m.`);
    timeMax = end.toISOString();
  } else {
    // Default: next 7 days
    timeMax = new Date(Date.now() + 7 * 86400000).toISOString();
  }

  const params = new URLSearchParams({
    timeMin: now,
    timeMax,
    maxResults: String(limit),
    singleEvents: 'true',
    orderBy: 'startTime',
    timeZone: tz,
  });

  const data = await calFetch(`/calendars/${calendarId}/events?${params}`);
  const events = data.items || [];

  if (flags.json) {
    process.stdout.write(JSON.stringify(events, null, 2) + '\n');
    return;
  }

  if (events.length === 0) {
    console.log('No events found in this range.');
    return;
  }

  console.log(C.bold(`Calendar — ${events.length} event(s)\n`));

  for (const ev of events) {
    const start = formatDateTime(ev.start);
    const end = formatDateTime(ev.end);
    const title = ev.summary || C.gray('(no title)');
    const loc = ev.location ? C.gray(`  📍 ${trunc(ev.location, 60)}`) : '';
    const attendeeCount = ev.attendees ? ev.attendees.length : 0;
    const attendeeSuffix = attendeeCount > 0 ? C.gray(` · ${attendeeCount} attendee(s)`) : '';

    console.log(`  ${C.cyan('●')} ${C.bold(title)}${attendeeSuffix}`);
    console.log(`    ${C.gray(start)} → ${C.gray(end)}`);
    if (loc) console.log(`   ${loc}`);
    console.log(`    ${C.gray('ID: ' + ev.id)}`);
    console.log('');
  }
}

async function cmdView() {
  const eventId = positional[0];
  if (!eventId) die('gcal view: missing <event-id>');

  const calendarId = encodeURIComponent(flags.calendar || 'primary');
  const ev = await calFetch(`/calendars/${calendarId}/events/${encodeURIComponent(eventId)}`);

  console.log(C.bold('Event Details'));
  console.log('─'.repeat(50));
  console.log(`${C.cyan('Title:')}       ${ev.summary || C.gray('(no title)')}`);
  console.log(`${C.cyan('ID:')}          ${ev.id}`);
  console.log(`${C.cyan('Status:')}      ${ev.status || ''}`);
  console.log(`${C.cyan('Start:')}       ${formatDateTime(ev.start)}`);
  console.log(`${C.cyan('End:')}         ${formatDateTime(ev.end)}`);
  if (ev.location) console.log(`${C.cyan('Location:')}    ${ev.location}`);
  if (ev.description) console.log(`${C.cyan('Description:')}\n${ev.description}`);
  if (ev.organizer) console.log(`${C.cyan('Organizer:')}   ${ev.organizer.email || ''}`);
  if (ev.attendees && ev.attendees.length > 0) {
    console.log(`${C.cyan('Attendees:')}`);
    for (const a of ev.attendees) {
      const resp = a.responseStatus ? ` (${a.responseStatus})` : '';
      console.log(`  - ${a.email}${resp}`);
    }
  }
  if (ev.htmlLink) console.log(`${C.cyan('Link:')}        ${ev.htmlLink}`);
  if (ev.created) console.log(`${C.cyan('Created:')}     ${formatDateTime({ dateTime: ev.created })}`);
  if (ev.updated) console.log(`${C.cyan('Updated:')}     ${formatDateTime({ dateTime: ev.updated })}`);
}

async function cmdCreate() {
  const title = flags.title;
  const start = flags.start;
  const end = flags.end;
  if (!title) die('gcal create: --title is required');
  if (!start) die('gcal create: --start is required (ISO 8601, e.g. 2026-06-11T10:00)');
  if (!end) die('gcal create: --end is required (ISO 8601, e.g. 2026-06-11T10:30)');

  const tz = flags.tz || 'UTC';
  const calendarId = encodeURIComponent(flags.calendar || 'primary');

  const body = {
    summary: title,
    start: { dateTime: toRFC3339(start, tz), timeZone: tz },
    end: { dateTime: toRFC3339(end, tz), timeZone: tz },
  };

  if (flags.location) body.location = flags.location;
  if (flags.desc) body.description = flags.desc;
  if (flags.attendees) {
    body.attendees = flags.attendees.split(',').map(e => ({ email: e.trim() }));
  }

  const ev = await calFetch(`/calendars/${calendarId}/events`, {
    method: 'POST',
    body: JSON.stringify(body),
  });

  console.log(C.green('✓ Event created'));
  console.log(`  ${C.cyan('Title:')} ${ev.summary}`);
  console.log(`  ${C.cyan('Start:')} ${formatDateTime(ev.start)}`);
  console.log(`  ${C.cyan('End:')}   ${formatDateTime(ev.end)}`);
  console.log(`  ${C.cyan('ID:')}    ${ev.id}`);
  if (ev.htmlLink) console.log(`  ${C.cyan('Link:')}  ${ev.htmlLink}`);
}

async function cmdUpdate() {
  const eventId = positional[0];
  if (!eventId) die('gcal update: missing <event-id>');

  const calendarId = encodeURIComponent(flags.calendar || 'primary');
  const tz = flags.tz || 'UTC';

  // Fetch existing event first
  const existing = await calFetch(`/calendars/${calendarId}/events/${encodeURIComponent(eventId)}`);
  const patch = {};

  if (flags.title) patch.summary = flags.title;
  if (flags.start) patch.start = { dateTime: toRFC3339(flags.start, tz), timeZone: tz };
  if (flags.end) patch.end = { dateTime: toRFC3339(flags.end, tz), timeZone: tz };
  if (flags.location) patch.location = flags.location;
  if (flags.desc) patch.description = flags.desc;
  if (flags.attendees) {
    patch.attendees = flags.attendees.split(',').map(e => ({ email: e.trim() }));
  }

  if (Object.keys(patch).length === 0) {
    die('gcal update: no fields to update. Provide at least one of --title, --start, --end, --location, --desc, --attendees');
  }

  const ev = await calFetch(`/calendars/${calendarId}/events/${encodeURIComponent(eventId)}`, {
    method: 'PATCH',
    body: JSON.stringify(patch),
  });

  console.log(C.green('✓ Event updated'));
  console.log(`  ${C.cyan('Title:')} ${ev.summary}`);
  console.log(`  ${C.cyan('Start:')} ${formatDateTime(ev.start)}`);
  console.log(`  ${C.cyan('End:')}   ${formatDateTime(ev.end)}`);
  console.log(`  ${C.cyan('ID:')}    ${ev.id}`);
}

async function cmdDelete() {
  const eventId = positional[0];
  if (!eventId) die('gcal delete: missing <event-id>');

  const calendarId = encodeURIComponent(flags.calendar || 'primary');
  await calFetch(`/calendars/${calendarId}/events/${encodeURIComponent(eventId)}`, {
    method: 'DELETE',
  });

  console.log(C.green(`✓ Event ${eventId} deleted.`));
}

async function cmdFreeBusy() {
  const tz = flags.tz || 'UTC';
  const startStr = flags.start ? toRFC3339(flags.start, tz) : isoNow();
  const endStr = flags.end
    ? toRFC3339(flags.end, tz)
    : new Date(Date.now() + 8 * 3600000).toISOString();

  const calIds = (flags.calendars || 'primary').split(',').map(s => s.trim());

  const body = {
    timeMin: startStr,
    timeMax: endStr,
    timeZone: tz,
    items: calIds.map(id => ({ id })),
  };

  const data = await calFetch('/freeBusy', {
    method: 'POST',
    body: JSON.stringify(body),
  });

  console.log(C.bold('Free/Busy Report'));
  console.log(`  Range: ${C.gray(startStr)} → ${C.gray(endStr)}\n`);

  for (const [calId, info] of Object.entries(data.calendars || {})) {
    const busy = info.busy || [];
    if (busy.length === 0) {
      console.log(`  ${C.green('●')} ${calId}: ${C.green('Free')}`);
    } else {
      console.log(`  ${C.red('●')} ${calId}: ${C.red(`${busy.length} busy period(s)`)}`);
      for (const slot of busy) {
        console.log(`    ${C.gray(formatDateTime({ dateTime: slot.start }))} → ${C.gray(formatDateTime({ dateTime: slot.end }))}`);
      }
    }
  }
}

async function cmdCalendars() {
  const data = await calFetch('/users/me/calendarList');
  const items = data.items || [];

  if (items.length === 0) {
    console.log('No calendars found.');
    return;
  }

  console.log(C.bold(`Calendars — ${items.length} found\n`));
  for (const cal of items) {
    const primary = cal.primary ? C.yellow(' (primary)') : '';
    const role = cal.accessRole ? C.gray(` [${cal.accessRole}]`) : '';
    console.log(`  ${C.cyan('●')} ${cal.summary || cal.id}${primary}${role}`);
    console.log(`    ${C.gray('ID: ' + cal.id)}`);
  }
}

// ─── Dispatch ────────────────────────────────────────────────────────────────

const USAGE = `
gcal — Google Calendar CLI for SLICC

Usage: gcal <command> [args] [--flags]

Commands:
  list        List upcoming events
              --date PERIOD    Look-ahead: 1d, 7d, 2w, 1m (default: 7d)
              --calendar ID    Calendar ID (default: primary)
              --limit N        Max results (default: 20)
              --json           Raw JSON output

  view <id>   View full event details
              --calendar ID    Calendar ID (default: primary)

  create      Create a new event
              --title TEXT     Event title (required)
              --start DT       ISO 8601 start (required)
              --end DT         ISO 8601 end (required)
              --tz TZ          IANA timezone (default: UTC)
              --calendar ID    Target calendar (default: primary)
              --location TEXT  Location
              --desc TEXT      Description
              --attendees LIST Comma-separated emails

  update <id> Patch an existing event (same flags as create, all optional)

  delete <id> Delete an event
              --calendar ID    Calendar ID (default: primary)

  freebusy    Query free/busy availability
              --start DT       Range start (default: now)
              --end DT         Range end (default: +8h)
              --tz TZ          IANA timezone (default: UTC)
              --calendars IDS  Comma-separated calendar IDs (default: primary)

  calendars   List all accessible calendars

Environment:
  GWS_CLIENT_ID, GWS_CLIENT_SECRET, GWS_REFRESH_TOKEN
  (refresh token must include https://www.googleapis.com/auth/calendar scope)
`.trim();

switch (subcommand) {
  case 'list':       await cmdList();      break;
  case 'view':       await cmdView();      break;
  case 'create':     await cmdCreate();    break;
  case 'update':     await cmdUpdate();    break;
  case 'delete':     await cmdDelete();    break;
  case 'freebusy':   await cmdFreeBusy();  break;
  case 'calendars':  await cmdCalendars(); break;
  case '--help':
  case 'help':
  case '':
    console.log(USAGE);
    break;
  default:
    process.stderr.write(`gcal: unknown command "${subcommand}". Run "gcal --help" for usage.\n`);
    process.exit(1);
}
