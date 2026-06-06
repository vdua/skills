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
2. Run the query battery above, capturing each JSON result.
3. Compute ratios (bounce, engagement, pages/visit, error rate, funnel steps,
   visibility split).
4. Label proxies (geography) and trend caveats honestly.
5. Build the executive report markup (per the design system below) and deliver it
   as a **SPRINKLE** (see "Sprinkle Output Specification"): a separate, long-lived
   owner scoop writes the `.shtml` and opens the panel. Dispose of the query worker.

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
- **Self-contained `.shtml`.** Inline the full executive report markup directly
  into the sprinkle file — do NOT load it from an external file via `readFile`
  at runtime (the report is a finished artifact; runtime file-loading adds an
  async failure path and a staleness coupling for no benefit here).
- **CSS isolation.** The report has its own inline `<style>`. To prevent it
  clashing with the panel/global sprinkle styles, render the report markup inside
  a sandboxed `<iframe>` whose `srcdoc` contains the inlined report HTML. The
  iframe scopes the report's CSS and makes it render exactly as designed. Set the
  iframe to `width:100%`, `border:none`, and a generous height (e.g. `min-height:90vh`).
- **No external resources** in the report markup — no CDN fonts, no JS libraries,
  no remote images. System font stack and inline SVG for charts only. (This keeps
  the panel offline-safe and the same markup print/export-clean if ever exported.)
- **No raw JSON or query commands in the output.** Methodology/caveats go in a
  small footer, not the body.
- **Every number shown must come from the query battery** — never fabricate.
  Where a metric is N/A or a checkpoint is absent, show it honestly (e.g. "—").

## Optional panel chrome
A minimal panel header (title `OpTel Report — <domain> (<window>)`) above the
iframe is fine. A "Reload"/"Regenerate" control may use `slicc.lick(...)` routed
to the owner scoop — but keep chrome minimal; the report is the content.

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
- The report renders inside the panel (iframe `srcdoc`), CSS-isolated, with no
  external/remote resources and no `readFile` runtime dependency.
- Exec Summary + Priority Actions appear first in the report (above the fold).
- KPI tiles and CWV are color-coded by status.
- At least the top-N breakdowns (errors, referrers/acquisition, top URLs,
  device, funnel) are rendered as bar charts, not just tables.
- Geographic section is visibly badged as inferred/proxy.
- A Methodology footer is present but unobtrusive.
- The owner scoop stays alive after opening (does not idle out) to handle licks.
