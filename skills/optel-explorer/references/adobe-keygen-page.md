# Adobe OpTel Domain Key Generator — Page Reference

URL: `https://aemcs-workspace.adobe.com/customer/generate-optel-domain-key`

> Note: The user-provided URL `generate-rum-domain-key` redirects to `generate-optel-domain-key`.

## Prerequisites

- User must be logged in with an Adobe employee account (IMS auth)
- Login is manual — automation waits for it to complete

## Detecting Login State

**Logged in**: Page title is "Generate OpTel Domain Key - AEM CS Workspace", and the page contains:
- A "Generate" button
- A user avatar + name in the top-right nav bar
- Text "Domain information" visible

**Not logged in**: Page shows Adobe IMS login form or redirects to `auth.services.adobe.com`.

Poll check (via eval):
```javascript
document.title.includes('Generate OpTel Domain Key')
```

## Page Structure (Angular Material)

```
Header bar:
  - Logo "AEM CS Workspace"
  - Nav: GenAI Bot, CSME, Reporting, SLA, On-Call
  - Search combobox (id varies, e.g. "8sxqh7dt") ← DO NOT use this for domain input
  - User avatar + name

Main card "Generate OpTel Domain Key":
  - Disclaimer text
  - "Domain information" heading
  - Domain input: <input id="mat-input-0" type="text"> ← THIS is the target input
  - Helper text: "e.g. odin.adobe.com"
  - Generate button: <button> containing text "Generate" and a paper-plane icon
```

## Key DOM Elements

| Element | Selector | Notes |
|---------|----------|-------|
| Domain input | `#mat-input-0` | Angular Material matInput; no aria-label, no placeholder |
| Generate button | `button` containing text "Generate" | Use `Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('Generate'))` |
| Search bar | First `<input>` on page | DO NOT confuse with domain input; this is the global search |

## Filling the Domain Input

Standard `playwright-cli fill` fails because the input has no `aria-label` or `placeholder`. Use native value setter:

```javascript
const el = document.getElementById('mat-input-0');
const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
setter.call(el, '<domain>');
el.dispatchEvent(new Event('input', { bubbles: true }));
el.dispatchEvent(new Event('change', { bubbles: true }));
```

## Clicking Generate

```javascript
Array.from(document.querySelectorAll('button'))
  .find(b => b.textContent.includes('Generate'))
  .click();
```

## Response States (after clicking Generate)

### Success — Key Generated

Page shows:
- "Generated key" label
- Masked key: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`
- "Show key" button

To reveal the key:
```javascript
Array.from(document.querySelectorAll('button'))
  .find(b => b.textContent.includes('Show key'))
  .click();
```

Then read the key value (it appears as visible text replacing the masked string). Look for a UUID-format string (e.g., `CB3955FE-97EA-47CE-8263-834B8E476566`).

Extraction approach:
```javascript
// After clicking "Show key", the key text appears in the DOM
// Look for UUID pattern in the page text
document.body.innerText.match(/[A-F0-9]{8}-[A-F0-9]{4}-[A-F0-9]{4}-[A-F0-9]{4}-[A-F0-9]{12}/i)?.[0]
```

### Failure — No Data

Page shows:
- Warning icon (triangle)
- Text: "No data found for this domain"

This means the domain has never sent RUM data to the OpTel system. A key cannot be generated for it.

Detection:
```javascript
document.body.innerText.includes('No data found for this domain')
```

## Full Automation Sequence

```bash
# 1. Check tab exists
playwright-cli tab-list

# 2. Navigate if needed
playwright-cli navigate https://aemcs-workspace.adobe.com/customer/generate-optel-domain-key --tab=<ID>

# 3. Wait for login (poll until title matches)
playwright-cli eval --tab=<ID> "document.title.includes('Generate OpTel Domain Key')"

# 4. Fill domain
playwright-cli eval --tab=<ID> "const el = document.getElementById('mat-input-0'); const s = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set; s.call(el, 'DOMAIN'); el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); 'filled'"

# 5. Click Generate
playwright-cli eval --tab=<ID> "Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('Generate')).click(); 'clicked'"

# 6. Wait 4 seconds
sleep 4

# 7. Check result
playwright-cli eval --tab=<ID> "document.body.innerText.includes('No data found') ? 'NO_DATA' : (document.body.innerText.match(/[A-F0-9]{8}-[A-F0-9]{4}-[A-F0-9]{4}-[A-F0-9]{4}-[A-F0-9]{12}/i)?.[0] || 'PENDING')"

# 8. If PENDING (key is masked), click Show key first:
playwright-cli eval --tab=<ID> "const b = Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('Show key')); if(b) b.click(); 'done'"
sleep 1
# Then re-run step 7 to capture the revealed key
```
