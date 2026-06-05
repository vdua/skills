// optel-explorer.jsh — AEM CS Workspace API client
// Performs direct API calls from the aemcs-workspace browser page context.
//
// Usage:
//   optel-explorer generate <domain>
//
// Requirements:
//   - https://aemcs-workspace.adobe.com must be open in a browser tab and logged in

const fsx = require("fs");
const AEMCS_URL = "https://aemcs-workspace.adobe.com";
const TMP_RESULT = "/tmp/optel-explorer-result.txt";

function sleep(ms) {
  return new Promise(r => setTimeout(r, ms));
}

async function findAemcsTab() {
  exec("playwright-cli tab-list > /tmp/optel-explorer-tabs.txt 2>&1");
  await sleep(3000);
  const tabList = await fsx.readFile("/tmp/optel-explorer-tabs.txt");
  if (!tabList) {
    console.error("ERROR: playwright-cli not available");
    process.exit(1);
  }
  // Collect all aemcs tab IDs — may be multiple, some with stale CDP sessions
  const matches = [...tabList.matchAll(/\[([A-F0-9]+)\] https:\/\/aemcs-workspace\.adobe\.com/g)];
  if (!matches.length) {
    console.error("ERROR: No aemcs-workspace.adobe.com tab found.");
    console.error("Please open https://aemcs-workspace.adobe.com and log in, then retry.");
    process.exit(1);
  }
  // Try each tab in reverse order (most recently opened last in list = try last-opened first)
  const tabIds = matches.map(m => m[1]).reverse();
  for (const tabId of tabIds) {
    exec(`playwright-cli eval --tab=${tabId} "document.title" > /tmp/optel-explorer-probe.txt 2>&1`);
    await sleep(2000);
    const probe = await fsx.readFile("/tmp/optel-explorer-probe.txt");
    if (probe && probe.includes("AEM CS Workspace")) {
      return tabId;
    }
  }
  console.error("ERROR: All aemcs-workspace.adobe.com tabs have stale CDP sessions.");
  console.error("Please reload https://aemcs-workspace.adobe.com and retry.");
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

    // Optionally save to domainkey.json
    const keyFile = "/optel/domainkey.json";
    try {
      const existing = JSON.parse(await fsx.readFile(keyFile).catch(() => "{}"));
      existing[domain] = parsed.domainKey;
      await fsx.writeFile(keyFile, JSON.stringify(existing, null, 2));
      console.log(`Saved:     ${keyFile}`);
    } catch (e) {
      console.log(`(Could not save to ${keyFile}: ${e.message})`);
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

// ─── Dispatch ────────────────────────────────────────────────────────────────

const args = process.argv.slice(2);
const command = args[0];

if (command === "generate") {
  await cmdGenerate(args[1]);
} else if (!command || command === "help" || command === "--help") {
  console.log("optel-explorer — AEM CS Workspace CLI");
  console.log("");
  console.log("Commands:");
  console.log("  generate <domain>    Generate an OpTel domain key for a domain");
  console.log("");
  console.log("Requirements:");
  console.log("  aemcs-workspace.adobe.com must be open in a logged-in browser tab");
} else {
  console.error("Unknown command: " + command);
  console.error("Run 'optel-explorer help' for usage.");
  process.exit(1);
}
