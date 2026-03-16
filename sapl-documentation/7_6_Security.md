---
layout: default
title: Security
parent: SAPL Node
nav_order: 706
---

## Security

This section covers securing the SAPL Node HTTP API: authentication, TLS, and interface binding. For bundle signing and signature verification, see [Policy Sources](../7_3_PolicySources/) and [Remote Bundles](../7_4_RemoteBundles/).

### Default Security Posture

SAPL Node defaults differ by deployment context. The binary is optimized for a quick development start. Packages and Docker are optimized for production safety.

**Binary (development):** Binds to `127.0.0.1`, no TLS, no authentication, `DIRECTORY` mode. Drop `.sapl` files in the working directory and start evaluating policies immediately.

**Packages and Docker (production):** `BUNDLES` mode with signature verification enabled. The node starts and accepts connections, but reports health `DOWN` and returns `INDETERMINATE` for all decisions until a signed bundle is deployed. This ensures the operator completes the bundle workflow before the node serves authorization decisions.

In both contexts, authorization requests without matching policies are denied.

**Binary (development)** security progression:

| Level | What to configure | Use case |
|-------|-------------------|----------|
| 0 | Nothing | Local development, learning, CI |
| 1 | Enable auth, generate credentials | Multi-service on same host |
| 2 | Enable TLS, bind to `0.0.0.0` | Network-exposed service |

**Packages and Docker (production)** security progression:

| Level | What to configure | Use case |
|-------|-------------------|----------|
| 0 | Configure public key (or allow-unsigned to opt out) | First start |
| 1 | Enable auth, generate credentials | Multi-service on same host, trusted network |
| 2 | Enable TLS, bind to `0.0.0.0` | Network-exposed service |
| 3 | TLS, auth, signed bundles, metrics | Production |

### Authentication

SAPL Node supports four authentication modes. Each mode is controlled by a boolean property under `io.sapl.node`. Multiple modes can be active at the same time. When all four modes are disabled, every request is rejected.

By default, `allow-no-auth` is enabled so the node is functional out of the box for development. For production, disable `allow-no-auth` and enable one or more credential-based modes.

A request is authenticated if it matches any enabled mode. The first successful match determines the client identity and PDP routing. The `pdp-id` from the matched credential entry selects which tenant's policies evaluate the request.

### Unauthenticated Access

```yaml
io.sapl.node:
  allow-no-auth: true
  default-pdp-id: "default"
```

When `allow-no-auth` is `true`, requests without credentials are accepted and routed to the `default-pdp-id`. This is intended for development environments or deployments where an API gateway or service mesh handles authentication before requests reach the node.

Do not enable unauthenticated access in production without a gateway in front of the node. Any client that can reach the HTTP port can submit authorization subscriptions and receive decisions.

### Basic Authentication

Enable Basic Auth and define users in the `users` list:

```yaml
io.sapl.node:
  allow-basic-auth: true
  users:
    - id: "service-a"
      pdp-id: "default"
      basic:
        username: "xwuUaRD65G"
        secret: "$argon2id$v=19$m=16384,t=2,p=1$..."
```

The `secret` field contains the Argon2 encoded password. Generate credentials with the CLI:

```shell
sapl generate basic --id service-a --pdp-id default
```

The command prints the plaintext password and the YAML configuration block. Store the plaintext securely. Only the encoded value goes into `application.yml`.

### API Key Authentication

API keys are sent as Bearer tokens in the `Authorization` header. The client sends `Authorization: Bearer sapl_...` on each request.

```yaml
io.sapl.node:
  allow-api-key-auth: true
  users:
    - id: "service-b"
      pdp-id: "production"
      api-key: "$argon2id$v=19$m=16384,t=2,p=1$..."
```

Generate a key with the CLI:

```shell
sapl generate apikey --id service-b --pdp-id production
```

The command prints the plaintext API key and the Argon2 encoded hash. The plaintext key is shown once and cannot be recovered from the hash.

### OAuth2 and JWT

SAPL Node can validate JWT tokens using Spring Security's resource server support. Enable OAuth2 authentication and configure the issuer:

```yaml
io.sapl.node:
  allow-oauth2-auth: true
  oauth:
    pdp-id-claim: "sapl_pdp_id"

spring.security.oauth2:
  resourceserver:
    jwt:
      issuer-uri: https://auth.example.com/realm
```

The node fetches the JWKS endpoint from the issuer URI and validates token signatures automatically. The `pdp-id-claim` property specifies which JWT claim contains the PDP identifier for tenant routing. If the claim is absent, the `default-pdp-id` is used.

### Multi Tenant Routing

Every credential entry includes a `pdp-id` that routes the client to a specific tenant's policies. For `MULTI_DIRECTORY` sources, the `pdp-id` maps to a subdirectory name. For `BUNDLES` sources, it maps to a bundle filename without the `.saplbundle` extension.

```yaml
io.sapl.node:
  default-pdp-id: "default"
  reject-on-missing-pdp-id: false
  users:
    - id: "prod-client"
      pdp-id: "production"
      api-key: "$argon2id$..."
    - id: "staging-client"
      pdp-id: "staging"
      api-key: "$argon2id$..."
```

When `reject-on-missing-pdp-id` is `false`, any user entry without a `pdp-id` is automatically assigned the `default-pdp-id`. When set to `true`, the node fails at startup if any user entry lacks a `pdp-id`.

For OAuth2, the PDP identifier is extracted from the JWT claim specified by `oauth.pdp-id-claim`. If the claim is missing and `reject-on-missing-pdp-id` is `false`, the token is routed to `default-pdp-id`.

### TLS

TLS is disabled by default so the node starts without a certificate. To enable TLS, configure a keystore:

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: file:/opt/sapl/tls/keystore.p12
    key-store-password: "${KEYSTORE_PASSWORD}"
    key-store-type: PKCS12
```

The default configuration restricts connections to modern cipher suites and protocol versions:

```yaml
server:
  ssl:
    enabled-protocols:
      - TLSv1.3
      - TLSv1.2
    protocol: TLSv1.3
    ciphers:
      - TLS_AES_128_GCM_SHA256
      - TLS_AES_256_GCM_SHA384
      - TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384
      - TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256
      - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
      - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
      - TLS_DHE_RSA_WITH_AES_256_GCM_SHA384
      - TLS_DHE_RSA_WITH_AES_128_GCM_SHA256
      - TLS_DHE_DSS_WITH_AES_256_GCM_SHA384
      - TLS_DHE_DSS_WITH_AES_128_GCM_SHA256
      - TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384
      - TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256
      - TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384
      - TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256
      - TLS_DHE_RSA_WITH_AES_256_CBC_SHA256
      - TLS_DHE_RSA_WITH_AES_128_CBC_SHA256
      - TLS_DHE_DSS_WITH_AES_256_CBC_SHA256
      - TLS_DHE_DSS_WITH_AES_128_CBC_SHA256
```

TLSv1.3 is preferred. TLSv1.2 is included for compatibility with older clients. All listed cipher suites use AES with GCM or CBC mode and require forward secrecy via ECDHE or DHE key exchange.

### Interface Binding

The `server.address` property controls which network interface the node listens on.

The default is `127.0.0.1` (localhost only). This means the node is not reachable from the network out of the box, which is appropriate for local development.

For container deployments, bind to all interfaces so Docker port mapping works:

```yaml
server:
  address: 0.0.0.0
```

In Docker Compose examples, this is set via `SERVER_ADDRESS=0.0.0.0`.

### Hardened Configuration Example

This is a complete `application.yml` for production deployments. It enables TLS with the default cipher suite list, API key authentication, signed bundles, and metrics. Copy this file and replace the placeholder values.

```yaml
io.sapl:
  pdp.embedded:
    pdp-config-type: BUNDLES
    policies-path: /opt/sapl/bundles
    metrics-enabled: true
    bundle-security:
      public-key-path: /opt/sapl/keys/signing.pub

  node:
    allow-no-auth: false
    allow-basic-auth: false
    allow-api-key-auth: true
    allow-oauth2-auth: false
    users:
      - id: "service-a"
        pdp-id: "default"
        api-key: "$argon2id$v=19$m=16384,t=2,p=1$..."

server:
  address: 0.0.0.0
  port: 8443
  ssl:
    enabled: true
    key-store: file:/opt/sapl/tls/keystore.p12
    key-store-password: "${KEYSTORE_PASSWORD}"
    key-store-type: PKCS12
    enabled-protocols:
      - TLSv1.3
      - TLSv1.2
    protocol: TLSv1.3

management:
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true

logging.level:
  "[io.sapl]": INFO
  "[org.springframework]": INFO
```

Generate API keys with `sapl generate apikey --id service-a --pdp-id default`. For the full property reference, see [Configuration](../7_2_Configuration/). For health checks and Kubernetes probes, see [Monitoring](../7_7_Monitoring/).

### Reverse Proxy Configuration

The streaming PDP endpoints (`/api/pdp/decide`, `/api/pdp/multi-decide`, `/api/pdp/multi-decide-all`) use Server-Sent Events (SSE) over long-lived HTTP POST connections. Default proxy configurations buffer responses and time out idle connections, both of which break SSE streaming.

The key requirements for any reverse proxy in front of SAPL Node:

1. **Disable response buffering.** SSE events must be flushed immediately to the client.
2. **Set a long read timeout.** Streaming connections stay open indefinitely. The proxy must not close them after a short idle period.
3. **Preserve chunked transfer encoding.** Do not add `Content-Length` headers to streaming responses.
4. **Forward the HTTP method.** All PDP endpoints use POST.

#### Keep-Alive Frames

SAPL Node can send periodic SSE comment frames (`:keep-alive`) on idle connections to prevent proxies and firewalls from dropping them. Set the interval in `application.yml`:

```yaml
io.sapl.node:
  keep-alive: 15
```

This sends a keep-alive frame every 15 seconds. Set the proxy read timeout to a value above this interval (e.g., 60 seconds). The default is `0` (disabled). See [Configuration](../7_2_Configuration/) for the property reference.

#### nginx

```nginx
location /api/pdp/ {
    proxy_pass http://127.0.0.1:8443;
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 3600s;
    proxy_set_header Connection '';
    proxy_http_version 1.1;
    chunked_transfer_encoding on;
}

location /actuator/ {
    proxy_pass http://127.0.0.1:8443;
}
```

#### Apache

Enable `mod_proxy` and `mod_proxy_http`. Disable response buffering for the PDP path:

```apache
ProxyPass /api/pdp/ http://127.0.0.1:8443/api/pdp/
ProxyPassReverse /api/pdp/ http://127.0.0.1:8443/api/pdp/
SetEnv proxy-sendchunked 1
SetEnv proxy-sendcl 0
ProxyTimeout 3600

ProxyPass /actuator/ http://127.0.0.1:8443/actuator/
ProxyPassReverse /actuator/ http://127.0.0.1:8443/actuator/
```

The non-streaming endpoints (`/api/pdp/decide-once`, `/api/pdp/multi-decide-all-once`) and actuator endpoints work with default proxy settings and do not require special configuration.
