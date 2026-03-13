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

By default, `allowNoAuth` is enabled so the node is functional out of the box for development. For production, disable `allowNoAuth` and enable one or more credential-based modes.

A request is authenticated if it matches any enabled mode. The first successful match determines the client identity and PDP routing. The `pdpId` from the matched credential entry selects which tenant's policies evaluate the request.

### Unauthenticated Access

```yaml
io.sapl.node:
  allowNoAuth: true
  defaultPdpId: "default"
```

When `allowNoAuth` is `true`, requests without credentials are accepted and routed to the `defaultPdpId`. This is intended for development environments or deployments where an API gateway or service mesh handles authentication before requests reach the node.

Do not enable unauthenticated access in production without a gateway in front of the node. Any client that can reach the HTTP port can submit authorization subscriptions and receive decisions.

### Basic Authentication

Enable Basic Auth and define users in the `users` list:

```yaml
io.sapl.node:
  allowBasicAuth: true
  users:
    - id: "service-a"
      pdpId: "default"
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
  allowApiKeyAuth: true
  users:
    - id: "service-b"
      pdpId: "production"
      apiKey: "$argon2id$v=19$m=16384,t=2,p=1$..."
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
  allowOauth2Auth: true
  oauth:
    pdpIdClaim: "sapl_pdp_id"

spring.security.oauth2:
  resourceserver:
    jwt:
      issuer-uri: https://auth.example.com/realm
```

The node fetches the JWKS endpoint from the issuer URI and validates token signatures automatically. The `pdpIdClaim` property specifies which JWT claim contains the PDP identifier for tenant routing. If the claim is absent, the `defaultPdpId` is used.

### Multi Tenant Routing

Every credential entry includes a `pdpId` that routes the client to a specific tenant's policies. For `MULTI_DIRECTORY` sources, the `pdpId` maps to a subdirectory name. For `BUNDLES` sources, it maps to a bundle filename without the `.saplbundle` extension.

```yaml
io.sapl.node:
  defaultPdpId: "default"
  rejectOnMissingPdpId: false
  users:
    - id: "prod-client"
      pdpId: "production"
      apiKey: "$argon2id$..."
    - id: "staging-client"
      pdpId: "staging"
      apiKey: "$argon2id$..."
```

When `rejectOnMissingPdpId` is `false`, any user entry without a `pdpId` is automatically assigned the `defaultPdpId`. When set to `true`, the node fails at startup if any user entry lacks a `pdpId`.

For OAuth2, the PDP identifier is extracted from the JWT claim specified by `oauth.pdpIdClaim`. If the claim is missing and `rejectOnMissingPdpId` is `false`, the token is routed to `defaultPdpId`.

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
    allowNoAuth: false
    allowBasicAuth: false
    allowApiKeyAuth: true
    allowOauth2Auth: false
    users:
      - id: "service-a"
        pdpId: "default"
        apiKey: "$argon2id$v=19$m=16384,t=2,p=1$..."

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
