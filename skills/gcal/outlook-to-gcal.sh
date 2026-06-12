#!/bin/bash
# Clone Outlook calendar events → Google Calendar (primary)
# Skips: cancelled events, all-day events (PTO etc.)
# Converts UTC times → Asia/Kolkata (IST = UTC+5:30)
# Organizer added to event description

outlook calendar --date 10d --json \
  | jq -r '
      .[]
      | select(.IsCancelled == false)
      | select(.IsAllDay == false)
      | select(.Subject | test("PTO|shows as free"; "i") | not)
      | .Subject as $title
      | .Organizer.EmailAddress.Name as $organizer
      | (.Start.DateTime | split(".")[0] + "Z") as $start
      | (.End.DateTime   | split(".")[0] + "Z") as $end
      | [$title, $organizer, $start, $end]
      | @tsv
    ' \
  | while IFS=$'\t' read -r title organizer start end; do
      # Convert UTC → IST by adding 5h30m using date command
      start_ist=$(date -d "$start + 5 hours 30 minutes" '+%Y-%m-%dT%H:%M:%S' 2>/dev/null \
                  || python3 -c "
from datetime import datetime, timedelta
dt = datetime.strptime('$start', '%Y-%m-%dT%H:%M:%SZ')
dt = dt + timedelta(hours=5, minutes=30)
print(dt.strftime('%Y-%m-%dT%H:%M:%S'))
")
      end_ist=$(date -d "$end + 5 hours 30 minutes" '+%Y-%m-%dT%H:%M:%S' 2>/dev/null \
                || python3 -c "
from datetime import datetime, timedelta
dt = datetime.strptime('$end', '%Y-%m-%dT%H:%M:%SZ')
dt = dt + timedelta(hours=5, minutes=30)
print(dt.strftime('%Y-%m-%dT%H:%M:%S'))
")

      echo "Creating: $title ($start_ist → $end_ist IST)"
      gcal create \
        --title "$title" \
        --start "$start_ist" \
        --end "$end_ist" \
        --tz "Asia/Kolkata" \
        --desc "Organizer: $organizer"
      echo ""
    done
