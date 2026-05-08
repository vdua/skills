# RUM Distiller Series Documentation

This document describes **series** as used in the optel-query skill and RUM Distiller. Series define numeric metrics computed per bundle; they are used for aggregations (totals) such as counts, sums, means, and percentiles.

## Overview

A **series** is a way to extract a metric from a bundle. Values of a series are always **numeric** and allow calculations such as:

- **Counting** (e.g. number of page views)
- **Summing** (e.g. total weight)
- **Averaging** (e.g. mean time on page)
- **Min / max / median / percentiles**

Conceptually, a series is a **function that takes a bundle and returns a number** (or `undefined` if the bundle does not contribute to that metric). Once a series is registered with `DataChunks.addSeries(name, seriesValueFn)`, the **totals** view computes aggregates over the filtered bundles for each series.

**References:**

- What is a series: [RUM Distiller README – Series](https://github.com/adobe/rum-distiller/blob/main/README.md#series)
- Available series in RUM Distiller: [RUM Distiller API – series](https://github.com/adobe/rum-distiller/blob/main/API.md#module_series)

---

## Totals (Aggregates)

When you filter data and read `dataChunks.totals`, each **registered series** has an aggregate object with:

| Property     | Description                          |
|--------------|--------------------------------------|
| `count`      | Number of bundles with a value       |
| `sum`        | Sum of series values                 |
| `mean`       | Average value                        |
| `min`        | Minimum value                        |
| `max`        | Maximum value                        |
| `median`     | Median value                         |
| `stddev`     | Standard deviation                   |
| `percentile(p)` | Arbitrary percentile (e.g. 50, 90, 99) |

Example (conceptually; the query CLI exposes **`pageViews.sum`** as the main **`result`**, and weighted totals for **`--facet-values`**):

```javascript
dataChunks.filter = { url: ['/home'], checkpoint: ['cwv-lcp'] };
const totals = dataChunks.totals;
// totals.pageViews.sum   → total page views
// totals.timeOnPage.mean → average time on page (if series registered)
```

---

## Series in RUM Distiller (API)

These are the **built-in series** provided by `@adobe/rum-distiller`. Not all are registered in this project’s `optel-query.jsh`; see “Series registered in this project” below.

| Series         | Returns | Description |
|----------------|--------|-------------|
| **pageViews**  | `number` | Count of page views (impressions). Pre-rendering is counted as a page view. |
| **visits**     | `number` | Count of visits (page view that does not follow an internal link; session start). |
| **bounces**    | `number` | Count of bounces (visit with no click events). |
| **lcp**        | `number` | Largest Contentful Paint (time for largest contentful element to load). |
| **cls**        | `number` | Cumulative Layout Shift (sum of layout shifts in the page view). |
| **inp**        | `number` | Interaction to Next Paint (time to next paint after an interaction). |
| **ttfb**       | `number` | Time to First Byte. |
| **engagement** | `number` | Count of “engaged” page views (at least some interaction or 4+ viewmedia/viewblock events). |
| **earned**     | `number` | Count of earned visits (not paid or owned). |
| **organic**    | `number` | Count of organic visits (not paid). |

---

## Series Registered in This Project

The following series are **actually added** inside `skills/optel-query/scripts/optel-query.jsh` (the data-chunks setup logic, previously in `datachunks.js`, is now inlined into that single script). Only these contribute to `dataChunks.totals` in the optel-query pipeline. **Keep this section in sync with `optel-query.jsh`** when new series are added (e.g. `dataChunks.addSeries(...)`).

### 1. `pageViews`

**Source**: `@adobe/rum-distiller` → `series.pageViews`

**What it does**: Counts page views (one per bundle). This is the primary series used for “how many page views match this filter.”

**Totals**: `dataChunks.totals.pageViews` has `count`, `sum`, `mean`, etc. The **query CLI** currently returns only `totals.pageViews.sum` as the query result.

**Use when**: User asks for counts of page views, visitors (as page views), or matching bundles.

---

### 2. `formBlockLoadTime`

**Source**: `skills/optel-query/scripts/optel-query.jsh` → `formBlockLoadTime()` (inlined from the former `facets.js`)

**What it does**: Returns the time (ms) for the form block to load in the bundle, using a configurable threshold. Bundles without a form load event do not contribute (undefined).

**Totals**: `dataChunks.totals.formBlockLoadTime` provides `mean`, `min`, `max`, etc. for form load time.

**Use when**: User asks about form load performance or time to form ready.

---

### 3. `timeOnPage`

**Source**: `skills/optel-query/scripts/optel-query.jsh` → `timeOnPage` (inlined from the former `series.js`)

**What it does**: Returns the maximum positive `timeDelta` among events in the bundle, in **seconds**. Represents the latest event time on the page. Returns `undefined` if there are no positive deltas.

**Totals**: `dataChunks.totals.timeOnPage` provides `mean`, `min`, `max`, etc. for time on page.

**Use when**: User asks about average or distribution of time spent on page.

---

### 4. `lcp`

**Source**: `@adobe/rum-distiller` → `series.lcp`

**What it does**: Largest Contentful Paint — time for the largest contentful element to load (in ms).

**Totals**: `dataChunks.totals.lcp` provides `count`, `sum`, `mean`, `min`, `max`, `percentile(p)` for LCP.

**Use when**: User asks about LCP, largest contentful paint, or Core Web Vitals load performance.

---

### 5. `cls`

**Source**: `@adobe/rum-distiller` → `series.cls`

**What it does**: Cumulative Layout Shift — sum of layout shifts in the page view (unitless score).

**Totals**: `dataChunks.totals.cls` provides `count`, `sum`, `mean`, `min`, `max`, `percentile(p)` for CLS.

**Use when**: User asks about CLS, layout shift, or visual stability (Core Web Vitals).

---

### 6. `inp`

**Source**: `@adobe/rum-distiller` → `series.inp`

**What it does**: Interaction to Next Paint — time to next paint after an interaction (in ms).

**Totals**: `dataChunks.totals.inp` provides `count`, `sum`, `mean`, `min`, `max`, `percentile(p)` for INP.

**Use when**: User asks about INP, responsiveness, or interaction latency (Core Web Vitals).

---

### 7. `ttfb`

**Source**: `@adobe/rum-distiller` → `series.ttfb`

**What it does**: Time to First Byte — time until the browser receives the first byte of the page response (in ms). Thresholds: good < 800ms, needs improvement 800–1800ms, poor > 1800ms.

**Totals**: `dataChunks.totals.ttfb` provides `count`, `sum`, `mean`, `min`, `max`, `percentile(p)` for TTFB.

**Use when**: User asks about TTFB, server response time, or backend latency (Core Web Vitals). Pair with `checkpoint: ['cwv-ttfb']` filter to restrict to bundles that have a TTFB measurement.

---

## Summary

- **Series** = numeric metric per bundle; registered with `addSeries(name, seriesValueFn)`.
- **Totals** = aggregates (count, sum, mean, min, max, median, stddev, percentiles) per series over filtered bundles.
- **RUM Distiller** provides: pageViews, visits, bounces, lcp, cls, inp, ttfb, engagement, earned, organic.
- **This project** registers: **pageViews**, **formBlockLoadTime**, **timeOnPage**, **lcp**, **cls**, **inp**, **ttfb**. The query CLI returns the weighted **pageViews** sum as the main numeric **`result`**, always includes **`samplingRatios`** (how many samples at each weight: 100 = 1 per 100 views, 10 = 1 per 10, 1 = full sampling), and can include other **series** via `--series` (e.g. `lcp`, `cls`, `inp`, `ttfb`, `formBlockLoadTime`). **`--facet-values`** responses use weighted totals for **`totalPageViews`**, **`filteredPageViews`**, and **`facetValues[].count`**, and include **`samplingRatios`** / **`filteredSamplingRatios`** plus per-row **`facetValues[].samplingRatios`** for **bundle counts** per weight.

When building queries, use **facets** to filter; series determine which **metrics** appear under **`series`** in the CLI output. The primary traffic number is always the weighted pageViews total plus the sampling breakdown.

---

## Related Documentation

- **RUM Distiller README (Concepts – Series)**: https://github.com/adobe/rum-distiller/blob/main/README.md#series
- **RUM Distiller API (series module)**: https://github.com/adobe/rum-distiller/blob/main/API.md#module_series
- **RUM Distiller API (DataChunks, totals)**: https://github.com/adobe/rum-distiller/blob/main/API.md#DataChunks+totals
- **Facets (filtering)**: See `facets.md` in this skill
