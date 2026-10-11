## Project 1 — stage A (TCP / UDP, IPv4)

Build:

```bash
mvn -pl project1 compile
```

TCP (two terminals):

```bash
java -cp project1/target/classes org.example.project1.tcp.TcpServer 9000 127.0.0.1
java -cp project1/target/classes org.example.project1.tcp.TcpClient 127.0.0.1 9000 hello
```

UDP:

```bash
java -cp project1/target/classes org.example.project1.udp.UdpServer 9001 127.0.0.1
java -cp project1/target/classes org.example.project1.udp.UdpClient 127.0.0.1 9001 hello
```

Protocol: one UTF-8 line `PING <text>` → `PONG <text>` (max 512 bytes).