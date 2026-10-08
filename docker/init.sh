#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
env_file=docker/.env
if [ ! -f "$env_file" ]; then
  (
    umask 077
    {
      echo "SOA_DB_PASSWORD=$(openssl rand -hex 24)"
      echo "SOA_TLS_PASSWORD=$(openssl rand -hex 24)"
      echo "SOA_PAYARA_MASTER_PASSWORD=$(openssl rand -hex 24)"
    } > "$env_file"
  )
  echo "Created $env_file"
fi
set -a; source "$env_file"; set +a

out=.local/docker/tls
if [ -f "$out/worker.p12" ] && [ "${1:-}" != --certs ]; then
  echo "Certificates already exist in $out (use --certs to regenerate)"
  exit 0
fi
mkdir -p "$out"
gen() {
  local alias=$1 host=$2 cn=$3 name=${1#soa-}
  rm -f "$out/$name.p12" "$out/$name.crt"
  keytool -genkeypair -alias "$alias" -keyalg RSA -keysize 2048 -validity 825 \
    -dname "CN=$cn, OU=SOA lab, O=ITMO" -ext "SAN=dns:localhost,dns:$host,ip:127.0.0.1" \
    -keystore "$out/$name.p12" -storetype PKCS12 -storepass:env SOA_TLS_PASSWORD -keypass:env SOA_TLS_PASSWORD
  keytool -exportcert -rfc -alias "$alias" -keystore "$out/$name.p12" -storepass:env SOA_TLS_PASSWORD -file "$out/$name.crt"
}
gen soa-worker wildfly "SOA Worker"
gen soa-hr payara "SOA HR"
rm -f "$out/worker-trust.p12"
keytool -importcert -noprompt -alias soa-worker -file "$out/worker.crt" \
  -keystore "$out/worker-trust.p12" -storetype PKCS12 -storepass:env SOA_TLS_PASSWORD
chmod 600 "$out"/*.p12
echo "Certificates written to $out"
