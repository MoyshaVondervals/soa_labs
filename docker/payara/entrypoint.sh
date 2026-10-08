#!/usr/bin/env bash
set -euo pipefail
asadmin=/opt/payara6/bin/asadmin
config=/opt/payara6/glassfish/domains/domain1/config
if [ ! -f /opt/.secured ]; then
  pwfile="$(umask 077; mktemp)"
  printf 'AS_ADMIN_MASTERPASSWORD=changeit\nAS_ADMIN_NEWMASTERPASSWORD=%s\n' "$PAYARA_MASTER_PASSWORD" > "$pwfile"
  $asadmin change-master-password --passwordfile "$pwfile" --savemasterpassword=true domain1
  rm -f "$pwfile"
  keytool -importkeystore -noprompt -srckeystore /opt/tls/hr.p12 -srcstoretype PKCS12 -srcstorepass:env TLS_PASSWORD \
    -srcalias soa-hr -destkeystore "$config/keystore.p12" -deststoretype PKCS12 \
    -deststorepass:env PAYARA_MASTER_PASSWORD -destkeypass:env PAYARA_MASTER_PASSWORD -destalias soa-hr
  touch /opt/.secured
fi
cp /deploy/hr.war /opt/payara6/glassfish/domains/domain1/autodeploy/
exec $asadmin start-domain --verbose domain1
