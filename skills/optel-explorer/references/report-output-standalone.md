# Report Output — Standalone HTML (Claude Code / terminal)

Use this after building the report content per [`report-recipe.md`](report-recipe.md).
This is the entire deliverable outside SLICC. (It is also the download artifact
inside SLICC — see [`report-output-sprinkle.md`](report-output-sprinkle.md).)

A single self-contained document `<!DOCTYPE html>`…`</html>`, e.g.
`<domain>-optel-report-<window>.html`.

**Fixed hex palette** (no app to inject tokens):
- Ink `#1a1a2e` on `#fafafa`/white; one accent (deep blue `#1f3a5f` or `#0b5fff`); muted `#6b7280`.
- Status: good `#1a7f47`, needs-improvement `#b7791f`, poor `#c0392b` — sparingly, only where it signals a decision.

(The recipe's shared rules already apply: one clean `write_file` shot, no external
resources, every number from the battery.)

Optionally also emit a short **Markdown summary** (exec summary + KPIs + priority
actions) for quick pasting.
