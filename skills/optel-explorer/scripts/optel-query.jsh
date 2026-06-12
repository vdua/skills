var __getOwnPropNames = Object.getOwnPropertyNames;
var __commonJS = (cb, mod) => function __require() {
  return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports;
};

// src/optel-query/domainkey.js
var require_domainkey = __commonJS({
  "src/optel-query/domainkey.js"(exports2, module2) {
    var rawFs2 = require("fs");
    var fsAsync2 = rawFs2.promises || rawFs2;
    var DEFAULT_DOMAINKEY_FILE = "/optel/domainkey.json";
    async function readDomainkeyFile() {
      const filePath = process.env.DOMAINKEY_FILE || DEFAULT_DOMAINKEY_FILE;
      try {
        const contents = String(await fsAsync2.readFile(filePath));
        return JSON.parse(contents);
      } catch (e) {
        return null;
      }
    }
    async function writeDomainkeyFile(domain, domainkey) {
      const filePath = process.env.DOMAINKEY_FILE || DEFAULT_DOMAINKEY_FILE;
      let existing = {};
      try {
        const contents = String(await fsAsync2.readFile(filePath));
        existing = JSON.parse(contents);
      } catch (e) {
      }
      existing[domain] = domainkey;
      try {
        await fsAsync2.writeFile(filePath, JSON.stringify(existing, null, 2));
      } catch (e) {
      }
    }
    async function fetchDomainKey(domain, override) {
      const __dbg = (m) => { if (typeof process !== "undefined" && (process.env.OPTEL_DEBUG || (process.argv || []).includes("--debug"))) process.stderr.write(`[optel-debug] fetchDomainKey: ${m}\n`); };
      __dbg(`start domain=${domain} override=${override ? "yes" : "no"}`);
      if (override) { __dbg("using --domainkey override, returning"); return override; }
      try {
        __dbg("reading domainkey file...");
        const keyMap = await readDomainkeyFile();
        __dbg(`domainkey file read; has key for domain=${!!(keyMap && keyMap[domain])}`);
        if (keyMap && keyMap[domain]) return keyMap[domain];
        __dbg("no local key; falling back to RUM_ADMIN_KEY admin fetch path");
        const auth = process.env.RUM_ADMIN_KEY;
        if (!auth) {
          throw new Error(
            `No domainkey found for "${domain}". Run: optel-explorer add-domain-key ${domain} <key>  \u2014 or pass --domainkey <key> directly.`
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
    var __depDbg = (m) => { if (typeof process !== "undefined" && (process.env.OPTEL_DEBUG || (process.argv || []).includes("--debug"))) process.stderr.write(`[optel-debug] deps: ${m}\n`); };
    async function loadRumDistiller() {
      if (!_rd) {
        __depDbg(`loading rum-distiller (cached=no) from ${typeof document !== "undefined" ? RD_URL : "@adobe/rum-distiller"}`);
        _rd = typeof document !== "undefined" ? await import(RD_URL) : require("@adobe/rum-distiller");
        __depDbg("rum-distiller loaded");
      } else {
        __depDbg("rum-distiller cache hit");
      }
      return _rd;
    }
    async function loadRumDistillerUtils() {
      if (!_rdUtils) {
        __depDbg(`loading rum-distiller utils (cached=no) from ${typeof document !== "undefined" ? RD_UTILS_URL : "@adobe/rum-distiller/utils.js"}`);
        _rdUtils = typeof document !== "undefined" ? await import(RD_UTILS_URL) : require("@adobe/rum-distiller/utils.js");
        __depDbg("rum-distiller utils loaded");
      } else {
        __depDbg("rum-distiller utils cache hit");
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
        const __dbg = (m) => { if (typeof process !== "undefined" && (process.env.OPTEL_DEBUG || (process.argv || []).includes("--debug"))) process.stderr.write(`[optel-debug] fetch: ${m}\n`); };
        // Redact the domainkey query param from logs.
        const __safeUrl = String(apiRequestURL).replace(/([?&]domainkey=)[^&]*/i, "$1<redacted>");
        const __start = Date.now();
        const resp = await fetch(apiRequestURL);
        __dbg(`status=${resp.status} for ${__safeUrl} (${Date.now() - __start}ms)`);
        // Surface non-OK HTTP statuses instead of silently returning empty bundles.
        // Callers decide how to react (e.g. 413 -> retry that day at daily granularity;
        // 401/403/5xx -> abort and report). Previously every non-2xx (and every network
        // or parse error) was swallowed as `{ rumBundles: [] }`, which made a hard failure
        // indistinguishable from a date range that genuinely has no data.
        if (!resp.ok) {
          const err = new Error(`RUM bundles API returned HTTP ${resp.status} for ${__safeUrl}`);
          err.name = "OptelHttpError";
          err.status = resp.status;
          err.safeUrl = __safeUrl;
          throw err;
        }
        const json = await resp.json();
        __dbg(`parsed json; rumBundles=${(json && json.rumBundles ? json.rumBundles.length : 0)} (${Date.now() - __start}ms)`);
        return json;
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
        let rumBundles;
        try {
          ({ rumBundles } = await this.fetch(this.apiURL(datePath, hour)));
        } catch (err) {
          // A 413 (Payload Too Large) on the hourly endpoint is common for very
          // high-traffic domains: a single hour of bundles exceeds the API size
          // limit. Signal the caller (fetchPeriod hourly branch) to retry the whole
          // day at daily granularity, which is served pre-aggregated and stays under
          // the limit. Any other HTTP error (401/403/5xx) propagates so the top level
          // can report it rather than silently returning zero results.
          if (err && err.status === 413) {
            return { date, hour, rumBundles: [], __http413: true };
          }
          throw err;
        }
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
        const __dbg = (m) => { if (typeof process !== "undefined" && (process.env.OPTEL_DEBUG || (process.argv || []).includes("--debug"))) process.stderr.write(`[optel-debug] fetchPeriod: ${m}\n`); };
        const start = new Date(startDate);
        const originalStart = new Date(start);
        const end = endDate ? new Date(endDate) : /* @__PURE__ */ new Date();
        const diff = end.getTime() - start.getTime();
        if (diff < 0) throw new Error("Start date must be before end date");
        const __dayCount = Math.round(diff / (1e3 * 60 * 60 * 24)) + 1;
        const __mode = (diff <= 1e3 * 60 * 60 * 24 * 7 && !interval || interval === "hourly") ? "hourly"
          : (diff <= 1e3 * 60 * 60 * 24 * 31 && !interval || interval === "daily") ? "daily" : "monthly";
        __dbg(`start=${startDate} end=${endDate} diffDays=${__dayCount} interval=${interval || "auto"} -> mode=${__mode}`);
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
          __dbg(`hourly: ${chunks.length} day-chunk(s), ${chunks.reduce((a, c) => a + c.length, 0)} hour-requests total`);
          let __ci = 0;
          for (const chunk of chunks) {
            __dbg(`hourly: fetching chunk ${++__ci}/${chunks.length} (${chunk.length} hours) in parallel...`);
            const bundles = await Promise.all(chunk.map((date) => this.fetchUTCHour(date, null, null)));
            // If any hour of this day returned 413, the hourly endpoint can't serve
            // this domain at hourly granularity. Drop the (empty) hourly buckets for the
            // day and refetch the whole day once at daily granularity. The hourly buckets
            // that DID succeed for the same day are also discarded to avoid double-counting,
            // since the daily fetch already covers the entire day.
            if (bundles.some((b) => b && b.__http413)) {
              const dayISO = chunk[0];
              __dbg(`hourly: chunk ${__ci}/${chunks.length} hit HTTP 413; falling back to daily fetch for ${String(dayISO).split("T")[0]}`);
              const dayBucket = await this.fetchUTCDay(dayISO, null, null);
              allBundles.push(dayBucket);
            } else {
              allBundles.push(...bundles);
            }
            __dbg(`hourly: chunk ${__ci}/${chunks.length} done; cumulative period-buckets=${allBundles.length}`);
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
          __dbg(`daily: ${chunks.length} chunk(s), ${chunks.reduce((a, c) => a + c.length, 0)} day-requests total`);
          let __ci = 0;
          for (const chunk of chunks) {
            __dbg(`daily: fetching chunk ${++__ci}/${chunks.length} (${chunk.length} days) in parallel...`);
            const bundles = await Promise.all(chunk.map((iso) => this.fetchUTCDay(iso, null, null)));
            allBundles.push(...bundles);
            __dbg(`daily: chunk ${__ci}/${chunks.length} done; cumulative period-buckets=${allBundles.length}`);
          }
        } else {
          const months = Math.round(diff / (1e3 * 60 * 60 * 24 * 31)) + 1;
          __dbg(`monthly: fetching ${months} month-request(s) in parallel...`);
          const promises = [];
          for (let i = 0; i < months; i += 1) {
            promises.push(this.fetchUTCMonth(start.toISOString(), originalStart, end));
            start.setMonth(start.getMonth() + 1);
          }
          allBundles.push(...await Promise.all(promises));
          __dbg(`monthly: done; period-buckets=${allBundles.length}`);
        }
        const __flat = allBundles.flatMap(
          (b) => b.rumBundles.filter((bundle) => !isBotTraffic(bundle)).map(filterEvents(allCheckpoints))
        );
        __dbg(`flattened bundles (bots removed) = ${__flat.length}`);
        return __flat;
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
      const { pageViews, lcp, cls, inp, ttfb, visits, bounces, engagement, earned, organic } = series;
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
      dataChunks.addSeries("visits", visits);
      dataChunks.addSeries("bounces", bounces);
      dataChunks.addSeries("engagement", engagement);
      dataChunks.addSeries("earned", earned);
      dataChunks.addSeries("organic", organic);
      dataChunks.addFacet("click.source", checkpointSource("click"), "some", "never");
      dataChunks.addFacet("click.target", checkpointTarget("click"), "some", "never");
      dataChunks.addFacet("viewblock.source", checkpointSource("viewblock"), "every", "never");
      dataChunks.addFacet("fill.source", checkpointSource("fill"), "some", "never");
      dataChunks.addFacet("loadresource.source", checkpointSource("loadresource"), "some");
      dataChunks.addFacet("viewmedia.target", checkpointTarget("viewmedia"), "some", "never");
      dataChunks.addFacet("missingresource.source", checkpointSource("missingresource"), "some", "never");
      const lrTargetRaw = checkpointTarget("loadresource");
      dataChunks.addFacet("loadresource.target", (bundle) => (lrTargetRaw(bundle) || []).map(String), "some", "never");
      const mrTargetRaw = checkpointTarget("missingresource");
      dataChunks.addFacet("missingresource.target", (bundle) => (mrTargetRaw(bundle) || []).map(String), "some", "never");
      dataChunks.addFacet("period", (bundle) => [new Date(bundle.timeSlot).toISOString().slice(0, 10)], "some", "none");
      dataChunks.addFacet("acquisitionSource", acquisitionSource, "some", "none");
      // Source/target facets for cwv-lcp, utm, language, consent checkpoints.
      dataChunks.addFacet("cwv-lcp.source", checkpointSource("cwv-lcp"), "some", "never");
      dataChunks.addFacet("utm.source", checkpointSource("utm"), "some", "never");
      dataChunks.addFacet("utm.target", checkpointTarget("utm"), "some", "never");
      dataChunks.addFacet("language.source", checkpointSource("language"), "some", "never");
      dataChunks.addFacet("language.target", checkpointTarget("language"), "some", "never");
      dataChunks.addFacet("consent.source", checkpointSource("consent"), "some", "never"); // provider
      dataChunks.addFacet("consent.target", checkpointTarget("consent"), "some", "never"); // show/hidden/suppressed
      // Foreground/background visibility is carried in the standard `target` field
      // on navigation checkpoints (values: visible/hidden). Exposed via the regular
      // *.target facet pattern rather than a bespoke "visibility" facet.
      dataChunks.addFacet("enter.target", checkpointTarget("enter"), "some", "never");
      dataChunks.addFacet("navigate.target", checkpointTarget("navigate"), "some", "never");
      dataChunks.addFacet("back_forward.target", checkpointTarget("back_forward"), "some", "never");
      dataChunks.addFacet("reload.target", checkpointTarget("reload"), "some", "never");
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
      }),
      ttfb: (series) => {
        let result = "N/A";
        const raw = series?.percentile?.(75);
        const count = Number(series?.count || 0);
        if (count >= CWV_MIN_COUNT && Number.isFinite(raw)) result = formatTime(raw / 1e3);
        return { p75: result };
      },
      timeOnPage: (series) => ({
        mean: formatTime(series?.mean),
        p50: formatTime(series?.percentile?.(50)),
        p75: formatTime(series?.percentile?.(75))
      }),
      // Count-style series (visits/bounces/engagement/earned/organic): the
      // underlying rum-distiller series returns the bundle weight when the
      // bundle qualifies (else 0), so the weighted count is the aggregate
      // `.sum`. Both the query path (totals[name]) and the facet path
      // (facet.metrics[name]) expose `.sum`, so this works for both.
      visits: (series) => ({ sum: Math.round(series?.sum || 0) }),
      bounces: (series) => ({ sum: Math.round(series?.sum || 0) }),
      engagement: (series) => ({ sum: Math.round(series?.sum || 0) }),
      earned: (series) => ({ sum: Math.round(series?.sum || 0) }),
      organic: (series) => ({ sum: Math.round(series?.sum || 0) })
    };
    function getSamplingRatios(dataChunks) {
      return dataChunks.totals.pageViews.values.reduce((acc, v) => {
        acc[v] = (acc[v] || 0) + 1;
        return acc;
      }, {});
    }
    async function getData(domain, startDate, endDate, interval, domainKey) {
      const __dbg = (m) => { if (typeof process !== "undefined" && (process.env.OPTEL_DEBUG || (process.argv || []).includes("--debug"))) process.stderr.write(`[optel-debug] getData: ${m}\n`); };
      __dbg("resolving domain key...");
      const resolvedKey = await fetchDomainKey(domain, domainKey);
      __dbg(`domain key resolved (present=${!!resolvedKey}); constructing Loader`);
      const loader = new Loader(domain, resolvedKey);
      // Probe: validate domain key with a single cheap request before firing the full batch.
      // Use the most recent day as the probe target — it is always a valid data path.
      __dbg("probing domain key with single request...");
      const probeDate = new Date(endDate || new Date());
      const probeY = probeDate.getUTCFullYear();
      const probeM = String(probeDate.getUTCMonth() + 1).padStart(2, "0");
      const probeD = String(probeDate.getUTCDate()).padStart(2, "0");
      const probeURL = loader.apiURL(`${probeY}/${probeM}/${probeD}`);
      const probeResp = await fetch(probeURL);
      __dbg(`probe status=${probeResp.status}`);
      if (probeResp.status === 403 || probeResp.status === 401) {
        throw new Error(
          `Invalid or expired domain key for "${domain}" (HTTP ${probeResp.status}). ` +
          `Run: optel-explorer generate ${domain}  — or pass --domainkey <key> directly.`
        );
      }
      __dbg("probe OK; calling fetchPeriod...");
      const bundles = await loader.fetchPeriod(startDate, endDate, [], interval);
      __dbg(`fetchPeriod returned ${bundles.length} bundles; building DataChunks...`);
      const dc = await getDataChunks(bundles);
      __dbg("DataChunks built");
      return dc;
    }
    // ── Result computation on an ALREADY-BUILT DataChunks ──────────────────────
    // These pure(ish) helpers take a built `dataChunks` and compute one result by
    // (re)setting its filter. Splitting them out lets a single fetched/parsed
    // DataChunks answer MANY queries in one process (see runBatch2) instead of
    // re-fetching the whole window per question.

    function computeCount(dataChunks, queryFilter = {}, series = []) {
      dataChunks.filter = queryFilter;
      const out = {
        result: dataChunks.totals.pageViews.sum,
        samplingRatios: getSamplingRatios(dataChunks)
      };
      if (series && series.length > 0) {
        const totals = dataChunks.totals;
        out.series = {};
        for (const name of series) {
          if (totals[name] != null && seriesValues[name]) out.series[name] = seriesValues[name](totals[name]);
        }
      }
      return out;
    }

    function computeFacetValues(dataChunks, facetName, queryFilter = {}, series = []) {
      const totalPageViews = dataChunks.totals.pageViews.sum;
      const totalSamplingRatios = getSamplingRatios(dataChunks);
      dataChunks.filter = queryFilter;
      const filteredPageViews = dataChunks.totals.pageViews.sum;
      const filteredSamplingRatios = getSamplingRatios(dataChunks);
      if (!dataChunks.facets[facetName]) {
        throw new Error(
          `Unknown facet "${facetName}". Registered facets: ${Object.keys(dataChunks.facets).join(", ")}`
        );
      }
      const facetValues = dataChunks.facets[facetName].map((facet) => {
        const samplingRatios = facet.entries.reduce((acc, entry) => {
          acc[entry.weight] = (acc[entry.weight] || 0) + 1;
          return acc;
        }, {});
        const row = { value: facet.value, count: facet.weight, samplingRatios };
        if (series) {
          series.forEach((name) => {
            if (seriesValues[name]) row[name] = seriesValues[name](facet.metrics[name]);
          });
        }
        return row;
      });
      return { facetValues, totalPageViews, filteredPageViews, samplingRatios: totalSamplingRatios, filteredSamplingRatios };
    }

    async function query2(domain, startDate, endDate, queryFilter = {}, series = [], interval, domainKey) {
      const dataChunks = await getData(domain, startDate, endDate, interval, domainKey);
      return computeCount(dataChunks, queryFilter, series);
    }
    async function getFacetValues2(domain, startDate, endDate, facetName, queryFilter = {}, series = [], interval, domainKey) {
      const dataChunks = await getData(domain, startDate, endDate, interval, domainKey);
      return computeFacetValues(dataChunks, facetName, queryFilter, series);
    }

    // ── Batch: fetch the window + build DataChunks ONCE, answer N requests ──────
    // requests: array of { id?, query?, facetValues?, series? }.
    //   - facetValues present -> a facet-values request (like getFacetValues)
    //   - otherwise            -> a count request (like query)
    // Returns { results: [ { id, type, result | error } ] } preserving input order.
    // One network fetch + one DataChunks build total, regardless of request count.
    async function runBatch2(domain, startDate, endDate, requests = [], interval, domainKey) {
      const __dbg = (m) => { if (typeof process !== "undefined" && (process.env.OPTEL_DEBUG || (process.argv || []).includes("--debug"))) process.stderr.write(`[optel-debug] runBatch: ${m}\n`); };
      const dataChunks = await getData(domain, startDate, endDate, interval, domainKey);
      __dbg(`DataChunks built once; answering ${requests.length} request(s) in-memory`);
      const results = [];
      for (let i = 0; i < requests.length; i += 1) {
        const req = requests[i] || {};
        const id = req.id != null ? req.id : i;
        const series = Array.isArray(req.series) ? req.series : [];
        const queryFilter = req.query || {};
        try {
          if (req.facetValues) {
            results.push({ id, type: "facetValues", facet: req.facetValues, result: computeFacetValues(dataChunks, req.facetValues, queryFilter, series) });
          } else {
            results.push({ id, type: "count", result: computeCount(dataChunks, queryFilter, series) });
          }
        } catch (err) {
          // One bad request (e.g. unknown facet) must not abort the whole batch.
          results.push({ id, type: req.facetValues ? "facetValues" : "count", error: err && err.message ? err.message : String(err) });
        }
      }
      return { results };
    }

    module2.exports = { getData, query: query2, getFacetValues: getFacetValues2, runBatch: runBatch2 };
  }
});

// src/optel-query/cli.js
var rawFs = require("fs");
var fsAsync = rawFs.promises || rawFs;
var { query, getFacetValues, runBatch } = require_query();
var VALID_INTERVALS = ["hourly", "daily", "monthly"];
// --- DEBUG INSTRUMENTATION ---
// Enable with OPTEL_DEBUG=1 (env) or by passing --debug as a CLI flag.
// All debug output goes to stderr so stdout JSON stays clean/parseable.
var DEBUG = !!process.env.OPTEL_DEBUG || process.argv.includes("--debug");
var __t0 = Date.now();
function dbg(...args) {
  if (!DEBUG) return;
  const ms = Date.now() - __t0;
  process.stderr.write(`[optel-debug +${ms}ms] ` + args.join(" ") + "\n");
}
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
  enter.source             - External referrer / traffic source
  click.source             - Clicked element selector
  viewblock.source         - Viewed content block
  fill.source              - Form field filled
  loadresource.source      - Resource loaded
  missingresource.source   - URL of failed resource (404, 405, etc.)
  cwv-lcp.source           - LCP element selector (e.g. img, .body-m)
  utm.source               - UTM parameter keys/values
  language.source          - Page language source (e.g. en-US)
  consent.source           - Consent provider

Checkpoint-Specific Facets (target):
  click.target             - Click destination URL
  viewmedia.target         - Media viewed
  loadresource.target      - Load time of network resource in ms (e.g. 250, 1200)
  missingresource.target   - HTTP status code of failed resource (e.g. 404, 405)
  utm.target               - UTM target values
  language.target          - Page language target (e.g. en-US, pt-BR, en-GB)
  consent.target           - Consent state (show/hidden/accept/reject/suppressed)

Visibility (foreground/background) — read 'target' on navigation checkpoints
  (values: visible = foreground tab, hidden = background tab/prerender):
  enter.target             - Visibility at page enter
  navigate.target          - Visibility on internal navigation
  back_forward.target      - Visibility on back/forward navigation
  reload.target            - Visibility on reload

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
  timeOnPage        - Time on page (derived from event timeDeltas; mean/p50/p75)
  formBlockLoadTime - Form-block load time (min/max/p50/p75/p95)
  visits            - Weighted count of visits (session starts) -> {sum}
  bounces           - Weighted count of bounces (visits with no click) -> {sum}
  engagement        - Weighted count of engaged page views -> {sum}
  earned            - Weighted count of earned visits (not paid/owned) -> {sum}
  organic           - Weighted count of organic visits (not paid) -> {sum}`);
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
  --series <csv>            Series metrics to include (lcp,cls,inp,ttfb,timeOnPage,visits,bounces,engagement,earned,organic,formBlockLoadTime)
  --batch <path>            Answer MANY requests with ONE fetch. <path> is a JSON
                            file: an array of {id?, query?, facetValues?, series?}
                            (or {requests:[...]}). The window is fetched and parsed
                            ONCE; every request is computed in-memory. Use this for
                            reports to avoid re-fetching the same days per query.
                            Ignores --query/--facet-values/--series. Output:
                            {result:{results:[{id,type,result|error},...]}}.
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
    batch: null,
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
    } else if (arg === "--batch" && i + 1 < args.length) {
      config.batch = args[++i];
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
  const args = process.argv.slice(2);
  dbg("argv => " + args[0])
  const config = parseArgs();
  dbg(`parsed args: domain=${config.domain} start=${config.startDate} end=${config.endDate} facet=${config.facetValues || "-"} series=[${config.series.join(",")}] interval=${config.interval || "auto"} output=${config.output || "stdout"}`);
  console.log("Fetching RUM data...");
  console.log(`Domain: ${config.domain}`);
  console.log(`Date Range: ${config.startDate} to ${config.endDate}`);
  if (config.batch) console.log(`Batch requests file: ${config.batch}`);
  if (!config.batch && Object.keys(config.query).length > 0) console.log(`Query Filter: ${JSON.stringify(config.query)}`);
  if (!config.batch && config.facetValues) console.log(`Getting values for facet: ${config.facetValues}`);
  if (!config.batch && config.series.length > 0) console.log(`Series: ${config.series.join(", ")}`);
  if (config.interval) console.log(`Interval: ${config.interval}`);
  if (config.domainKey) console.log("Domain key: (provided directly)");
  console.log("");
  try {
    let result;
    if (config.batch) {
      dbg("entering runBatch()...");
      let raw;
      try {
        raw = await fsAsync.readFile(config.batch, "utf8");
      } catch (e) {
        throw new Error(`Could not read --batch file "${config.batch}": ${e.message}`);
      }
      let parsed;
      try {
        parsed = JSON.parse(raw);
      } catch (e) {
        throw new Error(`--batch file "${config.batch}" is not valid JSON: ${e.message}`);
      }
      // Accept either a bare array or { requests: [...] }.
      const requests = Array.isArray(parsed) ? parsed : (parsed && Array.isArray(parsed.requests) ? parsed.requests : null);
      if (!requests) {
        throw new Error(`--batch file must be a JSON array of requests, or an object {"requests":[...]}.`);
      }
      console.log(`Batch: ${requests.length} request(s), one fetch`);
      console.log("");
      result = await runBatch(
        config.domain,
        config.startDate,
        config.endDate,
        requests,
        config.interval,
        config.domainKey
      );
    } else if (config.facetValues) {
      dbg("entering getFacetValues()...");
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
      dbg("entering query()...");
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
    dbg("query/getFacetValues returned; serializing output");
    const output = JSON.stringify({ result }, null, 2);
    if (config.output) {
      const lastSlash = config.output.lastIndexOf("/");
      if (lastSlash > 0) await fsAsync.mkdir(config.output.slice(0, lastSlash), { recursive: true });
      await fsAsync.writeFile(config.output, output);
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
return main().catch((err) => {
  console.error(err.message);
  if (err.stack) console.error(err.stack);
  process.exit(1);
});
