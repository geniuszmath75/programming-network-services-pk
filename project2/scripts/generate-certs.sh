#!/usr/bin/env bash

set -Eeuo pipefail

usage() {
    cat >&2 <<'EOF'
Usage:
  PUS_CERT_PASSWORD='at-least-6-chars' ./project2/scripts/generate-certs.sh [--force] [output-directory]

The output directory defaults to project2/.certs. It must not exist unless
--force is specified. Generated keystores contain private keys and must not be
committed.
EOF
}

die() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

force=0
if [[ "${1:-}" == "--force" ]]; then
    force=1
    shift
fi

if [[ $# -gt 1 ]]; then
    usage
    exit 2
fi

if [[ -z "${PUS_CERT_PASSWORD:-}" ]]; then
    die "set PUS_CERT_PASSWORD in the environment; it is never stored in this script"
fi

if [[ ${#PUS_CERT_PASSWORD} -lt 6 ]]; then
    die "PUS_CERT_PASSWORD must contain at least 6 characters"
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
project_dir="$(cd -- "$script_dir/.." && pwd -P)"
repo_dir="$(cd -- "$project_dir/.." && pwd -P)"
output_dir="${1:-"$project_dir/.certs"}"

if [[ "$output_dir" != /* ]]; then
    output_dir="$(pwd -P)/$output_dir"
fi

if [[ -e "$output_dir" ]]; then
    [[ -d "$output_dir" ]] || die "output path is not a directory: $output_dir"
    output_dir="$(cd -- "$output_dir" && pwd -P)"

    if (( force == 0 )); then
        die "output directory already exists: $output_dir (use --force to replace it)"
    fi

    if [[ "$output_dir" == "/" || "$output_dir" == "$repo_dir" ||
        "$output_dir" == "$project_dir" || "$output_dir" == "$script_dir" ]]; then
        die "refusing to remove unsafe output directory: $output_dir"
    fi

    rm -rf -- "$output_dir"
fi

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/keytool" ]]; then
    keytool="$JAVA_HOME/bin/keytool"
else
    keytool="$(command -v keytool || true)"
fi

[[ -n "$keytool" && -x "$keytool" ]] || die "JDK keytool not found; set JAVA_HOME to a JDK installation"

umask 077
mkdir -p -- "$output_dir"
chmod 700 "$output_dir"

store_password=(-storepass:env PUS_CERT_PASSWORD)
key_password=(-keypass:env PUS_CERT_PASSWORD)
store_type=(-storetype PKCS12)

"$keytool" -genkeypair \
    -alias pus-ca \
    -keyalg RSA \
    -keysize 3072 \
    -sigalg SHA256withRSA \
    -validity 3650 \
    -dname 'CN=PUS Demo Root CA, OU=Project 2, O=PUS, C=PL' \
    -ext 'BC:c' \
    -ext 'KU=keyCertSign,cRLSign' \
    -keystore "$output_dir/ca.p12" \
    "${store_type[@]}" \
    "${store_password[@]}" \
    "${key_password[@]}"

"$keytool" -exportcert \
    -alias pus-ca \
    -rfc \
    -keystore "$output_dir/ca.p12" \
    "${store_type[@]}" \
    "${store_password[@]}" \
    -file "$output_dir/ca.crt"

"$keytool" -genkeypair \
    -alias pus-server \
    -keyalg RSA \
    -keysize 3072 \
    -sigalg SHA256withRSA \
    -validity 825 \
    -dname 'CN=localhost, OU=Project 2, O=PUS, C=PL' \
    -ext 'BC=ca:false' \
    -ext 'KU=digitalSignature,keyEncipherment' \
    -ext 'EKU=serverAuth' \
    -ext 'SAN=DNS:localhost,IP:127.0.0.1' \
    -keystore "$output_dir/server-keystore.p12" \
    "${store_type[@]}" \
    "${store_password[@]}" \
    "${key_password[@]}"

"$keytool" -certreq \
    -alias pus-server \
    -sigalg SHA256withRSA \
    -keystore "$output_dir/server-keystore.p12" \
    "${store_type[@]}" \
    "${store_password[@]}" \
    "${key_password[@]}" \
    -file "$output_dir/server.csr"

"$keytool" -gencert \
    -alias pus-ca \
    -sigalg SHA256withRSA \
    -validity 825 \
    -infile "$output_dir/server.csr" \
    -rfc \
    -ext 'BC=ca:false' \
    -ext 'KU=digitalSignature,keyEncipherment' \
    -ext 'EKU=serverAuth' \
    -ext 'SAN=DNS:localhost,IP:127.0.0.1' \
    -keystore "$output_dir/ca.p12" \
    "${store_type[@]}" \
    "${store_password[@]}" \
    "${key_password[@]}" \
    -outfile "$output_dir/server.crt"

"$keytool" -importcert \
    -alias pus-ca \
    -noprompt \
    -file "$output_dir/ca.crt" \
    -keystore "$output_dir/server-keystore.p12" \
    "${store_type[@]}" \
    "${store_password[@]}"

"$keytool" -importcert \
    -alias pus-server \
    -noprompt \
    -file "$output_dir/server.crt" \
    -keystore "$output_dir/server-keystore.p12" \
    "${store_type[@]}" \
    "${store_password[@]}"

"$keytool" -genkeypair \
    -alias pus-client \
    -keyalg RSA \
    -keysize 3072 \
    -sigalg SHA256withRSA \
    -validity 825 \
    -dname 'CN=pus-client, OU=Project 2, O=PUS, C=PL' \
    -ext 'BC=ca:false' \
    -ext 'KU=digitalSignature' \
    -ext 'EKU=clientAuth' \
    -keystore "$output_dir/client-keystore.p12" \
    "${store_type[@]}" \
    "${store_password[@]}" \
    "${key_password[@]}"

"$keytool" -certreq \
    -alias pus-client \
    -sigalg SHA256withRSA \
    -keystore "$output_dir/client-keystore.p12" \
    "${store_type[@]}" \
    "${store_password[@]}" \
    "${key_password[@]}" \
    -file "$output_dir/client.csr"

"$keytool" -gencert \
    -alias pus-ca \
    -sigalg SHA256withRSA \
    -validity 825 \
    -infile "$output_dir/client.csr" \
    -rfc \
    -ext 'BC=ca:false' \
    -ext 'KU=digitalSignature' \
    -ext 'EKU=clientAuth' \
    -keystore "$output_dir/ca.p12" \
    "${store_type[@]}" \
    "${store_password[@]}" \
    "${key_password[@]}" \
    -outfile "$output_dir/client.crt"

"$keytool" -importcert \
    -alias pus-ca \
    -noprompt \
    -file "$output_dir/ca.crt" \
    -keystore "$output_dir/client-keystore.p12" \
    "${store_type[@]}" \
    "${store_password[@]}"

"$keytool" -importcert \
    -alias pus-client \
    -noprompt \
    -file "$output_dir/client.crt" \
    -keystore "$output_dir/client-keystore.p12" \
    "${store_type[@]}" \
    "${store_password[@]}"

"$keytool" -importcert \
    -alias pus-ca \
    -noprompt \
    -file "$output_dir/ca.crt" \
    -keystore "$output_dir/server-truststore.p12" \
    "${store_type[@]}" \
    "${store_password[@]}"

"$keytool" -importcert \
    -alias pus-ca \
    -noprompt \
    -file "$output_dir/ca.crt" \
    -keystore "$output_dir/client-truststore.p12" \
    "${store_type[@]}" \
    "${store_password[@]}"

rm -f -- "$output_dir/server.csr" "$output_dir/client.csr"

printf 'Generated Java keystores and trust stores in %s\n' "$output_dir"
