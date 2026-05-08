// src/optel-analyze-errors/main.js
var rawFs = require("fs");
var fs = rawFs.promises || rawFs;
async function pathExists(p) {
  if (typeof fs.exists === "function") return fs.exists(p);
  try {
    await fs.stat(p);
    return true;
  } catch {
    return false;
  }
}
async function ensureDir(dir) {
  if (await pathExists(dir)) return;
  try {
    await fs.mkdir(dir, { recursive: true });
  } catch {
    await fs.mkdir(dir);
  }
}
function extractErrorType(errorStr) {
  if (!errorStr) return null;
  const errorTypePatterns = [
    /^(typeerror|type error)/i,
    /^(referenceerror|reference error)/i,
    /^(syntaxerror|syntax error)/i,
    /^(rangeerror|range error)/i,
    /^(urierror|uri error)/i,
    /^(evalerror|eval error)/i,
    /^(aggregateerror|aggregate error)/i,
    /^(internalerror|internal error)/i
  ];
  const errorTypes = [
    "TypeError",
    "ReferenceError",
    "SyntaxError",
    "RangeError",
    "URIError",
    "EvalError",
    "AggregateError",
    "InternalError"
  ];
  for (let i = 0; i < errorTypePatterns.length; i++) {
    if (errorTypePatterns[i].test(errorStr)) {
      return errorTypes[i];
    }
  }
  return "Unknown";
}
function extractKeyIdentifiers(errorStr) {
  if (!errorStr) return [];
  const identifiers = /* @__PURE__ */ new Set();
  const propertyMatch = errorStr.match(/(?:property|property\s+['"])([a-zA-Z_$][a-zA-Z0-9_$]*)/gi);
  if (propertyMatch) {
    propertyMatch.forEach((m) => {
      const name = m.replace(/property\s*['"]?/gi, "").replace(/['"]/g, "").trim();
      if (name) identifiers.add(name.toLowerCase());
    });
  }
  const quotedMatch = errorStr.match(/['"]([a-zA-Z_$][a-zA-Z0-9_$]*)['"]/g);
  if (quotedMatch) {
    quotedMatch.forEach((m) => {
      const name = m.replace(/['"]/g, "").trim();
      if (name && name.length > 1) identifiers.add(name.toLowerCase());
    });
  }
  const patterns = [
    /(?:is\s+not\s+defined|is\s+undefined)[:\s]+([a-zA-Z_$][a-zA-Z0-9_$]*)/i,
    /(?:cannot\s+read|can't\s+read)[:\s]+([a-zA-Z_$][a-zA-Z0-9_$]*)/i,
    /(?:undefined\s+is\s+not\s+an\s+object)[:\s]+([a-zA-Z_$][a-zA-Z0-9_$]*)/i,
    /([a-zA-Z_$][a-zA-Z0-9_$]*)\s+is\s+not\s+a\s+function/i,
    /([a-zA-Z_$][a-zA-Z0-9_$]*)\s+is\s+undefined/i,
    /(?:can't\s+find\s+variable|cant\s+find\s+variable)[:\s]+([a-zA-Z_$][a-zA-Z0-9_$]*)/i
  ];
  patterns.forEach((pattern) => {
    const match = errorStr.match(pattern);
    if (match && match[1]) {
      identifiers.add(match[1].toLowerCase());
    }
  });
  return Array.from(identifiers);
}
function normalizeErrorMessage(errorStr) {
  if (!errorStr) return "";
  let normalized = errorStr.toLowerCase();
  normalized = normalized.replace(/[a-z]:\\[^\s]+/gi, "");
  normalized = normalized.replace(/\/[^\s]+:\d+:\d+/g, "");
  normalized = normalized.replace(/@[^\s]+/g, "");
  const browserNormalizations = {
    "is not an object": "is undefined",
    "is not a function": "is not a function",
    "cannot read property": "cannot read property",
    "can't read property": "cannot read property",
    "reading property": "cannot read property",
    "of undefined": "of undefined",
    "is undefined": "is undefined",
    "is not defined": "is undefined",
    "can't find variable": "is not defined",
    "cant find variable": "is not defined",
    "evaluating": "",
    "in": ""
  };
  Object.keys(browserNormalizations).forEach((key) => {
    const regex = new RegExp(key.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "gi");
    normalized = normalized.replace(regex, browserNormalizations[key]);
  });
  normalized = normalized.replace(/\s+/g, " ").trim();
  return normalized;
}
function tokenizeError(errorStr) {
  if (!errorStr) return [];
  const normalized = normalizeErrorMessage(errorStr);
  const tokens = normalized.split(/[\s.,;:!?()[\]{}'"]+/).filter((token) => {
    if (token.length < 2) return false;
    const stopWords = ["the", "a", "an", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "do", "does", "did", "will", "would", "could", "should", "may", "might", "must", "can", "cannot", "can't"];
    return !stopWords.includes(token.toLowerCase());
  }).map((t) => t.toLowerCase());
  return tokens;
}
function calculateJaccardSimilarity(tokens1, tokens2) {
  if (tokens1.length === 0 && tokens2.length === 0) return 1;
  if (tokens1.length === 0 || tokens2.length === 0) return 0;
  const set1 = new Set(tokens1);
  const set2 = new Set(tokens2);
  const intersection = new Set([...set1].filter((x) => set2.has(x)));
  const union = /* @__PURE__ */ new Set([...set1, ...set2]);
  return intersection.size / union.size;
}
function calculateCosineSimilarity(tokens1, tokens2) {
  if (tokens1.length === 0 && tokens2.length === 0) return 1;
  if (tokens1.length === 0 || tokens2.length === 0) return 0;
  const freq1 = {};
  const freq2 = {};
  tokens1.forEach((token) => {
    freq1[token] = (freq1[token] || 0) + 1;
  });
  tokens2.forEach((token) => {
    freq2[token] = (freq2[token] || 0) + 1;
  });
  const allTokens = /* @__PURE__ */ new Set([...tokens1, ...tokens2]);
  let dotProduct = 0;
  let magnitude1 = 0;
  let magnitude2 = 0;
  allTokens.forEach((token) => {
    const f1 = freq1[token] || 0;
    const f2 = freq2[token] || 0;
    dotProduct += f1 * f2;
    magnitude1 += f1 * f1;
    magnitude2 += f2 * f2;
  });
  const magnitude = Math.sqrt(magnitude1) * Math.sqrt(magnitude2);
  return magnitude === 0 ? 0 : dotProduct / magnitude;
}
function calculateLevenshteinSimilarity(str1, str2) {
  const len1 = str1.length;
  const len2 = str2.length;
  if (len1 === 0) return len2 === 0 ? 1 : 0;
  if (len2 === 0) return 0;
  const matrix = Array(len1 + 1).fill(null).map(() => Array(len2 + 1).fill(0));
  for (let i = 0; i <= len1; i++) matrix[i][0] = i;
  for (let j = 0; j <= len2; j++) matrix[0][j] = j;
  for (let i = 1; i <= len1; i++) {
    for (let j = 1; j <= len2; j++) {
      const cost = str1[i - 1] === str2[j - 1] ? 0 : 1;
      matrix[i][j] = Math.min(
        matrix[i - 1][j] + 1,
        matrix[i][j - 1] + 1,
        matrix[i - 1][j - 1] + cost
      );
    }
  }
  const distance = matrix[len1][len2];
  const maxLen = Math.max(len1, len2);
  return (maxLen - distance) / maxLen;
}
function calculateErrorMessageSimilarity(error1, error2) {
  if (!error1 || !error2) return 0;
  if (error1 === error2) return 1;
  const normalized1 = normalizeErrorMessage(error1);
  const normalized2 = normalizeErrorMessage(error2);
  const type1 = extractErrorType(error1);
  const type2 = extractErrorType(error2);
  const identifiers1 = extractKeyIdentifiers(error1);
  const identifiers2 = extractKeyIdentifiers(error2);
  const tokens1 = tokenizeError(error1);
  const tokens2 = tokenizeError(error2);
  const scores = {
    errorTypeMatch: type1 === type2 && type1 !== "Unknown" ? 1 : 0,
    identifierOverlap: identifiers1.length > 0 && identifiers2.length > 0 ? calculateJaccardSimilarity(identifiers1, identifiers2) : 0,
    jaccardSimilarity: calculateJaccardSimilarity(tokens1, tokens2),
    cosineSimilarity: calculateCosineSimilarity(tokens1, tokens2),
    levenshteinSimilarity: calculateLevenshteinSimilarity(normalized1, normalized2)
  };
  const weights = {
    errorTypeMatch: 0.15,
    identifierOverlap: 0.3,
    jaccardSimilarity: 0.25,
    cosineSimilarity: 0.2,
    levenshteinSimilarity: 0.1
  };
  if (scores.errorTypeMatch === 0 && type1 !== "Unknown" && type2 !== "Unknown") {
    return scores.jaccardSimilarity * 0.5;
  }
  if (scores.identifierOverlap > 0.7) {
    scores.jaccardSimilarity = Math.min(1, scores.jaccardSimilarity * 1.2);
    scores.cosineSimilarity = Math.min(1, scores.cosineSimilarity * 1.2);
  }
  const totalScore = scores.errorTypeMatch * weights.errorTypeMatch + scores.identifierOverlap * weights.identifierOverlap + scores.jaccardSimilarity * weights.jaccardSimilarity + scores.cosineSimilarity * weights.cosineSimilarity + scores.levenshteinSimilarity * weights.levenshteinSimilarity;
  return Math.min(1, Math.max(0, totalScore));
}
function parseError(errorStr) {
  if (!errorStr || typeof errorStr !== "string") {
    return {
      filename: null,
      line: null,
      column: null,
      errorDetails: errorStr || "unknown",
      rawLocation: "",
      fullPath: ""
    };
  }
  const parts = errorStr.split(" | ");
  const locationPart = parts[0] || "";
  const errorDetails = parts.slice(1).join(" | ").trim();
  if (locationPart.toLowerCase().includes("undefined error")) {
    return {
      filename: null,
      line: null,
      column: null,
      errorDetails: errorDetails || locationPart,
      rawLocation: locationPart,
      fullPath: locationPart
    };
  }
  let filename = null;
  let line = null;
  let column = null;
  const urlMatch = locationPart.match(/([^/@\s]+\.(?:js|css|html?))(?::(\d+))?(?::(\d+))?/i);
  if (urlMatch) {
    filename = urlMatch[1] || null;
    line = urlMatch[2] ? parseInt(urlMatch[2], 10) : null;
    column = urlMatch[3] ? parseInt(urlMatch[3], 10) : null;
  } else {
    const lineColMatch = locationPart.match(/:(\d+)(?::(\d+))?/);
    if (lineColMatch) {
      line = parseInt(lineColMatch[1], 10);
      column = lineColMatch[2] ? parseInt(lineColMatch[2], 10) : null;
    }
  }
  if (filename) {
    filename = filename.replace(/\.ACSHASH<[^>]+>/gi, "").replace(/\.ACSHASH[a-f0-9]+/gi, "").replace(/\.[a-f0-9]{8,16}\.(js|css|html?)/gi, ".$1").replace(/\?.*$/, "").split("/").pop();
  }
  return {
    filename,
    line,
    column,
    errorDetails,
    rawLocation: locationPart,
    fullPath: locationPart
  };
}
function calculateLineProximity(line1, line2) {
  if (line1 === null || line2 === null) return 0.5;
  const diff = Math.abs(line1 - line2);
  if (diff === 0) return 1;
  if (diff <= 5) return 0.9 + 0.1 * (5 - diff) / 5;
  if (diff <= 10) return 0.8 + 0.1 * (10 - diff) / 5;
  if (diff <= 50) return 0.5 + 0.3 * (50 - diff) / 40;
  if (diff <= 100) return 0.3 + 0.2 * (100 - diff) / 50;
  if (diff <= 500) return 0.1 + 0.2 * (500 - diff) / 400;
  return 0;
}
function calculateErrorSimilarity(error1, error2) {
  const parsed1 = error1.parsed || parseError(error1.exampleFullError);
  const parsed2 = error2.parsed || parseError(error2.exampleFullError);
  const errorDetailsSimilarity = calculateErrorMessageSimilarity(
    parsed1.errorDetails,
    parsed2.errorDetails
  );
  const hasFilename1 = parsed1.filename !== null && parsed1.filename !== void 0;
  const hasFilename2 = parsed2.filename !== null && parsed2.filename !== void 0;
  let filenameMatch = 0;
  let lineProximity = 0;
  if (hasFilename1 && hasFilename2) {
    filenameMatch = parsed1.filename === parsed2.filename ? 1 : 0;
    lineProximity = calculateLineProximity(parsed1.line, parsed2.line);
  } else if (hasFilename1 || hasFilename2) {
    filenameMatch = 0;
    lineProximity = calculateLineProximity(parsed1.line, parsed2.line);
  } else {
    filenameMatch = 0.5;
    lineProximity = calculateLineProximity(parsed1.line, parsed2.line);
  }
  let weights;
  if (hasFilename1 && hasFilename2) {
    weights = { filename: 0.3, lineProximity: 0.25, errorDetails: 0.45 };
  } else {
    weights = { filename: 0, lineProximity: 0.2, errorDetails: 0.8 };
  }
  const totalScore = filenameMatch * weights.filename + lineProximity * weights.lineProximity + errorDetailsSimilarity * weights.errorDetails;
  return totalScore;
}
function clusterSimilarErrors(errors, similarityThreshold = 0.6) {
  const clusters = [];
  const errorToCluster = /* @__PURE__ */ new Map();
  const similarityMatrix = [];
  console.log("  Building similarity matrix...");
  for (let i = 0; i < errors.length; i++) {
    similarityMatrix[i] = similarityMatrix[i] || [];
    if ((i + 1) % 50 === 0) {
      console.log(`    Calculating similarities ${i + 1}/${errors.length}...`);
    }
    for (let j = i + 1; j < errors.length; j++) {
      const similarity = calculateErrorSimilarity(errors[i], errors[j]);
      similarityMatrix[i][j] = similarity;
      if (!similarityMatrix[j]) {
        similarityMatrix[j] = [];
      }
      similarityMatrix[j][i] = similarity;
    }
    similarityMatrix[i][i] = 1;
  }
  console.log("  Building clusters...");
  for (let i = 0; i < errors.length; i++) {
    let clusterIndex = errorToCluster.get(i);
    if (clusterIndex === void 0) {
      clusterIndex = clusters.length;
      clusters.push([i]);
      errorToCluster.set(i, clusterIndex);
    }
    for (let j = i + 1; j < errors.length; j++) {
      const similarity = similarityMatrix[i][j];
      if (similarity >= similarityThreshold) {
        const jClusterIndex = errorToCluster.get(j);
        if (jClusterIndex === void 0) {
          clusters[clusterIndex].push(j);
          errorToCluster.set(j, clusterIndex);
        } else if (jClusterIndex !== clusterIndex) {
          const jCluster = clusters[jClusterIndex];
          clusters[clusterIndex].push(...jCluster);
          jCluster.forEach((idx) => errorToCluster.set(idx, clusterIndex));
          clusters[jClusterIndex] = null;
        }
      }
    }
  }
  const validClusters = clusters.filter((c) => c !== null);
  console.log(`  Found ${validClusters.length} clusters from ${errors.length} errors`);
  return { clusters: validClusters, errorToCluster, similarityMatrix };
}
function findClusterRepresentative(cluster, errors, similarityMatrix) {
  let bestIndex = cluster[0];
  let bestScore = errors[cluster[0]].count;
  for (const idx of cluster) {
    let similarityCount = 0;
    for (const otherIdx of cluster) {
      if (idx !== otherIdx && similarityMatrix[idx][otherIdx] >= 0.6) {
        similarityCount++;
      }
    }
    const score = errors[idx].count + similarityCount * 10;
    if (score > bestScore) {
      bestScore = score;
      bestIndex = idx;
    }
  }
  return bestIndex;
}
function findSimilarErrors(errors, similarityThreshold = 0.6) {
  const { clusters, errorToCluster, similarityMatrix } = clusterSimilarErrors(errors, similarityThreshold);
  const results = [];
  const processedErrors = /* @__PURE__ */ new Set();
  clusters.forEach((cluster, clusterIndex) => {
    if ((clusterIndex + 1) % 20 === 0) {
      console.log(`  Processing cluster ${clusterIndex + 1}/${clusters.length}...`);
    }
    const representativeIndex = findClusterRepresentative(cluster, errors, similarityMatrix);
    const representative = errors[representativeIndex];
    const parsed = representative.parsed || parseError(representative.exampleFullError);
    const similarErrors = [];
    cluster.forEach((errorIndex) => {
      if (errorIndex === representativeIndex) return;
      const otherError = errors[errorIndex];
      const otherParsed = otherError.parsed || parseError(otherError.exampleFullError);
      const similarity = similarityMatrix[representativeIndex][errorIndex];
      similarErrors.push({
        error: otherParsed.errorDetails,
        errorType: extractErrorType(otherParsed.errorDetails),
        exampleFullError: otherError.exampleFullError,
        filename: otherParsed.filename,
        line: otherParsed.line,
        column: otherParsed.column,
        count: otherError.count,
        similarity: Math.round(similarity * 1e3) / 1e3,
        lineDiff: parsed.line !== null && otherParsed.line !== null ? Math.abs(parsed.line - otherParsed.line) : null
      });
      processedErrors.add(errorIndex);
    });
    similarErrors.sort((a, b) => b.similarity - a.similarity);
    results.push({
      error: parsed.errorDetails,
      errorType: extractErrorType(parsed.errorDetails),
      exampleFullError: representative.exampleFullError,
      filename: parsed.filename,
      line: parsed.line,
      column: parsed.column,
      count: representative.count,
      clusterSize: cluster.length,
      similar: similarErrors
    });
    processedErrors.add(representativeIndex);
  });
  errors.forEach((error, index) => {
    if (!processedErrors.has(index)) {
      const parsed = error.parsed || parseError(error.exampleFullError);
      results.push({
        error: parsed.errorDetails,
        errorType: extractErrorType(parsed.errorDetails),
        exampleFullError: error.exampleFullError,
        filename: parsed.filename,
        line: parsed.line,
        column: parsed.column,
        count: error.count,
        clusterSize: 1,
        similar: []
      });
    }
  });
  return results;
}
async function main() {
  const args = process.argv.slice(2);
  if (args.length < 2) {
    console.error("Usage: node improved-error-similarity.jsh <input-file> <output-prefix> [--threshold 0.6]");
    console.error("");
    console.error("Example:");
    console.error("  node improved-error-similarity.jsh output/errors-jan26-2026.json errors-jan26");
    console.error("  node improved-error-similarity.jsh output/errors-jan26-2026.json errors-jan26 --threshold 0.7");
    process.exit(1);
  }
  const inputFile = args[0];
  const outputPrefix = args[1];
  let similarityThreshold = 0.6;
  const thresholdIndex = args.indexOf("--threshold");
  if (thresholdIndex !== -1 && args[thresholdIndex + 1]) {
    similarityThreshold = parseFloat(args[thresholdIndex + 1]);
  }
  if (!await pathExists(inputFile)) {
    console.error(`Error: Input file not found: ${inputFile}`);
    process.exit(1);
  }
  console.log("=".repeat(80));
  console.log("ERROR SIMILARITY ANALYSIS");
  console.log("=".repeat(80));
  console.log(`Input file: ${inputFile}`);
  console.log(`Output prefix: ${outputPrefix}`);
  console.log(`Similarity threshold: ${similarityThreshold}`);
  console.log("");
  try {
    console.log("Loading error data...");
    const errorData = JSON.parse(String(await fs.readFile(inputFile)));
    if (!errorData.result || !errorData.result.facetValues) {
      console.error("Error: Invalid input file format. Expected structure: { result: { facetValues: [...] } }");
      process.exit(1);
    }
    const errors = errorData.result.facetValues.map((facet) => ({
      exampleFullError: facet.value,
      count: facet.count,
      parsed: parseError(facet.value)
    }));
    console.log(`Loaded ${errors.length} errors`);
    console.log(`Total page views: ${errorData.result.totalPageViews || "N/A"}`);
    console.log(`Filtered page views: ${errorData.result.filteredPageViews || "N/A"}`);
    console.log("");
    console.log(`Finding similar errors (threshold: ${similarityThreshold})...`);
    const similarityResults = findSimilarErrors(errors, similarityThreshold);
    console.log("");
    const clusteredErrors = similarityResults.filter((r) => r.clusterSize > 1);
    const unclusteredErrors = similarityResults.filter((r) => r.clusterSize === 1);
    const stats = {
      totalErrors: errors.length,
      uniqueErrorGroups: similarityResults.length,
      clusteredErrors: clusteredErrors.length,
      unclusteredErrors: unclusteredErrors.length,
      errorsWithSimilarities: similarityResults.filter((r) => r.similar.length > 0).length,
      errorsWithoutSimilarities: similarityResults.filter((r) => r.similar.length === 0).length,
      totalSimilarityPairs: similarityResults.reduce((sum, r) => sum + r.similar.length, 0),
      averageSimilaritiesPerError: 0,
      averageClusterSize: 0,
      largestClusterSize: Math.max(...similarityResults.map((r) => r.clusterSize), 1),
      errorsWithExactLocationMatch: 0,
      errorsWithNearbyLocationMatch: 0,
      totalPageViews: errorData.result.totalPageViews,
      filteredPageViews: errorData.result.filteredPageViews
    };
    stats.averageSimilaritiesPerError = stats.totalSimilarityPairs / stats.totalErrors;
    stats.averageClusterSize = similarityResults.reduce((sum, r) => sum + r.clusterSize, 0) / similarityResults.length;
    similarityResults.forEach((result) => {
      const exactMatches = result.similar.filter((s) => s.lineDiff === 0);
      const nearbyMatches = result.similar.filter((s) => s.lineDiff !== null && s.lineDiff > 0 && s.lineDiff <= 10);
      if (exactMatches.length > 0) stats.errorsWithExactLocationMatch++;
      if (nearbyMatches.length > 0) stats.errorsWithNearbyLocationMatch++;
    });
    console.log("=".repeat(80));
    console.log("ANALYSIS STATISTICS");
    console.log("=".repeat(80));
    console.log(`\u{1F4CA} Input Statistics:`);
    console.log(`   Total Errors Analyzed: ${stats.totalErrors}`);
    console.log(`   Total Page Views: ${stats.totalPageViews || "N/A"}`);
    console.log(`   Filtered Page Views: ${stats.filteredPageViews || "N/A"}`);
    console.log("");
    console.log(`\u{1F50D} Deduplication Results:`);
    console.log(`   Unique Error Groups (after deduplication): ${stats.uniqueErrorGroups}`);
    console.log(`   \u2514\u2500 Groups with duplicates found: ${stats.clusteredErrors}`);
    console.log(`   \u2514\u2500 Groups with no duplicates: ${stats.unclusteredErrors}`);
    console.log(`   Average errors per group: ${stats.averageClusterSize.toFixed(2)}`);
    console.log(`   Largest group size: ${stats.largestClusterSize} errors`);
    console.log("");
    console.log(`\u{1F517} Similarity Analysis:`);
    console.log(`   Groups with similar errors: ${stats.errorsWithSimilarities}`);
    console.log(`   Groups without similar errors: ${stats.errorsWithoutSimilarities}`);
    console.log(`   Total similarity pairs found: ${stats.totalSimilarityPairs}`);
    console.log(`   Average similarities per group: ${stats.averageSimilaritiesPerError.toFixed(2)}`);
    console.log("");
    console.log(`\u{1F4CD} Location Matching:`);
    console.log(`   Groups with exact line matches: ${stats.errorsWithExactLocationMatch}`);
    console.log(`   Groups with nearby line matches (\xB110 lines): ${stats.errorsWithNearbyLocationMatch}`);
    console.log("=".repeat(80));
    console.log("");
    const lastSlash = inputFile.lastIndexOf("/");
    const outputDir = lastSlash > 0 ? inputFile.slice(0, lastSlash) : ".";
    if (!await pathExists(outputDir)) {
      await ensureDir(outputDir);
    }
    const baseOutputPath = outputDir === "." ? outputPrefix : `${outputDir}/${outputPrefix}`;
    const detailedOutput = {
      metadata: {
        analysisDate: (/* @__PURE__ */ new Date()).toISOString(),
        sourceFile: inputFile,
        similarityThreshold,
        analysisMethod: "Improved similarity (token-based + location)",
        statistics: stats
      },
      errors: similarityResults
    };
    const detailedFile = `${baseOutputPath}-similarity-analysis.json`;
    await fs.writeFile(detailedFile, JSON.stringify(detailedOutput, null, 2));
    console.log(`\u2705 Detailed analysis saved to: ${detailedFile}`);
    const simplifiedOutput = similarityResults.map((r) => ({
      error: r.error,
      errorType: r.errorType,
      filename: r.filename,
      line: r.line,
      column: r.column,
      count: r.count,
      similarCount: r.similar.length,
      exactLineMatches: r.similar.filter((s) => s.lineDiff === 0).length,
      nearbyLineMatches: r.similar.filter((s) => s.lineDiff !== null && s.lineDiff > 0 && s.lineDiff <= 10).length,
      topSimilarErrors: r.similar.slice(0, 5).map((s) => ({
        error: s.error,
        filename: s.filename,
        line: s.line,
        column: s.column,
        lineDiff: s.lineDiff,
        similarity: s.similarity,
        count: s.count
      }))
    }));
    const simplifiedFile = `${baseOutputPath}-similarity-simplified.json`;
    await fs.writeFile(simplifiedFile, JSON.stringify(simplifiedOutput, null, 2));
    console.log(`\u2705 Simplified view saved to: ${simplifiedFile}`);
    const fileGroups = {};
    similarityResults.forEach((error) => {
      const file = error.filename || "unknown";
      if (!fileGroups[file]) {
        fileGroups[file] = [];
      }
      fileGroups[file].push({
        error: error.error,
        errorType: error.errorType,
        line: error.line,
        column: error.column,
        count: error.count,
        similarInSameFile: error.similar.filter((s) => s.filename === file).length
      });
    });
    Object.keys(fileGroups).forEach((file) => {
      fileGroups[file].sort((a, b) => {
        if (a.line === null) return 1;
        if (b.line === null) return -1;
        return a.line - b.line;
      });
    });
    const fileGroupedOutput = {
      metadata: {
        analysisDate: (/* @__PURE__ */ new Date()).toISOString(),
        totalFiles: Object.keys(fileGroups).length,
        totalErrors: errors.length
      },
      fileGroups
    };
    const fileGroupedFile = `${baseOutputPath}-similarity-by-file.json`;
    await fs.writeFile(fileGroupedFile, JSON.stringify(fileGroupedOutput, null, 2));
    console.log(`\u2705 File-grouped errors saved to: ${fileGroupedFile}`);
    console.log("");
    console.log("=".repeat(80));
    console.log("\u2705 Analysis complete!");
    console.log("=".repeat(80));
  } catch (error) {
    console.error("Error:", error.message);
    if (error instanceof SyntaxError) {
      console.error("Invalid JSON in input file");
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
