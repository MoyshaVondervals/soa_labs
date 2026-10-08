#!/usr/bin/env bash
set -euo pipefail
target="${SOA_SSH_TARGET:-s409361@helios.cs.ifmo.ru}"
port="${SOA_SSH_PORT:-2222}"
key="${SOA_SSH_KEY:-$HOME/.ssh/helios_ed25519}"
echo 'Tunnel is up: open https://localhost:18443 (Ctrl+C to stop)'
exec ssh -o BatchMode=yes -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -N -i "$key" -p "$port" \
  -L 127.0.0.1:18443:127.0.0.1:18443 -L 127.0.0.1:19443:127.0.0.1:19443 "$target"
