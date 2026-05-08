# vdua-skills

Personal Claude Code plugin with skills for AEM Operational Telemetry (RUM) analysis.

## Install

```
/plugin marketplace add vdua/skills
/plugin install vdua-skills@vdua-skills
```

## Skills

- **`optel-query`** — translates natural language into structured RUM queries and executes them. Activates automatically for RUM/analytics questions (page views, clicks, errors, Core Web Vitals, traffic sources).
- **`optel-analyze-errors`** — analyzes JavaScript errors from RUM query output, deduplicates across browsers using token-based similarity.

## Environment

- `DOMAINKEY_FILE` — path to a JSON map `{"<domain>": "<key>"}`. Required for private domains.
- `RUM_ADMIN_KEY` — optional admin token to fetch missing domain keys.
- `--domainkey <key>` CLI flag — pass a domain key directly, bypassing env vars.

## Building scripts

The `.jsh` files under `skills/*/scripts/` are generated artifacts. To rebuild from source (requires the [optel-query](https://github.com/vdua/optel-query) repo):

```bash
cd /path/to/optel-query
npm install
npm run build
```

Then copy the updated `.jsh` files into `skills/*/scripts/`.
