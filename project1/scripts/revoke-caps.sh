#!/usr/bin/env bash
# Remove demo capabilities and delete project1/.cap-runtime/java.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/openjdk-25.0.2}"
JAVA_BIN="${JAVA_HOME}/bin/java"
CAP_DIR="${SCRIPT_DIR}/../.cap-runtime"
CAP_JAVA="${CAP_DIR}/java"

command -v setcap >/dev/null && command -v getcap >/dev/null \
  || { echo "error: install libcap2-bin" >&2; exit 1; }

if [[ -x "${JAVA_BIN}" ]] && getcap "${JAVA_BIN}" 2>/dev/null | grep -q '='; then
  sudo setcap -r "${JAVA_BIN}"
  echo "Restored ${JAVA_BIN}"
fi

if [[ -e "${CAP_JAVA}" ]]; then
  getcap "${CAP_JAVA}" 2>/dev/null | grep -q '=' && sudo setcap -r "${CAP_JAVA}" || true
  rm -rf "${CAP_DIR}"
  echo "Removed ${CAP_DIR}"
else
  echo "Nothing to remove at ${CAP_JAVA}"
fi
