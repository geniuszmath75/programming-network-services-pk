
## Project 1 — TCP / UDP (IPv4 + IPv6)

Build:

```bash
mvn -pl project1 compile
```

Protocol: one UTF-8 line `PING <text>` → `PONG <text>` (max 512 bytes).
The same client/server classes accept IPv4 or IPv6 addresses via `InetAddress`.

### IPv4

```bash
java -cp project1/target/classes org.example.project1.tcp.TcpServer 9000 127.0.0.1
java -cp project1/target/classes org.example.project1.tcp.TcpClient 127.0.0.1 9000 hello

java -cp project1/target/classes org.example.project1.udp.UdpServer 9001 127.0.0.1
java -cp project1/target/classes org.example.project1.udp.UdpClient 127.0.0.1 9001 hello
```

### IPv6

```bash
java -cp project1/target/classes org.example.project1.tcp.TcpServer 9000 ::1
java -cp project1/target/classes org.example.project1.tcp.TcpClient ::1 9000 hello

java -cp project1/target/classes org.example.project1.udp.UdpServer 9001 ::1
java -cp project1/target/classes org.example.project1.udp.UdpClient ::1 9001 hello
```
