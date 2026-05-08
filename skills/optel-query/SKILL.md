---
name: optel-query
description: Use when analyzing RUM (Real User Monitoring) data from AEM Operational Telemetry — page views, clicks, errors, Core Web Vitals (LCP/CLS/INP), form fills, traffic sources, or any analytics question about a domain. Builds structured queries and executes them via the bundled optel-query.jsh script.
---

# Optel-Query Skill

Translates natural language into structured RUM queries and executes them via the bundled `optel-query.jsh` script. Works in both Claude Code (Node 18+) and SLICC.

## Required References

Load on demand — do not load all at once:
- [`references/facets.md`](references/facets.md) — all 13 facets, combiners, negation support, examples
- [`references/checkpoints.md`](references/checkpoints.md) — all checkpoint types and source/target properties
- [`references/series.md`](references/series.md) — available metrics series (lcp, cls, inp, ttfb, formBlockLoadTime, timeOnPage)
- [`references/examples.md`](references/examples.md) — worked examples for common patterns

## Workflow

```
User Question → [1] Parse → [2] Build Query → [3] Execute → [4] Analyze → [5] Answer
                                  ↓                ↓
                          [checkpoints.md]    [facets.md]
```

## Running the Script

**Detect environment first** — `command -v optel-query` exits 0 in SLICC (registered by basename, no extension); exits 1 in Claude Code / plain Node.

**SLICC:** `optel-query <domain> <startDate> <endDate> [options]`
**Node:**  `node skills/optel-query/scripts/optel-query.jsh <domain> <startDate> <endDate> [options]`

Pass `--domainkey <key>` to supply a domain key directly, bypassing `DOMAINKEY_FILE` and `RUM_ADMIN_KEY` lookup. Useful when a key is already known (e.g. `"open"` for public domains) or when env vars are unavailable.

**Output shapes:**

Default (count):
```json
{"result": {"result": 2110, "samplingRatios": {"100": 10, "10": 11, "1": 1000}}}
```

With `--series`:
```json
{"result": {"result": 2110, "series": {"lcp": {"p75": "1.25s"}, "cls": {"p75": "0.05"}}}}
```

With `--facet-values`:
```json
{"result": {"facetValues": [{"value": "/home", "count": 523}], "totalPageViews": 1234, "filteredPageViews": 1066}}
```

`result.result` is the **weighted** page-view total (sum of bundle sampling weights), not a raw bundle count.

## Execution Rules

- Issue a single CLI call per distinct question. Do not split date windows in the agent — the loader handles long ranges via internal chunking.
- Always persist output with `--output` under `output/<domain>-<startDate>-<endDate>-<short-slug>/`.
- For errors: invoke the `optel-analyze-errors` skill (mandatory when reporting errors to the user).
- Do not modify existing code; stop and report if a change would be needed.
- Do not fabricate data; report only what the script returns.

## Granularity (`--interval`)

- **Default: omit `--interval`.** The loader auto-selects — hourly for ≤7-day ranges, daily for ≤31-day ranges, monthly for longer. Correct for almost every question.
- **Pass `--interval hourly`** only on explicit user cues that reject downsampling:
  - "don't downsample" / "no downsampling"
  - "use full data" / "full fidelity"
  - "all bundles" / "every bundle"
- Do **not** infer `--interval hourly` from comparison contexts or from phrases like "accurate counts" or "exact numbers". RUM bundles are already 1-in-100 sampled at collection, so those framings are misleading.
- `--interval daily` / `--interval monthly` are exposed on the CLI for power users but have no natural-language mapping — auto-selection already picks them for the matching ranges.

## Minimal Scope (mandatory)

Build the **smallest** query that answers the question. Expand only when the user asks for more.

| User intent | Prefer | Avoid by default |
|-------------|--------|-----------------|
| Traffic volume | Count query or `--facet-values url` | Multi-checkpoint filters |
| Referrers / sources | `--facet-values enter.source` + `checkpoint:["enter"]` | Unrelated checkpoints |
| Errors | `--facet-values error` + `checkpoint:["error"]` | Unfiltered error facet |
| Clicks / forms | `checkpoint` + relevant `*.source` only | Extra unneeded checkpoints |
| Performance (CWV) | `--series lcp,cls,inp` + matching checkpoint filter | `--series` for count-only questions |

## Facets vs Series

- **Facets** = filter/group — define *which* bundles are included
- **Series** = metrics — define *what* data to return (count, LCP p75, CLS p75, etc.)
- Default: only `pageViews` (count). Add `--series` only when the user asks for performance numbers.

---

## Step 1: Extract Dates

1. Run `date +%Y-%m-%d` — never hardcode today's date
2. Default end date: **yesterday** (avoid incomplete same-day data)
3. Default range: **last 7 days** when not specified
4. Format: always `YYYY-MM-DD`

| User says | Interpretation |
|-----------|----------------|
| "yesterday" | Previous day |
| "last week" | 7 days ending yesterday |
| "this month" | 1st of current month through yesterday |
| "last month" | Full previous calendar month |
| "Q1 2024" | Jan 1 – Mar 31, 2024 |
| "in the last 30 days" | 30 days ago through yesterday |

---

## Step 2: Identify Intent

**Consult `references/checkpoints.md` for checkpoint types, `references/facets.md` for facet properties.**

| Intent | Keywords | Primary Facets |
|--------|----------|----------------|
| Page Analysis | "page", "URL", "/home" | `url` |
| Device/Platform | "mobile", "desktop", "iOS", "Android" | `userAgent` |
| Traffic Sources | "from Google", "referrer", "traffic source" | `enter.source` |
| Clicks | "clicks", "interactions", "button" | `checkpoint`, `click.source` |
| Forms | "form", "signup", "submission", "fills" | `fill.source`, `formsubmit` |
| Content | "viewed", "scrolled to", "content blocks" | `viewblock.source` |
| Performance | "LCP", "CLS", "INP", "slow", "load time" | `checkpoint` (cwv-lcp, cwv-cls, cwv-inp) + `--series` |
| Errors | "errors", "404", "broken" | `checkpoint` (error, 404) |
| Conversions | "checkout", "purchase", "buy" | Multiple facets for funnel |

---

## Step 3: Construct Query Object

**Verify every facet's combiner in `references/facets.md` before using it.**

Combiner types:
- **`some` (OR within values)**: `userAgent`, `error`, `click.source`, `click.target`, `fill.source`, `loadresource.source`, `viewmedia.target`, `enter.source`
- **`every` (AND within values)**: `url`, `checkpoint`, `navigate.source`, `viewblock.source`

Different facets always combine with **AND** across each other.

**Negation** — only 4 facets support it: `!url`, `!userAgent`, `!checkpoint`, `!error`

Common patterns:
```json
{"userAgent": ["mobile"], "url": ["/checkout"]}
{"checkpoint": ["fill"], "!checkpoint": ["formsubmit"]}
{"checkpoint": ["enter"], "enter.source": ["search:google"]}
{"checkpoint": ["error"], "url": ["/payment"]}
{"checkpoint": ["cwv-lcp"]}
```

---

## Step 3b: Select Series

Consult `references/series.md`. Add `--series` only when the user asks for metrics:
- Core Web Vitals → `--series lcp,cls,inp`
- Form load time → `--series formBlockLoadTime`
- Time on page → `--series timeOnPage`

---

## Step 4: Validate Before Generating CLI

- [ ] Dates in `YYYY-MM-DD`, startDate ≤ endDate
- [ ] All facet names exist in `references/facets.md` (only 13 valid facets)
- [ ] Combiners verified per facet
- [ ] Negation only on supported facets
- [ ] JSON syntax valid

---

## Quick Reference: Common Filters

```bash
--query '{"userAgent":["mobile"]}'
--query '{"url":["/checkout"]}'
--query '{"checkpoint":["click"],"click.source":[".buy-button"]}'
--query '{"checkpoint":["error"]}'
--query '{"checkpoint":["fill"],"!checkpoint":["formsubmit"]}'
--query '{"checkpoint":["enter"],"enter.source":["search:google"]}'
--query '{"checkpoint":["cwv-lcp"]}' --series lcp,cls,inp
```

---

- **DO NOT** guess facet names — check `references/facets.md`
- **DO NOT** assume checkpoint properties — verify in `references/checkpoints.md`
- **DO** consult examples in `references/examples.md` before constructing queries
