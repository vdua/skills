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

**What it does**: Extracts the URL path from a bundle, sanitized to remove PII (IDs, hashes, encoded data). The URL **must** contain the domain name and scheme as well.

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

**What it does**: Extracts the element/link that triggered navigation (CSS selector or identifier).

**Filter Example**:
```javascript
dataChunks.filter = {
  'navigate.source': ['.nav-menu a', '.cta-button']
};
// Matches bundles where navigation came from nav menu AND CTA button
```

---

### 5. `enter.source`
**Combiner**: `some` | **Negative Support**: ❌ No

**What it does**: Extracts raw referrer URLs (where users came from). Values are full URLs, e.g. `https://www.google.com/`. Use `acquisitionSource` (see below) for classified paid/owned traffic filtering.

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

**What it does**: Identifies resources being loaded (CSS, JS, images).

**Note**: This facet is defined twice in datachunks.js with different combiners. The second definition (line 44) with `some` combiner is the active one.

**Filter Example**:
```javascript
dataChunks.filter = {
  'loadresource.source': ['/styles/main.css', '/scripts/app.js']
};
// Matches bundles loading main.css OR app.js
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

### 12. `viewmedia.target`
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

### 13. `missingresource.source`
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

### 14. `missingresource.target`
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

### 15. `acquisitionSource`
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
  'enter.source': ['search:google', 'social:facebook']
};
// Matches: visits from Google search AND Facebook
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
// Matches: viewed product image OR clicked to details page
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
- `navigate.source` - Navigation triggers (every)
- `enter.source` - Referrers/traffic sources (every)

### Resources
- `loadresource.source` - Loaded resources (some)
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

## Checkpoint Reference

Checkpoint types observed in RUM data and their source/target semantics. Only the facets listed in **Facet Format** are defined in the query pipeline; for others use `checkpoint: ['name']` only. Run `node cli.js <domain> <start> <end> --facet-values checkpoint` to see which checkpoints exist for your site.

| Checkpoint | Source | Target | Facet Format |
|------------|--------|--------|--------------|
| `top` | — | — | checkpoint only |
| `enter` | Referrer URL (where the user came from) | Document visibilityState (optional) | `enter.source` |
| `navigate` | Referrer URL | Document visibilityState (optional) | `navigate.source` (element that triggered nav) |
| `redirect` | Optional `redirect_from` URL param | Redirect count and duration (e.g. `2:150`, `1~50`) | checkpoint only |
| `back_forward` | Referrer URL | Document visibilityState | checkpoint only |
| `reload` | Referrer URL | Document visibilityState | checkpoint only |
| `click` | CSS selector / idealized selector of clicked element | href or destination URL if link | `click.source` / `click.target` |
| `viewblock` | Block class name or identifier (e.g. hero, features) | — | `viewblock.source` |
| `viewmedia` | — | Media URL (image/video/audio), cleaned of query params | `viewmedia.target` |
| `loadresource` | Resource URL (fragment, .json, .js, API) | Duration in ms (optional) | `loadresource.source` |
| `missingresource` | Resource URL that failed to load | HTTP response status (e.g. 404) | `missingresource.source` / `missingresource.target` |
| `fill` | CSS selector of form field that was filled | — | `fill.source` |
| `formsubmit` | Form selector / identifier | Form action URL | checkpoint only |
| `search` | Search field selector or form identifier | — | checkpoint only |
| `login` | Form selector (form with one password field) | — | checkpoint only |
| `signup` | Form selector (form with multiple password fields) | — | checkpoint only |
| `error` | Error location or script path (e.g. /scripts/main.js, inline) | Error message or type (e.g. TypeError) | `error` (values as `"source \| target"`) |
| `404` | — | Missing or not-found URL | checkpoint only |
| `language` | Document language (e.g. from `html lang`) | Navigator language preference | checkpoint only |
| `utm` | UTM parameter name (e.g. utm_source, utm_medium) | UTM parameter value | checkpoint only |
| `acquisition` | Traffic source / campaign details | — | checkpoint only |
| `paid` | Ad network name (google, facebook, microsoft, linkedin, etc.) | Click/campaign param name (e.g. gclid, fbclid) | checkpoint only |
| `email` | Email platform (mailchimp, marketo) | Campaign param name (e.g. mc_cid, mkt_tok) | checkpoint only |
| `consent` | Consent provider (onetrust, trustarc, usercentrics) | Banner state: show, hidden, suppressed | checkpoint only |
| `a11y` | Accessibility audience level (off, low, medium, high) | Scale string (e.g. off:low:medium:high) | checkpoint only |
| `cwv` | Metric name (LCP, CLS, INP, TTFB) | Metric value | checkpoint only |
| `cwv-ttfb` | — | Time to First Byte value (ms) | checkpoint only |
| `cwv-lcp` | LCP element selector or description | — | checkpoint only |
| `cwv-cls` | — | Cumulative Layout Shift value | checkpoint only |
| `cwv-inp` | — | Interaction to Next Paint value (ms) | checkpoint only |
| `prerender` | Referrer URL | Document visibilityState or prerendered | checkpoint only |

---

## Related Documentation

- **AEM Operational Telemetry**: https://www.aem.live/docs/operational-telemetry
- **AEM Developer – Operational Telemetry**: https://www.aem.live/developer/operational-telemetry
- **RUM Distiller README**: https://github.com/adobe/rum-distiller/blob/main/README.md
- **RUM Distiller API**: https://github.com/adobe/rum-distiller/blob/main/API.md

---

## Summary

This skill defines **14 facets** for filtering RUM data:
- **4 basic facets**: url, userAgent, checkpoint, error
- **10 checkpoint-specific facets**: navigate.source, loadresource.source, click.source, click.target, viewblock.source, fill.source, viewmedia.target, missingresource.source, missingresource.target

**Facets with negative support** (can use `!facetName`): url, userAgent, checkpoint, error

**To filter data**: Set `dataChunks.filter` to an object with facet names as keys and arrays of values to match.

