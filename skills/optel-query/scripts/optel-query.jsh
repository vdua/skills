var __getOwnPropNames = Object.getOwnPropertyNames;
var __commonJS = (cb, mod) => function __require() {
  return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports;
};

// src/optel-query/domainkey.js
var require_domainkey = __commonJS({
  "src/optel-query/domainkey.js"(exports2, module2) {
    var rawFs2 = require("fs");
    var fs2 = rawFs2.promises || rawFs2;
    async function readDomainkeyFile() {
      if (!process.env.DOMAINKEY_FILE) return null;
      try {
        const contents = String(await fs2.readFile(process.env.DOMAINKEY_FILE));
        return JSON.parse(contents);
      } catch (e) {
        return null;
      }
    }
    async function writeDomainkeyFile(domain, domainkey) {
      if (!process.env.DOMAINKEY_FILE) return;
      let existing = {};
      try {
        const contents = String(await fs2.readFile(process.env.DOMAINKEY_FILE));
        existing = JSON.parse(contents);
      } catch (e) {
      }
      existing[domain] = domainkey;
      await fs2.writeFile(process.env.DOMAINKEY_FILE, JSON.stringify(existing, null, 2));
    }
    async function fetchDomainKey(domain, override) {
      if (override) return override;
      try {
        const keyMap = await readDomainkeyFile();
        if (keyMap && keyMap[domain]) return keyMap[domain];
        const auth = process.env.RUM_ADMIN_KEY;
        if (!auth) {
          throw new Error(
            `No domainkey found for "${domain}". Add it to domainkey.json in the current directory.`
          );
        }
        let org;
        if (domain.endsWith(":all") && domain !== "aem.live:all") {
          [org] = domain.split(":");
        }
        const issueResp = await fetch(
          `https://rum.fastly-aem.page/${org ? `orgs/${org}/key` : `domainkey/${domain}`}`,
          { headers: { authorization: `Bearer ${auth}` } }
        );
        let domainkey = "";
        try {
          domainkey = (await issueResp.json())[org ? "orgkey" : "domainkey"];
        } catch (e) {
        }
        if (issueResp.status === 403 || domainkey === "") {
          const n = /* @__PURE__ */ new Date();
          const y = n.getFullYear();
          const m = String(n.getMonth() + 1).padStart(2, "0");
          const d = String(n.getDate()).padStart(2, "0");
          const probeResp = await fetch(
            `https://rum.fastly-aem.page/bundles/${domain}/${y}/${m}/${d}?domainkey=open`
          );
          if (probeResp.status === 200) return "open";
        }
        await writeDomainkeyFile(domain, domainkey);
        return domainkey;
      } catch (e) {
        throw new Error("Error Getting Domain Key: " + e.message);
      }
    }
    module2.exports = { fetchDomainKey };
  }
});

// src/optel-query/deps.js
var require_deps = __commonJS({
  "src/optel-query/deps.js"(exports2, module2) {
    var RD_URL = "https://esm.sh/@adobe/rum-distiller@1.23.0/es2022/rum-distiller.mjs";
    var RD_UTILS_URL = "https://esm.sh/@adobe/rum-distiller@1.23.0/es2022/utils.mjs";
    var _rd = null;
    var _rdUtils = null;
    async function loadRumDistiller() {
      if (!_rd) {
        _rd = typeof document !== "undefined" ? await import(RD_URL) : require("@adobe/rum-distiller");
      }
      return _rd;
    }
    async function loadRumDistillerUtils() {
      if (!_rdUtils) {
        _rdUtils = typeof document !== "undefined" ? await import(RD_UTILS_URL) : require("@adobe/rum-distiller/utils.js");
      }
      return _rdUtils;
    }
    module2.exports = { loadRumDistiller, loadRumDistillerUtils };
  }
});

// src/optel-query/loader.js
var require_loader = __commonJS({
  "src/optel-query/loader.js"(exports2, module2) {
    var { loadRumDistillerUtils } = require_deps();
    function filterByDateRange(data, start, end) {
      if (start || end) {
        return data.filter((bundle) => {
          const time = new Date(bundle.timeSlot);
          return (start ? time >= start : true) && (end ? time <= end : true);
        });
      }
      return data;
    }
    function isBotTraffic(bundle) {
      return bundle?.userAgent?.includes("bot");
    }
    function filterEvents(checkpoints = []) {
      return (bundle) => {
        if (checkpoints.length > 0) {
          return { ...bundle, events: bundle.events.filter((e) => checkpoints.includes(e.checkpoint)) };
        }
        return bundle;
      };
    }
    var Loader = class {
      constructor(DOMAIN, DOMAIN_KEY) {
        if (!DOMAIN_KEY) throw new Error("Domain key not found for domain: " + DOMAIN);
        this.DOMAIN = DOMAIN;
        this.DOMAIN_KEY = DOMAIN_KEY;
        this.API_ENDPOINT = "https://rum.fastly-aem.page";
        this.ORG = void 0;
      }
      apiURL(datePath, hour) {
        const u = new URL(this.API_ENDPOINT);
        u.pathname = [
          ...this.ORG ? ["orgs", this.ORG, "bundles"] : ["bundles", this.DOMAIN],
          datePath,
          hour
        ].filter((p) => !!p).join("/");
        u.searchParams.set("domainkey", this.DOMAIN_KEY);
        return u.toString();
      }
      async fetch(apiRequestURL) {
        try {
          const resp = await fetch(apiRequestURL);
          return await resp.json();
        } catch (err) {
          return { rumBundles: [] };
        }
      }
      async fetchUTCMonth(utcISOString, start, end) {
        const [date] = utcISOString.split("T");
        const datePath = date.split("-").slice(0, 2).join("/");
        const { rumBundles } = await this.fetch(this.apiURL(datePath));
        const { addCalculatedProps } = await loadRumDistillerUtils();
        rumBundles.forEach((b) => addCalculatedProps(b));
        return { date: utcISOString.split("T")[0], rumBundles: filterByDateRange(rumBundles, start, end) };
      }
      async fetchUTCHour(utcISOString, start, end) {
        const [date, time] = utcISOString.split("T");
        const datePath = date.split("-").join("/");
        const hour = time.split(":")[0];
        const { rumBundles } = await this.fetch(this.apiURL(datePath, hour));
        const { addCalculatedProps } = await loadRumDistillerUtils();
        rumBundles.forEach((b) => addCalculatedProps(b));
        return { date, hour, rumBundles: filterByDateRange(rumBundles, start, end) };
      }
      async fetchUTCDay(utcISOString, start, end) {
        const [date] = utcISOString.split("T");
        const datePath = date.split("-").join("/");
        const { rumBundles } = await this.fetch(this.apiURL(datePath));
        const { addCalculatedProps } = await loadRumDistillerUtils();
        rumBundles.forEach((b) => addCalculatedProps(b));
        return { date, rumBundles: filterByDateRange(rumBundles, start, end) };
      }
      async fetchPeriod(startDate, endDate, allCheckpoints = [], interval = void 0, startDateHour = 0, endDateHour = 0) {
        const start = new Date(startDate);
        const originalStart = new Date(start);
        const end = endDate ? new Date(endDate) : /* @__PURE__ */ new Date();
        const diff = end.getTime() - start.getTime();
        if (diff < 0) throw new Error("Start date must be before end date");
        const chunks = [];
        const allBundles = [];
        if (diff <= 1e3 * 60 * 60 * 24 * 7 && !interval || interval === "hourly") {
          const days = Math.round(diff / (1e3 * 60 * 60 * 24)) + 1;
          end.setHours(endDateHour);
          for (let i = 0; i < days; i += 1) {
            chunks.push([]);
            const maxJ = i === days - 1 ? 24 - startDateHour : 24;
            for (let j = 0; j < maxJ; j += 1) {
              chunks[chunks.length - 1].push(end.toISOString());
              end.setTime(end.getTime() - 3600 * 1e3);
            }
          }
          for (const chunk of chunks) {
            const bundles = await Promise.all(chunk.map((date) => this.fetchUTCHour(date, null, null)));
            allBundles.push(...bundles);
          }
        } else if (diff <= 1e3 * 60 * 60 * 24 * 31 && !interval || interval === "daily") {
          const days = Math.round(diff / (1e3 * 60 * 60 * 24)) + 1;
          const chunkSize = 30;
          for (let i = 0; i < days; i += chunkSize) {
            const daysInChunk = Math.min(chunkSize, days - i);
            const chunk = [];
            for (let j = 0; j < daysInChunk; j += 1) {
              chunk.push(end.toISOString());
              end.setTime(end.getTime() - 24 * 3600 * 1e3);
            }
            chunks.push(chunk);
          }
          for (const chunk of chunks) {
            const bundles = await Promise.all(chunk.map((iso) => this.fetchUTCDay(iso, null, null)));
            allBundles.push(...bundles);
          }
        } else {
          const months = Math.round(diff / (1e3 * 60 * 60 * 24 * 31)) + 1;
          const promises = [];
          for (let i = 0; i < months; i += 1) {
            promises.push(this.fetchUTCMonth(start.toISOString(), originalStart, end));
            start.setMonth(start.getMonth() + 1);
          }
          allBundles.push(...await Promise.all(promises));
        }
        return allBundles.flatMap(
          (b) => b.rumBundles.filter((bundle) => !isBotTraffic(bundle)).map(filterEvents(allCheckpoints))
        );
      }
    };
    module2.exports = { Loader };
  }
});

// src/optel-query/datachunks.js
var require_datachunks = __commonJS({
  "src/optel-query/datachunks.js"(exports2, module2) {
    var { loadRumDistiller } = require_deps();
    function isValidError(event) {
      return event.checkpoint === "error" && event.source !== "focus-loss" && !event.source?.includes("helix-rum-enhancer");
    }
    function errorDetails(bundle) {
      return Array.from(bundle.events.filter(isValidError).reduce((acc, { source, target }) => {
        acc.add(`${source} | ${target}`);
        return acc;
      }, /* @__PURE__ */ new Set()));
    }
    function isFormLoadEvent(event) {
      return event.checkpoint === "viewblock" && event.source.match(/form/);
    }
    function getFormLoadEvent(events) {
      return events.find(isFormLoadEvent);
    }
    function formBlockLoadTime(threshold = 2 * 60 * 1e3) {
      return function time(bundle) {
        const sortedEvents = bundle.events.sort((a, b) => a.timeDelta - b.timeDelta);
        const formLoad = getFormLoadEvent(sortedEvents);
        if (threshold && formLoad?.timeDelta > threshold) {
          return void 0;
        }
        if (formLoad?.timeDelta > 0) {
          return formLoad?.timeDelta / 1e3;
        }
        return void 0;
      };
    }
    var timeOnPage = (bundle) => {
      const deltas = bundle.events.map((evt) => evt.timeDelta).filter((delta) => delta > 0);
      if (deltas.length === 0) return void 0;
      return Math.max(...deltas) / 1e3;
    };
    async function getDataChunks(rumBundles) {
      const { DataChunks, series, facets, facetFns } = await loadRumDistiller();
      const { url, userAgent, checkpoint, acquisitionSource } = facets;
      const { checkpointSource, checkpointTarget } = facetFns;
      const { pageViews, lcp, cls, inp, ttfb } = series;
      const dataChunks = new DataChunks();
      dataChunks.load([{ rumBundles }]);
      dataChunks.addSeries("pageViews", pageViews);
      dataChunks.addSeries("formBlockLoadTime", formBlockLoadTime());
      dataChunks.addFacet("url", url, "every", "none");
      dataChunks.addFacet("userAgent", userAgent, "some", "none");
      dataChunks.addFacet("checkpoint", checkpoint, "every", "none");
      dataChunks.addFacet("enter.source", checkpointSource("enter"), "some", "none");
      dataChunks.addFacet("navigate.source", checkpointSource("navigate"), "every", "never");
      dataChunks.addFacet("error", errorDetails, "some", "none");
      dataChunks.addFacet("loadresource.source", checkpointSource("loadresource"), "every", "never");
      dataChunks.addSeries("timeOnPage", timeOnPage);
      dataChunks.addSeries("formBlockLoadTime", formBlockLoadTime(2 * 60 * 1e3));
      dataChunks.addSeries("lcp", lcp);
      dataChunks.addSeries("cls", cls);
      dataChunks.addSeries("inp", inp);
      dataChunks.addSeries("ttfb", ttfb);
      dataChunks.addFacet("click.source", checkpointSource("click"), "some", "never");
      dataChunks.addFacet("click.target", checkpointTarget("click"), "some", "never");
      dataChunks.addFacet("viewblock.source", checkpointSource("viewblock"), "every", "never");
      dataChunks.addFacet("fill.source", checkpointSource("fill"), "some", "never");
      dataChunks.addFacet("loadresource.source", checkpointSource("loadresource"), "some");
      dataChunks.addFacet("viewmedia.target", checkpointTarget("viewmedia"), "some", "never");
      dataChunks.addFacet("missingresource.source", checkpointSource("missingresource"), "some", "never");
      const mrTargetRaw = checkpointTarget("missingresource");
      dataChunks.addFacet("missingresource.target", (bundle) => (mrTargetRaw(bundle) || []).map(String), "some", "never");
      dataChunks.addFacet("period", (bundle) => [new Date(bundle.timeSlot).toISOString().slice(0, 10)], "some", "none");
      dataChunks.addFacet("acquisitionSource", acquisitionSource, "some", "none");
      return dataChunks;
    }
    module2.exports = { getDataChunks };
  }
});

// src/optel-query/query.js
var require_query = __commonJS({
  "src/optel-query/query.js"(exports2, module2) {
    var { fetchDomainKey } = require_domainkey();
    var { Loader } = require_loader();
    var { getDataChunks } = require_datachunks();
    function formatTime(seconds) {
      if (seconds < 1) return `${(seconds * 1e3).toFixed(0)}ms`;
      return `${seconds.toFixed(2)}s`;
    }
    var CWV_MIN_COUNT = 10;
    function normalizeLCPMilliseconds(lcpValue) {
      const v = Number(lcpValue);
      if (!Number.isFinite(v)) return NaN;
      return v < 50 ? v * 1e3 : v;
    }
    function formatINP(inpValue) {
      const v = Number(inpValue);
      const ms = v < 10 ? v * 1e3 : v;
      return { ms, text: `${(ms / 1e3).toFixed(2)}s` };
    }
    var seriesValues = {
      lcp: (series) => {
        let result = "N/A";
        const raw = series?.percentile?.(75);
        const count = Number(series?.count || 0);
        const ms = normalizeLCPMilliseconds(raw);
        if (count >= CWV_MIN_COUNT && Number.isFinite(ms)) result = formatTime(ms / 1e3);
        return { p75: result };
      },
      cls: (series) => {
        let result = "N/A";
        const raw = series?.percentile?.(75);
        const count = Number(series?.count || 0);
        if (count >= CWV_MIN_COUNT && Number.isFinite(raw)) result = raw.toFixed(3);
        return { p75: result };
      },
      inp: (series) => {
        let result = "N/A";
        const raw = series?.percentile?.(75);
        const count = Number(series?.count || 0);
        if (count >= CWV_MIN_COUNT && Number.isFinite(raw)) result = formatINP(raw).text;
        return { p75: result };
      },
      formBlockLoadTime: (series) => ({
        min: formatTime(series?.min),
        max: formatTime(series?.max),
        p50: formatTime(series?.percentile(50)),
        p75: formatTime(series?.percentile(75)),
        p95: formatTime(series?.percentile(95))
      })
    };
    function getSamplingRatios(dataChunks) {
      return dataChunks.totals.pageViews.values.reduce((acc, v) => {
        acc[v] = (acc[v] || 0) + 1;
        return acc;
      }, {});
    }
    async function getData(domain, startDate, endDate, interval, domainKey) {
      const resolvedKey = await fetchDomainKey(domain, domainKey);
      const loader = new Loader(domain, resolvedKey);
      const bundles = await loader.fetchPeriod(startDate, endDate, [], interval);
      return getDataChunks(bundles);
    }
    async function query2(domain, startDate, endDate, queryFilter = {}, series = [], interval, domainKey) {
      const dataChunks = await getData(domain, startDate, endDate, interval, domainKey);
      dataChunks.filter = queryFilter;
      const out = {
        result: dataChunks.totals.pageViews.sum,
        samplingRatios: getSamplingRatios(dataChunks)
      };
      if (series && series.length > 0) {
        const totals = dataChunks.totals;
        out.series = {};
        for (const name of series) {
          if (totals[name] != null) out.series[name] = seriesValues[name](totals[name]);
        }
      }
      return out;
    }
    async function getFacetValues2(domain, startDate, endDate, facetName, queryFilter = {}, series = [], interval, domainKey) {
      const dataChunks = await getData(domain, startDate, endDate, interval, domainKey);
      const totalPageViews = dataChunks.totals.pageViews.sum;
      const totalSamplingRatios = getSamplingRatios(dataChunks);
      dataChunks.filter = queryFilter;
      const filteredPageViews = dataChunks.totals.pageViews.sum;
      const filteredSamplingRatios = getSamplingRatios(dataChunks);
      const facetValues = dataChunks.facets[facetName].map((facet) => {
        const samplingRatios = facet.entries.reduce((acc, entry) => {
          acc[entry.weight] = (acc[entry.weight] || 0) + 1;
          return acc;
        }, {});
        const row = { value: facet.value, count: facet.weight, samplingRatios };
        if (series) {
          series.forEach((name) => {
            row[name] = seriesValues[name](facet.metrics[name]);
          });
        }
        return row;
      });
      return { facetValues, totalPageViews, filteredPageViews, samplingRatios: totalSamplingRatios, filteredSamplingRatios };
    }
    module2.exports = { getData, query: query2, getFacetValues: getFacetValues2 };
  }
});

// src/optel-query/cli.js
var rawFs = require("fs");
var fs = rawFs.promises || rawFs;
var { query, getFacetValues } = require_query();
var VALID_INTERVALS = ["hourly", "daily", "monthly"];
function parseArgs() {
  const args = process.argv.slice(2);
  if (args.includes("--list-facets")) {
    console.log(`
Available Facets for Filtering
===============================

Core Facets:
  url              - URL paths (sanitized)
  userAgent        - Device type (desktop, mobile, tablet, bot)
  checkpoint       - Event types (click, fill, lcp, etc.)
  error            - Error details (source | target)

Checkpoint-Specific Facets (source):
  navigate.source          - Navigation source
  click.source             - Clicked element selector
  viewblock.source         - Viewed content block
  fill.source              - Form field filled
  loadresource.source      - Resource loaded
  missingresource.source   - URL of failed resource (404, 405, etc.)

Checkpoint-Specific Facets (target):
  click.target             - Click destination URL
  viewmedia.target         - Media viewed
  missingresource.target   - HTTP status code of failed resource (e.g. 404, 405)

Time:
  period            - Calendar date of the bundle (YYYY-MM-DD); use with --facet-values for daily trends

Acquisition:
  acquisitionSource - Acquisition source (parsed from enter events)

Usage:
  Use these facet names in --query JSON objects or with --facet-values

Examples:
  --query '{"url":["/home"],"checkpoint":["click"]}'
  --facet-values checkpoint
  --facet-values error

For detailed documentation, see facets.md
`);
    process.exit(0);
  }
  if (args.includes("--list-series")) {
    console.log(`
Available Series
================

  pageViews         - Weighted page-view count (always available)
  lcp               - Largest Contentful Paint (p75)
  cls               - Cumulative Layout Shift (p75)
  inp               - Interaction to Next Paint (p75)
  ttfb              - Time to First Byte (p75)
  timeOnPage        - Time on page (derived from event timeDeltas)
  formBlockLoadTime - Form-block load time (min/max/p50/p75/p95)`);
    process.exit(0);
  }
  if (args.includes("--help") || args.includes("-h") || args.length === 0) {
    console.log(`
optel-query CLI
===============

Usage:
  optel-query.jsh <domain> <startDate> <endDate> [options]

Arguments:
  domain       Domain to query (e.g., 'example.com')
  startDate    Start date in YYYY-MM-DD format
  endDate      End date in YYYY-MM-DD format

Options:
  --query <json>            Filter query as JSON, e.g. '{"url":["/home"]}'
  --facet-values <name>     Get values for a facet instead of a count
  --series <csv>            Series metrics to include (lcp,cls,inp,formBlockLoadTime)
  --interval <granularity>  hourly | daily | monthly; omit for auto-selection
  --domainkey <key>         Domain key to use directly, bypassing DOMAINKEY_FILE / RUM_ADMIN_KEY lookup
  --output <path>           Write result JSON to file instead of stdout
  --list-facets             Print facet catalog and exit
  --list-series             Print series catalog and exit
  --help, -h                Show this help and exit
`);
    process.exit(0);
  }
  if (args.length < 3) {
    console.error("Error: Missing required arguments");
    console.error("Usage: optel-query.jsh <domain> <startDate> <endDate> [options]");
    console.error("Run with --help for more information");
    process.exit(1);
  }
  const config = {
    domain: args[0],
    startDate: args[1],
    endDate: args[2],
    query: {},
    series: [],
    output: null,
    facetValues: null,
    interval: void 0,
    domainKey: void 0
  };
  for (let i = 3; i < args.length; i++) {
    const arg = args[i];
    if (arg === "--query" && i + 1 < args.length) {
      try {
        config.query = JSON.parse(args[++i]);
      } catch (e) {
        console.error(`Error: Invalid JSON in --query: ${e.message}`);
        process.exit(1);
      }
    } else if (arg === "--output" && i + 1 < args.length) {
      config.output = args[++i];
    } else if (arg === "--series" && i + 1 < args.length) {
      config.series = args[++i].split(",").map((s) => s.trim()).filter(Boolean);
    } else if (arg === "--facet-values" && i + 1 < args.length) {
      config.facetValues = args[++i];
    } else if (arg === "--interval" && i + 1 < args.length) {
      const v = args[++i];
      if (!VALID_INTERVALS.includes(v)) {
        console.error(`Error: --interval must be one of ${VALID_INTERVALS.join(", ")}`);
        process.exit(1);
      }
      config.interval = v;
    } else if (arg === "--domainkey" && i + 1 < args.length) {
      config.domainKey = args[++i];
    }
  }
  return config;
}
async function main() {
  const config = parseArgs();
  console.log("Fetching RUM data...");
  console.log(`Domain: ${config.domain}`);
  console.log(`Date Range: ${config.startDate} to ${config.endDate}`);
  if (Object.keys(config.query).length > 0) console.log(`Query Filter: ${JSON.stringify(config.query)}`);
  if (config.facetValues) console.log(`Getting values for facet: ${config.facetValues}`);
  if (config.series.length > 0) console.log(`Series: ${config.series.join(", ")}`);
  if (config.interval) console.log(`Interval: ${config.interval}`);
  if (config.domainKey) console.log("Domain key: (provided directly)");
  console.log("");
  try {
    let result;
    if (config.facetValues) {
      result = await getFacetValues(
        config.domain,
        config.startDate,
        config.endDate,
        config.facetValues,
        config.query,
        config.series,
        config.interval,
        config.domainKey
      );
    } else {
      result = await query(
        config.domain,
        config.startDate,
        config.endDate,
        config.query,
        config.series,
        config.interval,
        config.domainKey
      );
    }
    const output = JSON.stringify({ result }, null, 2);
    if (config.output) {
      const lastSlash = config.output.lastIndexOf("/");
      if (lastSlash > 0) await fs.mkdir(config.output.slice(0, lastSlash), { recursive: true });
      await fs.writeFile(config.output, output);
      console.log(`Results written to: ${config.output}`);
    } else {
      console.log(output);
    }
  } catch (error) {
    console.error("\nError executing query:");
    console.error(error.message);
    if (error.stack) {
      console.error("\nStack trace:");
      console.error(error.stack);
    }
    process.exit(1);
  }
}
(async () => {
  await main();
})().catch((err) => {
  console.error(err.message);
  if (err.stack) console.error(err.stack);
  process.exit(1);
});
