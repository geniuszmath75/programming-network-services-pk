# Project 2 — bezpieczna komunikacja (PUS)

Cel: demonstrator bezpiecznej komunikacji klient–serwer w Javie, przygotowany
pod wymagania indywidualnego Projektu 2:

- TLS 1.3 nad TCP,
- własne CA i certyfikaty X.509,
- wzajemne uwierzytelnianie certyfikatami (mTLS),
- własny protokół aplikacyjny `PING`/`PONG`,
- klient i serwer QUIC v1 z ALPN.

Aktualnie działają części TCP/TLS/mTLS oraz QUIC v1.

## Technologia i wymagania

- **Java 25** — wymagany pełny JDK, nie samo JRE,
- Maven,
- standardowe JSSE z JDK (`SSLContext`, `SSLSocket`, `SSLServerSocket`),
- JDK `keytool` do generowania certyfikatów,
- `tech.kwik:kwik:0.12.1` — czysta javowa implementacja QUIC używana przez
  klienta i serwer, skonfigurowana w [`project2/pom.xml`](pom.xml).

Projekt nie wymaga OpenSSL, JNI ani bibliotek natywnych.

Przykładowa konfiguracja środowiska:

```bash
export JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/openjdk-25.0.2}"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
```

Hasło do lokalnych keystore’ów jest przekazywane przez zmienną środowiskową.
Nie zapisuj go w repozytorium ani w argumentach poleceń:

```bash
export PUS_CERT_PASSWORD='local-demo-password'
```

## Mapa kodu

| Ścieżka | Rola |
|---|---|
| `src/main/java/org/example/project2/cli/CommandLineSupport.java` | Wspólne parsowanie argumentów klienta i serwera |
| `src/main/java/org/example/project2/protocol/ProtocolMessage.java` | Typy wiadomości `PING`, `PONG`, `ERROR` i walidacja danych |
| `src/main/java/org/example/project2/protocol/MessageCodec.java` | Kodowanie/dekodowanie ramek protokołu |
| `src/main/java/org/example/project2/protocol/ProtocolException.java` | Błędy formatu i walidacji protokołu |
| `src/main/java/org/example/project2/protocol/ProtocolSupport.java` | Wspólna obsługa żądań i odpowiedzi TCP/QUIC |
| `src/main/java/org/example/project2/tls/TlsSupport.java` | Ładowanie keystore/truststore i konfiguracja TLS 1.3 |
| `src/main/java/org/example/project2/tls/TlsServer.java` | Serwer TCP z obowiązkowym certyfikatem klienta |
| `src/main/java/org/example/project2/tls/TlsClient.java` | Klient TCP przedstawiający certyfikat klienta |
| `src/main/java/org/example/project2/quic/QuicServer.java` | Serwer QUIC v1 z ALPN `pus-demo/1` |
| `src/main/java/org/example/project2/quic/QuicClient.java` | Klient QUIC v1 z weryfikacją certyfikatu serwera |
| `scripts/generate-certs.sh` | Własne CA, certyfikaty i trust store’y przez `keytool` |
| `.certs/` | Lokalne keystore’y i certyfikaty; katalog jest ignorowany przez Git |

## Budowanie

Z katalogu głównego repozytorium:

```bash
mvn -pl project2 clean compile
```

Klasy wynikowe znajdują się w `project2/target/classes`.

## Certyfikaty

Skrypt tworzy:

- `ca.p12` i `ca.crt` — własne CA,
- `server-keystore.p12` — klucz prywatny i certyfikat serwera,
- `client-keystore.p12` — klucz prywatny i certyfikat klienta,
- `server-truststore.p12` — CA zaufane przez serwer,
- `client-truststore.p12` — CA zaufane przez klienta.

Certyfikat serwera zawiera:

- SAN: `localhost`, `127.0.0.1`,
- EKU: `serverAuth`.

Certyfikat klienta zawiera EKU `clientAuth`.

Generowanie:

```bash
export PUS_CERT_PASSWORD='local-demo-password'
./project2/scripts/generate-certs.sh
```

Domyślny katalog wyjściowy to `project2/.certs`. Aby wygenerować materiały
ponownie, jawnie użyj `--force`:

```bash
./project2/scripts/generate-certs.sh --force
```

Opcjonalnie można podać inny katalog:

```bash
./project2/scripts/generate-certs.sh /tmp/pus-project2-certs
```

## Demo — TCP z TLS 1.3 i mTLS

Najpierw wygeneruj certyfikaty i zbuduj moduł:

```bash
export PUS_CERT_PASSWORD='local-demo-password'
./project2/scripts/generate-certs.sh
mvn -pl project2 compile
```

Terminal 1 — serwer:

```bash
"$JAVA_HOME/bin/java" \
  -cp project2/target/classes \
  org.example.project2.tls.TlsServer 9443
```

Serwer używa `server-keystore.p12`, ufa CA z `server-truststore.p12` i wymaga
certyfikatu klienta przez `setNeedClientAuth(true)`.

Terminal 2 — poprawny klient:

```bash
"$JAVA_HOME/bin/java" \
  -cp project2/target/classes \
  org.example.project2.tls.TlsClient \
  localhost 9443 project2/.certs hello
```

Oczekiwany wynik:

```text
PONG requestId=1 payload=hello
```

Parametry:

```text
TlsServer [port] [certificate-directory]
TlsClient [host] [port] [certificate-directory] [payload]
```

Domyślny port to `9443`, a domyślny katalog certyfikatów to
`project2/.certs`.

## Demo — odrzucenie nieprawidłowego certyfikatu

Certyfikat serwera nie ma przeznaczenia `clientAuth`, więc nie może służyć jako
certyfikat klienta. Przy działającym serwerze można przygotować tymczasowy
keystore:

```bash
rm -rf /tmp/pus-invalid-client
mkdir -p /tmp/pus-invalid-client
cp project2/.certs/server-keystore.p12 \
   /tmp/pus-invalid-client/client-keystore.p12
cp project2/.certs/server-truststore.p12 \
   /tmp/pus-invalid-client/client-truststore.p12
```

Uruchomienie klienta z tym katalogiem powinno zakończyć się błędem handshake’u:

```bash
"$JAVA_HOME/bin/java" \
  -cp project2/target/classes \
  org.example.project2.tls.TlsClient \
  localhost 9443 /tmp/pus-invalid-client rejected-client
```

Serwer powinien zgłosić:

```text
TLS handshake rejected
```

## Demo — QUIC v1

QUIC używa UDP, TLS 1.3 wewnątrz QUIC oraz ALPN `pus-demo/1`. Serwer korzysta
z certyfikatu `pus-server` z `server-keystore.p12`, a klient weryfikuje go
względem CA z `client-truststore.p12`.

Terminal 1 — serwer:

```bash
"$JAVA_HOME/bin/java" \
  -cp project2/target/classes:$HOME/.m2/repository/tech/kwik/kwik/0.12.1/kwik-0.12.1.jar \
  org.example.project2.quic.QuicServer 9444
```

Jeżeli uruchamiasz bez Mavenowego classpath, dodaj również runtime’owe
zależności Kwik (`agent15`, `hkdf` i `io.whitfin.siphash`). Najwygodniej użyć
`mvn dependency:build-classpath` albo classpath skonfigurowanego przez IDE.

Terminal 2 — klient:

```bash
"$JAVA_HOME/bin/java" \
  -cp project2/target/classes:<kwik-runtime-classpath> \
  org.example.project2.quic.QuicClient \
  localhost 9444 project2/.certs hello-quic
```

Oczekiwany wynik:

```text
PONG requestId=1 payload=hello-quic
```

Parametry:

```text
QuicServer [port] [certificate-directory]
QuicClient [host] [port] [certificate-directory] [payload]
```

## Protokół aplikacyjny

Protokół jest tekstowy, liniowy i działa zarówno nad TCP, jak i nad
pojedynczym strumieniem QUIC.

```text
PING <requestId> <base64Payload>\n
PONG <requestId> <base64Payload>\n
ERROR <errorCode> <base64Message>\n
```

Przykład:

```text
PING 1 aGVsbG8=
PONG 1 aGVsbG8=
```

Założenia i walidacja:

- payload jest binarny, ale na przewodzie używa Base64,
- `requestId` musi być dodatnią liczbą całkowitą,
- maksymalny payload ma 1024 bajty,
- maksymalna ramka ma 4096 bajtów,
- wymagane jest poprawne UTF-8,
- ramki kończą się LF; CRLF jest akceptowane,
- błędny typ, Base64, request ID, UTF-8 lub rozmiar powoduje
  `ProtocolException`,
- komunikaty błędów nie zawierają sekretów ani zawartości certyfikatów.

Aktualne kody błędów protokołu:

```text
FRAME_TOO_LARGE
INVALID_FORMAT
INVALID_MESSAGE_TYPE
INVALID_REQUEST_ID
INVALID_PAYLOAD
INVALID_ERROR
INVALID_UTF8
INCOMPLETE_FRAME
```

## Bezpieczeństwo

- używany jest wyłącznie TLS 1.3,
- klient weryfikuje certyfikat serwera względem własnego trust store’a oraz
  nazwy hosta,
- serwer wymaga certyfikatu klienta i weryfikuje go względem własnego CA,
- klucze prywatne pozostają w lokalnych plikach PKCS12 z prawami ograniczonymi
  przez `umask 077`,
- hasło jest pobierane ze zmiennej `PUS_CERT_PASSWORD`,
- `project2/.certs/` jest ignorowane przez Git,
- serwer loguje odrzucenie handshake’u bez ujawniania szczegółów certyfikatu.

Są to certyfikaty demonstracyjne. Nie używaj ich w środowisku produkcyjnym.

## Pokrycie wymagań Projektu 2

| Ocena | Wymaganie | Realizacja |
|---|---|---|
| 3.0 | TCP klient–serwer z TLS | `TlsClient`, `TlsServer`, TLS 1.3 |
| 3.5 | Własne CA i certyfikaty | `scripts/generate-certs.sh`, `keytool` |
| 4.0 | Uwierzytelnianie obu stron certyfikatami | `setNeedClientAuth(true)` i certyfikat klienta |
| 4.5 | Klient QUIC | `QuicClient`, custom trust store, QUIC v1 |
| 5.0 | Serwer QUIC | `QuicServer`, ALPN `pus-demo/1`, obsługa strumieni |

## Znane ograniczenia

- serwer TCP obsługuje połączenia sekwencyjnie,
- demo jest przeznaczone do uruchamiania lokalnego,
- certyfikat serwera zawiera tylko `localhost` i `127.0.0.1`,
- generowane certyfikaty mają hasło podane przez użytkownika, ale nie są
  przeznaczone do produkcji,
- Kwik nie oferuje po stronie serwera konfiguracji wymuszającej mTLS, dlatego
  wzajemne uwierzytelnianie certyfikatami jest demonstrowane na TCP; QUIC
  weryfikuje certyfikat serwera,
- testy automatyczne i pełna dokumentacja prezentacji są kolejnym etapem.
