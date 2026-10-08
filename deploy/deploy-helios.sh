#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
target="${SOA_SSH_TARGET:-s409361@helios.cs.ifmo.ru}"
port="${SOA_SSH_PORT:-2222}"
key="${SOA_SSH_KEY:-$HOME/.ssh/helios_ed25519}"
ssh_opts=(-o BatchMode=yes -o ConnectTimeout=20 -i "$key")

for file in worker-service/build/libs/worker.war hr-service/build/libs/hr.war; do
  [ -f "$file" ] || { echo "Missing $file: run ./gradlew build first"; exit 1; }
done
driver=.local/dist/postgresql.jar
if [ ! -f "$driver" ]; then
  mkdir -p .local/dist
  curl -fsSL --retry 5 -o "$driver" https://repo.maven.apache.org/maven2/org/postgresql/postgresql/42.7.5/postgresql-42.7.5.jar
fi

echo '==> Upload'
ssh "${ssh_opts[@]}" -p "$port" "$target" 'umask 077; mkdir -p soa-lab2/dist soa-lab2/deploy'
scp -q "${ssh_opts[@]}" -P "$port" worker-service/build/libs/worker.war hr-service/build/libs/hr.war "$driver" "$target:soa-lab2/dist/"
scp -q "${ssh_opts[@]}" -P "$port" deploy/server/setup.sh deploy/server/start.sh deploy/server/stop.sh \
  deploy/server/secrets.sh deploy/server/Configure.java deploy/server/config.env.example "$target:soa-lab2/deploy/"

echo '==> Stop, configure, start'
ssh "${ssh_opts[@]}" -p "$port" "$target" '
  set -e
  cd soa-lab2
  if [ ! -f config.env ]; then
    sed "s/s409361/$(whoami)/g" deploy/config.env.example > config.env && chmod 600 config.env
  fi
  bash deploy/stop.sh
  bash deploy/setup.sh
  bash deploy/start.sh'

echo '==> Download certificates to .local/helios'
mkdir -p .local/helios
scp -q "${ssh_opts[@]}" -P "$port" "$target:soa-lab2/tls/worker.crt" "$target:soa-lab2/tls/hr.crt" .local/helios/
echo 'Done. Open through the tunnel: bash deploy/tunnel-helios.sh, then https://localhost:18443'
