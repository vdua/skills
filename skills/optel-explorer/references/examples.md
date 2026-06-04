# Create-Query Examples (Facets + Series)

This file contains worked examples that use **facets** (grouping/filtering) and **series** (which metrics to return). Default is count only (`pageViews`); when the user asks for performance numbers, form load time, or similar, add the appropriate series and `--series` to the CLI command.

---

## Example 1: Weekly trend of CWV numbers for a domain

**User question:** What is the weekly trend of CWV numbers for the domain example.com from date1 to date2?

**Intent:** Performance (Core Web Vitals). User wants LCP, INP, CLS metrics over time for the whole domain (no URL or device filter). For a “weekly trend” you typically run one query per week and aggregate; each query returns the requested series for that week.

**Output:**

| Field | Value |
|-------|--------|
| domain | example.com |
| startDate | date1 (YYYY-MM-DD) |
| endDate | date2 (YYYY-MM-DD) |
| query | `{}` (no facet filter; entire domain) or restrict by checkpoint if needed, e.g. `{"checkpoint":["cwv-lcp","cwv-cls","cwv-inp"]}` |
| series | `['lcp', 'inp', 'cls']` |

**CLI command:**
```bash
node cli.js example.com <date1> <date2> --series lcp,inp,cls
```

If you need a filter so only bundles with CWV data are included:
```bash
node cli.js example.com <date1> <date2> --query '{"checkpoint":["cwv-lcp","cwv-cls","cwv-inp"]}' --series lcp,inp,cls
```

**Note:** For a true “weekly trend”, run this once per week (each week ≤ 7 days) and combine the results (e.g. in a script or report). The date range limit (max 7 days per query) applies.

---

## Example 2: How much time it takes to load a form for a specific URL

**User question:** How much time it takes to load a form for the URL a.com/xyz?

**Intent:** Form load performance for one URL. Restrict by `url` facet and request the `formBlockLoadTime` series.

**Output:**

| Field | Value |
|-------|--------|
| domain | example.com (or the domain that hosts a.com/xyz; clarify if “a.com” is the domain) |
| startDate | date1 (YYYY-MM-DD) |
| endDate | date2 (YYYY-MM-DD) |
| query | `{"url": ["https://example.com/xyz"]}` (use the full URL or the sanitized path returned by the URL facet for that page) |
| series | `['formBlockLoadTime']` |

**CLI command:**
```bash
node cli.js example.com <date1> <date2> --query '{"url":["https://example.com/xyz"]}' --series formBlockLoadTime
```

**Note:** The `url` facet value must match what the RUM pipeline exposes (often full URL or path; see `facets.md`). If the user said “a.com/xyz”, the domain might be `a.com` and the path `/xyz`; then:
```bash
node cli.js a.com <date1> <date2> --query '{"url":["https://a.com/xyz"]}' --series formBlockLoadTime
```
Use the create-query skill’s URL normalization and facet documentation to choose the correct `url` value.
