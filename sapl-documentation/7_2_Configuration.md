---
layout: default
title: Configuration
parent: SAPL Node
nav_order: 702
---

## SAPL Node Configuration

SAPL Node is configured via Spring Boot's `application.yml`. This page is the reference for all runtime settings that control PDP behavior, authentication, and diagnostics.

For policy level configuration such as the combining algorithm, variables, and secrets, see [PDP Configuration](../2_2_PDPConfiguration/). For bundle security and remote bundle properties, see [Remote Bundles](../7_4_RemoteBundles/).

### Configuration File Location

Place an `application.yml` in a `config/` directory next to the JAR. Spring Boot loads this file automatically on startup.

To use a different location, pass the path as a startup argument:

```shell
java -jar sapl-node-4.0.0-SNAPSHOT.jar --spring.config.location=file:/etc/sapl/application.yml
```

In containerized deployments, every property can be set via environment variables. Spring Boot converts property names to uppercase with underscores replacing dots and camelCase boundaries. For example, `io.sapl.pdp.embedded.pdp-config-type` becomes `IO_SAPL_PDP_EMBEDDED_PDPCONFIGTYPE`.

Configuration values follow Spring Boot's standard precedence: command line arguments override environment variables, and environment variables override values in `application.yml`.

### PDP Properties

All properties live under the prefix `io.sapl.pdp.embedded`:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables the embedded PDP auto configuration. |
| `pdp-config-type` | `PDPDataSource` | `RESOURCES` | Policy source type. One of `RESOURCES`, `DIRECTORY`, `MULTI_DIRECTORY`, `BUNDLES`, or `REMOTE_BUNDLES`. See [Policy Sources](../7_3_PolicySources/). |
| `index` | `IndexType` | `NAIVE` | Indexing algorithm. `NAIVE` for small policy sets, `CANONICAL` for large collections with faster retrieval at the cost of slower index updates. |
| `config-path` | `String` | `/policies` | Path to `pdp.json`. For `RESOURCES`, this is relative to the classpath root. For filesystem sources, it is an absolute or relative filesystem path. |
| `policies-path` | `String` | `/policies` | Path to `.sapl` files or bundles. Same path resolution rules as `config-path`. |
| `metrics-enabled` | `boolean` | `false` | Records PDP decision metrics for Prometheus via Micrometer. See [Monitoring](../7_7_Monitoring/). |
| `print-trace` | `boolean` | `false` | Logs the full JSON evaluation trace on each decision. |
| `print-json-report` | `boolean` | `false` | Logs the JSON evaluation report on each decision. |
| `print-text-report` | `boolean` | `false` | Logs a human readable text evaluation report on each decision. |
| `pretty-print-reports` | `boolean` | `false` | Pretty prints JSON in logged traces and reports. |
| `print-subscription-events` | `boolean` | `false` | Logs new authorization subscription lifecycle events. |
| `print-unsubscription-events` | `boolean` | `false` | Logs ended authorization subscription lifecycle events. |

Bundle security sub properties (`bundle-security.*`) and remote bundle sub properties (`remote-bundles.*`) are documented in [Remote Bundles](../7_4_RemoteBundles/).

### Node Properties

All properties live under the prefix `io.sapl.node`:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `allowNoAuth` | `boolean` | `false` | Permits unauthenticated requests. |
| `allowBasicAuth` | `boolean` | `true` | Enables HTTP Basic authentication. |
| `allowApiKeyAuth` | `boolean` | `false` | Enables API key authentication via Bearer tokens. |
| `allowOauth2Auth` | `boolean` | `false` | Enables OAuth2/JWT authentication. |
| `rejectOnMissingPdpId` | `boolean` | `false` | Rejects users at startup if their `pdpId` is not set. When `false`, missing values default to `defaultPdpId`. |
| `defaultPdpId` | `String` | `"default"` | Fallback PDP identifier for users without an explicit `pdpId`. |
| `users[].id` | `String` | | Client identifier for logging and diagnostics. |
| `users[].pdpId` | `String` | | PDP identifier that routes this client to a specific tenant's policies. |
| `users[].basic.username` | `String` | | Username for HTTP Basic authentication. |
| `users[].basic.secret` | `String` | | Argon2 encoded password for HTTP Basic authentication. |
| `users[].apiKey` | `String` | | Argon2 encoded API key. The client sends the plaintext key as a Bearer token in the `Authorization` header. |
| `oauth.pdpIdClaim` | `String` | `"sapl_pdp_id"` | JWT claim name used to extract the PDP identifier for tenant routing. |

See [Security](../7_6_Security/) for details on each authentication mode and credential generation.

### CLI Argument Overrides

Any property can be passed as a command line argument using Spring Boot's `--property=value` syntax:

```shell
java -jar sapl-node-4.0.0-SNAPSHOT.jar --io.sapl.pdp.embedded.pdp-config-type=BUNDLES --io.sapl.pdp.embedded.policies-path=/opt/bundles
```

In Docker, set the equivalent environment variable:

```shell
docker run -e IO_SAPL_NODE_ALLOWNOAUTH=true ghcr.io/heutelbeck/sapl-node:4.0.0-SNAPSHOT
```

### Spring Profiles

Spring profiles allow environment specific configuration overrides. Activate a profile at startup:

```shell
java -jar sapl-node-4.0.0-SNAPSHOT.jar --spring.profiles.active=docker
```

Place a `application-docker.yml` in the config directory. Properties in the profile file override the defaults from `application.yml`. This is useful for toggling TLS, authentication modes, or log levels between development and production environments.

### Minimal Configuration

This configuration runs a single directory PDP on plain HTTP with no authentication. It is suitable for local development and testing only.

```yaml
io.sapl:
  pdp.embedded:
    config-path: .
    policies-path: .
  node:
    allowNoAuth: true

server:
  address: localhost
  port: 8080
  ssl:
    enabled: false
```

Place `.sapl` files and `pdp.json` in the working directory. The PDP monitors the directory and reloads on changes.

### Production Configuration

This configuration uses signed bundles with API key authentication, TLS, and metrics. For the full hardened version with cipher suites and interface binding, see [Security](../7_6_Security/).

```yaml
io.sapl:
  pdp.embedded:
    pdp-config-type: BUNDLES
    policies-path: /opt/sapl/bundles
    metrics-enabled: true
    bundle-security:
      publicKeyPath: /opt/sapl/keys/signing.pub

  node:
    allowApiKeyAuth: true
    users:
      - id: "service-a"
        pdpId: "default"
        apiKey: "$argon2id$v=19$m=16384,t=2,p=1$..."

server:
  port: 8443
  ssl:
    enabled: true
    key-store: file:/opt/sapl/tls/keystore.p12
    key-store-password: "${KEYSTORE_PASSWORD}"
    key-store-type: PKCS12
```

Generate the API key hash with `java -jar sapl-node-4.0.0-SNAPSHOT.jar generate apikey --id service-a --pdp-id default`. The command prints the Argon2 encoded value for the configuration and the plaintext key for the client. See [Deployment](../7_1_Deployment/) for the full CLI reference.
