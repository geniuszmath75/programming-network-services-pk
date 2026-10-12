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

### RAW sockets (IPv4, IP proto 253)

Uses JDK Panama FFM (`socket`/`sendto`/`recvfrom`). Needs `CAP_NET_RAW` or root.
Also pass `--enable-native-access=ALL-UNNAMED` for FFM.

```bash
export JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/openjdk-25.0.2}"

sudo "$JAVA_HOME/bin/java" --enable-native-access=ALL-UNNAMED -cp project1/target/classes \
  org.example.project1.raw.RawServer

sudo "$JAVA_HOME/bin/java" --enable-native-access=ALL-UNNAMED -cp project1/target/classes \
  org.example.project1.raw.RawClient 127.0.0.1 hello
```
