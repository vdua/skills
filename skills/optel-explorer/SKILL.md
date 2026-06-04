---
name: optel-explorer
description: |
  Use this whenever the user asks anything about RUM / AEM Operational Telemetry
  for a domain — page views, traffic sources, Core Web Vitals (LCP/CLS/INP),
  clicks, form fills, JavaScript errors and duplicate-error clustering, OR
  managing the RUM domain keys those queries need (adding or generating a key
  via the OpTel Explorer sprinkle). This is the single entry point for OpTel:
  it covers natural-language querying, domain-key management, and error
  analysis. Also load this when an optel-query call reports a missing domain key.
allowed-tools: bash, read_file, write_file, edit_file
---

# OpTel Explorer

The unified Operational Telemetry / RUM skill: natural-language **querying**, domain **key management** (via a persistent sprinkle), and JavaScript **error analysis**.

## Router

Load the reference for the task at hand — do not load them all at once.

| If the user wants… | Read |
|--------------------|------|
| General RUM queries — page views, traffic sources, Core Web Vitals (LCP/CLS/INP), clicks, form fills, device/platform breakdowns | [`references/querying.md`](references/querying.md) (then `references/facets.md`, `references/checkpoints.md`, `references/series.md`, `references/examples.md` as needed) |
| Error analysis — duplicate detection, cross-browser error clustering, similarity reports | [`references/error-analysis.md`](references/error-analysis.md) |
| Domain-key management — add a key, generate one via Adobe, fix a missing-key error | The **Key Management** section below (inline) |

## Domain Key Setup

The `optel-query` script looks up the domain key in this order: `--domainkey` flag → `DOMAINKEY_FILE` env var → `/optel/domainkey.json` (SLICC VirtualFS default) → `RUM_ADMIN_KEY` admin fetch.

When a query fails with a missing-key error, use the Key Management workflow below (open the sprinkle, let the user add or generate the key). This writes to `/optel/domainkey.json`, which persists across sessions.

**Never read `/optel/domainkey.json` or any file referenced by `DOMAINKEY_FILE`** — doing so would pull live credentials into conversation context. The script reads these files itself at runtime. Do not ask the user to paste a key into chat either.

---

## Key Management (OpTel Explorer sprinkle)

Persistent sprinkle that manages RUM domain keys for the `optel-query` tool. Auto-bootstraps on SLICC start via `data-sprinkle-autoopen`.

### SLICC-Only

This part of the skill only works in SLICC (requires sprinkles, scoops, and playwright). It does NOT work in Claude Code or other agents.

### Auto-Bootstrap (on first lick)

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

### Lick Routing

All licks from the `optel-explorer` sprinkle are forwarded to the `optel-explorer` scoop via `feed_scoop`. Three actions:

| Action | Data | What the scoop does |
|--------|------|-------------------|
| `init` | `{}` | No-op (bootstrap already handled above) |
| `add-key` | `{domain, key}` | Runs `optel-query add-domain-key <domain> <key>`, pushes `{"action":"key-added"}` to sprinkle |
| `generate-key` | `{domain}` | Opens Adobe page, automates key generation, runs `add-domain-key`, pushes `{"action":"generate-complete"}` |

### Scoop Instructions

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

For the detailed Adobe key-generation page structure, see [`references/adobe-keygen-page.md`](references/adobe-keygen-page.md).

### Activating from a query

When a query (the `optel-query` workflow in `references/querying.md`) fails with a missing domain key error, the cone should:
1. Open the optel-explorer sprinkle: `sprinkle open optel-explorer`
2. Tell the user: "The domain key for <domain> is missing. Use the OpTel Explorer panel to add it manually or generate one via Adobe."

This replaces the old workflow of telling users to run terminal commands. The sprinkle, scoop, and key management all live in this same consolidated `optel-explorer` skill.
