#!/usr/bin/env bash
set -euo pipefail
asadmin=/opt/payara6/bin/asadmin
$asadmin start-domain domain1
$asadmin set configs.config.server-config.network-config.protocols.protocol.http-listener-2.ssl.cert-nickname=soa-hr
$asadmin set configs.config.server-config.network-config.network-listeners.network-listener.http-listener-1.enabled=false
$asadmin create-jvm-options \
  '-Dworker.base.url=https\://wildfly\:8443:-Dworker.public.base.url=https\://localhost\:8543:-Dworker.truststore=/opt/tls/worker-trust.p12:-Dapp.cors.origin=https\://localhost\:8543'
$asadmin stop-domain domain1
