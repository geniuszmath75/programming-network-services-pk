#!/usr/bin/env bash
# Demo firewall for project1 (dedicated nftables table inet pus_project1).
# Usage: ./firewall.sh allow|deny|reset
set -euo pipefail

TABLE=pus_project1
TCP_PORTS="{ 801, 9000 }"
UDP_PORTS="{ 802, 9001 }"
IP_PROTO=253

command -v nft >/dev/null || { echo "error: install nftables" >&2; exit 1; }

delete_table() {
  sudo nft delete table inet "${TABLE}" 2>/dev/null || true
}

apply_rules() {
  local action=$1
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
    apply_rules accept
    ;;
  deny)
    # TCP gets an explicit reset; UDP/RAW use generic reject.
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
    delete_table
    echo "Removed table inet ${TABLE} (if it existed)."
    ;;
  *)
    echo "Usage: $0 allow|deny|reset" >&2
    exit 1
    ;;
esac
