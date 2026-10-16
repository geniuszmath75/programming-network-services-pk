# Project 1 — starter pack klienta i serwera (PUS)

Cel: samodzielna komunikacja w warstwie 4 (TCP/UDP) i 3 (SOCK_RAW), IPv4+IPv6, Linux capabilities, firewall.
Technologia: **Java 25**, gniazda JDK + Panama FFM (libc) dla RAW — bez gotowej biblioteki protokołu.

## Mapa kodu

| Ścieżka | Rola |
|---------|------|
| `common/Messages.java` | Protokół aplikacji `PING`/`PONG` |
| `common/Net.java` | Etykieta IPv4/IPv6 + UDP socket pod IPv6 |
| `tcp/TcpServer.java`, `TcpClient.java` | TCP (strumień, połączeniowy) |
| `udp/UdpServer.java`, `UdpClient.java` | UDP (datagram, bezpołączeniowy) |
| `raw/Libc.java` | FFM → `socket`/`sendto`/`recvfrom`/`setsockopt` |
| `raw/IpPacket.java` | Parse nagłówka IPv4 z recv |
| `raw/RawSocket.java` | SOCK_RAW, proto **253** |
| `raw/RawServer.java`, `RawClient.java` | Klient/serwer RAW |
| `scripts/grant-caps.sh` / `revoke-caps.sh` | Capabilities bez root w runtime |
| `scripts/firewall.sh` | nftables allow/deny/reset |

Protokół aplikacji (wszędzie ten sam): jedna linia UTF-8 `PING <text>` → `PONG <text>` (max 512 B).

## Wymagania

- JDK **25** (`JAVA_HOME`, nie systemowe Java 21)
- Maven
- Linux: `libcap2-bin`, `patchelf` (capabilities), `nftables` (firewall)

```bash
export JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/openjdk-25.0.2}"
export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl project1 compile
```

## Demo — TCP / UDP (IPv4)

```bash
java -cp project1/target/classes org.example.project1.tcp.TcpServer 9000 127.0.0.1
java -cp project1/target/classes org.example.project1.tcp.TcpClient 127.0.0.1 9000 hello

java -cp project1/target/classes org.example.project1.udp.UdpServer 9001 127.0.0.1
java -cp project1/target/classes org.example.project1.udp.UdpClient 127.0.0.1 9001 hello
```

## Demo — TCP / UDP (IPv6)

```bash
java -cp project1/target/classes org.example.project1.tcp.TcpServer 9000 ::1
java -cp project1/target/classes org.example.project1.tcp.TcpClient ::1 9000 hello

java -cp project1/target/classes org.example.project1.udp.UdpServer 9001 ::1
java -cp project1/target/classes org.example.project1.udp.UdpClient ::1 9001 hello
```

## Linux capabilities (porty &lt;1024 + RAW bez root)

Nie wolno `setcap` na stockowym `$JAVA_HOME/bin/java` — wtedy `libjli.so` się nie ładuje (`$ORIGIN` ignorowane przy AT_SECURE).
Skrypt buduje kopię `project1/.cap-runtime/java` z absolutnym RPATH + capabilities.

```bash
sudo apt install libcap2-bin patchelf
./project1/scripts/grant-caps.sh
CAP_JAVA=project1/.cap-runtime/java

"$CAP_JAVA" -cp project1/target/classes org.example.project1.tcp.TcpServer 801 127.0.0.1
java -cp project1/target/classes org.example.project1.tcp.TcpClient 127.0.0.1 801 hello

"$CAP_JAVA" -cp project1/target/classes org.example.project1.udp.UdpServer 802 127.0.0.1
java -cp project1/target/classes org.example.project1.udp.UdpClient 127.0.0.1 802 hello
```

Cofnięcie: `./project1/scripts/revoke-caps.sh`

## RAW (IPv4, IP proto 253)

```bash
CAP_JAVA=project1/.cap-runtime/java

"$CAP_JAVA" --enable-native-access=ALL-UNNAMED -cp project1/target/classes \
  org.example.project1.raw.RawServer

"$CAP_JAVA" --enable-native-access=ALL-UNNAMED -cp project1/target/classes \
  org.example.project1.raw.RawClient 127.0.0.1 hello
```

Fallback: `sudo "$JAVA_HOME/bin/java" --enable-native-access=ALL-UNNAMED -cp ... RawServer`

## Firewall (nftables)

Tabela `inet pus_project1` — TCP `801,9000`, UDP `802,9001`, IP proto `253`.

```bash
# terminal 1
java -cp project1/target/classes org.example.project1.tcp.TcpServer 9000 127.0.0.1

# terminal 2
./project1/scripts/firewall.sh deny    # Connection refused
java -cp project1/target/classes org.example.project1.tcp.TcpClient 127.0.0.1 9000 hello

./project1/scripts/firewall.sh allow
java -cp project1/target/classes org.example.project1.tcp.TcpClient 127.0.0.1 9000 hello

./project1/scripts/firewall.sh reset
```

## Pokrycie ocen (z zasad P1)

| Ocena | Wymaganie | Gdzie w projekcie |
|-------|-----------|-------------------|
| 3.0 | TCP, UDP, IPv4 | `tcp/*`, `udp/*` |
| 3.5 | IPv6 | ten sam kod, host `::1` |
| 4.0 | gniazda surowe | `raw/*`, proto 253 |
| 4.5 | capabilities | `scripts/grant-caps.sh`, porty 801/802 |
| 5.0 | firewall | `scripts/firewall.sh` |
