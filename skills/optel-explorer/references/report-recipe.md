# OpTel Full-Report Recipe

Assembles a full narrative OpTel report (mirrors the on-screen **tools.aem.live**
sections) from `optel-query` calls. Builds on [`querying.md`](querying.md) — CLI
invocation, environment detection, domain-key lookup, `--batch` format, output
shapes, and the 413→daily fallback all live there. This file adds the report
battery, assembly, and delivery.

Pipeline: **Environment → Query → Report → Delivery.**

## 1. Environment

Identify once with `test-env` (see querying.md). The result picks the executor in
Query and the wrapper in Delivery — don't re-detect.

| Environment | Executor (Query) | Wrapper (Delivery) |
|-------------|------------------|--------------------|
| Claude Code / terminal | subagent, or run inline | standalone `.html` |
| SLICC | a disposable worker scoop | `.shtml` sprinkle + standalone `.html` |

## 2. Query

Build ONE `--batch` file (format in querying.md) covering every section below, then
a single call — not ~25 separate fetches:

```bash
optel-query $D $S $E --batch report-queries.json --output report-results.json
```

`$D` = domain, `$S`/`$E` = start/end (`YYYY-MM-DD`). Sections 1, 11, 12 are
synthesised (no query). Each batch item is a `query` filter and/or `series` /
`facetValues` (per querying.md); always include `period` for trends. The battery:

| § | Section | Batch items (`query` / `series` / `facetValues`) | Compute |
|---|---------|--------------------------------------------------|---------|
| 2 | Traffic / KPIs | count; `series: visits,bounces,engagement,earned,organic` (→`{sum}`) | bounce=`bounces/visits`, engagement=`engagement/result`, pages/visit=`result/visits` |
| 3 | Core Web Vitals | `series: lcp,cls,inp,ttfb` (→`{p75}`) | `"N/A"` = <10 samples |
| 4 | JS Errors | `checkpoint:error` count; same + `facetValues:error` | error rate = errors / total page views |
| 5 | Performance | `checkpoint:redirect`; `checkpoint:cwv-lcp` + `facetValues:cwv-lcp.source`; `checkpoint:loadresource` + `facetValues:loadresource.target`; slow bucket `loadresource.target:[500,1200]` + `facetValues:loadresource.source` | — |
| 6 | Engagement | `facetValues:userAgent`; `checkpoint:click` count + `facetValues:click.source`, `click.target`; `checkpoint:enter` + `facetValues:enter.target` | visible/hidden split from `enter.target` |
| 7 | Acquisition | `checkpoint:enter` + `facetValues:enter.source`; `facetValues:acquisitionSource`; `checkpoint:utm` + `facetValues:utm.source` | — |
| 8 | Content | `facetValues:url`; `checkpoint:language` + `facetValues:language.target` | — |
| 9 | Conversion Funnel | counts for `checkpoint:` top, click, fill, formsubmit; `checkpoint:fill` + `facetValues:fill.source` | step ratios; ⚠ fill/formsubmit may be empty in short windows — verify monthly |
| 10 | Geographic (PROXY) | `facetValues:url` (locale via URL prefixes); `checkpoint:consent` + `facetValues:consent.target` | no IP geo in RUM — badge inferred |

**Trends** — a single window has none. Facet by `period` for a per-day breakdown, or
run the same query over two adjacent windows and diff. State a direction only when
backed by multiple periods.

## 3. Report

Same content regardless of wrapper. Every number must come from the battery (show
"—" for N/A); never fabricate. No raw JSON or query commands in the body —
methodology/caveats go in a small footer.

**Structure (BLUF):**
1. **Header band** — title, domain, window, generated date.
2. **Executive Summary** — 2–3 sentence bottom line + 3 bolded key findings. First and most important.
3. **Priority Actions (at top)** — ranked: action, expected impact, reach×severity. Computed from sections 4/5/9.
4. **KPI tiles** — page views, visits, bounce rate, engagement rate, + 4 CWV; each status-colored.
5. **Detail sections**, each ending in a one-line "**So what:**": Traffic/KPIs · CWV · JS Errors · Performance · Engagement · Acquisition · Content · Funnel · Geographic (badged "INFERRED — PROXY").
6. **Footer** — methodology & caveats (sampled/weighted RUM, granularity, geo inferred, window), muted.

**Design:**
- Centered single column, max-width ~960px, system sans stack, line-height ~1.6, tiles wrap on mobile.
- One restrained accent + semantic status only (no rainbow).
- Top-N breakdowns (errors, acquisition, top URLs, device, funnel) as inline-SVG / CSS **bar charts**, labeled with values. Funnel = descending bars with step ratios. No pies/3D/chartjunk. Tables only where a ranked list beats a bar chart.
- If `period` data exists, a small inline-SVG sparkline for page views (and optionally errors).
- Tone: concise, decision-oriented, active voice.

**Write the file in ONE clean `write_file` shot** — never assemble via shell heredocs
/ `cat >>` / `sed` (that has corrupted output). No external resources (no CDN fonts,
JS libs, remote images) — system fonts + inline SVG only.

## 4. Delivery

Wrap the report per the environment from step 1:

- **Claude Code / terminal** → [`report-output-standalone.md`](report-output-standalone.md)
- **SLICC** → [`report-output-sprinkle.md`](report-output-sprinkle.md)
