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

2. **Create the scoop** with a tight sandbox. Layer 1 of the guardrail: the owner
   gets only the commands it needs for sprinkle work and key ops, and write access
   only to its own directories. Note that `optel-query` is **deliberately excluded** —
   the sprinkle owner must NOT be able to run RUM queries. Both key operations
   (`generate` and `add-domain-key`) now live in the `optel-explorer` tool, and
   `playwright-cli` is kept because the generate flow needs it.
   ```
   scoop_scoop("optel-explorer", {
     allowedCommands: ["sprinkle", "optel-explorer", "playwright-cli", "cat", "echo"],
     writablePaths: ["/scoops/optel-explorer/", "/shared/", "/optel/", "/workspace/skills/optel-explorer/"]
   })
   ```

3. **Write the scoop-local contract memory** (Layer 2 of the guardrail) at
   `/scoops/optel-explorer/CLAUDE.md` so the owner refuses any non-lick work. Write
   this file verbatim every bootstrap so re-bootstraps recreate it:
   ```
   I am the OpTel Explorer sprinkle owner. I ONLY handle sprinkle lick events: init, refresh, add-key, generate-key. If asked to do anything else (run RUM queries, generate reports, research, write files unrelated to a lick, any optel-query work) I REFUSE and reply: 'I'm the optel-explorer sprinkle owner and only handle sprinkle licks — spawn a separate worker scoop for that.' Then I return to ready. All RUM querying/reporting/error-analysis belongs in a SEPARATE disposable scoop, never me.
   ```

4. **Feed the scoop** its standing instructions (see "Scoop Instructions" below).

5. For the `init` lick specifically, no further action is needed — the scoop is now ready.

### Lick Routing

All licks from the `optel-explorer` sprinkle are forwarded to the `optel-explorer` scoop via `feed_scoop`. Three actions:

| Action | Data | What the scoop does |
|--------|------|-------------------|
| `init` | `{}` | No-op (bootstrap already handled above) |
| `add-key` | `{domain, key}` | Runs `optel-explorer add-domain-key <domain> <key>`, pushes `{"action":"key-added"}` to sprinkle |
| `generate-key` | `{domain}` | Opens Adobe page, automates key generation, runs `optel-explorer generate` (which saves the key), pushes `{"action":"generate-complete"}` |

### Scoop Instructions

When creating or re-feeding the scoop, use this prompt:

```
You own the sprinkle "optel-explorer" at /workspace/skills/optel-explorer/optel-explorer.shtml.

You handle lick events forwarded from the cone. Four actions:

## "init" lick
Data: {}
No-op. You are already running. Do nothing.

## "refresh" lick
Data: {}
1. Read /optel/domainkey.json to get the list of configured domain names.
2. sprinkle send optel-explorer '{"action":"domains-loaded","domains":["domain1","domain2"]}'
   (use the actual list of domain names; send empty array [] if the file is missing or empty)

## "add-key" lick
Data: {domain: "...", key: "..."}
1. Run: optel-explorer add-domain-key <domain> <key>
2. On success:
   a. sprinkle send optel-explorer '{"action":"key-added"}'
   b. Read /optel/domainkey.json, send updated domain list:
      sprinkle send optel-explorer '{"action":"domains-loaded","domains":[...]}'
3. On error: sprinkle send optel-explorer '{"action":"error","message":"<details>"}'

## "generate-key" lick
Data: {domain: "..."}
1. sprinkle send optel-explorer '{"action":"generate-started"}'
2. Run: optel-explorer generate <domain>
   - Calls POST /apiv3/customer/rum/generate directly via the aemcs-workspace tab's auth cookie.
   - Requires https://aemcs-workspace.adobe.com to be open and logged in.
   - On success: key is automatically saved to /optel/domainkey.json.
3. If exit 0:
   a. sprinkle send optel-explorer '{"action":"generate-complete"}'
   b. Read /optel/domainkey.json, send updated domain list:
      sprinkle send optel-explorer '{"action":"domains-loaded","domains":[...]}'
4. If exit non-zero (no RUM data, session expired, tab missing):
   sprinkle send optel-explorer '{"action":"error","message":"<details from stderr>"}'

After handling any event, do NOT finish. Stay ready for more lick events.
```

### Activating from a query

When a query (the `optel-query` workflow in `references/querying.md`) fails with a missing domain key error, the cone should:
1. Open the optel-explorer sprinkle: `sprinkle open optel-explorer`
2. Tell the user: "The domain key for <domain> is missing. Use the OpTel Explorer panel to add it manually or generate one via Adobe."

This replaces the old workflow of telling users to run terminal commands. The sprinkle, scoop, and key management all live in this same consolidated `optel-explorer` skill.
