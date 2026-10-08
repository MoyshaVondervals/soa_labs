#!/usr/bin/env bash
set -euo pipefail
SOA_HOME="$HOME/soa-lab2"
if [ -f "$SOA_HOME/config.env" ]; then source "$SOA_HOME/config.env"; fi
export JAVA_HOME=/usr/local/openjdk17
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS='-Xms32m -Xmx128m -XX:ActiveProcessorCount=2'
export JAVA_OPTS='-Xms128m -Xmx512m -XX:MaxMetaspaceSize=256m -XX:ActiveProcessorCount=2 -Djava.net.preferIPv4Stack=true'
export LAUNCH_JBOSS_IN_BACKGROUND=true
cd "$SOA_HOME"
mkdir -p run logs
source deploy/secrets.sh

if [ ! -f run/wildfly.pid ] || ! kill -0 "$(cat run/wildfly.pid)" 2>/dev/null; then
  rm -f wildfly/standalone/deployments/worker.war*
  daemon -p "$SOA_HOME/run/wildfly.pid" -o "$SOA_HOME/logs/wildfly.log" -f "$SOA_HOME/wildfly/bin/standalone.sh" \
    -b=0.0.0.0 -bmanagement=127.0.0.1 -Djboss.socket.binding.port-offset=10000
fi
ready=false
for attempt in $(seq 1 60); do
  if [ -f run/wildfly.pid ] && ! kill -0 "$(cat run/wildfly.pid)" 2>/dev/null; then break; fi
  if curl --noproxy '*' --connect-timeout 2 --max-time 3 --cacert "$SOA_HOME/tls/worker.crt" -s -o /dev/null https://localhost:18443/; then ready=true; break; fi
  sleep 1
done
if [ "$ready" != true ]; then
  echo 'WildFly did not start. Last log lines:'
  grep -E 'ERROR|Address already in use' logs/wildfly.log | tail -5 || tail -20 logs/wildfly.log
  exit 1
fi
cp dist/worker.war wildfly/standalone/deployments/worker.war
touch wildfly/standalone/deployments/worker.war.dodeploy
deployed=false
for attempt in $(seq 1 90); do
  if [ -f wildfly/standalone/deployments/worker.war.failed ]; then cat wildfly/standalone/deployments/worker.war.failed; exit 1; fi
  if [ -f wildfly/standalone/deployments/worker.war.deployed ] && [ ! -f wildfly/standalone/deployments/worker.war.dodeploy ]; then deployed=true; break; fi
  sleep 1
done
[ "$deployed" = true ] || { echo 'Worker deployment timed out; inspect logs/wildfly.log'; exit 1; }

if ! payara/bin/asadmin list-domains --domaindir "$SOA_HOME/payara-domains" | grep -q 'soa-lab2 running'; then
  payara/bin/asadmin start-domain --domaindir "$SOA_HOME/payara-domains" soa-lab2
fi
soa_asadmin_passwordfile
payara/bin/asadmin --host 127.0.0.1 --port 19448 --user admin --passwordfile "$SOA_ASADMIN_PW" \
  deploy --force=true --contextroot / --name hr "$SOA_HOME/dist/hr.war"
ready=false
for attempt in $(seq 1 30); do
  code="$(curl --noproxy '*' --connect-timeout 2 --max-time 3 --cacert "$SOA_HOME/tls/hr.crt" -s -o /dev/null -w '%{http_code}' https://localhost:19443/hr/fire/0 || true)"
  if [ "$code" = 405 ]; then ready=true; break; fi
  sleep 1
done
[ "$ready" = true ] || { echo 'HR did not become ready; inspect payara-domains/soa-lab2/logs/server.log'; exit 1; }
echo "Worker/UI: https://${SOA_SERVER_HOST:-localhost}:18443"
echo "HR:        https://${SOA_SERVER_HOST:-localhost}:19443"
