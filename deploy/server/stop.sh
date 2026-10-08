#!/usr/bin/env bash
set -euo pipefail
SOA_HOME="$HOME/soa-lab2"
if [ -f "$SOA_HOME/config.env" ]; then source "$SOA_HOME/config.env"; fi
export JAVA_HOME=/usr/local/openjdk17
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS='-Xms32m -Xmx128m -XX:ActiveProcessorCount=2'
if [ -x "$SOA_HOME/payara/bin/asadmin" ] && "$SOA_HOME/payara/bin/asadmin" list-domains --domaindir "$SOA_HOME/payara-domains" 2>/dev/null | grep -q 'soa-lab2 running'; then
  mkdir -p "$SOA_HOME/run"
  source "$SOA_HOME/deploy/secrets.sh"
  soa_asadmin_passwordfile
  "$SOA_HOME/payara/bin/asadmin" --user admin --passwordfile "$SOA_ASADMIN_PW" stop-domain --domaindir "$SOA_HOME/payara-domains" soa-lab2 \
    || "$SOA_HOME/payara/bin/asadmin" stop-domain --kill=true --domaindir "$SOA_HOME/payara-domains" soa-lab2
fi
if [ -f "$SOA_HOME/run/wildfly.pid" ]; then
  launcher="$(cat "$SOA_HOME/run/wildfly.pid")"
  if kill -0 "$launcher" 2>/dev/null && ps -p "$launcher" -o command= | grep -Fq "$SOA_HOME/wildfly/bin/standalone.sh"; then
    pkill -TERM -P "$launcher" || true
    kill -TERM "$launcher" 2>/dev/null || true
    for attempt in $(seq 1 30); do kill -0 "$launcher" 2>/dev/null || break; sleep 1; done
  fi
  rm -f "$SOA_HOME/run/wildfly.pid"
fi
echo 'Stopped.'
