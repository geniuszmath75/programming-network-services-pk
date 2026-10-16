#!/usr/bin/env bash
# Demo firewall for project1 using a dedicated nftables table (does not replace system FW).
# Usage: ./firewall.sh allow|deny|reset
set -euo pipefail

# --- demo traffic covered by the rules ---
TABLE=pus_project1
TCP_PORTS="{ 801, 9000 }"   # privileged + unprivileged TCP
UDP_PORTS="{ 802, 9001 }"   # privileged + unprivileged UDP
IP_PROTO=253                # RAW demo protocol

command -v nft >/dev/null || { echo "error: install nftables" >&2; exit 1; }

# Removes the demo table if present (safe no-op when missing).
delete_table() {
  sudo nft delete table inet "${TABLE}" 2>/dev/null || true
}

# Installs an INPUT chain with the same ports/proto and a single verdict (e.g. accept).
# Used by "allow"; "deny" needs a custom TCP reset so it has its own block below.
apply_rules() {
  local action=$1   # nft verdict: accept / drop / …
  delete_table
  sudo nft -f - <<EOF
table inet ${TABLE} {
  chain input {
    type filter hook input priority filter; policy accept;
    tcp dport ${TCP_PORTS} ${action} comment "pus-project1-tcp"
    udp dport ${UDP_PORTS} ${action} comment "pus-project1-udp"
    ip protocol ${IP_PROTO} ${action} comment "pus-project1-raw"
  }
}
EOF
  echo "${action^^}: TCP ${TCP_PORTS}, UDP ${UDP_PORTS}, IP proto ${IP_PROTO}"
  sudo nft list chain inet "${TABLE}" input
}

case "${1:-}" in
  allow)
    # Explicit ACCEPT rules for demo traffic (policy is already accept).
    apply_rules accept
    ;;
  deny)
    # REJECT so demos fail immediately (TCP reset → Connection refused).
    delete_table
    sudo nft -f - <<EOF
table inet ${TABLE} {
  chain input {
    type filter hook input priority filter; policy accept;
    tcp dport ${TCP_PORTS} reject with tcp reset comment "pus-project1-tcp"
    udp dport ${UDP_PORTS} reject comment "pus-project1-udp"
    ip protocol ${IP_PROTO} reject comment "pus-project1-raw"
  }
}
EOF
    echo "DENY: TCP ${TCP_PORTS}, UDP ${UDP_PORTS}, IP proto ${IP_PROTO}"
    sudo nft list chain inet "${TABLE}" input
    echo "Expected: TcpClient → Connection refused; UdpClient/RawClient → error/timeout."
    ;;
  reset)
    # Cleanup: drop the whole demo table.
    delete_table
    echo "Removed table inet ${TABLE} (if it existed)."
    ;;
  *)
    echo "Usage: $0 allow|deny|reset" >&2
    exit 1
    ;;
esac
