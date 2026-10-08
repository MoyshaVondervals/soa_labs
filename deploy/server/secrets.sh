SOA_SECRETS="$SOA_HOME/secrets.env"
if [ ! -f "$SOA_SECRETS" ]; then
  (
    umask 077
    if [ -f "$SOA_HOME/tls/password.txt" ]; then tls="$(cat "$SOA_HOME/tls/password.txt")"; else tls="$(openssl rand -hex 24)"; fi
    {
      echo "SOA_TLS_PASSWORD=$tls"
      echo "SOA_PAYARA_ADMIN_PASSWORD=$(openssl rand -hex 24)"
      echo "SOA_PAYARA_MASTER_PASSWORD=$(openssl rand -hex 24)"
    } > "$SOA_SECRETS"
  )
  rm -f "$SOA_HOME/tls/password.txt"
fi
set -a
source "$SOA_SECRETS"
set +a
if [ -z "${SOA_DB_PASSWORD:-}" ] && [ -f "$HOME/.pgpass" ]; then
  SOA_DB_PASSWORD="$(awk -v h="${SOA_DB_HOST:-pg}" -v p="${SOA_DB_PORT:-5432}" -v d="${SOA_DB_NAME:-studs}" -v u="$SOA_DB_USER" '
    { line = $0; gsub(/\\\\/, "\001", line); gsub(/\\:/, "\002", line); n = split(line, f, ":")
      if (n >= 5 && (f[1] == "*" || f[1] == h) && (f[2] == "*" || f[2] == p) && (f[3] == "*" || f[3] == d) && (f[4] == "*" || f[4] == u)) {
        pw = f[5]; for (i = 6; i <= n; i++) pw = pw ":" f[i]
        gsub(/\002/, ":", pw); gsub(/\001/, "\\", pw); print pw; exit } }' "$HOME/.pgpass")"
fi
: "${SOA_DB_PASSWORD:?No database password: add a line for $SOA_DB_USER to ~/.pgpass}"
export SOA_DB_PASSWORD
export WORKER_TRUSTSTORE_PASSWORD="$SOA_TLS_PASSWORD"

soa_asadmin_passwordfile() {
  SOA_ASADMIN_PW="$(umask 077; mktemp "$SOA_HOME/run/asadmin.XXXXXX")"
  trap 'rm -f "$SOA_ASADMIN_PW"' EXIT
  printf 'AS_ADMIN_PASSWORD=%s\nAS_ADMIN_MASTERPASSWORD=%s\n' "$SOA_PAYARA_ADMIN_PASSWORD" "$SOA_PAYARA_MASTER_PASSWORD" > "$SOA_ASADMIN_PW"
}
