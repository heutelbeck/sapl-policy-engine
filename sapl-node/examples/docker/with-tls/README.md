# SAPL Node with TLS on HTTP and RSocket

End-to-end demonstration of TLS on both transports with one certificate from PEM files. The same certificate and key serve the HTTP server and the RSocket server.

The setup is intentionally minimal: no authentication, one policy that permits `read on doc`, just enough to prove the TLS plumbing works.

## Quick start

```bash
# 1. Generate a self-signed development certificate at ./tls/server.pem
./generate-certificate.sh

# 2. Start the SAPL Node
docker compose up

# 3. From a separate terminal, make an HTTPS decision request.
#    The self-signed certificate is its own authority.
curl --cacert tls/server.pem -X POST https://localhost:8443/api/pdp/decide-once -H 'Content-Type: application/json' -d '{"subject":"alice","action":"read","resource":"doc"}'

# Expected: {"decision":"PERMIT"}

# 4. Or via the SAPL CLI over RSocket TLS:
sapl decide-once --remote --rsocket --rsocket-tls --insecure --host localhost --port 7000 -s '"alice"' -a '"read"' -r '"doc"'

# Expected: {"decision":"PERMIT"}
```

## How it works

`docker-compose.yml` names the PEM files of one certificate, which both transports serve:

```yaml
- IO_SAPL_NODE_TLS_CERTIFICATECHAIN=/pdp/tls/server.pem
- IO_SAPL_NODE_TLS_PRIVATEKEY=/pdp/tls/server.key
```

These are the environment variables of `io.sapl.node.tls.certificate-chain` and `io.sapl.node.tls.private-key`. The node maps them to the Spring Boot SSL bundle `sapl-node`, which `server.ssl.bundle` and `sapl.pdp.rsocket.ssl.bundle` name unless they name a bundle of their own.

Rotating the certificate means replacing the two files. The node serves the renewed certificate to new connections without a restart, and it logs a warning from 14 days before a certificate expires.

## Files

| File | Purpose |
|---|---|
| `generate-certificate.sh` | Generates `./tls/server.pem` and `./tls/server.key`, a self-signed P-256 certificate valid for 365 days, CN=localhost, SAN for `localhost` + `127.0.0.1`. |
| `docker-compose.yml` | SAPL Node container with HTTPS on 8443, RSocket TLS on 7000, mounting `./tls` (certificate) and `./policies` (policies) read-only. |
| `policies/pdp.json` | PDP configuration: `PRIORITY_PERMIT` voting, `DENY` default. |
| `policies/permit-read-doc.sapl` | Single policy permitting `read` on `doc`. |

## Production checklist

The development certificate here is for local exploration only. Before exposing this to the network:

- [ ] Replace the self-signed certificate with one issued by a CA your clients trust. Same PEM files, the certificate followed by its chain.
- [ ] Keep the private key readable only by the user of the container, and source it from a secrets manager, not the repository.
- [ ] Enable authentication. The demo runs with `IO_SAPL_NODE_ALLOWNOAUTH=true`; flip to `false` and configure one or more of `IO_SAPL_NODE_ALLOWBASICAUTH`, `IO_SAPL_NODE_ALLOWAPIKEYAUTH`, `IO_SAPL_NODE_ALLOWOAUTH2AUTH` with corresponding `IO_SAPL_NODE_USERS_*` entries (see `examples/docker/single-node/` for the basic auth pattern, `examples/docker/with-keycloak/` for OAuth2/JWT).
- [ ] Bind the host port to a specific interface (`127.0.0.1:8443:8443`) if the host is multi-homed and you only want local access.
- [ ] Configure metrics scraping (`/actuator/prometheus`) and health endpoints (`/actuator/health`) over the same HTTPS port.
