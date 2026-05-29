---
name: optel-explorer
description: Manage RUM domain keys for Operational Telemetry via a persistent sprinkle UI. Auto-opens on SLICC start. Provides a visual interface to list configured domains, manually add keys, or generate keys via the Adobe workspace page with browser automation. Use when a domain key is missing, when the user wants to manage RUM keys, or when optel-query reports a missing key error.
---

# OpTel Explorer

Persistent sprinkle that manages RUM domain keys for the `optel-query` tool. Auto-bootstraps on SLICC start via `data-sprinkle-autoopen`.

## SLICC-Only Skill

This skill only works in SLICC (requires sprinkles, scoops, and playwright). It does NOT work in Claude Code or other agents.

## Auto-Bootstrap (on first lick)

When you receive the `init` lick from the optel-explorer sprinkle (or any lick when the scoop doesn't exist yet):

1. **Create the `/optel/` directory** if it doesn't exist:
   ```bash
   mkdir -p /optel
   ```

2. **Create the scoop** with write access to `/optel/`:
   ```
   scoop_scoop("optel-explorer", {
     writablePaths: ["/scoops/optel-explorer/", "/shared/", "/optel/", "/workspace/skills/optel-explorer/"]
   })
   ```

3. **Feed the scoop** its standing instructions (see "Scoop Instructions" below).

4. For the `init` lick specifically, no further action is needed — the scoop is now ready.

## Lick Routing

All licks from the `optel-explorer` sprinkle are forwarded to the `optel-explorer` scoop via `feed_scoop`. Three actions:

| Action | Data | What the scoop does |
|--------|------|-------------------|
| `init` | `{}` | No-op (bootstrap already handled above) |
| `add-key` | `{domain, key}` | Runs `optel-query add-domain-key <domain> <key>`, pushes `{"action":"key-added"}` to sprinkle |
| `generate-key` | `{domain}` | Opens Adobe page, automates key generation, runs `add-domain-key`, pushes `{"action":"generate-complete"}` |

## Scoop Instructions

When creating or re-feeding the scoop, use this prompt:

```
You own the sprinkle "optel-explorer" at /workspace/skills/optel-explorer/optel-explorer.shtml.

You handle lick events forwarded from the cone. Two actions:

## "add-key" lick
Data: {domain: "...", key: "..."}
1. Run: optel-query add-domain-key <domain> <key>
2. On success: sprinkle send optel-explorer '{"action":"key-added"}'
3. On error: sprinkle send optel-explorer '{"action":"error","message":"<details>"}'

## "generate-key" lick
Data: {domain: "..."}
1. Push: sprinkle send optel-explorer '{"action":"generate-started"}'
2. Check if the Adobe tab is open: playwright-cli tab-list
   - Look for URL containing "aemcs-workspace.adobe.com"
   - If not found: playwright-cli navigate https://aemcs-workspace.adobe.com/customer/generate-optel-domain-key
3. Check if user is logged in (look for "Generate" button or user avatar in snapshot). If not logged in, poll every 10 seconds for up to 2 minutes.
4. Fill the domain (Angular Material input, id="mat-input-0"):
   playwright-cli eval --tab=<ID> "const el = document.getElementById('mat-input-0'); const s = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set; s.call(el, '<domain>'); el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); 'done'"
5. Click Generate:
   playwright-cli eval --tab=<ID> "Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('Generate')).click(); 'clicked'"
6. Wait 4 seconds, then inspect page:
   - If "No data found for this domain" → sprinkle send optel-explorer '{"action":"error","message":"No data found for <domain> — domain has no RUM data."}'
   - If key is shown (look for "Generated key" text + masked key + "Show key" button):
     a. Click "Show key" button to reveal the key
     b. Read the key value from the DOM
     c. Run: optel-query add-domain-key <domain> <key>
     d. sprinkle send optel-explorer '{"action":"generate-complete"}'

After handling any event, do NOT finish. Stay ready for more lick events.
```

## Activating from optel-query

When `optel-query` fails with a missing domain key error and this skill is installed, the cone should:
1. Open the optel-explorer sprinkle: `sprinkle open optel-explorer`
2. Tell the user: "The domain key for <domain> is missing. Use the OpTel Explorer panel to add it manually or generate one via Adobe."

This replaces the old workflow of telling users to run terminal commands.
