#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
docker compose cp ../worker-service/build/libs/worker.war wildfly:/opt/wildfly/standalone/deployments/worker.war
docker compose cp ../hr-service/build/libs/hr.war payara:/opt/payara6/glassfish/domains/domain1/autodeploy/hr.war
echo "Copied; servers pick up the new WARs within a few seconds"
