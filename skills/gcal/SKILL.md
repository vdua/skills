---
name: gcal
description: |
  Use this when the user wants to interact with Google Calendar — list upcoming
  events, view event details, create new events, update or delete existing events,
  or query free/busy availability. Covers the gcal command (list, view, create,
  update, delete, freebusy). Uses the same GWS_* OAuth2 env vars as the gmail
  skill, but requires the calendar scope. For Gmail (email, inbox, send, reply)
  use the gmail skill instead.
allowed-tools: bash
---

# gcal

Direct API access to Google Calendar via OAuth2 refresh token flow. Uses the same
`GWS_CLIENT_ID`, `GWS_CLIENT_SECRET`, and `GWS_REFRESH_TOKEN` environment variables
as the gmail skill — but the refresh token must include the
`https://www.googleapis.com/auth/calendar` scope.

## Quick start

```bash
# List upcoming events (next 7 days)
gcal list

# List events for a specific range
gcal list --date 14d

# List events for a specific calendar
gcal list --calendar work@example.com

# View a single event (full details)
gcal view <event-id>

# Create an event
gcal create --title "Team standup" --start "2026-06-11T10:00" --end "2026-06-11T10:30"

# Create with timezone, location, description, attendees
gcal create --title "Lunch" --start "2026-06-11T12:00" --end "2026-06-11T13:00" \
  --tz "Asia/Kolkata" --location "Café" --desc "Catch up" --attendees "a@x.com,b@x.com"

# Update an event
gcal update <event-id> --title "New title" --start "2026-06-11T11:00" --end "2026-06-11T11:30"

# Delete an event
gcal delete <event-id>

# Free/busy query (who's free in a time range)
gcal freebusy --start "2026-06-11T09:00" --end "2026-06-11T17:00"

# List available calendars
gcal calendars
```

## Authentication

Requires the same env vars as the gmail skill:

| Variable | Description |
|----------|-------------|
| `GWS_CLIENT_ID` | OAuth2 client ID |
| `GWS_CLIENT_SECRET` | OAuth2 client secret |
| `GWS_REFRESH_TOKEN` | Refresh token with calendar scope |

The refresh token **must** include `https://www.googleapis.com/auth/calendar`.
If your current token only has the Gmail scope, generate a new one with both scopes
(see OAuth scope section below).

## Commands

### gcal list [options]

List upcoming events from your primary calendar (or a specified one).

**Options:**
- `--date PERIOD` — look-ahead window: `1d`, `7d`, `2w`, `1m` (default: `7d`)
- `--calendar ID` — calendar ID (default: `primary`)
- `--limit N` — max events to return (default: 20)
- `--json` — output raw JSON array

### gcal view \<event-id\>

View full details of a single event: title, time, location, description, attendees, status.

### gcal create --title TEXT --start DATETIME --end DATETIME [options]

Create a new event.

**Options:**
- `--title TEXT` — event title (required)
- `--start DATETIME` — ISO 8601: `2026-06-11T10:00` (required)
- `--end DATETIME` — ISO 8601: `2026-06-11T10:30` (required)
- `--tz TIMEZONE` — IANA timezone (default: `UTC`)
- `--calendar ID` — calendar to add to (default: `primary`)
- `--location TEXT` — event location
- `--desc TEXT` — event description
- `--attendees EMAILS` — comma-separated list of attendee emails

### gcal update \<event-id\> [options]

Patch an existing event. Only the fields you pass are changed.

**Options:** same as `create`, all optional.

### gcal delete \<event-id\> [options]

Delete an event permanently.

**Options:**
- `--calendar ID` — calendar ID (default: `primary`)

### gcal freebusy [options]

Query free/busy status for calendars in a time range.

**Options:**
- `--start DATETIME` — range start, ISO 8601 (default: now)
- `--end DATETIME` — range end, ISO 8601 (default: +8h)
- `--tz TIMEZONE` — IANA timezone (default: `UTC`)
- `--calendars IDS` — comma-separated calendar IDs (default: `primary`)

### gcal calendars

List all calendars accessible to the authenticated account.

## OAuth scope for Calendar

If your current `GWS_REFRESH_TOKEN` was generated with only the Gmail scope, you need
a new token that includes the Calendar scope. Generate one with both scopes combined:

```
https://accounts.google.com/o/oauth2/v2/auth?
  client_id=YOUR_CLIENT_ID
  &redirect_uri=urn:ietf:wg:oauth:2.0:oob
  &response_type=code
  &scope=https://www.googleapis.com/auth/gmail.modify%20https://www.googleapis.com/auth/calendar
  &access_type=offline
  &prompt=consent
```

Exchange the code:

```bash
curl -X POST https://oauth2.googleapis.com/token \
  -d client_id=YOUR_CLIENT_ID \
  -d client_secret=YOUR_CLIENT_SECRET \
  -d code=YOUR_AUTH_CODE \
  -d redirect_uri=urn:ietf:wg:oauth:2.0:oob \
  -d grant_type=authorization_code
```

Update `~/.slicc/secrets.env` with the new `GWS_REFRESH_TOKEN` value and reload SLICC.

## Common errors

- **`insufficient authentication scopes`** — your refresh token doesn't include the
  calendar scope. Generate a new token with both Gmail + Calendar scopes.
- **`missing GWS_* env vars`** — env vars not loaded; check `~/.slicc/secrets.env`.
- **`Event not found`** — the event-id is wrong or belongs to a different calendar;
  use `gcal list --json` to get correct IDs.
