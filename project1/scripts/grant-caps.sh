#!/usr/bin/env bash
# Builds a project-local java launcher with file capabilities for RAW + ports <1024.
# Why a copy? setcap on stock $JAVA_HOME/bin/java breaks libjli.so (AT_SECURE ignores $ORIGIN).
set -euo pipefail

# --- paths: scripts/ → .cap-runtime/java next to project1 ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/openjdk-25.0.2}"
JAVA_BIN="${JAVA_HOME}/bin/java"
CAP_JAVA="${SCRIPT_DIR}/../.cap-runtime/java"

# --- prerequisites: JDK 25 java + setcap/getcap + patchelf ---
[[ -x "${JAVA_BIN}" ]] || { echo "error: missing ${JAVA_BIN} (set JAVA_HOME)" >&2; exit 1; }
command -v setcap >/dev/null && command -v getcap >/dev/null \
  || { echo "error: install libcap2-bin" >&2; exit 1; }
command -v patchelf >/dev/null \
  || { echo "error: install patchelf" >&2; exit 1; }

# --- repair: strip caps from real JDK java if an older demo left it broken ---
if getcap "${JAVA_BIN}" 2>/dev/null | grep -q '='; then
  echo "Stripping capabilities from ${JAVA_BIN}"
  sudo setcap -r "${JAVA_BIN}"
fi

# --- build capped launcher: copy → absolute RPATH to libjli → setcap ---
mkdir -p "$(dirname "${CAP_JAVA}")"
cp -f "${JAVA_BIN}" "${CAP_JAVA}"
chmod 700 "${CAP_JAVA}"
# Absolute RPATH so the dynamic linker finds libjli.so under AT_SECURE
patchelf --set-rpath "${JAVA_HOME}/lib" "${CAP_JAVA}"
# cap_net_raw = SOCK_RAW; cap_net_bind_service = listen on ports <1024
sudo setcap 'cap_net_raw,cap_net_bind_service=ep' "${CAP_JAVA}"

echo "Ready: ${CAP_JAVA}"
getcap "${CAP_JAVA}"
echo "Use this binary for ports <1024 and RAW (see project1/README.md)."
