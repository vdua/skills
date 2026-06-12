# Querying RUM Data (optel-query)

Translates natural language into structured RUM queries and executes them via the bundled `optel-query.jsh` script. Works in both Claude Code (Node 18+) and SLICC.

## Required References

Load on demand — do not load all at once:
- [`facets.md`](facets.md) — all facets, combiners, negation support, examples
- [`checkpoints.md`](checkpoints.md) — all checkpoint types and source/target properties
- [`series.md`](series.md) — available metrics series (lcp, cls, inp, ttfb, formBlockLoadTime, timeOnPage)
- [`examples.md`](examples.md) — worked examples for common patterns

## Workflow

```
User Question → [1] Parse → [2] Build Query → [3] Execute → [4] Analyze → [5] Answer
                                  ↓                ↓
                          [checkpoints.md]    [facets.md]
```

## Running the Script

**Detect environment first** — `command -v optel-query` exits 0 in SLICC (registered by basename, no extension); exits 1 in Claude Code / plain Node.

**SLICC:** `optel-query <domain> <startDate> <endDate> [options]`
**Node:**  `node skills/optel-explorer/scripts/optel-query.jsh <domain> <startDate> <endDate> [options]`

Pass `--domainkey <key>` to supply a domain key directly, bypassing file and env var lookup. Useful when a key is already known (e.g. `"open"` for public domains).

## Domain Key Setup

The script looks up the domain key in this order: `--domainkey` flag → `DOMAINKEY_FILE` env var → `/optel/domainkey.json` (SLICC VirtualFS default) → `RUM_ADMIN_KEY` admin fetch.

If a domain key is missing, use the **Key Management** workflow in the main `SKILL.md` (the OpTel Explorer sprinkle) to add or generate one. Open the sprinkle with `sprinkle open optel-explorer` and tell the user to add the key there.

**Never read `/optel/domainkey.json` or any file referenced by `DOMAINKEY_FILE`** — doing so would pull live credentials into conversation context. The script reads these files itself at runtime.

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
- **Many questions over the SAME window → use `--batch <file>` (one fetch).** Each CLI call re-fetches the whole window; for multi-question workloads (e.g. reports) put all questions in a JSON array `[{id?, query?, facetValues?, series?}, ...]` and run `optel-query <domain> <s> <e> --batch <file>`. The window is fetched and parsed once; every request is answered in-memory. Output: `{result:{results:[{id,type,result|error},...]}}` in input order. A bad request is reported per-item and does not abort the batch. Each item: `facetValues` ⇒ a facet request, `series` ⇒ a metrics request, else a plain count; `query` adds filters.
  ```json
  [
    { "id": "pageviews" },
    { "id": "kpis", "series": ["visits","bounces","engagement"] },
    { "id": "errors", "query": {"checkpoint":["error"]} },
    { "id": "error-sources", "query": {"checkpoint":["error"]}, "facetValues": "error" },
    { "id": "top-urls", "facetValues": "url" }
  ]
  ```
- Always persist output with `--output` under `output/<domain>-<startDate>-<endDate>-<short-slug>/`.
- For errors: follow the error-analysis workflow in [`error-analysis.md`](error-analysis.md) (mandatory when reporting errors to the user).
- Do not modify existing code; stop and report if a change would be needed.
- Do not fabricate data; report only what the script returns.

## Granularity (`--interval`)

- **Default: omit `--interval`.** The loader auto-selects — hourly for ≤7-day ranges, daily for ≤31-day ranges, monthly for longer. Correct for almost every question.

### High-traffic domains and HTTP 413 (auto-fallback)

Very high-traffic domains (e.g. `www.adobe.com`) can exceed the bundles API size limit on the **hourly** endpoint, which returns **HTTP 413 (Payload Too Large)**. The loader handles this automatically: when **any hour of a day** returns 413 in hourly mode, it transparently **refetches that whole day at daily granularity** (the daily endpoint is served pre-aggregated and stays under the limit). You get correct data with no flags.

Other non-OK HTTP statuses (401/403 → bad/expired key; 5xx → server error; network/parse failures) are now **surfaced as errors** rather than silently returning empty results. A genuinely empty date range still returns `result: 0` with no error — so "0 with no error" now reliably means "no data," not "a request failed."
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
| Clicks | `checkpoint` + relevant `*.source` only | Extra unneeded checkpoints |
| Performance (CWV) | `--series lcp,cls,inp` + matching checkpoint filter | `--series` for count-only questions |

## Facets vs Series

- **Facets** = filter/group — define *which* bundles are included
- **Series** = metrics — define *what* data to return (count, LCP p75, CLS p75, form load time, time on page, etc.)
- Default: only `pageViews` (count). Add `--series` only when the user asks for metrics beyond page-view counts.

---

## Step 1: Extract Dates

1. Run `date +%Y-%m-%d` — never hardcode today's date
2. Default end date: **yesterday** (avoid incomplete same-day data)
3. Default range: **last 30 days** when not specified
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

**Consult `checkpoints.md` for checkpoint types, `facets.md` for facet properties.**

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

**Verify every facet's combiner in `facets.md` before using it.**

Combiner types:
- **`some` (OR within values)**: `userAgent`, `error`, `click.source`, `click.target`, `fill.source`, `loadresource.source`, `loadresource.target`, `viewmedia.target`, `enter.source`, `missingresource.source`, `missingresource.target`, `acquisitionSource`
- **`every` (AND within values)**: `url`, `checkpoint`, `navigate.source`, `viewblock.source`

**Important caveats:**
- iOS traffic does not report Core Web Vitals (browser limitation). Filtering by iOS + requesting CWV series yields no data.
- `enter.source` values are referrer URLs (e.g. `https://www.google.com/`), `(direct)` for address bar/bookmarks/iOS apps, or `android-app://` URIs. For classified sources like `paid:search:google`, use `acquisitionSource` instead.

Different facets always combine with **AND** across each other.

**Negation** — only 4 facets support it: `!url`, `!userAgent`, `!checkpoint`, `!error`

Common patterns:
```json
{"userAgent": ["mobile"], "url": ["https://www.example.com/checkout"]}
{"checkpoint": ["fill"], "!checkpoint": ["formsubmit"]}
{"checkpoint": ["enter"], "enter.source": ["https://www.google.com/"]}
{"checkpoint": ["error"], "url": ["https://www.example.com/payment"]}
{"checkpoint": ["cwv-lcp"]}
```

---

## Step 3b: Select Series

Consult `series.md`. Add `--series` only when the user asks for metrics beyond page-view counts:
- Core Web Vitals → `--series lcp,cls,inp`
- Form load time → `--series formBlockLoadTime`
- Time on page → `--series timeOnPage`
- TTFB → `--series ttfb`

Note: series names are `lcp`, `cls`, `inp` (not `cwv-lcp` etc. — those are checkpoint names, not series names).

---

## Step 4: Validate Before Generating CLI

- [ ] Dates in `YYYY-MM-DD`, startDate ≤ endDate
- [ ] All facet names exist in `facets.md`
- [ ] Combiners verified per facet
- [ ] Negation only on supported facets
- [ ] JSON syntax valid

---

## Quick Reference: Common Filters

```bash
--query '{"userAgent":["mobile"]}'
--query '{"url":["https://www.example.com/checkout"]}'
--query '{"checkpoint":["click"],"click.source":[".buy-button"]}'
--query '{"checkpoint":["error"]}'
--query '{"checkpoint":["fill"],"!checkpoint":["formsubmit"]}'
--query '{"checkpoint":["enter"],"enter.source":["https://www.google.com/"]}'
--query '{"checkpoint":["cwv-lcp"]}' --series lcp,cls,inp
```

---

- **DO NOT** guess facet names — check `facets.md`
- **DO NOT** assume checkpoint properties — verify in `checkpoints.md`
- **DO** consult examples in `examples.md` before constructing queries
