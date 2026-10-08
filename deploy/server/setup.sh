#!/usr/bin/env bash
set -euo pipefail
umask 077
SOA_HOME="$HOME/soa-lab2"
if [ -f "$SOA_HOME/config.env" ]; then source "$SOA_HOME/config.env"; fi
: "${SOA_SERVER_HOST:?Set SOA_SERVER_HOST in ~/soa-lab2/config.env}"
: "${SOA_DB_USER:?Set SOA_DB_USER in ~/soa-lab2/config.env}"
[[ "$SOA_SERVER_HOST" =~ ^[A-Za-z0-9.-]+$ ]] || { echo 'Invalid SOA_SERVER_HOST'; exit 1; }
export SOA_SERVER_HOST SOA_DB_USER SOA_DB_HOST SOA_DB_PORT SOA_DB_NAME SOA_DB_SCHEMA
export JAVA_HOME=/usr/local/openjdk17
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS='-Xms32m -Xmx128m -XX:ActiveProcessorCount=2'
mkdir -p "$SOA_HOME"/{downloads,dist,tls,logs,run}
cd "$SOA_HOME"
source deploy/secrets.sh

if [ ! -d wildfly ]; then
  [ -s downloads/wildfly.zip ] || fetch -q -o downloads/wildfly.zip https://repository.jboss.org/nexus/repository/releases/org/wildfly/wildfly-dist/35.0.1.Final/wildfly-dist-35.0.1.Final.zip
  [ "$(sha256 -q downloads/wildfly.zip)" = '1e4ac11ac3970ae6a73156dd11b88ce5243220d96ab346498c88c562262a62ac' ]
  unzip -q downloads/wildfly.zip
  mv wildfly-35.0.1.Final wildfly
  rm -f -- downloads/wildfly.zip
fi
if [ ! -d payara ]; then
  [ -s downloads/payara.zip ] || fetch -q -o downloads/payara.zip https://repo.maven.apache.org/maven2/fish/payara/distributions/payara/6.2025.11/payara-6.2025.11.zip
  [ "$(sha256 -q downloads/payara.zip)" = '4f8248f1fc2cedf14a829dbec73768bc824c2c418d60843c30a365a6ef76f486' ]
  unzip -q downloads/payara.zip
  mv payara6 payara
  rm -f -- downloads/payara.zip
fi
chmod +x wildfly/bin/*.sh payara/bin/asadmin

for service in worker hr; do
  cn=$([ "$service" = worker ] && echo 'SOA Worker' || echo 'SOA HR')
  if [ ! -f "tls/$service.p12" ]; then
    keytool -genkeypair -alias "soa-$service" -keyalg RSA -keysize 2048 -validity 365 \
      -dname "CN=$cn,OU=SOA Lab,O=ITMO,C=RU" -ext "SAN=dns:$SOA_SERVER_HOST,dns:localhost,ip:127.0.0.1" \
      -storetype PKCS12 -keystore "tls/$service.p12" \
      -storepass:env SOA_TLS_PASSWORD -keypass:env SOA_TLS_PASSWORD -noprompt
  fi
  keytool -exportcert -alias "soa-$service" -keystore "tls/$service.p12" -storepass:env SOA_TLS_PASSWORD -rfc -file "tls/$service.crt"
done
rm -f tls/worker-trust.p12
keytool -importcert -alias soa-worker -keystore tls/worker-trust.p12 -storetype PKCS12 \
  -storepass:env SOA_TLS_PASSWORD -file tls/worker.crt -noprompt

rm -rf payara-domains/soa-lab2
soa_asadmin_passwordfile
payara/bin/asadmin --user admin --passwordfile "$SOA_ASADMIN_PW" create-domain --savemasterpassword=true \
  --portbase 19000 --domaindir "$SOA_HOME/payara-domains" soa-lab2 >/dev/null
keytool -importkeystore -srckeystore tls/hr.p12 -srcstoretype PKCS12 -srcstorepass:env SOA_TLS_PASSWORD -srcalias soa-hr \
  -destkeystore payara-domains/soa-lab2/config/keystore.p12 -deststoretype PKCS12 \
  -deststorepass:env SOA_PAYARA_MASTER_PASSWORD -destkeypass:env SOA_PAYARA_MASTER_PASSWORD -destalias soa-hr -noprompt

mkdir -p wildfly/modules/org/postgresql/main
cp dist/postgresql.jar wildfly/modules/org/postgresql/main/postgresql.jar
cat > wildfly/modules/org/postgresql/main/module.xml <<'XML'
<module xmlns="urn:jboss:module:1.9" name="org.postgresql"><resources><resource-root path="postgresql.jar"/></resources><dependencies><module name="java.sql"/><module name="java.logging"/><module name="java.xml"/><module name="java.security.jgss"/><module name="java.transaction.xa"/><module name="java.naming"/><module name="java.management"/><module name="java.security.sasl"/><module name="jakarta.transaction.api"/></dependencies></module>
XML

java -Xmx128m deploy/Configure.java "$SOA_HOME"
chmod 600 wildfly/standalone/configuration/standalone.xml payara-domains/soa-lab2/config/domain.xml tls/* secrets.env
echo 'Setup complete.'
