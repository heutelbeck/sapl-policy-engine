# SAPL Node with TLS on HTTP and RSocket

End-to-end demonstration of TLS termination on both transports using a single shared Spring Boot SSL bundle. One keystore covers the HTTP server and the RSocket server.

The setup is intentionally minimal: no authentication, one policy that permits `read on doc`, just enough to prove the TLS plumbing works.

## Quick start

```bash
# 1. Generate a self-signed development keystore at ./tls/keystore.p12
./generate-keystore.sh

# 2. Start the SAPL Node
docker compose up

# 3. From a separate terminal, make an HTTPS decision request.
#    -k skips certificate verification (self-signed cert).
curl -k -X POST https://localhost:8443/api/pdp/decide-once \
  -H 'Content-Type: application/json' \
  -d '{"subject":"alice","action":"read","resource":"doc"}'

# Expected: {"decision":"PERMIT"}

# 4. Or via the SAPL CLI over RSocket TLS:
sapl decide-once --remote --rsocket --rsocket-tls --insecure \
  --host localhost --port 7000 \
  -s '"alice"' -a '"read"' -r '"doc"'

# Expected: {"decision":"PERMIT"}
```

## How it works

`docker-compose.yml` defines a single SSL bundle named `sapl-bundle` via environment variables that Spring Boot binds to `spring.ssl.bundle.jks.sapl-bundle.*`:

```yaml
- SPRING_SSL_BUNDLE_JKS_SAPL-BUNDLE_KEY_ALIAS=sapl-node
- SPRING_SSL_BUNDLE_JKS_SAPL-BUNDLE_KEYSTORE_LOCATION=file:/pdp/tls/keystore.p12
- SPRING_SSL_BUNDLE_JKS_SAPL-BUNDLE_KEYSTORE_PASSWORD=changeit
- SPRING_SSL_BUNDLE_JKS_SAPL-BUNDLE_KEYSTORE_TYPE=PKCS12
```

Both transports then reference the bundle by name:

```yaml
# HTTPS on 8443
- SERVER_SSL_ENABLED=true
- SERVER_SSL_BUNDLE=sapl-bundle

# RSocket TLS on 7000
- SAPL_PDP_RSOCKET_ENABLED=true
- SAPL_PDP_RSOCKET_SSL_BUNDLE=sapl-bundle
```

Rotating the certificate means replacing `./tls/keystore.p12` and restarting the container. The bundle definition is the single source of TLS material; no per-transport duplication.

## Files

| File | Purpose |
|---|---|
| `generate-keystore.sh` | Generates `./tls/keystore.p12` with a self-signed RSA-2048 cert valid for 365 days, CN=localhost, SAN for `localhost` + `127.0.0.1`. Uses keystore password `changeit`. |
| `docker-compose.yml` | SAPL Node container with HTTPS on 8443, RSocket TLS on 7000, mounting `./tls` (keystore) and `./policies` (policies) read-only. |
| `policies/pdp.json` | PDP configuration: `PRIORITY_PERMIT` voting, `DENY` default. |
| `policies/permit-read-doc.sapl` | Single policy permitting `read` on `doc`. |

## Production checklist

The development keystore here is for local exploration only. Before exposing this to the network:

- [ ] Replace the self-signed cert with one issued by a CA your clients trust. Same `keystore.p12` shape; just swap the file.
- [ ] Strengthen the keystore password (env var `SPRING_SSL_BUNDLE_JKS_SAPL-BUNDLE_KEYSTORE_PASSWORD`) and source it from a secrets manager, not the compose file.
- [ ] Enable authentication. The demo runs with `IO_SAPL_NODE_ALLOWNOAUTH=true`; flip to `false` and configure one or more of `IO_SAPL_NODE_ALLOWBASICAUTH`, `IO_SAPL_NODE_ALLOWAPIKEYAUTH`, `IO_SAPL_NODE_ALLOWOAUTH2AUTH` with corresponding `IO_SAPL_NODE_USERS_*` entries (see `examples/docker/single-node/` for the basic auth pattern, `examples/docker/with-keycloak/` for OAuth2/JWT).
- [ ] Bind the host port to a specific interface (`127.0.0.1:8443:8443`) if the host is multi-homed and you only want local access.
- [ ] Enable HTTP/2 if your load balancer terminates TLS and the path between LB and node carries enough concurrent requests to benefit. The demo disables HTTP/2 (`SERVER_HTTP2_ENABLED=false`) because the bundled Jetty does not ship the ALPN dependency required for HTTP/2-over-TLS.
- [ ] Configure metrics scraping (`/actuator/prometheus`) and health endpoints (`/actuator/health`) on the same TLS bundle.
