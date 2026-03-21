#!/bin/bash
# ---------------------------------------------------------------------------
# Compare the current OpenAPI contract against the baseline on main and fail
# only on backward-incompatible changes.
#
# Usage:
#   ./scripts/openapi-diff.sh
#   ./scripts/openapi-diff.sh /path/to/baseline.yaml contracts/openapi.yaml
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

BASELINE_SPEC="${1:-}"
CURRENT_SPEC="${2:-contracts/openapi.yaml}"
TEMP_BASELINE=""
# The repo ships an OpenAPI 3.1 contract. The requested `openapi-diff` package
# is kept in package.json, but actual breaking-change detection uses oasdiff
# because `openapi-diff` only supports OpenAPI 3.0.x.
OASDIFF_VERSION="1.10.9"

cleanup() {
  if [[ -n "$TEMP_BASELINE" && -f "$TEMP_BASELINE" ]]; then
    rm -f "$TEMP_BASELINE"
  fi
}
trap cleanup EXIT

resolve_oasdiff_binary() {
  if command -v oasdiff >/dev/null 2>&1; then
    command -v oasdiff
    return 0
  fi

  local os
  local arch
  local asset
  case "$(uname -s)" in
    Linux) os="linux" ;;
    Darwin) os="darwin" ;;
    *)
      echo "Unsupported OS for oasdiff bootstrap: $(uname -s)" >&2
      return 1
      ;;
  esac

  case "$(uname -m)" in
    x86_64|amd64) arch="amd64" ;;
    arm64|aarch64) arch="arm64" ;;
    *)
      echo "Unsupported architecture for oasdiff bootstrap: $(uname -m)" >&2
      return 1
      ;;
  esac

  if [[ "$os" == "darwin" ]]; then
    asset="oasdiff_${OASDIFF_VERSION}_darwin_all.tar.gz"
  else
    asset="oasdiff_${OASDIFF_VERSION}_${os}_${arch}.tar.gz"
  fi

  local install_dir="${XDG_CACHE_HOME:-$HOME/.cache}/weekly-commitments/oasdiff/v${OASDIFF_VERSION}"
  local binary="$install_dir/oasdiff"
  if [[ ! -x "$binary" ]]; then
    mkdir -p "$install_dir"
    local archive
    archive="$(mktemp "${TMPDIR:-/tmp}/oasdiff.XXXXXX.tar.gz")"
    curl -sSfL \
      "https://github.com/Tufin/oasdiff/releases/download/v${OASDIFF_VERSION}/${asset}" \
      -o "$archive"
    tar -xzf "$archive" -C "$install_dir" oasdiff
    chmod +x "$binary"
    rm -f "$archive"
  fi

  printf '%s\n' "$binary"
}

if [[ -z "$BASELINE_SPEC" ]]; then
  git fetch origin main --depth=1 >/dev/null 2>&1 || true
  TEMP_BASELINE="$(mktemp "${TMPDIR:-/tmp}/openapi-main.XXXXXX.yaml")"
  if ! git show origin/main:contracts/openapi.yaml > "$TEMP_BASELINE" 2>/dev/null; then
    echo "No OpenAPI spec found on main branch – skipping breaking-change check."
    exit 0
  fi
  BASELINE_SPEC="$TEMP_BASELINE"
fi

OASDIFF_BIN="$(resolve_oasdiff_binary)"
"$OASDIFF_BIN" breaking "$BASELINE_SPEC" "$CURRENT_SPEC" --fail-on ERR
