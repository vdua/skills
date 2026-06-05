# RUM Distiller Facets Documentation

This document provides comprehensive documentation for all facets defined in `optel-query.jsh` for the optel-query skill. These facets are used to create filter objects for filtering Real User Monitoring (RUM) data from AEM Operational Telemetry.

## Overview

Facets are used to filter RUM bundles (page views). Each facet has a name that you use in filter objects. When filtering:
- Different facets are combined using logical AND (all must match)
- Multiple values for the same facet use the facet's combiner (`some` = any match, `every` = all match)
- Facets with negative support allow exclusion filters using `!facetName`

## Filter Object Structure

```javascript
// Set filter on dataChunks
dataChunks.filter = {
  "facetName": ["value1", "value2"],
  "anotherFacet": ["value"],
  "!negativeFacet": ["excludeThis"]  // Only for facets with negative support
};
```

---

## Available Facets

### 1. `url`
**Combiner**: `every` | **Negative Support**: ✅ Yes (`!url`)

**What it does**: Extracts the URL path from a bundle, sanitized to remove PII (IDs, hashes, encoded data). The URL **must** contain the domain name and scheme as well. URLs with long numbers or IDs may be grouped by pattern (e.g. `/products/12345` and `/products/67890` may appear as a single grouped pattern).

**Filter Example**:
```javascript
dataChunks.filter = {
  url: ['https://www.example.com/home', 'https://www.example.com/products', 'https://www.example.com/checkout']
};
// Matches bundles where URL is one of these paths
```

**Negative Filter Example**:
```javascript
dataChunks.filter = {
  '!url': ['https://www.example.com/admin', 'https://www.example.com/test']
};
// Excludes admin and test pages
```

---

### 2. `userAgent`
**Combiner**: `some` | **Negative Support**: ✅ Yes (`!userAgent`)

**What it does**: Extracts device type (desktop/mobile/tablet) and OS (windows/ios/android/mac/linux).

**Caveats**:
- **iOS does not report Core Web Vitals** — this is a browser limitation on iOS. Filtering by `ios` and requesting CWV series will yield no data.
- **`bot`** includes crawlers that access your site and execute JavaScript (search engines, monitoring bots). These are visible in RUM data.

**Filter Example**:
```javascript
dataChunks.filter = {
  userAgent: ['mobile', 'ios']
};
// Matches mobile devices OR iOS devices
```

**Negative Filter Example**:
```javascript
dataChunks.filter = {
  '!userAgent': ['bot']
};
// Excludes bot traffic
```

---

### 3. `checkpoint`
**Combiner**: `every` | **Negative Support**: ✅ Yes (`!checkpoint`)

**What it does**: Extracts checkpoint types (event names) that occur in a bundle.

**Available checkpoints** (validated from RUM data and [Helix RUM Enhancer](https://github.com/adobe/helix-rum-enhancer); use `--facet-values checkpoint` for your domain/date range to see what exists):
`top`, `loadresource`, `language`, `viewmedia`, `viewblock`, `cwv-ttfb`, `a11y`, `enter`, `cwv-lcp`, `missingresource`, `click`, `cwv-cls`, `utm`, `cwv-inp`, `redirect`, `fill`, `navigate`, `error`, `acquisition`, `cwv`, `back_forward`, `reload`, `formsubmit`, `login`, `signup`, `prerender`, `paid`, `email`, `404`, `search`, `consent`

**Filter Example**:
```javascript
dataChunks.filter = {
  checkpoint: ['click', 'fill']
};
// Matches bundles with click AND fill events
```

**Negative Filter Example**:
```javascript
dataChunks.filter = {
  '!checkpoint': ['error']
};
// Excludes bundles with errors
```

---

### 4. `navigate.source`
**Combiner**: `every` | **Negative Support**: ❌ No

**What it does**: Extracts the internal referrer URL — the page the user navigated from within the same site. Values are full URLs (e.g. `https://example.com/cards/credit-cards.html`). For external referrers, use `enter.source` instead.

**Filter Example**:
```javascript
dataChunks.filter = {
  'navigate.source': ['https://www.example.com/cards/credit-cards.html']
};
// Matches bundles where user navigated from the credit cards page
```

---

### 5. `enter.source`
**Combiner**: `some` | **Negative Support**: ❌ No

**What it does**: Extracts the referrer for external traffic (where users came from). Values are:
- Referrer domain URLs (e.g. `https://www.google.com/`, `https://www.hdfc.bank.in/`)
- `(direct)` — address bar, bookmarks, or iOS app links
- `android-app://` URIs (e.g. `android-app://com.google.android.gm/`) when traffic comes from Android apps

Use `acquisitionSource` (see below) for classified paid/owned/earned traffic filtering (e.g. `paid:search:google`).

**Filter Example**:
```javascript
dataChunks.filter = {
  'enter.source': ['https://www.google.com/', 'https://www.facebook.com/']
};
// Matches bundles from Google OR Facebook referrers
```

---

### 6. `loadresource.source`
**Combiner**: `some` | **Negative Support**: ❌ No

**What it does**: Identifies resources being loaded (JSON APIs, JS, CSS, HTML fragments). Values are full URLs.

**Filter Example**:
```javascript
dataChunks.filter = {
  'loadresource.source': ['https://www.example.com/libs/granite/csrf/token.json', 'https://www.example.com/eds-v1-forms/common/apiDataSecurity.js']
};
// Matches bundles loading either of these resources
```

---

### 7. `click.source`
**Combiner**: `some` | **Negative Support**: ❌ No

**What it does**: Extracts CSS selectors of clicked elements.

**Filter Example**:
```javascript
dataChunks.filter = {
  'click.source': ['.buy-button', '.add-to-cart', '.checkout-btn']
};
// Matches bundles with clicks on ANY of these elements
```

---

### 8. `click.target`
**Combiner**: `some` | **Negative Support**: ❌ No

**What it does**: Extracts destinations of clicks (href values, URLs).

**Filter Example**:
```javascript
dataChunks.filter = {
  'click.target': ['/checkout', '/cart', 'https://external-site.com']
};
// Matches bundles with clicks leading to ANY of these destinations
```

---

### 9. `viewblock.source`
**Combiner**: `every` | **Negative Support**: ❌ No

**What it does**: Extracts names/identifiers of content blocks that were viewed.

**Filter Example**:
```javascript
dataChunks.filter = {
  'viewblock.source': ['hero', 'features']
};
// Matches bundles where hero AND features blocks were viewed
```

---

### 10. `fill.source`
**Combiner**: `some` | **Negative Support**: ❌ No

**What it does**: Extracts CSS selectors of form fields that were filled.

**Filter Example**:
```javascript
dataChunks.filter = {
  'fill.source': ['input[name="email"]', 'input[name="phone"]']
};
// Matches bundles with email OR phone fields filled
```

---

### 11. `error`
**Combiner**: `some` | **Negative Support**: ✅ Yes (`!error`)

**What it does**: Extracts error details combining both source (location) and target (message) in format "source | target".

**Filter Example**:
```javascript
dataChunks.filter = {
  error: ['/scripts/main.js | TypeError', '/scripts/analytics.js | Network Error']
};
// Matches bundles with errors from specific scripts and error types
```

**Negative Filter Example**:
```javascript
dataChunks.filter = {
  '!error': ['/scripts/non-critical.js | Warning']
};
// Excludes specific errors from non-critical script
```

**Note**: Values are in format "errorSource | errorTarget". Use `--facet-values error` to discover actual error combinations in your data.

---

### 12. `loadresource.target`
**Combiner**: `some` | **Negative Support**: ❌ No

**What it does**: Extracts the load time in milliseconds for each network resource loaded via the `loadresource` checkpoint. Values are strings (e.g. `"250"`, `"1200"`). Most meaningful when scoped to a specific resource via `loadresource.source` — otherwise load times from fast CSS files and slow JS bundles are mixed together, producing a distribution that is not actionable.

**Distribution Example** (primary use case):
```bash
# Distribution of load times for a specific resource
--query '{"checkpoint":["loadresource"],"loadresource.source":["https://example.com/scripts/heavy.js"]}' --facet-values loadresource.target
```

**Threshold Example** (which resources take more than 250ms to load):
```bash
# Step 1 — check what load time values exist across all resources
--query '{"checkpoint":["loadresource"]}' --facet-values loadresource.target
# Output: {"100": 42, "250": 18, "500": 7, "1200": 3}

# Step 2 — filter to values above your threshold, then facet by source to see which resources are slow
--query '{"checkpoint":["loadresource"],"loadresource.target":["500","1200"]}' --facet-values loadresource.source
```
**Note**: There is no `>250` operator — values are exact-matched strings. Pick the values above your threshold from step 1 and enumerate them in step 2.

**Time-series Example** (load time distribution per day for a specific resource):
```bash
# See how load time distribution changes over the date range
--query '{"checkpoint":["loadresource"],"loadresource.source":["https://example.com/scripts/heavy.js"]}' --facet-values period
# Then drill into a specific day:
--query '{"checkpoint":["loadresource"],"loadresource.source":["https://example.com/scripts/heavy.js"],"period":["2024-03-15"]}' --facet-values loadresource.target
```

---

### 13. `viewmedia.target`
**Combiner**: `some` | **Negative Support**: ❌ No

**What it does**: Extracts URLs of viewed media (images/videos), cleaned of query parameters.

**Filter Example**:
```javascript
dataChunks.filter = {
  'viewmedia.target': ['/images/hero.jpg', '/videos/demo.mp4']
};
// Matches bundles where these media files were viewed
```

---

### 14. `missingresource.source`
**Combiner**: `some` | **Negative Support**: ❌ No

**What it does**: Extracts the URL of each resource that failed to load (any HTTP error — 404, 405, 500, etc.).

**Use with**: `checkpoint: ['missingresource']` filter to scope to failure events; pair with `missingresource.target` to filter by a specific HTTP status code.

**Filter Example**:
```javascript
dataChunks.filter = {
  checkpoint: ['missingresource'],
  'missingresource.source': ['/api/v1/loan-apply', '/api/otp/send']
};
// Page views where these specific endpoints failed to load
```

**Facet-values Example**:
```bash
# enumerate all resource URLs that failed on a page
--query '{"checkpoint":["missingresource"],"url":["https://example.com/page"]}' --facet-values missingresource.source
```

---

### 15. `missingresource.target`
**Combiner**: `some` | **Negative Support**: ❌ No

**What it does**: Extracts the HTTP status code returned when a resource failed to load (e.g. `"404"`, `"405"`, `"500"`).

**Use with**: `checkpoint: ['missingresource']` filter; pair with `missingresource.source` to also identify which URLs are affected.

**Filter Example**:
```javascript
dataChunks.filter = {
  checkpoint: ['missingresource'],
  'missingresource.target': ['405']
};
// Page views where any resource returned HTTP 405
```

**Facet-values Example**:
```bash
# see the breakdown of HTTP status codes across all failed resources
--query '{"checkpoint":["missingresource"],"url":["https://example.com/page"]}' --facet-values missingresource.target
```

---

### 16. `acquisitionSource`
**Combiner**: `some` | **Negative Support**: ❌ No

**What it does**: Classifies how visitors were acquired — paid, owned, or earned — by reclassifying `paid`, `email`, and `utm` checkpoints into a unified hierarchical source string. Format: `{paidOwned}:{category}:{vendor}`. Each level is returned as a separate value so you can filter at any granularity.

**Sources captured**:
- `paid` checkpoint (ad click IDs): always classified as `paid`
- `utm` checkpoint (`utm_source` / `utm_medium`): classified by UTM value
- `email` checkpoint (mailchimp/marketo): classified as `owned:email`

**Not captured**: purely organic/direct traffic with no UTM params or click IDs.

**Value hierarchy** (all returned for one event, filter at any level):
| Value | Meaning |
|---|---|
| `paid` | Any paid traffic |
| `paid:search` | Paid search (any vendor) |
| `paid:search:google` | Paid Google search |
| `paid:social:facebook` | Paid Facebook |
| `paid:display:microsoft` | Microsoft display |
| `owned:email:marketo` | Marketo email |
| `owned:email:mailchimp` | Mailchimp email |

Supported vendors: `google`, `instagram`, `facebook`, `bing`, `tiktok`, `youtube`, `linkedin`, `x` (twitter), `snapchat`, `microsoft`, `pinterest`, `reddit`, `spotify`, `criteo`, `taboola`, `outbrain`, `yahoo`, `marketo`, `eloqua`, `substack`, `yandex`, `baidu`, `amazon`, `chatgpt`, `perplexity`.

**Filter Example**:
```javascript
dataChunks.filter = {
  acquisitionSource: ['paid']
};
// Matches all paid traffic (any channel, any vendor)
```

```javascript
dataChunks.filter = {
  acquisitionSource: ['paid:social']
};
// Matches paid social traffic (facebook, linkedin, etc.)
```

```javascript
dataChunks.filter = {
  acquisitionSource: ['owned:email:marketo']
};
// Matches Marketo email campaigns specifically
```

---

## Understanding Combiners

Each facet uses a combiner strategy that determines how multiple filter values are matched:

- **`some`** (OR logic): Bundle matches if it satisfies **ANY** of the filter values
- **`every`** (AND logic): Bundle matches only if it satisfies **ALL** filter values

**Examples**:
```javascript
// 'some' combiner - matches if ANY value matches
dataChunks.filter = {
  'click.target': ['/checkout', '/cart']  // click goes to /checkout OR /cart
};

// 'every' combiner - matches if ALL values match
dataChunks.filter = {
  'viewblock.source': ['hero', 'features']  // hero AND features were viewed
};
```

**Important**: When combining different facets, they use AND logic:
```javascript
dataChunks.filter = {
  userAgent: ['mobile'],           // mobile OR tablet (some)
  checkpoint: ['click', 'fill'],   // click AND fill (every)
  url: ['https://www.example.com/checkout']               // /checkout (every)
};
// Matches: (mobile) AND (click AND fill) AND (/checkout)
```

---

## Common Filter Patterns

### Pattern 1: Mobile users who clicked conversion buttons
```javascript
dataChunks.filter = {
  userAgent: ['mobile'],
  'click.source': ['.buy-button', '.add-to-cart', '.checkout-btn']
};
// Matches: mobile users who clicked ANY conversion button
```

### Pattern 2: Form interactions without errors
```javascript
dataChunks.filter = {
  checkpoint: ['fill'],
  '!checkpoint': ['error']
};
// Matches: bundles with form fills AND no errors
```

### Pattern 3: Specific page engagement
```javascript
dataChunks.filter = {
  url: ['https://www.example.com/products/shoes'],
  checkpoint: ['viewblock', 'click', 'viewmedia']
};
// Matches: /products/shoes with viewblock AND click AND viewmedia
```

### Pattern 4: Traffic source analysis
```javascript
dataChunks.filter = {
  'enter.source': ['https://www.google.com/', 'https://www.facebook.com/']
};
// Matches: visits referred from Google OR Facebook (some combiner = OR)
```

### Pattern 5: Error monitoring on checkout
```javascript
dataChunks.filter = {
  url: ['https://www.example.com/checkout', 'https://www.example.com/payment'],
  checkpoint: ['error'],
  error: ['/scripts/payment.js | TypeError', '/scripts/payment.js | Network Error']
};
// Matches: checkout/payment pages with specific errors from payment script
```

### Pattern 6: Content block engagement
```javascript
dataChunks.filter = {
  'viewblock.source': ['hero', 'features', 'testimonials'],
  'click.source': ['.cta-button']
};
// Matches: viewed ALL three blocks AND clicked CTA
```

### Pattern 7: Media interaction tracking
```javascript
dataChunks.filter = {
  'viewmedia.target': ['/images/product-hero.jpg'],
  'click.target': ['/products/details']
};
// Matches: viewed product image AND clicked to details page (cross-facet = AND)
```

### Pattern 8: Exclude bot traffic and test pages
```javascript
dataChunks.filter = {
  '!userAgent': ['bot'],
  '!url': ['https://www.example.com/test', 'https://www.example.com/staging']
};
// Excludes: bots and test/staging pages
```

---

## Quick Reference: Facets by Category

### Page & Device
- `url` - Absolute page Path (every, negative ✅)
- `userAgent` - Device type & OS (some, negative ✅)

### Events
- `checkpoint` - Event types (every, negative ✅)

### Navigation & Traffic
- `navigate.source` - Internal referrer URL (page navigated from) (every)
- `enter.source` - External referrers/traffic sources (some)

### Resources
- `loadresource.source` - Loaded resources (some)
- `loadresource.target` - Load time of network resource in ms, e.g. `"250"` (some)
- `missingresource.source` - Failed resource URLs (some)
- `missingresource.target` - HTTP status of failed resources, e.g. `"404"`, `"405"` (some)

### User Interactions
- `click.source` - Clicked elements (some)
- `click.target` - Click destinations (some)
- `viewblock.source` - Viewed content blocks (every)
- `viewmedia.target` - Viewed media (some)
- `fill.source` - Filled form fields (some)

### Errors
- `error` - Error details (source | target) (some, negative ✅)

---

## Best Practices for Creating Filters

1. **Understand Combiners**: Know whether your facet uses `some` (OR) or `every` (AND) logic
2. **Use Negative Facets**: Only 4 facets support negatives: `url`, `userAgent`, `checkpoint`, `error`
3. **Combine Facets**: Different facets are AND'ed together for precise targeting
4. **Check Checkpoint First**: Use `checkpoint` to verify events exist before filtering by their source/target
5. **Consider Performance**: Broad filters process faster; start general then refine
6. **Remember Weights**: Bundles have sampling weights; use totals for accurate metrics

---

## Related Documentation

- **AEM Operational Telemetry**: https://www.aem.live/docs/operational-telemetry
- **AEM Developer – Operational Telemetry**: https://www.aem.live/developer/operational-telemetry
- **RUM Distiller README**: https://github.com/adobe/rum-distiller/blob/main/README.md
- **RUM Distiller API**: https://github.com/adobe/rum-distiller/blob/main/API.md
- **Checkpoint details**: See `checkpoints.md` for full checkpoint source/target semantics

