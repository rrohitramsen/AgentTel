#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# Validate attribute parity across Go, Node.js, and Python SDKs
# against the canonical attributes.json schema.
# ─────────────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors (no-op if not a terminal)
if [ -t 1 ]; then
  RED='\033[0;31m'
  GREEN='\033[0;32m'
  YELLOW='\033[0;33m'
  CYAN='\033[0;36m'
  BOLD='\033[1m'
  RESET='\033[0m'
else
  RED='' GREEN='' YELLOW='' CYAN='' BOLD='' RESET=''
fi

pass() { echo -e "${GREEN}PASS${RESET} $1"; }
fail() { echo -e "${RED}FAIL${RESET} $1"; }
warn() { echo -e "${YELLOW}WARN${RESET} $1"; }
info() { echo -e "${CYAN}INFO${RESET} $1"; }

CANONICAL_FILE="$REPO_ROOT/attributes.json"
EXIT_CODE=0

# ─────────────────────────────────────────────────────────────────────────────
# 1. Parse canonical attributes from attributes.json
# ─────────────────────────────────────────────────────────────────────────────
if [ ! -f "$CANONICAL_FILE" ]; then
  fail "attributes.json not found at $CANONICAL_FILE"
  exit 1
fi

echo -e "\n${BOLD}Canonical Attribute Schema Validation${RESET}"
echo "────────────────────────────────────────────────────────────"

CANONICAL=$(jq -r '.. | .key? // empty' "$CANONICAL_FILE" | sort -u)
CANONICAL_COUNT=$(echo "$CANONICAL" | wc -l | tr -d ' ')
info "Canonical attributes: $CANONICAL_COUNT keys from attributes.json"

# Write canonical to temp file
TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT
echo "$CANONICAL" > "$TMPDIR/canonical.txt"

# ─────────────────────────────────────────────────────────────────────────────
# 2. Extract attributes from each SDK
# ─────────────────────────────────────────────────────────────────────────────

# --- Go SDK ---
echo -e "\n${BOLD}Go SDK${RESET}"
GO_ATTRS_FILE="$REPO_ROOT/agenttel-go/attributes/attributes.go"
GO_AGENTIC_FILE="$REPO_ROOT/agenttel-go/attributes/agentic.go"

if [ -f "$GO_ATTRS_FILE" ] && [ -f "$GO_AGENTIC_FILE" ]; then
  # Extract all quoted strings matching agenttel.* or gen_ai.*
  {
    grep -oE '"agenttel\.[^"]*"' "$GO_ATTRS_FILE" | tr -d '"'
    grep -oE '"gen_ai\.[^"]*"' "$GO_ATTRS_FILE" | tr -d '"'
    grep -oE '"agenttel\.[^"]*"' "$GO_AGENTIC_FILE" | tr -d '"'
    grep -oE '"gen_ai\.[^"]*"' "$GO_AGENTIC_FILE" 2>/dev/null | tr -d '"' || true
  } | sort -u > "$TMPDIR/go.txt"

  GO_COUNT=$(wc -l < "$TMPDIR/go.txt" | tr -d ' ')
  info "Go attributes: $GO_COUNT keys"

  # Diff: missing from Go (in canonical but not in Go)
  GO_MISSING=$(comm -23 "$TMPDIR/canonical.txt" "$TMPDIR/go.txt")
  GO_EXTRA=$(comm -13 "$TMPDIR/canonical.txt" "$TMPDIR/go.txt")

  if [ -z "$GO_MISSING" ] && [ -z "$GO_EXTRA" ]; then
    pass "Go SDK — all $CANONICAL_COUNT attributes match canonical"
  else
    if [ -n "$GO_MISSING" ]; then
      MISSING_COUNT=$(echo "$GO_MISSING" | wc -l | tr -d ' ')
      fail "Go SDK — $MISSING_COUNT attributes MISSING from Go:"
      echo "$GO_MISSING" | while read -r key; do
        echo "    - $key"
      done
      EXIT_CODE=1
    fi
    if [ -n "$GO_EXTRA" ]; then
      EXTRA_COUNT=$(echo "$GO_EXTRA" | wc -l | tr -d ' ')
      warn "Go SDK — $EXTRA_COUNT EXTRA attributes not in canonical:"
      echo "$GO_EXTRA" | while read -r key; do
        echo "    + $key"
      done
      EXIT_CODE=1
    fi
  fi
else
  warn "Go SDK — attribute files not found, skipping"
fi

# --- Node.js SDK ---
echo -e "\n${BOLD}Node.js SDK${RESET}"
NODE_ATTRS_FILE="$REPO_ROOT/agenttel-node/src/attributes.ts"
NODE_AGENTIC_FILE="$REPO_ROOT/agenttel-node/src/agentic-attributes.ts"

if [ -f "$NODE_ATTRS_FILE" ] && [ -f "$NODE_AGENTIC_FILE" ]; then
  # Extract all quoted strings matching agenttel.* or gen_ai.* (single quotes)
  {
    grep -oE "'agenttel\.[^']*'" "$NODE_ATTRS_FILE" | tr -d "'"
    grep -oE "'gen_ai\.[^']*'" "$NODE_ATTRS_FILE" | tr -d "'"
    grep -oE "'agenttel\.[^']*'" "$NODE_AGENTIC_FILE" | tr -d "'"
    grep -oE "'gen_ai\.[^']*'" "$NODE_AGENTIC_FILE" 2>/dev/null | tr -d "'" || true
  } | sort -u > "$TMPDIR/node.txt"

  NODE_COUNT=$(wc -l < "$TMPDIR/node.txt" | tr -d ' ')
  info "Node.js attributes: $NODE_COUNT keys"

  # Diff: missing from Node.js (in canonical but not in Node.js)
  NODE_MISSING=$(comm -23 "$TMPDIR/canonical.txt" "$TMPDIR/node.txt")
  NODE_EXTRA=$(comm -13 "$TMPDIR/canonical.txt" "$TMPDIR/node.txt")

  if [ -z "$NODE_MISSING" ] && [ -z "$NODE_EXTRA" ]; then
    pass "Node.js SDK — all $CANONICAL_COUNT attributes match canonical"
  else
    if [ -n "$NODE_MISSING" ]; then
      MISSING_COUNT=$(echo "$NODE_MISSING" | wc -l | tr -d ' ')
      fail "Node.js SDK — $MISSING_COUNT attributes MISSING from Node.js:"
      echo "$NODE_MISSING" | while read -r key; do
        echo "    - $key"
      done
      EXIT_CODE=1
    fi
    if [ -n "$NODE_EXTRA" ]; then
      EXTRA_COUNT=$(echo "$NODE_EXTRA" | wc -l | tr -d ' ')
      warn "Node.js SDK — $EXTRA_COUNT EXTRA attributes not in canonical:"
      echo "$NODE_EXTRA" | while read -r key; do
        echo "    + $key"
      done
      EXIT_CODE=1
    fi
  fi
else
  warn "Node.js SDK — attribute files not found, skipping"
fi

# --- Python SDK ---
echo -e "\n${BOLD}Python SDK${RESET}"
PY_ATTRS_FILE="$REPO_ROOT/agenttel-python/src/agenttel/attributes.py"
PY_AGENTIC_FILE="$REPO_ROOT/agenttel-python/src/agenttel/agentic_attributes.py"
PY_GENAI_FILE="$REPO_ROOT/agenttel-python/src/agenttel/genai/attributes.py"

PY_FILES_FOUND=0
PY_FILES=()
for f in "$PY_ATTRS_FILE" "$PY_AGENTIC_FILE" "$PY_GENAI_FILE"; do
  if [ -f "$f" ]; then
    PY_FILES_FOUND=$((PY_FILES_FOUND + 1))
    PY_FILES+=("$f")
  fi
done

if [ "$PY_FILES_FOUND" -gt 0 ]; then
  # Extract all quoted strings matching agenttel.* or gen_ai.* (double quotes)
  {
    for f in "${PY_FILES[@]}"; do
      grep -oE '"agenttel\.[^"]*"' "$f" 2>/dev/null | tr -d '"' || true
      grep -oE '"gen_ai\.[^"]*"' "$f" 2>/dev/null | tr -d '"' || true
    done
  } | sort -u > "$TMPDIR/python.txt"

  PY_COUNT=$(wc -l < "$TMPDIR/python.txt" | tr -d ' ')
  info "Python attributes: $PY_COUNT keys (from $PY_FILES_FOUND files)"

  # Diff: missing from Python (in canonical but not in Python)
  PY_MISSING=$(comm -23 "$TMPDIR/canonical.txt" "$TMPDIR/python.txt")
  PY_EXTRA=$(comm -13 "$TMPDIR/canonical.txt" "$TMPDIR/python.txt")

  if [ -z "$PY_MISSING" ] && [ -z "$PY_EXTRA" ]; then
    pass "Python SDK — all $CANONICAL_COUNT attributes match canonical"
  else
    if [ -n "$PY_MISSING" ]; then
      MISSING_COUNT=$(echo "$PY_MISSING" | wc -l | tr -d ' ')
      fail "Python SDK — $MISSING_COUNT attributes MISSING from Python:"
      echo "$PY_MISSING" | while read -r key; do
        echo "    - $key"
      done
      EXIT_CODE=1
    fi
    if [ -n "$PY_EXTRA" ]; then
      EXTRA_COUNT=$(echo "$PY_EXTRA" | wc -l | tr -d ' ')
      warn "Python SDK — $EXTRA_COUNT EXTRA attributes not in canonical:"
      echo "$PY_EXTRA" | while read -r key; do
        echo "    + $key"
      done
      EXIT_CODE=1
    fi
  fi
else
  warn "Python SDK — attribute files not found, skipping"
fi

# ─────────────────────────────────────────────────────────────────────────────
# 3. Summary
# ─────────────────────────────────────────────────────────────────────────────
echo -e "\n────────────────────────────────────────────────────────────"
if [ "$EXIT_CODE" -eq 0 ]; then
  pass "${BOLD}All SDKs match the canonical attribute schema${RESET}"
else
  fail "${BOLD}Attribute parity check failed — see mismatches above${RESET}"
fi

exit "$EXIT_CODE"
