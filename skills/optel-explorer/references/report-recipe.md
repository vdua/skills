# OpTel Full-Report Recipe (worker-scoop orchestration)

This recipe assembles a full narrative OpTel report that mirrors the on-screen
**tools.aem.live** report sections, built entirely from `optel-query` calls.

> `optel-query` returns one result per call by design; the narrative, ratios,
> funnels, and trends are assembled after running the query battery below.
> The final **deliverable is a SPRINKLE** (an in-SLICC panel), not a Markdown
> or standalone HTML file — see the "Sprinkle Output Specification" at the end.
>
> **Delegation (read carefully — two distinct scoops):**
> - **Query battery → a DISPOSABLE worker scoop**, NEVER `optel-explorer-scoop`
>   (the key-management owner). This worker runs the commands, captures JSON,
>   computes the cross-metric ratios, builds the report markup, and is then
>   discarded.
> - **Sprinkle ownership → a SEPARATE, LONG-LIVED scoop** named identically to
>   the sprinkle (e.g. `report-viewer`). The cone NEVER writes `.shtml` or runs
>   `sprinkle` commands itself — it delegates that to this owner scoop, which
>   builds the `.shtml`, opens the sprinkle, and stays alive to handle licks.
>   This is ALSO not `optel-explorer-scoop`.
> A clean flow: disposable worker produces the report markup → hands it to the
> sprinkle-owner scoop → owner embeds it in the `.shtml` and opens the panel.

## Conventions

- `$D` = domain (e.g. `www.adobe.com`), `$S`/`$E` = start/end dates (`YYYY-MM-DD`).
- The CLI reads the domain key itself from `/optel/domainkey.json` (or
  `--domainkey`). Never read or print the key.
- All counts are **weighted page views**; `--facet-values` returns
  `facetValues[]` with `value`/`count` plus `totalPageViews`/`filteredPageViews`.
- Series emit objects: CWV series → `{p75}`; count series
  (visits/bounces/engagement/earned/organic) → `{sum}`; timeOnPage → `{mean,p50,p75}`.
- For high-traffic domains the hourly endpoint 413s; the script auto-falls-back
  to daily — do NOT change interval handling to "fix" this.

## Use `--batch` for the whole report (one fetch, not ~25)

`optel-query` re-fetches the entire window on EVERY call. A report runs ~25
queries, so naively it pulls the same daily bundles ~25× (~750 HTTP requests for
a 30-day high-traffic domain). **Use `--batch` instead: it fetches + parses the
window ONCE and answers every request in-memory** (~30 requests total, one
DataChunks build).

Write all report questions into one JSON file (array of requests; each is
`{id?, query?, facetValues?, series?}` — `facetValues` ⇒ a facet request, else a
count), then make a single call:

```bash
optel-query $D $S $E --batch /shared/report-queries.json --output /shared/report-results.json
```

Output shape: `{ "result": { "results": [ { "id", "type", "result" | "error" }, ... ] } }`,
in input order. A bad request (e.g. unknown facet) is reported as a per-item
`error` and does NOT abort the batch.

Example batch file (subset — include every section's queries below):
```json
[
  { "id": "pageviews" },
  { "id": "kpis", "series": ["visits","bounces","engagement","earned","organic"] },
  { "id": "cwv", "series": ["lcp","cls","inp","ttfb"] },
  { "id": "errors", "query": {"checkpoint":["error"]} },
  { "id": "error-sources", "query": {"checkpoint":["error"]}, "facetValues": "error" },
  { "id": "device", "facetValues": "userAgent" },
  { "id": "visibility", "query": {"checkpoint":["enter"]}, "facetValues": "enter.target" },
  { "id": "top-urls", "facetValues": "url" },
  { "id": "period", "facetValues": "period" }
]
```

The query battery in the sections below lists the individual commands for
reference/readability — but when actually generating a report, fold them ALL
into a single `--batch` file and run one call. Compute trends from the `period`
request rather than separate windows where possible (still one fetch).

---

## Section 1 — Executive Summary
Synthesised AFTER the rest. Pull the headline numbers from Sections 2–4
(page views, visits, bounce rate, engagement rate, top CWV, error rate) and
write 3–5 sentences. No dedicated query.

## Section 2 — Traffic / KPIs
```bash
# Page views (the main result)
optel-query $D $S $E
# Visits, bounces, engagement, earned, organic (weighted counts -> {sum})
optel-query $D $S $E --series visits,bounces,engagement,earned,organic
```
Compute (worker-side):
- **Bounce rate** = `bounces.sum / visits.sum`
- **Engagement rate** = `engagement.sum / result` (page views)
- **Pages per visit** = `result / visits.sum`

## Section 3 — Core Web Vitals
```bash
optel-query $D $S $E --series lcp,cls,inp,ttfb
```
Report each `{p75}`. `"N/A"` means fewer than 10 samples in the window.

## Section 4 — JS Errors
```bash
# Error page-view count (compute error rate = this / total page views)
optel-query $D $S $E --query '{"checkpoint":["error"]}'
# Top error source|target strings
optel-query $D $S $E --query '{"checkpoint":["error"]}' --facet-values error
```

## Section 5 — Performance (redirects, LCP element)
```bash
# Redirect count
optel-query $D $S $E --query '{"checkpoint":["redirect"]}'
# LCP element source (img, .body-m, video, ...)
optel-query $D $S $E --query '{"checkpoint":["cwv-lcp"]}' --facet-values cwv-lcp.source
# Slow resources: list load-time buckets, then drill into slow ones
optel-query $D $S $E --query '{"checkpoint":["loadresource"]}' --facet-values loadresource.target
optel-query $D $S $E --query '{"checkpoint":["loadresource"],"loadresource.target":["500","1200"]}' --facet-values loadresource.source
```

## Section 6 — User Engagement (device, clicks, foreground/background)
```bash
# Device / OS split
optel-query $D $S $E --facet-values userAgent
# Click count + sources + targets
optel-query $D $S $E --query '{"checkpoint":["click"]}'
optel-query $D $S $E --query '{"checkpoint":["click"]}' --facet-values click.source
optel-query $D $S $E --query '{"checkpoint":["click"]}' --facet-values click.target
# Foreground vs background tab loads (visible/hidden) — via enter.target
optel-query $D $S $E --query '{"checkpoint":["enter"]}' --facet-values enter.target
# (optionally also navigate.target / back_forward.target / reload.target)
```
Worker computes the visible/hidden split (e.g. ~75% visible / 25% hidden).

## Section 7 — Traffic & Acquisition
```bash
# External referrers
optel-query $D $S $E --query '{"checkpoint":["enter"]}' --facet-values enter.source
# Classified paid/owned/earned channels
optel-query $D $S $E --facet-values acquisitionSource
# UTM count + breakdown
optel-query $D $S $E --query '{"checkpoint":["utm"]}'
optel-query $D $S $E --query '{"checkpoint":["utm"]}' --facet-values utm.source
optel-query $D $S $E --query '{"checkpoint":["utm"]}' --facet-values utm.target
```

## Section 8 — Content (top URLs, language)
```bash
# Top pages
optel-query $D $S $E --facet-values url
# Language source / target locales
optel-query $D $S $E --query '{"checkpoint":["language"]}' --facet-values language.target
optel-query $D $S $E --query '{"checkpoint":["language"]}' --facet-values language.source
```

## Section 9 — Conversion Funnel (top -> click -> formsubmit)
```bash
# Funnel stages (each is a weighted page-view count; compute step ratios)
optel-query $D $S $E --query '{"checkpoint":["top"]}'
optel-query $D $S $E --query '{"checkpoint":["click"]}'
optel-query $D $S $E --query '{"checkpoint":["fill"]}'
optel-query $D $S $E --query '{"checkpoint":["formsubmit"]}'
# Form-field sources (where fills happen)
optel-query $D $S $E --query '{"checkpoint":["fill"]}' --facet-values fill.source
```
> NOTE: `fill` / `formsubmit` may be **EMPTY** in short windows (they were
> absent in the 2-day adobe.com window). Verify on a monthly window before
> concluding the funnel is broken.

## Section 10 — Geographic (PROXY ONLY)
> **Not real geography.** RUM collects no IP geolocation; no checkpoint carries
> country/region. Approximate via (a) URL locale prefixes and (b) consent volume.
```bash
# Locale via URL prefixes (/in/, /br/, /mena_ar/, /la/, ...) — aggregate url
optel-query $D $S $E --facet-values url
# Consent dialog exposure/acceptance as an engagement proxy
optel-query $D $S $E --query '{"checkpoint":["consent"]}' --facet-values consent.target
```
Clearly label this section as inferred, not measured geography.

## Section 11 — Business Impact
Synthesised. Combine conversion-funnel ratios (Section 9), error rate
(Section 4), and engagement (Section 2) into a qualitative impact narrative.
No dedicated query.

## Section 12 — Priority Actions
Synthesised. Rank issues by reach x severity using the numbers above
(e.g. high-volume errors, slow LCP element, low engagement segments).

---

## Trends ("rising / falling / trending up")
A single window cannot express a trend. Either:
- Run `--facet-values period` to get a per-day breakdown inside the window:
  ```bash
  optel-query $D $S $E --facet-values period
  optel-query $D $S $E --query '{"checkpoint":["error"]}' --facet-values period
  ```
- OR run the SAME query over two adjacent windows (e.g. this week vs last week)
  and compute the delta worker-side.

State trend direction explicitly only when backed by multiple periods/windows.

---

## Worker checklist
1. Spawn a disposable worker scoop (never `optel-explorer-scoop`) for the queries.
2. Fold ALL section queries into ONE `--batch` file and run a single
   `optel-query $D $S $E --batch <file> --output <results>` (one fetch, not ~25).
   Read back the `results[]` array by `id`.
3. Compute ratios (bounce, engagement, pages/visit, error rate, funnel steps,
   visibility split).
4. Label proxies (geography) and trend caveats honestly.
5. Deliver the executive report as a **SPRINKLE**: a separate, long-lived owner
   scoop writes the report as a FULL-DOCUMENT-mode `.shtml` (the report IS the
   document; SLICC iframes it and injects theming), colored with S2 tokens — see
   the Sprinkle Output Specification — and opens the panel. Dispose of the query
   worker.

---

# Sprinkle Output Specification (REQUIRED deliverable)

The report is delivered as a **sprinkle** — an in-SLICC panel the user views
directly — NOT a Markdown file or a standalone `.html` file. The layout is described
below; only the container is a `.shtml` sprinkle.

## Ownership & delegation (do not skip)
- The sprinkle is owned by a **dedicated, long-lived scoop named identically to
  the sprinkle** (e.g. sprinkle `report-viewer` ↔ scoop `report-viewer`). It is
  NOT `optel-explorer-scoop` and NOT the disposable query worker.
- The **cone never** writes `.shtml` or runs `sprinkle` commands — it delegates to
  the owner scoop, which builds the file, runs `sprinkle open <name>`, and stays
  alive to handle lick events (e.g. a Reload/regenerate button).
- Read `/workspace/skills/sprinkles/SKILL.md` (and its style guide) before
  authoring the `.shtml` — follow its structure and the `data-sprinkle-autoopen`
  convention.

## Hard requirements
- **The sprinkle is a FULL-DOCUMENT-mode `.shtml`** — the report IS the document.
  Per the sprinkles SKILL, a `.shtml` that begins with `<!DOCTYPE html>` /
  `<html>` is rendered by SLICC inside a sandboxed iframe automatically, AND the
  parent injects its S2 theme tokens + toggles a `.theme-light` class on the
  sprinkle's `<html>`. So you get CSS isolation AND theming for free.
  - Do **NOT** hand-roll a nested `<iframe srcdoc=...>` and do **NOT** load the
    report via `readFile` at runtime. Both re-implement what full-document mode
    already does; the `srcdoc` route also requires escaping ~30KB into an
    attribute, which has repeatedly corrupted the file. Just make the report the
    document.
- **Theme with S2 tokens — never hard-code colors.** The report's palette MUST be
  expressed in theme-aware S2 custom properties so it follows light/dark with the
  app (see `/workspace/skills/sprinkles/style-guide.md`). Mapping:
  - page bg → `var(--s2-bg-base)`; cards/tiles/header band → `var(--s2-bg-elevated)`
    (band may tint: `color-mix(in srgb, var(--s2-accent) 12%, var(--s2-bg-elevated))`);
    zebra/nested → `var(--s2-bg-layer-1/2)`.
  - text → `var(--s2-content-default)`; muted/labels/footer → `var(--s2-content-secondary)`.
  - accent (rules, key numbers, neutral bar fills) → `var(--s2-accent)`.
  - status: good → `var(--s2-positive)`, warning/needs-improvement → `var(--s2-notice)`,
    critical/poor → `var(--s2-negative)`, info → `var(--s2-informative)`.
  - subtle tints ("So what:" callouts, tile accents) →
    `color-mix(in srgb, var(--s2-<semantic>) 8-12%, transparent)`.
  - text on filled bars → `var(--s2-gray-25)` (never `#fff`).
  - borders → `1px solid color-mix(in srgb, var(--s2-content-default) 12%, transparent)`.
  - Set `color-scheme: light dark` on `:root`. Do NOT use
    `@media (prefers-color-scheme)` for theme colors (desyncs from the parent's
    class toggle). One-off colors not covered by a token → `light-dark(<l>,<d>)`.
- **Declare a rail icon** in `<head>`: `<link rel="icon" href="chart-bar" />`.
- **No external resources** — no CDN fonts, no JS libraries, no remote images.
  System font stack and inline SVG for charts only.
- **Write the `.shtml` in ONE clean `write_file` shot.** Never assemble it from
  shell heredocs / `cat >> file.part` / `sed` escaping pipelines — that corrupted
  the file previously. (A full HTML document needs no attribute escaping anyway.)
- **No raw JSON or query commands in the output.** Methodology/caveats in a small
  footer. Every number must come from the query battery — show "—" for N/A.
- Optionally ALSO write a standalone self-contained `.html` copy (same markup but
  with a fixed palette) if a shareable/exportable file is wanted — but the
  sprinkle deliverable itself is the full-document S2-themed `.shtml`.
- **No external resources** in the report markup — no CDN fonts, no JS libraries,
  no remote images. System font stack and inline SVG for charts only. (This keeps
  the panel offline-safe and the same markup print/export-clean if ever exported.)
- **No raw JSON or query commands in the output.** Methodology/caveats go in a
  small footer, not the body.
- **Every number shown must come from the query battery** — never fabricate.
  Where a metric is N/A or a checkpoint is absent, show it honestly (e.g. "—").

## Panel chrome + Download/Export

Keep chrome minimal — the report is the content. A small top toolbar (the report
title + a "Download report" button) is the expected pattern.

### Required: a "Download report" button (download the standalone .html)
The S2-themed sprinkle renders correctly only inside SLICC (its `var(--s2-*)`
tokens are injected by the parent). For a shareable artifact, ALSO write a
**standalone, self-contained `.html`** alongside the sprinkle: same content/layout
but with a FIXED palette (concrete hex, no S2 tokens) so it renders anywhere
(browser, email, print-to-PDF). e.g. `/shared/<domain>-optel-report-<window>.html`.

Wire a "Download report" button that delivers that standalone file directly to the
user's downloads — entirely client-side, no lick or owner-scoop round-trip. The
bridge exposes `slicc.readFile(path)`, so the button reads the standalone file and
triggers a Blob download (the SLICC sprinkle iframe is sandboxed with
`allow-downloads`, so a programmatic `<a download>` click is honored):
```html
<button id="dl-btn" class="sprinkle-btn sprinkle-btn--secondary">Download report</button>
<script>
  document.getElementById('dl-btn').addEventListener('click', async function () {
    var btn = this, orig = btn.textContent;
    btn.textContent = 'Preparing…'; btn.disabled = true;
    try {
      var html = await slicc.readFile('/shared/<domain>-optel-report-<window>.html');
      var url = URL.createObjectURL(new Blob([html], { type: 'text/html' }));
      var a = document.createElement('a');
      a.href = url; a.download = '<domain>-optel-report-<window>.html';
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
      btn.textContent = 'Downloaded';
    } catch (e) {
      console.error('download failed', e); btn.textContent = 'Download failed';
    }
    setTimeout(function () { btn.textContent = orig; btn.disabled = false; }, 1500);
  });
</script>
```
(Full-document mode auto-injects the bridge; use `slicc`, not `bridge`. For a PDF,
generate the `.pdf` as a separate step and `readFile`/download that instead.)

So this report flow produces TWO artifacts: the themed sprinkle (viewing in SLICC)
and the fixed-palette standalone `.html` (download/share) — keep them in sync when
regenerating. A "Reload"/"Regenerate" control, by contrast, IS a `slicc.lick(...)`
routed to the owner (it re-runs the query battery); only the file download stays
client-side.

## Structure (BLUF — Bottom Line Up Front)
1. **Header band** — report title, domain, date window, "generated" date.
2. **Executive Summary** — 2-3 sentence "bottom line," then **3 key findings**
   as short, bolded takeaways. This is the most important block; put it first.
3. **Priority Actions (TOP, not bottom)** — a ranked, scannable list of the
   recommended actions, each with: rank, action, **expected impact**, and a
   reach×severity indicator. Executives should be able to act from this block
   alone. (You still compute these from Sections 4/5/9 data.)
4. **KPI tiles row** — Page views, Visits, Bounce rate, Engagement rate, plus the
   four CWV (LCP/CLS/INP/TTFB). Each tile shows the value, a label, and a
   **status color** (see palette). CWV tiles colored by good/needs-improvement/poor.
5. **Detail sections** in this order, each ending with a one-line "**So what:**"
   implication: Traffic & KPIs · Core Web Vitals · JavaScript Errors ·
   Performance · User Engagement · Traffic & Acquisition · Content ·
   Conversion Funnel · Geographic (clearly badged "INFERRED — PROXY").
6. **Footer** — Methodology & Caveats (sampled/weighted RUM, daily granularity,
   geography inferred, window), small/muted.

## Design system
- **Layout:** centered single column, max-width ~960px, generous whitespace,
  clear section rhythm. Mobile-friendly (tiles wrap).
- **Type:** system sans stack
  (`-apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif`).
  Large, confident section headers; comfortable body line-height (~1.6).
- **Palette (one restrained accent + semantic status):**
  - Ink / text: `#1a1a2e` on white/`#fafafa` background.
  - Accent (headers, rules, key numbers): a deep professional blue, e.g.
    `#1f3a5f` or `#0b5fff`. Use ONE accent; do not rainbow.
  - Status: good = `#1a7f47` (green), warning/needs-improvement = `#b7791f`
    (amber), critical/poor = `#c0392b` (red). Use sparingly and only where it
    signals a decision.
  - Muted/secondary text: `#6b7280`.
- **KPI tiles:** card with subtle border/shadow, big number, small uppercase
  label, optional status dot/left-border in the status color.
- **Charts:** prefer simple **inline-SVG or pure-CSS horizontal bar charts** for
  top-N breakdowns (top URLs, referrers, errors, device split, funnel). No 3D,
  no pie charts for >3 slices, no chartjunk, no gridline clutter. Label bars with
  the value. A funnel can be stacked descending bars with step-ratio annotations.
- **Trend:** if the per-day `period` data is available, render a small inline-SVG
  sparkline/line for page views (and optionally errors) to support
  "rising/falling" claims visually. Only show a trend if data backs it.
- **Tables:** only where a ranked list is clearer than a bar chart; zebra rows,
  right-aligned numbers, no heavy borders.
- **Badges:** a small amber/grey "INFERRED — PROXY" badge on the Geographic
  section header; a small "p75" tag on CWV.

## Tone
Concise, decision-oriented, executive. Active voice. Each detail section earns
its place by ending in an explicit implication ("So what:"). Avoid hedging walls
of text; let the numbers and the action list carry the weight.

## Acceptance
- The sprinkle is registered and OPEN (`sprinkle list` shows it `[open]`), owned
  by its same-named long-lived scoop (not `optel-explorer`, not the query worker).
- The `.shtml` is full-document mode (starts with `<!DOCTYPE html>`), with NO
  hand-rolled nested iframe, no `srcdoc`, and no `readFile`. SLICC iframes it.
- Colors use S2 tokens (theme-aware); ~0 hard-coded hex; `color-scheme: light dark`
  on `:root`. The report follows the app light/dark theme.
- A rail icon is declared (`<link rel="icon" href="chart-bar" />`).
- Exec Summary + Priority Actions appear first in the report (above the fold).
- KPI tiles and CWV are color-coded by status.
- At least the top-N breakdowns (errors, referrers/acquisition, top URLs,
  device, funnel) are rendered as bar charts, not just tables.
- Geographic section is visibly badged as inferred/proxy.
- A Methodology footer is present but unobtrusive.
- The owner scoop stays alive after opening (does not idle out) to handle licks.
