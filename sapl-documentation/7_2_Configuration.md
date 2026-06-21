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

In containerized deployments, every property can be set via environment variables. Spring Boot converts property names to uppercase with underscores replacing dots and hyphens. For example, `io.sapl.pdp.embedded.pdp-config-type` becomes `IO_SAPL_PDP_EMBEDDED_PDP_CONFIG_TYPE`.

Configuration values follow Spring Boot's standard precedence: command line arguments override environment variables, and environment variables override values in `application.yml`.

### PDP Properties

All properties live under the prefix `io.sapl.pdp.embedded`:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables the embedded PDP auto configuration. |
| `pdp-config-type` | `PDPDataSource` | `RESOURCES` | Policy source type. One of `RESOURCES`, `DIRECTORY`, `MULTI_DIRECTORY`, `BUNDLES`, or `REMOTE_BUNDLES`. SAPL Node overrides this to `DIRECTORY` (binary) or `BUNDLES` (packages, Docker). See [Policy Sources](../7_3_PolicySources/). |
| `config-path` | `String` | `.` | Path to `pdp.json`. For `RESOURCES`, this is relative to the classpath root. For filesystem sources, it is an absolute or relative filesystem path. The SAPL Node default is `.` (current directory). Package installations override this to `/var/lib/sapl`. |
| `policies-path` | `String` | `.` | Path to `.sapl` files or bundles. Same path resolution rules as `config-path`. The SAPL Node default is `.` (current directory). |
| `function-cache-size` | `int` | `10000` | Maximum number of entries in the function result cache. SAPL functions are pure and side-effect-free, so results are cached across evaluations using Window-TinyLFU eviction. Set to `0` to disable caching. |
| `coarse-timestamps` | `boolean` | `false` | Uses a coarse-resolution cached clock for observability timestamps (decision trace and attribute value freshness) instead of the accurate system clock. Cheaper per decision at high throughput, at the cost of coarser timestamp precision. Temporal policy reasoning (time PIP, certificate validity, JWT expiry, scheduling) always uses the accurate clock. |
| `metrics-enabled` | `boolean` | `false` (code) / `true` (shipped) | Records PDP decision metrics for Prometheus via Micrometer. The engine default is `false`; the node's bundled `application.yml` enables it. See [Monitoring](../7_7_Monitoring/). |
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
| `allow-no-auth` | `boolean` | `false` | Permits unauthenticated requests. Disabled by default (fail-closed). Set to `true` for local exploration when no upstream authentication is in place. |
| `allow-basic-auth` | `boolean` | `false` | Enables HTTP Basic authentication. |
| `allow-api-key-auth` | `boolean` | `false` | Enables API key authentication via Bearer tokens. |
| `allow-oauth2-auth` | `boolean` | `false` | Enables OAuth2/JWT authentication. |
| `reject-on-missing-pdp-id` | `boolean` | `false` | Rejects users at startup if their `pdp-id` is not set. When `false`, missing values default to `default-pdp-id`. |
| `default-pdp-id` | `String` | `"default"` | Fallback PDP identifier for users without an explicit `pdp-id`. |
| `users[].id` | `String` | | Client identifier for logging and diagnostics. |
| `users[].pdp-id` | `String` | | PDP identifier that routes this client to a specific tenant's policies. |
| `users[].basic.username` | `String` | | Username for HTTP Basic authentication. |
| `users[].basic.secret` | `String` | | Argon2 encoded password for HTTP Basic authentication. |
| `users[].api-key-id` | `String` | | Public identifier of the API key (the middle segment of the `sapl_<id>_<secret>` wire format), generated by `sapl generate apikey` and printed alongside the encoded key. Required for every api-key user: the server refuses to start if an `api-key` is configured without its `api-key-id`. Used to route incoming API key requests to the matching user in O(1). |
| `users[].api-key` | `String` | | Argon2 encoded API key. The client sends the plaintext key as a Bearer token in the `Authorization` header. |
| `oauth.pdp-id-claim` | `String` | `"sapl_pdp_id"` | JWT claim name used to extract the PDP identifier for tenant routing. |
| `scalar.oauth-client-id` | `String` | | OIDC client id pre-filled in the Scalar API reference Authorize dialog. Optional and independent of whether OAuth2 is enabled server-side. |
| `scalar.oauth-redirect-uri` | `String` | `/scalar` | Redirect URI used by the Scalar API reference OAuth2 flow. |
| `keep-alive` | `long` | `15` | Seconds between SSE keep-alive frames on idle streaming connections. Prevents proxies and firewalls from dropping inactive connections and lets the server detect clients that drop without closing. Always on and cannot be disabled; values below `1` are raised to the default. Keep it below the smallest proxy idle timeout on the path. See [Reverse Proxy Configuration](../7_6_Security/#reverse-proxy-configuration). |
| `http.sse.keep-alive-pool-size` | `int` | `0` (auto) | Size of the scheduled thread pool that emits SSE keep-alive frames. `0` (or any non-positive value) auto-sizes to `max(2, availableProcessors / 2)`. |
| `http.auth-cache.positive-ttl` | `Duration` | `5m` | Time successful authentication results stay cached on the bypass-Spring `/api/pdp/*` HTTP path before re-verification. Higher values trade staleness for fewer Argon2 verifications per second. |
| `http.auth-cache.negative-ttl` | `Duration` | `5s` | Time failed authentication results stay cached. Short by design so a transient lookup miss recovers quickly while still throttling brute-force probes. |
| `http.auth-cache.max-size` | `long` | `10000` | Maximum number of cached authentication outcomes. Caps memory exposure when a client cycles through many distinct `Authorization` headers. Caffeine evicts least-recently-used entries when the cap is reached. |

See [Security](../7_6_Security/) for details on each authentication mode and credential generation.

### RSocket Properties

All properties live under the prefix `sapl.pdp.rsocket`. The RSocket endpoint is enabled by default on port 7000.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables the protobuf RSocket PDP endpoint. |
| `port` | `int` | `7000` | TCP port for RSocket connections. Ignored when `socket-path` is set. |
| `socket-path` | `String` | | Unix domain socket path. When set, the server binds to this socket instead of TCP. Requires platform support (Linux epoll or macOS kqueue). |
| `max-inbound-payload-size` | `int` | `16777215` | Maximum size in bytes of an inbound RSocket payload. The RSocket protocol fixes the per-frame ceiling at 16 MB; the configured value must be at least that, since any single frame must fit. Per-IP and per-account caps belong at an upstream load balancer or firewall. |
| `ssl.bundle` | `String` | | Name of a Spring Boot SSL bundle (configured under `spring.ssl.bundle.*`) used to terminate TLS on the RSocket transport. When unset, the server speaks plain TCP. The same bundle definition can be shared with the HTTP server, so a single keystore covers both transports. |

Connection lifetime is soft. JWT credentials are validated at every decision call. Expired tokens are then rejected, and the client is expected to reconnect with a refreshed credential. The server does not maintain a separate hard-disconnect timer.

Connection counts are bounded by the OS file-descriptor limit (`ulimit -n` on Linux). Per-IP or per-account caps are not enforced inside the node and need to be applied at an upstream load balancer or firewall.

Example:

```yaml
sapl:
  pdp:
    rsocket:
      enabled: true
      port: 7000
```

Unix domain socket (alternative to TCP):

```yaml
sapl:
  pdp:
    rsocket:
      enabled: true
      socket-path: /var/run/sapl.sock
```

TLS via a shared SSL bundle (same bundle as the HTTP server):

```yaml
spring:
  ssl:
    bundle:
      jks:
        sapl-bundle:
          key:
            alias: sapl-node
            password: changeit
          keystore:
            location: file:/etc/sapl/keystore.p12
            password: changeit
            type: PKCS12

server:
  ssl:
    enabled: true
    bundle: sapl-bundle

sapl:
  pdp:
    rsocket:
      enabled: true
      ssl:
        bundle: sapl-bundle
```

CLI clients connect with `--rsocket --rsocket-tls` (and `--insecure` to skip certificate verification against self-signed dev certificates).

The RSocket endpoint shares the same authentication configuration as the HTTP endpoints (`io.sapl.node.users`, `allow-basic-auth`, `allow-api-key-auth`, `allow-oauth2-auth`). Authentication occurs once at connection setup. See [RSocket API](../6_1_HTTPApi/#rsocket-api) for the wire protocol specification.

### OpenID Authorization API Properties

The OpenID Authorization API binding at `/access/v1/evaluation` is enabled by default and shares the authentication configuration with the rest of the HTTP transport. One additional knob caps request body size:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `io.sapl.server.openid-authz-api.enabled` | `boolean` | `true` | Enables the OpenID Authorization API 1.0 binding. |
| `io.sapl.node.http.max-request-body-bytes` | `long` | `65536` | Caps the request body size on the HTTP PDP endpoints, covering both `/api/pdp/*` and the OpenID Authorization API on `/access/v1/*`. Requests whose `Content-Length` exceeds the limit are rejected with `413 Content Too Large` before any body bytes are read. Authorization subscriptions are small (typically below 1 KiB); raise only when policies routinely receive large `properties` maps. Mirrors the `sapl.pdp.rsocket.max-inbound-payload-size` guard on the RSocket transport. |

### CLI Argument Overrides

Any property can be passed as a command line argument using Spring Boot's `--property=value` syntax:

```shell
sapl --io.sapl.pdp.embedded.pdp-config-type=BUNDLES --io.sapl.pdp.embedded.policies-path=/opt/bundles
```

In Docker, set the equivalent environment variable:

```shell
docker run -e IO_SAPL_NODE_ALLOWNOAUTH=true ghcr.io/heutelbeck/sapl-node:4.1.0-SNAPSHOT
```

### Spring Profiles

Spring profiles allow environment specific configuration overrides. Activate a profile at startup:

```shell
sapl --spring.profiles.active=docker
```

Place a `application-docker.yml` in the config directory. Properties in the profile file override the defaults from `application.yml`. This is useful for toggling TLS, authentication modes, or log levels between development and production environments.

### Default Configuration

SAPL Node ships with a fail-closed default: out of the box no authentication mode is enabled and the server refuses to start. Configure at least one credential mode (or `allow-no-auth: true`) before launching. Place `.sapl` files in the working directory and start the server with the chosen auth setup. The PDP monitors the directory and reloads on changes.

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
    allow-no-auth: false
    allow-basic-auth: false
    allow-api-key-auth: false
    allow-oauth2-auth: false

server:
  address: 127.0.0.1
  port: 8080
  ssl:
    enabled: false
```

The server binds to `127.0.0.1` (localhost only). This is safe for development. For container or network deployments, set `server.address: 0.0.0.0`.

For local exploration without configuring credentials, override the auth default at startup:

```shell
sapl --io.sapl.node.allow-no-auth=true
```

### Package and Docker Defaults

Linux packages (DEB/RPM) and the Docker image default to `BUNDLES` mode with signature verification enabled. This is the secure production default. The node will not start until bundle security is configured: either provide a public key for signature verification or explicitly set `bundle-security.allow-unsigned: true`.

To deploy your first bundle:

```shell
sapl bundle keygen -o signing
sapl bundle create -i ./policies -o /var/lib/sapl/default.saplbundle -k signing.pem
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
        api-key-id: "<from-generator>"
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
