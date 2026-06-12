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
| General RUM queries — page views, traffic sources, Core Web Vitals (LCP/CLS/INP), clicks, form fills, device/platform breakdowns | [`references/querying.md`](references/querying.md) |
| Error analysis — duplicate detection, cross-browser error clustering, similarity reports | [`references/error-analysis.md`](references/error-analysis.md) |
| Domain-key management — add a key, generate one via Adobe, fix a missing-key error | The **Key Management** section below (inline) |

## Domain Key Setup

The `optel-query` script looks up the domain key in this order: `--domainkey` flag → `DOMAINKEY_FILE` env var → `/optel/domainkey.json` (SLICC VirtualFS default) → `RUM_ADMIN_KEY` admin fetch.

When a query fails with a missing-key error, use the Key Management workflow below (open the sprinkle, let the user add or generate the key). This writes to `/optel/domainkey.json`, which persists across sessions.

**Never read `/optel/domainkey.json` or any file referenced by `DOMAINKEY_FILE`** — doing so would pull live credentials into conversation context. The script reads these files itself at runtime. Do not ask the user to paste a key into chat either.

---

## Key Management (SLICC-only)

Domain keys live in `/optel/domainkey.json`. Manage them with the `optel-explorer` CLI (SLICC-only; `generate` requires playwright):

```bash
optel-explorer generate <domain>              # Generate a key via Adobe and save it
optel-explorer add-domain-key <domain> <key>  # Save a key
optel-explorer remove-domain-key <domain>     # Remove a key
```

`generate` requires `https://aemcs-workspace.adobe.com` open and logged in. Always mutate the key store through these subcommands — never edit `/optel/domainkey.json` directly (the VFS `writeFile` does not truncate and will corrupt the file; the CLI handles truncation safely).
