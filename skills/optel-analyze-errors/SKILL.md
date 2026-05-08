---
name: optel-analyze-errors
description: Analyze JavaScript errors from RUM data, identify duplicates using improved similarity algorithms, and generate comprehensive error analysis reports.
---

# Analyze Errors Skill

## Purpose

This skill enables AI agents to analyze JavaScript errors from RUM (Real User Monitoring) data, identify duplicate errors across different browsers and deployments, and generate comprehensive analysis reports. The skill uses advanced similarity algorithms optimized for cross-browser error comparison.

## Input

The skill receives a **file path** pointing to a JSON file containing error data from RUM queries (e.g., `output/errors-jan26-2026.json`). The file contains error facet values from the `optel-query.jsh` script executed with `--facet-values error`.

## Required Dependencies

**IMPORTANT**: The agent MUST use the improved error similarity utility for accurate duplicate detection:

- **`improved-error-similarity.jsh`** - Command-line utility for error analysis and duplicate detection
  - Uses token-based similarity methods
  - Extracts error types and key identifiers
  - Handles cross-browser error format differences
  - Can be executed directly:
    ```bash
    # Detect: command -v improved-error-similarity exits 0 in SLICC, 1 in Node
    # SLICC
    improved-error-similarity <input-file> <output-prefix>

    # Node
    node skills/optel-analyze-errors/scripts/improved-error-similarity.jsh <input-file> <output-prefix>
    ```


## Input Format

The agent receives a **file path** pointing to a JSON file containing error data from RUM queries.

### Input File Structure

The input file follows this structure (from `optel-query.jsh` with `--facet-values error`):

```json
{
  "result": {
    "facetValues": [
      {
        "value": "error string",
        "count": number,
        "samplingRatios": { "100": 10, "10": 5 }
      }
    ],
    "totalPageViews": number,
    "filteredPageViews": number
  }
}
```

**Semantics** (optel-query): **`count`**, **`totalPageViews`**, and **`filteredPageViews`** are **weighted** page-view totals (sum of bundle sampling weights), consistent with the default query `result`. **RUM bundle counts per weight** appear in **`samplingRatios`** / **`filteredSamplingRatios`** and each **`facetValues[].samplingRatios`**.

### Error String Format

**Format**: `location | error details`

**Location variations** (filename may be missing):
- `filename.js:line:column | error details` - With filename
- `:line:column | error details` - Without filename
- `@url/path/to/file.js:line:column | error details` - With full URL
- `functionName@url/path/to/file.js:line:column | error details` - With function name
- `undefined error | error details` - No location info
- `| error details` - No location separator

**Important Notes**:
- The filename may be **undefined/missing** in some browsers
- Location format varies by browser (Chrome, Firefox, Safari format differently)
- Error details come after the `|` separator
- Some errors may have no location information at all

### Example Input File

**File**: `output/errors-jan26-2026.json`

```json
{
  "result": {
    "facetValues": [
      {
        "value": "e._insertCursor@https://assets.adobedtm.com/80673311e435/029b16140ccd/launch-39d52f236cd6.min.js:21:6125 | referenceerror: ir is not defined",
        "count": 589
      },
      {
        "value": "HTMLDocument.<anonymous>@https://applyonline.hdfcbank.com/etc.clientlibs/HDFC_CC_UnifiedURL/clientlibs/clientlib-unified-embed.min.ACSHASH<uuid>.js:8497:17 | typeerror: Cannot set properties of null (setting 'onclick')",
        "count": 450
      },
      {
        "value": "undefined error | typeerror: Cannot set properties of null (setting 'innerHTML')",
        "count": 343
      },
      {
        "value": "@https://applyonline.hdfcbank.com/etc.clientlibs/HDFC_CC_UnifiedURL/clientlibs/clientlib-unified-embed.min.ACSHASH<uuid>.js:8497:8 | typeerror: null is not an object (evaluating 'ctaBtn.onclick = function () {modal.style.display = \"none\";}')",
        "count": 272
      }
    ],
    "totalPageViews": 9553,
    "filteredPageViews": 2862
  }
}
```

## Output Format

The agent must produce:

1. **Duplicate Analysis Report** - Errors grouped by similarity
2. **Statistics Summary** - Total errors, duplicates found, similarity metrics
3. **File-Grouped Errors** - Errors organized by source file
4. **Simplified View** - High-level duplicate clusters

All outputs are saved to the `output/` directory.

---

## Quick Workflow Overview

```
Input File → Run Utility → Generate Analysis Reports
                ↓
    improved-error-similarity <input-file> <output-prefix>   # SLICC
    node skills/optel-analyze-errors/scripts/improved-error-similarity.jsh <input-file> <output-prefix>   # Node
                ↓
    [output-prefix]-similarity-analysis.json
    [output-prefix]-similarity-simplified.json
    [output-prefix]-similarity-by-file.json
```

**Key Principle**: Execute the improved error similarity utility as a command-line tool. It handles parsing, similarity calculation, and report generation automatically.

---

## Step-by-Step Process

> **📚 Utility Reminder**: Execute `improved-error-similarity.jsh` as a command-line utility. It handles all parsing, similarity calculation, and report generation automatically.

### Step 1: Execute the Error Analysis Utility

The skill receives a **file path** (e.g., `output/errors-jan26-2026.json`) containing error data from RUM queries.

**Run the utility** (`command -v improved-error-similarity` exits 0 in SLICC, 1 in Node):
```bash
# SLICC
improved-error-similarity <input-file> <output-prefix> [--threshold 0.6]
# Node
node skills/optel-analyze-errors/scripts/improved-error-similarity.jsh <input-file> <output-prefix> [--threshold 0.6]
```

**Parameters**:
- `<input-file>`: Path to the error JSON file (e.g., `output/my-run/errors.json`)
- `<output-prefix>`: **Bare filename only — no directory component** (e.g., `errors-analysis`). The script automatically writes output files into the same directory as `<input-file>`, so passing a path here will produce a broken double-directory path.
- `--threshold`: Optional similarity threshold (default: 0.6)

**Example**:
```bash
# SLICC — output files land in output/my-run/ alongside the input
improved-error-similarity output/my-run/errors.json errors-analysis
improved-error-similarity output/my-run/errors.json errors-analysis --threshold 0.7

# Node
node skills/optel-analyze-errors/scripts/improved-error-similarity.jsh output/my-run/errors.json errors-analysis
node skills/optel-analyze-errors/scripts/improved-error-similarity.jsh output/my-run/errors.json errors-analysis --threshold 0.7
```

**What the utility does**:
1. Loads error data from the input file
2. Parses all error strings to extract filename, line, column, and error details
3. Calculates similarity between all error pairs using improved algorithms
4. Groups similar errors based on the threshold
5. Generates three output files with different views of the analysis

**Input file structure**:
- File contains `result.facetValues` array
- Each entry has `value` (error string) and `count` (occurrences)
- Error strings follow format: `location | error details`

### Step 3: Utility Output

The utility automatically finds duplicate errors and generates reports.

**Similarity Threshold**: Default `0.6` (adjustable via `--threshold` flag)
- `> 0.7`: Very likely the same error
- `0.6 - 0.7`: Similar errors (may be related)
- `< 0.6`: Different errors

**What the utility does**:
1. Compares each error against all other errors
2. Groups errors with similarity >= threshold
3. Sorts similar errors by similarity score (highest first)
4. Calculates line differences for location-based analysis
5. Generates comprehensive statistics

### Step 4: Review Generated Reports

The utility automatically generates three output files:

#### 5.1 Detailed Analysis Report
**File**: `<output-prefix>-similarity-analysis.json`

Contains:
- Metadata (analysis date, source file, threshold, method)
- Comprehensive statistics summary
- Full error details with similarity scores
- All similar errors for each error
- Line differences and location information

#### 5.2 Simplified View
**File**: `<output-prefix>-similarity-simplified.json`

Contains:
- High-level error information (only errors with similarities)
- Top 5 similar errors per error
- Counts and metrics
- Line differences
- Useful for quick review

#### 5.3 File-Grouped Errors
**File**: `<output-prefix>-similarity-by-file.json`

Contains:
- Errors grouped by source file
- Sorted by line number within each file
- Similar errors within same file
- Useful for developers debugging specific files

**Statistics Generated**:
- Total errors analyzed
- Errors with duplicates found
- Errors without duplicates
- Total similarity pairs
- Average similarities per error
- Errors with exact location matches
- Errors with nearby location matches (±10 lines)
- Total weighted page views and filtered weighted page views (from facet JSON)

---

## Output File Structure

### error-similarity-analysis.json
```json
{
  "metadata": {
    "analysisDate": "2026-01-22T...",
    "sourceFile": "output/errors-jan26-2026.json",
    "totalErrors": 150,
    "similarityThreshold": 0.6,
    "analysisMethod": "Improved similarity (token-based + location)",
    "statistics": {
      "totalErrors": 150,
      "errorsWithSimilarities": 45,
      "totalSimilarityPairs": 120,
      "averageSimilaritiesPerError": 0.8,
      "totalPageViews": 9553,
      "filteredPageViews": 2862
    }
  },
  "errors": [
    {
      "error": "referenceerror: ir is not defined",
      "exampleFullError": "e._insertCursor@https://assets.adobedtm.com/.../launch-39d52f236cd6.min.js:21:6125 | referenceerror: ir is not defined",
      "filename": "launch-39d52f236cd6.min.js",
      "line": 21,
      "column": 6125,
      "count": 589,
      "similar": [
        {
          "error": "referenceerror: ir is not defined",
          "exampleFullError": "global code@https://applyonline.hdfcbank.com/cards/credit-cards.html:2:3 | referenceerror: Can't find variable: ir",
          "filename": "credit-cards.html",
          "line": 2,
          "column": 3,
          "count": 143,
          "similarity": 0.85,
          "lineDiff": 19
        }
      ]
    }
  ]
}
```

---

## Key Takeaways

1. ✅ **Execute** `improved-error-similarity` (SLICC) or `node skills/optel-analyze-errors/scripts/improved-error-similarity.jsh` (Node) — detect with `command -v improved-error-similarity`
2. ✅ **Provide** input file path and output prefix as arguments
3. ✅ **Use** `--threshold` flag to adjust similarity sensitivity (default: 0.6)
4. ✅ **Review** all three generated output files for comprehensive analysis
5. ✅ **Compare** results across different time periods or deployments
6. ✅ **Remember**: Cross-browser error comparison requires semantic similarity, not character-level similarity!
