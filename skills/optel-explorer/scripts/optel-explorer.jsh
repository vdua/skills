// optel-explorer.jsh — AEM CS Workspace API client
// Performs direct API calls from the aemcs-workspace browser page context.
//
// Usage:
//   optel-explorer generate <domain>
//
// Requirements:
//   - https://aemcs-workspace.adobe.com must be open in a browser tab and logged in

const rawFs = require("fs");
const fsx = rawFs.promises || rawFs;
const AEMCS_URL = "https://aemcs-workspace.adobe.com";

// Scratch-file paths, resolved lazily by initTmpDir() before first use. `/tmp` is
// NOT writable inside the optel-explorer scoop sandbox (the scoop can only write
// /optel, /shared, /scoops/optel-explorer), which previously made every generate
// fail with `ENOENT: /tmp/optel-explorer-tabs.txt` before it could even look for the
// aemcs tab. We write flat files into the chosen dir and probe writability with the
// ASYNC fs API (the VFS shim in this runtime does not support fs.*Sync reliably —
// the rest of the script already uses `await fsx.writeFile/readFile`).
let TMP_DIR = "/tmp";
let TMP_RESULT = `${TMP_DIR}/optel-explorer-result.txt`;
let TMP_TABS = `${TMP_DIR}/optel-explorer-tabs.txt`;
let TMP_PROBE = `${TMP_DIR}/optel-explorer-probe.txt`;

async function initTmpDir() {
  const candidates = [
    typeof process !== "undefined" && process.env && process.env.OPTEL_TMP_DIR,
    "/optel",
    "/shared",
    "/scoops/optel-explorer",
    "/tmp"
  ].filter(Boolean);
  for (const dir of candidates) {
    try {
      const probe = `${dir}/.optel-write-probe`;
      await fsx.writeFile(probe, "ok");
      try { await fsx.unlink(probe); } catch (e) { /* leftover probe is harmless */ }
      TMP_DIR = dir;
      break;
    } catch (e) { /* try next */ }
  }
  TMP_RESULT = `${TMP_DIR}/optel-explorer-result.txt`;
  TMP_TABS = `${TMP_DIR}/optel-explorer-tabs.txt`;
  TMP_PROBE = `${TMP_DIR}/optel-explorer-probe.txt`;
  return TMP_DIR;
}

function sleep(ms) {
  return new Promise(r => setTimeout(r, ms));
}

// Shared domain-key save helper. Reads the existing key store (default {} if
// missing), sets/updates the entry for `domain`, and writes it back with 2-space
// indent. Uses the ASYNC fs API only — the VFS shim in this runtime does NOT
// support fs.*Sync reliably (the rest of this script already awaits fsx.*).
const DOMAINKEY_FILE = (typeof process !== "undefined" && process.env && process.env.DOMAINKEY_FILE)
  || (typeof exec !== "undefined" ? "/optel/domainkey.json" : `${process.env.HOME}/.optel/domainkey.json`);
async function saveDomainKey(domain, key) {
  let existing = {};
  try {
    existing = JSON.parse(await fsx.readFile(DOMAINKEY_FILE).catch(() => "{}"));
  } catch (e) {
    existing = {};
  }
  existing[domain] = key;
  const keyDir = DOMAINKEY_FILE.slice(0, DOMAINKEY_FILE.lastIndexOf("/")) || "/";
  await fsx.mkdir(keyDir, { recursive: true });
  await writeJsonTruncating(DOMAINKEY_FILE, existing);
  return DOMAINKEY_FILE;
}

// Write a JSON object to `path`, guaranteeing the file is truncated first. The VFS
// shim's writeFile does NOT truncate an in-place overwrite (a shorter payload
// leaves trailing bytes from the old content → corrupt JSON), and fsx.unlink is
// undefined in this runtime. The reliable truncation primitive is the shell `rm`,
// reachable via the `exec` global, which we await via a sentinel file.
async function writeJsonTruncating(path, obj) {
  try { exec(`rm -f ${path}`); } catch (e) { /* best-effort */ }
  // Give the async shell rm a beat to land before we recreate the file.
  await sleep(150);
  await fsx.writeFile(path, JSON.stringify(obj, null, 2));
}

// Remove a domain entry from the key store. Deletes the existing file first so the
// VFS write truncates cleanly (the shim does not reliably truncate an in-place
// overwrite), then writes the reduced object back. Returns true if the domain
// existed and was removed, false if it was not present.
async function removeDomainKey(domain) {
  let existing = {};
  try {
    existing = JSON.parse(await fsx.readFile(DOMAINKEY_FILE).catch(() => "{}"));
  } catch (e) {
    existing = {};
  }
  const had = Object.prototype.hasOwnProperty.call(existing, domain);
  delete existing[domain];
  const keyDir = DOMAINKEY_FILE.slice(0, DOMAINKEY_FILE.lastIndexOf("/")) || "/";
  await fsx.mkdir(keyDir, { recursive: true });
  await writeJsonTruncating(DOMAINKEY_FILE, existing);
  return had;
}

async function listTabsRaw() {
  exec(`playwright-cli tab-list > ${TMP_TABS} 2>&1`);
  await sleep(3000);
  return (await fsx.readFile(TMP_TABS)) || "";
}

// Probe a single aemcs tab: returns true if its CDP session is live and it is the
// AEM CS Workspace app (so we know it's usable for the authenticated API call).
async function probeAemcsTab(tabId) {
  exec(`playwright-cli eval --tab=${tabId} "document.title" > ${TMP_PROBE} 2>&1`);
  await sleep(2000);
  const probe = await fsx.readFile(TMP_PROBE);
  return !!(probe && probe.includes("AEM CS Workspace"));
}

function extractAemcsTabIds(tabList) {
  // Collect all aemcs tab IDs — may be multiple, some with stale CDP sessions.
  // Try most-recently-opened first (last in list).
  return [...tabList.matchAll(/\[([A-F0-9]+)\] https:\/\/aemcs-workspace\.adobe\.com/g)]
    .map(m => m[1])
    .reverse();
}

// List aemcs tabs and return the first one whose page is ready (live CDP session +
// "AEM CS Workspace" title). Returns null if none are ready right now.
async function findReadyAemcsTab() {
  const tabList = await listTabsRaw();
  if (!tabList) {
    console.error("ERROR: playwright-cli not available");
    process.exit(1);
  }
  for (const tabId of extractAemcsTabIds(tabList)) {
    if (await probeAemcsTab(tabId)) return tabId;
  }
  return null;
}

async function findAemcsTab() {
  // 1) Fast path: a ready aemcs tab already exists.
  let ready = await findReadyAemcsTab();
  if (ready) return ready;

  // 2) Auto-open one. The SPA needs time to navigate, run auth redirects, and
  //    register its CDP session; opening is async and the tab may not appear in the
  //    very next tab-list, so we POLL (re-list + probe) rather than wait a single
  //    fixed interval. The user must be LOGGED IN for the later API call to work;
  //    auto-open only handles the "tab missing / not yet ready" case.
  console.error(`No ready aemcs-workspace.adobe.com tab — opening ${AEMCS_URL} ...`);
  exec(`playwright-cli open ${AEMCS_URL} > ${TMP_PROBE} 2>&1`);

  const POLL_INTERVAL_MS = 3000;
  const MAX_POLLS = 12; // ~36s total, enough for cold SPA load + auth redirect
  for (let attempt = 1; attempt <= MAX_POLLS; attempt += 1) {
    await sleep(POLL_INTERVAL_MS);
    ready = await findReadyAemcsTab();
    if (ready) return ready;
  }

  console.error("ERROR: An aemcs-workspace.adobe.com tab could not be made ready.");
  console.error("It may still be loading, the session may be stale, or you may not be logged in.");
  console.error("Please open https://aemcs-workspace.adobe.com, ensure you are logged in, then retry.");
  process.exit(1);
}

async function aemcsApi(tabId, method, path, body) {
  const bodyJson = body ? JSON.stringify(body).replace(/'/g, "'\\''") : "{}";
  const js = `
    (() => {
      const jwt = document.cookie.match(/authToken=([^;]+)/)?.[1];
      if (!jwt) return JSON.stringify({__error: 'no authToken cookie — please log in to aemcs-workspace.adobe.com'});
      return new Promise((resolve) => {
        const xhr = new XMLHttpRequest();
        xhr.open('${method}', '${path}');
        xhr.setRequestHeader('Authorization', 'Bearer ' + jwt);
        xhr.setRequestHeader('Content-Type', 'application/json');
        xhr.setRequestHeader('Accept', 'application/json, text/plain, */*');
        xhr.onload = () => resolve(JSON.stringify({status: xhr.status, body: xhr.responseText}));
        xhr.onerror = () => resolve(JSON.stringify({__error: 'network error'}));
        xhr.send('${bodyJson}');
      });
    })()
  `;
  const escaped = js.replace(/'/g, "'\\''");
  exec(`playwright-cli eval --tab=${tabId} '${escaped}' > ${TMP_RESULT} 2>&1`);
  await sleep(3000);
  const raw = await fsx.readFile(TMP_RESULT);
  try {
    return JSON.parse(raw);
  } catch (e) {
    return { __error: "failed to parse response: " + raw.substring(0, 200) };
  }
}

// ─── Commands ────────────────────────────────────────────────────────────────

async function cmdGenerate(domain) {
  if (!domain) {
    console.error("Usage: optel-explorer generate <domain>");
    console.error("Example: optel-explorer generate applyonline.hdfc.bank.in");
    process.exit(1);
  }

  await initTmpDir();
  const tabId = await findAemcsTab();
  console.log(`Generating OpTel domain key for: ${domain}`);

  const result = await aemcsApi(tabId, "POST", "/apiv3/customer/rum/generate", { domain });

  if (result.__error) {
    console.error("ERROR: " + result.__error);
    process.exit(1);
  }

  if (result.status !== 200) {
    console.error(`ERROR: API returned HTTP ${result.status}`);
    console.error(result.body);
    process.exit(1);
  }

  let parsed;
  try {
    parsed = JSON.parse(result.body);
  } catch (e) {
    console.error("ERROR: Could not parse API response: " + result.body);
    process.exit(1);
  }

  if (parsed.status === "success" && parsed.domainKey) {
    console.log(`Domain:    ${parsed.domain}`);
    console.log(`Key:       ${parsed.domainKey}`);
    console.log(`Status:    success`);

    // Save to domainkey.json via the shared helper.
    try {
      const keyFile = await saveDomainKey(domain, parsed.domainKey);
      console.log(`Saved:     ${keyFile}`);
    } catch (e) {
      console.log(`(Could not save to ${DOMAINKEY_FILE}: ${e.message})`);
    }
  } else if (parsed.status === "failed" || parsed.status === "no_data" || (result.body && result.body.includes("No data found"))) {
    console.error(`ERROR: No RUM data found for domain "${domain}".`);
    console.error("The domain must have existing OpTel/RUM data before a key can be generated.");
    process.exit(1);
  } else {
    console.error("ERROR: Unexpected response:");
    console.error(JSON.stringify(parsed, null, 2));
    process.exit(1);
  }
}

async function cmdAddDomainKey(domain, key) {
  if (!domain || !key) {
    console.error("Usage: optel-explorer add-domain-key <domain> <key>");
    console.error("Example: optel-explorer add-domain-key applyonline.hdfc.bank.in <key>");
    process.exit(1);
  }
  await saveDomainKey(domain, key);
  console.log(`Saved key for ${domain} to ${DOMAINKEY_FILE}`);
}

async function cmdRemoveDomainKey(domain) {
  if (!domain) {
    console.error("Usage: optel-explorer remove-domain-key <domain>");
    process.exit(1);
  }
  const had = await removeDomainKey(domain);
  if (had) {
    console.log(`Removed key for ${domain} from ${DOMAINKEY_FILE}`);
  } else {
    console.log(`No key found for ${domain}; nothing to remove.`);
  }
}

// ─── Dispatch ────────────────────────────────────────────────────────────────

const args = process.argv.slice(2);
const command = args[0];

async function main() {
  if (command === "generate") {
    await cmdGenerate(args[1]);
  } else if (command === "add-domain-key") {
    await cmdAddDomainKey(args[1], args[2]);
  } else if (command === "remove-domain-key") {
    await cmdRemoveDomainKey(args[1]);
  } else if (!command || command === "help" || command === "--help") {
    console.log("optel-explorer — AEM CS Workspace CLI");
    console.log("");
    console.log("Commands:");
    console.log("  generate <domain>               Generate an OpTel domain key for a domain");
    console.log("  add-domain-key <domain> <key>   Save a domain key to the local key store (/optel/domainkey.json)");
    console.log("  remove-domain-key <domain>      Remove a domain key from the local key store");
    console.log("");
    console.log("Requirements:");
    console.log("  aemcs-workspace.adobe.com must be open in a logged-in browser tab");
  } else {
    console.error("Unknown command: " + command);
    console.error("Run 'optel-explorer help' for usage.");
    process.exit(1);
  }
}

return main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
