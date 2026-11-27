# SAPL Node over RSocket+TLS with OAuth2 client_credentials

End-to-end demonstration of a Spring Boot consumer reaching the SAPL Node over RSocket+TLS using an OAuth2-managed JWT. The consumer never sees the token directly: Spring's `OAuth2AuthorizedClientManager` mints and refreshes a JWT against Keycloak; the SAPL starter encodes the current token into the RSocket setup-frame metadata on every connect. When the JWT expires the Node disposes the connection, the client reconnects, the manager hands out a fresh token, and operation continues.

This example composes the techniques shown in `with-keycloak` (OAuth2 JWT issuance) and `with-tls` (shared SSL bundle on both transports), plus a small consumer Spring Boot app.

## Quick start

```bash
# 1. Generate a self-signed development keystore at ./tls/keystore.p12
./generate-keystore.sh

# 2. Build and run all three containers (Keycloak, SAPL Node, consumer)
docker compose up --build

# 3. From a separate terminal, call the consumer
curl http://localhost:8090/check
# Expected: PERMIT

curl 'http://localhost:8090/check?subject=alice&action=delete&resource=doc'
# Expected: DENY
```

The consumer reaches the SAPL Node at `sapl-node:7000` (the internal Docker network name) over RSocket TLS, identifying itself with a JWT from `keycloak:8080`. The host machine never sees the JWT.

## What's wired where

Consumer (`./consumer/src/main/resources/application.yml`):

```yaml
io.sapl.pdp.remote:
  enabled: true
  type: rsocket
  host: sapl-node
  port: 7000
  tls: true
  ignoreCertificates: true     # self-signed cert, see "Production checklist"
  oauth2:
    client-registration-id: sapl-pdp

spring.security.oauth2.client:
  registration.sapl-pdp:
    provider: keycloak
    client-id: sapl-pdp-client
    client-secret: sapl-pdp-secret
    authorization-grant-type: client_credentials
  provider.keycloak:
    issuer-uri: http://keycloak:8080/realms/sapl-demo
```

The starter's `RemotePDPAutoConfiguration` sees `io.sapl.pdp.remote.oauth2.client-registration-id=sapl-pdp` and pulls the matching Spring registration through Spring's `ReactiveClientRegistrationRepository`. Spring's `OAuth2AuthorizedClientManager` caches and refreshes the access token. On the RSocket transport, every (re)connect re-evaluates a `Mono<Payload>` setup-frame supplier, which mints a fresh BEARER metadata payload from the current token.

SAPL Node (`docker-compose.yml`):

- Accepts OAuth2 JWT only (`IO_SAPL_NODE_ALLOWOAUTH2AUTH=true`, all other auth modes off).
- Validates against the Keycloak realm via `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI`.
- Terminates TLS on both HTTPS (8443) and RSocket (7000) using one shared Spring SSL bundle (`sapl-bundle`).
- Disposes RSocket connections on JWT `exp`. The client's reconnect path mints a new token automatically.

Keycloak (`./keycloak/realm-export.json`):

- Realm `sapl-demo`.
- Client `sapl-pdp-client` configured for `client_credentials` only (`serviceAccountsEnabled: true`, all other flows disabled).
- Short access-token lifespan (60 s) so the reconnect-on-expiry behaviour is exercised quickly.

## Watching token refresh in action

Tail the SAPL Node logs while running a steady stream of requests:

```bash
docker compose logs -f sapl-node
```

Every ~60 s you will see the RSocket connection close on the server side and the client immediately reconnect with a freshly minted token. No errors surface to the consumer; the `/check` endpoint keeps returning `PERMIT` without interruption.

## Files

| File | Purpose |
|---|---|
| `generate-keystore.sh` | Generates `./tls/keystore.p12` with a self-signed RSA-2048 cert, SAN for `sapl-node`, `localhost`, and `127.0.0.1`. Password `changeit`. |
| `docker-compose.yml` | Keycloak + SAPL Node + consumer on a single Docker network. SAPL Node exposes HTTPS:8443 and RSocket TLS:7000; consumer exposes HTTP:8090. |
| `keycloak/realm-export.json` | Realm with one `client_credentials`-only client (`sapl-pdp-client`). Imported on Keycloak startup. |
| `policies/pdp.json` | PDP configuration: `PRIORITY_PERMIT`, default `DENY`. |
| `policies/permit-read-doc.sapl` | Single policy permitting `read` on `doc`. |
| `consumer/` | Minimal Spring Boot service. Single endpoint `/check` calls the PDP via the starter. |

## Production checklist

This demo is intentionally permissive. Before exposing this pattern to the network:

- [ ] Replace the self-signed keystore with a CA-issued cert; remove `ignoreCertificates: true` from the consumer config and rely on the JVM truststore (or load a private-CA cert into it).
- [ ] Move `client-secret` out of `application.yml` into a secrets manager.
- [ ] Adjust `access.token.lifespan` to your security/refresh-cost trade-off. 60 s is fine for a demo; production typically uses 5-15 min.
- [ ] Add `audience` claim validation in Keycloak + `audiences` matching on the SAPL Node side.
- [ ] Configure metrics scraping (`/actuator/prometheus`) and health endpoints on a separate management port.
