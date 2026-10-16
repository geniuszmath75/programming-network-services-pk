## Project 1 — TCP / UDP / RAW

Requires **JDK 25** (bytecode version 69). System `/usr/bin/java` is often older (e.g. 21) —
set `JAVA_HOME` before build/run. With `sudo`, always use `"$JAVA_HOME/bin/java"` (sudo resets `PATH`).

```bash
export JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/openjdk-25.0.2}"
export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl project1 compile
```

Protocol: one UTF-8 line `PING <text>` → `PONG <text>` (max 512 bytes).
TCP/UDP accept IPv4 or IPv6 via `InetAddress`. RAW is IPv4-only (IP protocol 253).

### TCP / UDP — IPv4

```bash
java -cp project1/target/classes org.example.project1.tcp.TcpServer 9000 127.0.0.1
java -cp project1/target/classes org.example.project1.tcp.TcpClient 127.0.0.1 9000 hello

java -cp project1/target/classes org.example.project1.udp.UdpServer 9001 127.0.0.1
java -cp project1/target/classes org.example.project1.udp.UdpClient 127.0.0.1 9001 hello
```

### TCP / UDP — IPv6

```bash
java -cp project1/target/classes org.example.project1.tcp.TcpServer 9000 ::1
java -cp project1/target/classes org.example.project1.tcp.TcpClient ::1 9000 hello

java -cp project1/target/classes org.example.project1.udp.UdpServer 9001 ::1
java -cp project1/target/classes org.example.project1.udp.UdpClient ::1 9001 hello
```

### Linux capabilities (ports &lt;1024 + RAW without root)

Do **not** `setcap` on `$JAVA_HOME/bin/java` — glibc then ignores `$ORIGIN` and `libjli.so` fails to load.
`grant-caps.sh` builds `project1/.cap-runtime/java` (copy + absolute RPATH via `patchelf` + capabilities).

```bash
sudo apt install libcap2-bin patchelf   # once
export JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/openjdk-25.0.2}"
./project1/scripts/grant-caps.sh
CAP_JAVA=project1/.cap-runtime/java
```

Revoke: `./project1/scripts/revoke-caps.sh`

#### Privileged TCP / UDP (ports &lt;1024)

```bash
"$CAP_JAVA" -cp project1/target/classes org.example.project1.tcp.TcpServer 801 127.0.0.1
java -cp project1/target/classes org.example.project1.tcp.TcpClient 127.0.0.1 801 hello

"$CAP_JAVA" -cp project1/target/classes org.example.project1.udp.UdpServer 802 127.0.0.1
java -cp project1/target/classes org.example.project1.udp.UdpClient 127.0.0.1 802 hello
```

### RAW sockets (IPv4, IP proto 253)

JDK Panama FFM; needs `CAP_NET_RAW` (`grant-caps.sh`) or root. Use `--enable-native-access=ALL-UNNAMED`.

```bash
CAP_JAVA=project1/.cap-runtime/java

"$CAP_JAVA" --enable-native-access=ALL-UNNAMED -cp project1/target/classes \
  org.example.project1.raw.RawServer

"$CAP_JAVA" --enable-native-access=ALL-UNNAMED -cp project1/target/classes \
  org.example.project1.raw.RawClient 127.0.0.1 hello
```

Fallback: `sudo "$JAVA_HOME/bin/java" --enable-native-access=ALL-UNNAMED -cp ... RawServer`

### Firewall (nftables) — allow / deny demo traffic

One script, dedicated table `inet pus_project1` (does not replace the system firewall).
Covers TCP `801,9000`, UDP `802,9001`, and IP protocol `253` (RAW).

```bash
sudo apt install nftables   # if needed
```

Demo sequence (TCP; same idea for UDP/RAW):

```bash
# terminal 1 — server
java -cp project1/target/classes org.example.project1.tcp.TcpServer 9000 127.0.0.1

# terminal 2
./project1/scripts/firewall.sh deny    # client → Connection refused
java -cp project1/target/classes org.example.project1.tcp.TcpClient 127.0.0.1 9000 hello

./project1/scripts/firewall.sh allow   # client works again
java -cp project1/target/classes org.example.project1.tcp.TcpClient 127.0.0.1 9000 hello

./project1/scripts/firewall.sh reset   # remove demo table
```

Inspect: `sudo nft list chain inet pus_project1 input`
