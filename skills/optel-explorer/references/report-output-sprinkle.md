# Report Output — Sprinkle (SLICC)

Use after building the report content per [`report-recipe.md`](report-recipe.md).
In SLICC the primary deliverable is a `.shtml` sprinkle; also write the standalone
`.html` (see [`report-output-standalone.md`](report-output-standalone.md)) as the
download artifact.

The disposable query worker writes the `.shtml`, runs `sprinkle open <name>`, and is
then discarded — the sprinkle is **standalone** (full-document `.shtml` needs no
owner scoop to keep running). The cone never writes `.shtml` or runs `sprinkle`
itself; it delegates to the worker. Read `/workspace/skills/sprinkles/SKILL.md` +
its style guide before authoring.

## The `.shtml`

FULL-DOCUMENT mode (starts with `<!DOCTYPE html>`) — SLICC iframes it for CSS
isolation AND injects S2 theming. Do **NOT** hand-roll a nested `<iframe srcdoc>`
or load the report via `readFile` at runtime — just make the report the document.
Use `data-sprinkle-autoopen` and a rail icon `<link rel="icon" href="chart-bar" />`.
(The recipe's shared rules apply: one clean `write_file` shot, no external resources.)

**Theme with S2 tokens, never hard-coded hex** (follows light/dark with the app):
- bg → `--s2-bg-base`; cards/header band → `--s2-bg-elevated` (band may tint `color-mix(in srgb, var(--s2-accent) 12%, var(--s2-bg-elevated))`); zebra → `--s2-bg-layer-1/2`.
- text → `--s2-content-default`; muted → `--s2-content-secondary`; accent → `--s2-accent`.
- status: `--s2-positive` / `--s2-notice` / `--s2-negative` / `--s2-informative`; subtle tints via `color-mix(... 8–12%, transparent)`.
- text on filled bars → `--s2-gray-25` (never `#fff`); borders → `1px solid color-mix(in srgb, var(--s2-content-default) 12%, transparent)`.
- `color-scheme: light dark` on `:root`; never `@media (prefers-color-scheme)` (desyncs from the parent's class toggle); one-off colors → `light-dark(<l>,<d>)`.

## Download button

Wire a client-side "Download report" button (no lick round-trip) — the sprinkle
iframe allows downloads, so read the standalone `.html` and trigger a Blob download:
```html
<button id="dl-btn" class="sprinkle-btn sprinkle-btn--secondary">Download report</button>
<script>
  document.getElementById('dl-btn').addEventListener('click', async function () {
    var html = await slicc.readFile('/shared/<domain>-optel-report-<window>.html');
    var url = URL.createObjectURL(new Blob([html], { type: 'text/html' }));
    var a = document.createElement('a');
    a.href = url; a.download = '<domain>-optel-report-<window>.html';
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
  });
</script>
```

## Acceptance

Sprinkle is `[open]`; `.shtml` is full-document with no nested iframe/`srcdoc`/runtime
`readFile`; ~0 hard-coded hex (S2 tokens, `color-scheme: light dark`); rail icon
declared; Exec Summary + Priority Actions above the fold; KPI/CWV status-colored;
top-N breakdowns are bar charts; Geographic badged proxy; methodology footer present.
