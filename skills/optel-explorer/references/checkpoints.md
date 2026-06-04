# AEM Operational Telemetry Checkpoints

This document provides comprehensive documentation for all checkpoints in AEM Operational Telemetry. Checkpoints are named events in the sequence of loading a page and interacting with it as a visitor.

## Available Checkpoints (from RUM data)

The following **26 checkpoints** have been observed in live RUM data (validated from `--facet-values checkpoint`). Use `node cli.js <domain> <start> <end> --facet-values checkpoint` to see which checkpoints exist for your domain and date range.

| Checkpoint | Category (below) |
|------------|------------------|
| `top` | Page Load |
| `loadresource` | Resource Loading |
| `language` | Localization |
| `viewmedia` | Content Engagement |
| `viewblock` | Content Engagement |
| `cwv-ttfb` | Performance |
| `a11y` | Accessibility |
| `enter` | Traffic |
| `cwv-lcp` | Performance |
| `missingresource` | Resource Loading |
| `click` | User Interaction |
| `cwv-cls` | Performance |
| `utm` | Marketing |
| `cwv-inp` | Performance |
| `redirect` | Navigation |
| `fill` | User Interaction |
| `navigate` | Navigation |
| `error` | Error Tracking |
| `acquisition` | Marketing |
| `cwv` | Performance (meta) |
| `back_forward` | Navigation |
| `reload` | Navigation |
| `formsubmit` | User Interaction |
| `login` | User Interaction |
| `signup` | User Interaction |
| `prerender` | Page Load |
| `paid` | Marketing (Helix RUM Enhancer) |
| `email` | Marketing (Helix RUM Enhancer) |

**References:**
- [AEM Operational Telemetry](https://www.aem.live/docs/operational-telemetry)
- [AEM Developer – Operational Telemetry](https://www.aem.live/developer/operational-telemetry)
- [Helix RUM Enhancer](https://github.com/adobe/helix-rum-enhancer) – client-side instrumentation for AEM/Helix sites (defines how checkpoints such as `click`, `viewblock`, `loadresource`, `redirect`, `formsubmit`, `search`, `login`, `signup`, `consent`, `paid`, `email`, `a11y`, etc. are sent)

---

## Overview

A **checkpoint** is a specific event type that occurs during a page view. Each checkpoint represents a measurable interaction or milestone in the user's experience. Checkpoints are collected as part of Real User Monitoring (RUM) bundles and can be used for:

- Performance analysis
- User behavior tracking
- Error monitoring
- Conversion tracking
- Content engagement analysis

## Data Structure

Every checkpoint event contains:
- **`checkpoint`**: The event name (lowercase, no special characters)
- **`source`**: The origin/element that triggered the event (optional, varies by checkpoint)
- **`target`**: The destination/object of the event (optional, varies by checkpoint)
- **`timeDelta`**: Milliseconds since page load
- **`value`**: Numeric value for metrics like Core Web Vitals (optional)

---

## Core Performance Checkpoints

### `top`
**Category**: Page Load | **Source**: N/A | **Target**: N/A

**What it tracks**: The page loading sequence has begun and first JavaScript code is being executed.

**When it fires**: Even before blocks are decorated or content is visible

**Use cases**:
- Track initial page load timing
- Measure time to interactive
- Identify slow-loading pages

**Filter example**:
```javascript
dataChunks.filter = {
  checkpoint: ['top']
};
// All page views that started loading
```

---

### `cwv`
**Category**: Performance | **Source**: Metric type | **Target**: Metric value

**What it tracks**: Core Web Vitals (CWV) readiness or actual readings for LCP, CLS, or INP.

**When it fires**:
- When the page is ready to collect CWV readings
- When each CWV metric is recorded
- Multiple instances can occur during one page view (asynchronous)

**Use cases**:
- Monitor overall Core Web Vitals health
- Track when metrics become available
- Performance baseline establishment

**Note**: This is a meta-checkpoint. Use specific vitals checkpoints (`cwv-lcp`, `cwv-cls`, `cwv-inp`, `cwv-ttfb`) for detailed analysis.

---

### `cwv-lcp`
**Category**: Performance | **Source**: LCP element selector | **Target**: N/A

**What it tracks**: Largest Contentful Paint - time for the largest contentful element to load.

**When it fires**: When the browser renders the most prominent content (usually the largest image)

**Performance thresholds**:
- **Good**: < 2.5 seconds
- **Needs Improvement**: 2.5 - 4.0 seconds
- **Poor**: > 4.0 seconds

**Use cases**:
- Optimize loading of hero images
- Identify slow-loading primary content
- A/B test content performance

**Filter example**:
```javascript
dataChunks.filter = {
  checkpoint: ['cwv-lcp']
};
// Pages with LCP measurements
```

---

### `cwv-cls`
**Category**: Performance | **Source**: N/A | **Target**: CLS value

**What it tracks**: Cumulative Layout Shift - visual stability during page load.

**When it fires**: Throughout the page lifetime as layout shifts occur

**Performance thresholds**:
- **Good**: < 0.1
- **Needs Improvement**: 0.1 - 0.25
- **Poor**: > 0.25

**Use cases**:
- Identify pages with layout instability
- Fix elements causing unexpected shifts
- Improve user experience

---

### `cwv-inp`
**Category**: Performance | **Source**: N/A | **Target**: INP value

**What it tracks**: Interaction to Next Paint - responsiveness to user interactions.

**When it fires**: After user interactions (clicks, taps, keyboard inputs)

**Performance thresholds**:
- **Good**: < 200 ms
- **Needs Improvement**: 200 - 500 ms
- **Poor**: > 500 ms

**Use cases**:
- Identify laggy interactions
- Optimize JavaScript execution
- Improve perceived performance

**Note**: INP replaced FID (First Input Delay) as a Core Web Vital.

---

### `cwv-ttfb`
**Category**: Performance | **Source**: N/A | **Target**: TTFB value

**What it tracks**: Time to First Byte - server response time.

**When it fires**: When the first byte of the response arrives

**Performance thresholds**:
- **Good**: < 800 ms
- **Needs Improvement**: 800 - 1800 ms
- **Poor**: > 1800 ms

**Use cases**:
- Monitor server performance
- Identify backend bottlenecks
- CDN effectiveness analysis

---

## Navigation & Traffic Checkpoints

### `enter`
**Category**: Traffic Source | **Source**: Referrer URL | **Target**: N/A

**What it tracks**: How visitors arrive at the page (external referrers).

**Source values**:
- External domain URLs (e.g., `https://google.com`)
- `direct` - URL typed directly, bookmarks, or iOS app links
- Classified values like `search:google`, `social:facebook`

**Use cases**:
- Traffic source analysis
- Campaign attribution
- Referrer tracking
- Identify top external sources

**Filter example**:
```javascript
dataChunks.filter = {
  checkpoint: ['enter'],
  'enter.source': ['search:google', 'social:facebook']
};
// Visits from Google search or Facebook
```

---

### `navigate`
**Category**: Navigation | **Source**: Navigation element/link | **Target**: N/A

**What it tracks**: Internal navigation paths between pages.

**When it fires**: When users click links to navigate within the site

**Use cases**:
- Discover internal navigation patterns
- Identify popular navigation paths
- Optimize site structure
- Track navigation from specific elements

**Filter example**:
```javascript
dataChunks.filter = {
  checkpoint: ['navigate'],
  'navigate.source': ['.nav-menu', '.footer-links']
};
// Navigation from menu or footer
```

---

### `back_forward`
**Category**: Navigation | **Source**: N/A | **Target**: N/A

**What it tracks**: Browser back or forward button usage.

**When it fires**: When the user navigates via the browser’s back or forward button

**Use cases**:
- Understand navigation patterns
- Detect back-button usage after form or checkout

---

### `reload`
**Category**: Navigation | **Source**: N/A | **Target**: N/A

**What it tracks**: Page reload events.

**When it fires**: When the user reloads the page (e.g. F5, reload button)

**Use cases**:
- Track reload frequency
- Identify pages that users reload (e.g. errors or confusion)

---

### `redirect`
**Category**: Navigation | **Source**: Optional query param (e.g. redirect_from) | **Target**: Redirect count and duration

**What it tracks**: Redirect hops and timing to reach the page. In [Helix RUM Enhancer](https://github.com/adobe/helix-rum-enhancer), the redirect plugin sends **source** from the `redirect_from` URL parameter (if present) and **target** as redirect count and duration (e.g. `2:150` or estimated `1~50`).

**Use cases**:
- Identify excessive redirects
- Optimize redirect chains
- Improve page load performance

---

## User Interaction Checkpoints

### `click`
**Category**: Interaction | **Source**: CSS selector of clicked element | **Target**: href/destination URL

**What it tracks**: User clicks on any element (links, buttons, etc.).

**When it fires**: On any click event in the page

**Source**: CSS selector or element identifier (e.g., `.cta-button`, `#submit-btn`)
**Target**: The href value if the element is a link (e.g., `/checkout`, `https://external.com`)

**Use cases**:
- Track button/link clicks
- Identify popular UI elements
- Measure conversion actions
- Analyze user engagement

**Filter example**:
```javascript
dataChunks.filter = {
  checkpoint: ['click'],
  'click.source': ['.buy-button', '.add-to-cart'],
  'click.target': ['/checkout']
};
// Clicks on purchase buttons leading to checkout
```

---

### `fill`
**Category**: Form Interaction | **Source**: CSS selector of form field | **Target**: N/A

**What it tracks**: Form fields filled by the user.

**When it fires**: When a user interacts with and fills a form field

**Source**: CSS selector of the field (e.g., `input[name="email"]`, `#phone-field`)

**Privacy**: The actual data entered is NOT captured

**Use cases**:
- Form field engagement analysis
- Identify form abandonment points
- Optimize form design
- Track which fields users interact with

**Filter example**:
```javascript
dataChunks.filter = {
  checkpoint: ['fill'],
  'fill.source': ['input[name="email"]', 'input[name="phone"]']
};
// Email or phone fields filled
```

---

### `formsubmit`
**Category**: Form Interaction | **Source**: Form identifier/selector | **Target**: Form action URL

**What it tracks**: Form submissions.

**When it fires**: When a form is submitted

**Source**: Which form on the page was submitted
**Target**: The form's action URL (where data is sent)

**Use cases**:
- Track successful form submissions
- Measure conversion rates
- Identify which forms convert best
- Form completion analysis

**Filter example**:
```javascript
dataChunks.filter = {
  checkpoint: ['formsubmit'],
  'formsubmit.target': ['/api/contact', '/api/signup']
};
// Contact or signup form submissions
```

---

### `search`
**Category**: Interaction | **Source**: Search query/field | **Target**: N/A

**What it tracks**: Site search performed by users.

**When it fires**: When a user performs a search using a search input field

**Use cases**:
- Track search usage
- Identify popular search terms
- Improve search functionality

---

## Content Engagement Checkpoints

### `viewblock`
**Category**: Content Visibility | **Source**: Block class name/identifier | **Target**: N/A

**What it tracks**: Content blocks that scroll into view.

**When it fires**: When a block becomes visible in the viewport

**Source**: The class name of the block (e.g., `hero`, `features`, `testimonials`)

**Visibility threshold**: Block is at least partially visible

**Use cases**:
- Content engagement tracking
- Identify viewed vs ignored content
- Scroll depth analysis
- A/B test content effectiveness

**Filter example**:
```javascript
dataChunks.filter = {
  checkpoint: ['viewblock'],
  'viewblock.source': ['hero', 'features', 'testimonials']
};
// Users who viewed all three blocks
```

---

### `viewmedia`
**Category**: Media Visibility | **Source**: N/A | **Target**: Media URL

**What it tracks**: Images or videos that scroll into view.

**When it fires**: When media becomes at least 25% visible in the browser

**Target**: URL of the image or video (cleaned of query parameters)

**Use cases**:
- Media engagement tracking
- Identify most viewed assets
- Optimize media loading
- Content performance analysis

**Filter example**:
```javascript
dataChunks.filter = {
  checkpoint: ['viewmedia'],
  'viewmedia.target': ['/images/hero.jpg', '/videos/demo.mp4']
};
// Specific media viewed
```

---

## Resource Loading Checkpoints

### `loadresource`
**Category**: Resource Loading | **Source**: Resource URL | **Target**: N/A

**What it tracks**: Fragments and JSON API endpoints loaded for the site.

**When it fires**: When external resources are fetched

**Source**: URL of the loaded resource (CSS, JS, JSON, fragments)

**Use cases**:
- Track resource loading times
- Identify slow resources
- Monitor API call patterns
- Optimize resource loading

**Filter example**:
```javascript
dataChunks.filter = {
  checkpoint: ['loadresource'],
  'loadresource.source': ['/fragments/header.json', '/api/products']
};
// Specific fragments or API endpoints loaded
```

---

## Error & Debugging Checkpoints

### `error`
**Category**: Error Tracking | **Source**: Error location/script | **Target**: Error message/type

**What it tracks**: Unhandled JavaScript errors.

**When it fires**: When a JavaScript error occurs and is not handled by application code

**Source**: Location or script where error occurred (e.g., `/scripts/main.js`, `inline`)
**Target**: Error message or type (e.g., `TypeError`, `ReferenceError`, `Network Error`)

**Use cases**:
- Bug tracking and monitoring
- Identify problematic scripts
- Error rate analysis
- Prioritize fixes by frequency

**Filter example**:
```javascript
dataChunks.filter = {
  checkpoint: ['error'],
  error: ['/scripts/payment.js | TypeError', '/scripts/payment.js | Network Error']
};
// Specific errors in payment script (use --facet-values error to discover actual values)
```

---

### `404`
**Category**: Error Tracking | **Source**: N/A | **Target**: Missing URL

**What it tracks**: Page not found (404) responses.

**When it fires**: When a 404 error page is served

**Use cases**:
- Identify broken links
- Track missing content
- Monitor content migration issues
- SEO impact analysis

**Filter example**:
```javascript
dataChunks.filter = {
  checkpoint: ['404']
};
// All 404 errors
```

---

## Specialized Checkpoints

### `language`
**Category**: Localization | **Source**: Selected language | **Target**: N/A

**What it tracks**: Content languages used and user language preferences.

**Use cases**:
- Language preference analysis
- Localization effectiveness
- Multi-language site optimization

---

### `a11y`
**Category**: Accessibility | **Source**: Audience level | **Target**: Level scale

**What it tracks**: Approximate accessibility audience (off, low, medium, high) based on user preferences and behavior. In [Helix RUM Enhancer](https://github.com/adobe/helix-rum-enhancer), the a11y plugin scores preferences (e.g. prefers-reduced-motion, zoom, touch) and keyboard usage; **source** is the reported audience (`off`, `low`, `medium`, `high`) and **target** is the scale string `off:low:medium:high`. The plugin can also send **error** checkpoints for focus traps or focus loss.

**Use cases**:
- Accessibility feature usage
- Compliance monitoring
- A11y optimization

---

### `consent`
**Category**: Privacy | **Source**: Consent provider name | **Target**: Banner state

**What it tracks**: Consent provider detected and banner state (shown, hidden, or suppressed).

**When it fires**: When a supported consent management platform is detected. In [Helix RUM Enhancer](https://github.com/adobe/helix-rum-enhancer), the consent plugin sends one checkpoint per page; **source** is the provider (`onetrust`, `trustarc`, or `usercentrics`) and **target** is the state (`show`, `hidden`, or `suppressed`).

**Use cases**:
- Consent banner effectiveness
- GDPR/CCPA compliance tracking
- User consent patterns

---

### `acquisition`
**Category**: Marketing | **Source**: Traffic source details | **Target**: N/A

**What it tracks**: Inorganic traffic sources (paid campaigns, ads).

**Use cases**:
- Campaign performance tracking
- Marketing attribution
- Paid vs organic analysis
- ROI measurement

---

### `utm`
**Category**: Marketing | **Source**: UTM parameter name | **Target**: UTM parameter value

**What it tracks**: Visits with UTM (campaign) parameters in the URL.

**When it fires**: When the page was loaded with UTM query parameters (e.g. utm_source, utm_medium, utm_campaign). Each utm_* key-value pair is sent as a checkpoint (source = key, target = value). Parameters that may leak PII (e.g. utm_id, utm_term) are not sent by Helix RUM Enhancer.

**Use cases**:
- Campaign attribution
- Marketing channel analysis
- Link the checkpoint to campaign performance

---

### `paid`
**Category**: Marketing | **Source**: Ad network name | **Target**: Parameter name

**What it tracks**: Paid/organic traffic from ad networks (e.g. Google, Facebook, Microsoft, LinkedIn).

**When it fires**: When the URL contains known paid-click parameters (e.g. gclid, wbraid, fbclid, msclkid, li_fat_id). Emitted by the martech plugin in [Helix RUM Enhancer](https://github.com/adobe/helix-rum-enhancer) when such query parameters are present.

**Source values** (examples): `google`, `doubleclick`, `microsoft`, `facebook`, `twitter`, `linkedin`, `pinterest`, `tiktok`

**Use cases**:
- Paid campaign attribution
- Distinguish paid vs organic traffic
- ROI and ad performance analysis

---

### `email`
**Category**: Marketing | **Source**: Email platform name | **Target**: Parameter name

**What it tracks**: Traffic from email campaigns (e.g. Mailchimp, Marketo).

**When it fires**: When the URL contains known email campaign parameters (e.g. mc_cid, mkt_tok). Emitted by the martech plugin in [Helix RUM Enhancer](https://github.com/adobe/helix-rum-enhancer).

**Source values** (examples): `mailchimp`, `marketo`

**Use cases**:
- Email campaign attribution
- Email vs other channel comparison
- Campaign link effectiveness

---

### `missingresource`
**Category**: Resource Loading | **Source**: N/A | **Target**: Missing resource URL (optional)

**What it tracks**: Resources that failed to load (e.g. 404 for assets, broken fragments).

**When it fires**: When a requested resource (script, style, fragment, image) fails to load

**Use cases**:
- Find broken assets or APIs
- Monitor fragment/API availability
- Fix missing resources affecting UX

---

### `login`
**Category**: User Interaction | **Source**: N/A | **Target**: N/A

**What it tracks**: Login actions or login flow entry.

**When it fires**: When the user triggers or completes a login (implementation-dependent)

**Use cases**:
- Track login attempts and success
- Funnel analysis for authenticated users

---

### `signup`
**Category**: User Interaction | **Source**: N/A | **Target**: N/A

**What it tracks**: Sign-up / registration actions.

**When it fires**: When the user starts or completes a sign-up flow (implementation-dependent)

**Use cases**:
- Registration funnel analysis
- Conversion tracking for new users

---

### `prerender`
**Category**: Page Load | **Source**: N/A | **Target**: N/A

**What it tracks**: Page views that were prerendered (e.g. for prefetch or speculation).

**When it fires**: When the page load is attributed to prerendering

**Use cases**:
- Distinguish prerendered vs normal page views
- Sampling and metric interpretation

---

## Checkpoint Categories Summary

| Category | Checkpoints |
|----------|-------------|
| **Page Load** | top, prerender |
| **Performance** | cwv, cwv-ttfb, cwv-lcp, cwv-cls, cwv-inp |
| **Navigation** | enter, navigate, redirect, back_forward, reload |
| **User Interaction** | click, fill, formsubmit, login, signup, search |
| **Content Engagement** | viewblock, viewmedia |
| **Resource Loading** | loadresource, missingresource |
| **Errors** | error, 404 |
| **Marketing / Traffic** | utm, acquisition, paid, email |
| **Specialized** | language, a11y, consent |

---

## Using Checkpoints in Filters

### Filter by specific checkpoints
```javascript
dataChunks.filter = {
  checkpoint: ['click', 'fill', 'formsubmit']
};
// Only bundles with ALL three events (combiner: 'every')
```

### Exclude checkpoints (negative filter)
```javascript
dataChunks.filter = {
  '!checkpoint': ['error', '404']
};
// Exclude bundles with errors or 404s
```

### Combine checkpoint with source/target
```javascript
dataChunks.filter = {
  checkpoint: ['click'],
  'click.source': ['.buy-button'],
  'click.target': ['/checkout']
};
// Buy button clicks going to checkout
```

### Multi-checkpoint engagement analysis
```javascript
dataChunks.filter = {
  checkpoint: ['viewblock', 'click', 'viewmedia'],
  'viewblock.source': ['hero', 'features'],
  url: ['/products']
};
// Product pages with high engagement
```

---

## Best Practices for Agents

1. **Start with checkpoint filter**: Always filter by checkpoint first to ensure events exist
2. **Understand source/target context**: Source and target meanings vary by checkpoint type
3. **Use performance checkpoints for metrics**: cwv-lcp, cwv-cls, cwv-inp, cwv-ttfb for performance analysis
4. **Combine for funnel analysis**: Track user journey with enter → viewblock → click → fill → formsubmit
5. **Monitor errors proactively**: Regular filters on error and 404 checkpoints
6. **Engagement scoring**: Count viewblock + viewmedia + click for engagement metrics
7. **Conversion tracking**: Follow click → fill → formsubmit sequence for conversions

---

## Related Documentation

- **Facets Documentation**: See `facets.md` for filtering by checkpoint source and target
- **AEM Operational Telemetry**: https://www.aem.live/docs/operational-telemetry
- **AEM Developer – Operational Telemetry**: https://www.aem.live/developer/operational-telemetry
- **Helix RUM Enhancer** (checkpoint instrumentation): https://github.com/adobe/helix-rum-enhancer
- **Core Web Vitals**: https://web.dev/vitals/

---

## Quick Reference Table

Aligned with the [Checkpoint Reference in facets.md](facets.md#checkpoint-reference). **Has Source** / **Has Target** indicate whether the checkpoint event carries source/target data in RUM payloads.

| Checkpoint | Has Source | Has Target | Primary Use Case |
|------------|:----------:|:----------:|-------------------|
| `top` | ❌ | ❌ | Page load start |
| `enter` | ✅ | ✅ | Traffic sources (referrer; optional visibilityState) |
| `navigate` | ✅ | ✅ | Internal navigation (element; optional visibilityState) |
| `redirect` | ✅ | ✅ | Redirect hops (optional redirect_from; count/duration) |
| `back_forward` | ✅ | ✅ | Back/forward navigation (referrer; visibilityState) |
| `reload` | ✅ | ✅ | Page reload (referrer; visibilityState) |
| `click` | ✅ | ✅ | User clicks (element selector; href/destination) |
| `viewblock` | ✅ | ❌ | Content block visibility (block identifier) |
| `viewmedia` | ❌ | ✅ | Media visibility (media URL) |
| `loadresource` | ✅ | ✅ | Resource loading (resource URL; optional duration) |
| `missingresource` | ✅ | ✅ | Failed resource load (resource URL; response status) |
| `fill` | ✅ | ❌ | Form field interactions (field selector) |
| `formsubmit` | ✅ | ✅ | Form submissions (form selector; action URL) |
| `search` | ✅ | ❌ | Site search (search field/form identifier) |
| `login` | ✅ | ❌ | Login flow (form with one password field) |
| `signup` | ✅ | ❌ | Sign-up flow (form with multiple password fields) |
| `error` | ✅ | ✅ | JavaScript errors (location/script; message/type) |
| `404` | ❌ | ✅ | Page not found (missing URL) |
| `language` | ✅ | ✅ | Language (doc lang; navigator preference) |
| `utm` | ✅ | ✅ | UTM/campaign params (param name; param value) |
| `acquisition` | ✅ | ❌ | Traffic source / campaign details |
| `paid` | ✅ | ✅ | Paid ad traffic (ad network; param name) |
| `email` | ✅ | ✅ | Email campaign traffic (platform; param name) |
| `consent` | ✅ | ✅ | Consent (provider; show/hidden/suppressed) |
| `a11y` | ✅ | ✅ | Accessibility (audience level; scale string) |
| `cwv` | ✅ | ✅ | Core Web Vitals meta (metric name; value) |
| `cwv-ttfb` | ❌ | ✅ | Time to First Byte (ms) |
| `cwv-lcp` | ✅ | ❌ | Largest Contentful Paint (LCP element) |
| `cwv-cls` | ❌ | ✅ | Cumulative Layout Shift (value) |
| `cwv-inp` | ❌ | ✅ | Interaction to Next Paint (ms) |
| `prerender` | ✅ | ✅ | Prerendered page view (referrer; visibilityState) |

