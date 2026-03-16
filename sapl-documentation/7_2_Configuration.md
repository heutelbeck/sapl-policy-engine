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

SAPL Node ships with a self-documenting `application.yml` built into the JAR. The defaults are functional out of the box: no TLS, no authentication required, policies loaded from the current directory.

To override defaults, place an `application.yml` in a `config/` directory next to the JAR. Spring Boot loads this file automatically on startup, and its values take precedence over the built-in defaults.

To use a different location, pass the path as a startup argument:

```shell
sapl --spring.config.location=file:/etc/sapl/application.yml
```

In containerized deployments, every property can be set via environment variables. Spring Boot converts property names to uppercase with underscores replacing dots and camelCase boundaries. For example, `io.sapl.pdp.embedded.pdp-config-type` becomes `IO_SAPL_PDP_EMBEDDED_PDPCONFIGTYPE`.

Configuration values follow Spring Boot's standard precedence: command line arguments override environment variables, and environment variables override values in `application.yml`.

### PDP Properties

All properties live under the prefix `io.sapl.pdp.embedded`:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables the embedded PDP auto configuration. |
| `pdp-config-type` | `PDPDataSource` | `RESOURCES` | Policy source type. One of `RESOURCES`, `DIRECTORY`, `MULTI_DIRECTORY`, `BUNDLES`, or `REMOTE_BUNDLES`. SAPL Node overrides this to `DIRECTORY` (binary) or `BUNDLES` (packages, Docker). See [Policy Sources](../7_3_PolicySources/). |
| `index` | `IndexType` | `NAIVE` | Indexing algorithm. `NAIVE` for small policy sets, `CANONICAL` for large collections with faster retrieval at the cost of slower index updates. |
| `config-path` | `String` | `.` | Path to `pdp.json`. For `RESOURCES`, this is relative to the classpath root. For filesystem sources, it is an absolute or relative filesystem path. The SAPL Node default is `.` (current directory). Package installations override this to `/var/lib/sapl`. |
| `policies-path` | `String` | `.` | Path to `.sapl` files or bundles. Same path resolution rules as `config-path`. The SAPL Node default is `.` (current directory). |
| `metrics-enabled` | `boolean` | `true` | Records PDP decision metrics for Prometheus via Micrometer. See [Monitoring](../7_7_Monitoring/). |
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
| `allow-no-auth` | `boolean` | `true` | Permits unauthenticated requests. Enabled by default for zero-configuration development. Disable in production or when a gateway handles authentication. |
| `allow-basic-auth` | `boolean` | `false` | Enables HTTP Basic authentication. |
| `allow-api-key-auth` | `boolean` | `false` | Enables API key authentication via Bearer tokens. |
| `allow-oauth2-auth` | `boolean` | `false` | Enables OAuth2/JWT authentication. |
| `reject-on-missing-pdp-id` | `boolean` | `false` | Rejects users at startup if their `pdp-id` is not set. When `false`, missing values default to `default-pdp-id`. |
| `default-pdp-id` | `String` | `"default"` | Fallback PDP identifier for users without an explicit `pdp-id`. |
| `users[].id` | `String` | | Client identifier for logging and diagnostics. |
| `users[].pdp-id` | `String` | | PDP identifier that routes this client to a specific tenant's policies. |
| `users[].basic.username` | `String` | | Username for HTTP Basic authentication. |
| `users[].basic.secret` | `String` | | Argon2 encoded password for HTTP Basic authentication. |
| `users[].api-key` | `String` | | Argon2 encoded API key. The client sends the plaintext key as a Bearer token in the `Authorization` header. |
| `oauth.pdp-id-claim` | `String` | `"sapl_pdp_id"` | JWT claim name used to extract the PDP identifier for tenant routing. |
| `keep-alive` | `long` | `0` | Seconds between SSE keep-alive frames on idle streaming connections. Prevents proxies and firewalls from dropping inactive connections. `0` disables keep-alive. See [Reverse Proxy Configuration](../7_6_Security/#reverse-proxy-configuration). |

See [Security](../7_6_Security/) for details on each authentication mode and credential generation.

### CLI Argument Overrides

Any property can be passed as a command line argument using Spring Boot's `--property=value` syntax:

```shell
sapl --io.sapl.pdp.embedded.pdp-config-type=BUNDLES --io.sapl.pdp.embedded.policies-path=/opt/bundles
```

In Docker, set the equivalent environment variable:

```shell
docker run -e IO_SAPL_NODE_ALLOWNOAUTH=true ghcr.io/heutelbeck/sapl-node:4.0.0
```

### Spring Profiles

Spring profiles allow environment specific configuration overrides. Activate a profile at startup:

```shell
sapl --spring.profiles.active=docker
```

Place a `application-docker.yml` in the config directory. Properties in the profile file override the defaults from `application.yml`. This is useful for toggling TLS, authentication modes, or log levels between development and production environments.

### Default Configuration

SAPL Node works out of the box with no configuration file. The built-in defaults run a single-directory PDP on plain HTTP at `localhost:8443` with no authentication required. Place `.sapl` files in the working directory and start the server. The PDP monitors the directory and reloads on changes.

The `pdp.json` file is optional. When absent, the PDP uses the default combining algorithm (`PRIORITY_DENY` with `DENY` default and `PROPAGATE` error handling).

The effective defaults are:

```yaml
io.sapl:
  pdp.embedded:
    pdp-config-type: DIRECTORY
    config-path: .
    policies-path: .
    metrics-enabled: true
  node:
    allow-no-auth: true

server:
  address: 127.0.0.1
  port: 8443
  ssl:
    enabled: false
```

The server binds to `127.0.0.1` (localhost only). This is safe for development. For container or network deployments, set `server.address: 0.0.0.0`.

### Package and Docker Defaults

Linux packages (DEB/RPM) and the Docker image default to `BUNDLES` mode with signature verification enabled. This is the secure production default. The node will not start until bundle security is configured: either provide a public key for signature verification or explicitly set `bundle-security.allow-unsigned: true`.

To deploy your first bundle:

```shell
sapl bundle keygen -o signing
sapl bundle create -i ./my-policies -o /var/lib/sapl/default.saplbundle -k signing.pem
```

Then configure the public key in `application.yml`:

```yaml
io.sapl.pdp.embedded:
  bundle-security:
    public-key-path: /etc/sapl/signing.pub
```

The PDP detects the new bundle automatically and begins serving decisions.

To opt out of signature verification during evaluation, set `bundle-security.allow-unsigned: true`. The node logs a warning on every startup when signature verification is disabled.

### Production Configuration

This configuration uses signed bundles with API key authentication, TLS, and metrics. For the full hardened version with cipher suites and interface binding, see [Security](../7_6_Security/).

```yaml
io.sapl:
  pdp.embedded:
    pdp-config-type: BUNDLES
    policies-path: /opt/sapl/bundles
    metrics-enabled: true
    bundle-security:
      public-key-path: /opt/sapl/keys/signing.pub

  node:
    allow-api-key-auth: true
    users:
      - id: "service-a"
        pdp-id: "default"
        api-key: "$argon2id$v=19$m=16384,t=2,p=1$..."

server:
  port: 8443
  ssl:
    enabled: true
    key-store: file:/opt/sapl/tls/keystore.p12
    key-store-password: "${KEYSTORE_PASSWORD}"
    key-store-type: PKCS12
```

Generate the API key hash with `sapl generate apikey --id service-a --pdp-id default`. The command prints the Argon2 encoded value for the configuration and the plaintext key for the client. See [Getting Started](../7_1_GettingStarted/) for the full CLI reference.
