#!/usr/bin/env bash
# Create project1/.cap-runtime/java with cap_net_raw + cap_net_bind_service.
# Copy + absolute RPATH: setcap on stock java breaks libjli.so ($ORIGIN ignored).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/openjdk-25.0.2}"
JAVA_BIN="${JAVA_HOME}/bin/java"
CAP_JAVA="${SCRIPT_DIR}/../.cap-runtime/java"

[[ -x "${JAVA_BIN}" ]] || { echo "error: missing ${JAVA_BIN} (set JAVA_HOME)" >&2; exit 1; }
command -v setcap >/dev/null && command -v getcap >/dev/null \
  || { echo "error: install libcap2-bin" >&2; exit 1; }
command -v patchelf >/dev/null \
  || { echo "error: install patchelf" >&2; exit 1; }

# Repair leftover setcap on the real JDK launcher.
if getcap "${JAVA_BIN}" 2>/dev/null | grep -q '='; then
  echo "Stripping capabilities from ${JAVA_BIN}"
  sudo setcap -r "${JAVA_BIN}"
fi

mkdir -p "$(dirname "${CAP_JAVA}")"
cp -f "${JAVA_BIN}" "${CAP_JAVA}"
chmod 755 "${CAP_JAVA}"
patchelf --set-rpath "${JAVA_HOME}/lib" "${CAP_JAVA}"
sudo setcap 'cap_net_raw,cap_net_bind_service=ep' "${CAP_JAVA}"

echo "Ready: ${CAP_JAVA}"
getcap "${CAP_JAVA}"
echo "Use this binary for ports <1024 and RAW (see project1/README.md)."
