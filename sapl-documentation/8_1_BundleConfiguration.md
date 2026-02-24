---
layout: default
title: Bundle Configuration
parent: Remote Bundles
grand_parent: SAPL Reference
nav_order: 751
---

## Remote Bundle Configuration

SAPL PDP nodes can fetch `.saplbundle` files from a remote HTTP server. This enables
centralized policy distribution without requiring filesystem access on the node.

### Deployment Models

- **Open core:** Any HTTP server (S3, CDN, Nginx, Artifactory) serves bundles as
  static files.
- **Enterprise:** A Policy Administration Point (PAP) manages bundles for a node
  cluster using the same HTTP protocol.

### Enabling Remote Bundles

Set the PDP configuration type to `REMOTE_BUNDLES`:

```yaml
io.sapl.pdp.embedded:
  pdpConfigType: REMOTE_BUNDLES

  remoteBundles:
    baseUrl: https://pap.example.com/bundles
    pdpIds:
      - production
      - staging
```

Bundles are addressed by convention: `{baseUrl}/{pdpId}`. The example above resolves to:
- `GET https://pap.example.com/bundles/production`
- `GET https://pap.example.com/bundles/staging`

### Configuration Reference

All properties live under `io.sapl.pdp.embedded.remoteBundles`:

| Property             | Type                     | Default      | Description                              |
|----------------------|--------------------------|--------------|------------------------------------------|
| `baseUrl`            | `String`                 | _(required)_ | Base URL of the bundle server.           |
| `pdpIds`             | `List<String>`           | _(required)_ | PDP identifiers to fetch bundles for.    |
| `mode`               | `POLLING` or `LONG_POLL` | `POLLING`    | Change detection mode.                   |
| `pollInterval`       | `Duration`               | `30s`        | Interval between polls (POLLING mode).   |
| `longPollTimeout`    | `Duration`               | `30s`        | Server hold time (LONG_POLL mode).       |
| `authHeaderName`     | `String`                 | _(none)_     | HTTP header name for authentication.     |
| `authHeaderValue`    | `String`                 | _(none)_     | HTTP header value for authentication.    |
| `followRedirects`    | `boolean`                | `true`       | Follow HTTP 3xx redirects.               |
| `pdpIdPollIntervals` | `Map<String, Duration>`  | _(empty)_    | Per-pdpId poll interval overrides.       |
| `firstBackoff`       | `Duration`               | `500ms`      | Initial backoff after a fetch failure.   |
| `maxBackoff`         | `Duration`               | `5s`         | Maximum backoff after repeated failures. |

### Change Detection

#### Regular Polling (works with any HTTP server)

The node sends `GET {baseUrl}/{pdpId}` at the configured interval. HTTP conditional
requests (`If-None-Match` with ETag) avoid redundant downloads. The server responds
`304 Not Modified` if the bundle has not changed.

```yaml
io.sapl.pdp.embedded:
  remoteBundles:
    mode: POLLING
    pollInterval: 30s
```

#### Long-Poll (requires server support)

The node sends `GET {baseUrl}/{pdpId}` with `If-None-Match`. The server holds the
connection until the bundle changes or a timeout occurs. On change, the server responds
`200 OK` with the new bundle. On timeout, the server responds `304 Not Modified` and the
node reconnects immediately.

```yaml
io.sapl.pdp.embedded:
  remoteBundles:
    mode: LONG_POLL
    longPollTimeout: 30s
```

If the server does not support long-polling (responds immediately with 304), the
behavior degrades gracefully to regular polling.

### Authentication

The node sends a configurable HTTP header on every request:

```yaml
io.sapl.pdp.embedded:
  remoteBundles:
    authHeaderName: Authorization
    authHeaderValue: Bearer eyJhbGciOiJSUz...
```

This covers OAuth2 bearer tokens, static API keys, and custom authentication headers.
Both `authHeaderName` and `authHeaderValue` must be provided together or both omitted.

### Bundle Security

Remote bundles use the same signature verification as local bundles via the shared
`bundleSecurity` configuration block. Signatures are mandatory by default for remote
bundles.

```yaml
io.sapl.pdp.embedded:
  pdpConfigType: REMOTE_BUNDLES

  bundleSecurity:
    publicKeyPath: /path/to/key.pub
    # OR
    publicKey: MCowBQYDK2VwAyEA...

    # Per-tenant key bindings (optional)
    keys:
      prod-key: MCowBQYDK2VwAyEA...
    tenants:
      production: [prod-key]
```

For individual tenants that should accept unsigned bundles without enabling the global escape hatch, use the `unsignedTenants` list:

```yaml
  bundleSecurity:
    publicKeyPath: /path/to/key.pub
    unsignedTenants:
      - development
      - staging
```

Tenants listed here may load unsigned bundles while all other tenants still require valid signatures.

For development only, the 2-factor escape hatch disables signature verification globally:

```yaml
  bundleSecurity:
    allowUnsigned: true
    acceptRisks: true
```

### Per-pdpId Poll Interval

Each pdpId inherits the global `pollInterval` unless overridden:

```yaml
io.sapl.pdp.embedded:
  remoteBundles:
    pollInterval: 60s
    pdpIdPollIntervals:
      staging: 10s    # Override for staging
```

In this example, `production` polls every 60 seconds while `staging` polls every 10
seconds.

### Health and Lifecycle

The node exposes three health states via Spring Boot Actuator:

| State | Condition | Health Status |
|-------|-----------|---------------|
| DOWN | No bundle fetched yet (startup) | DOWN |
| UP | Bundle loaded, remote reachable | UP |
| DEGRADED | Bundle loaded, remote unreachable | UP (with warning) |

At startup, the node is DOWN. It transitions to UP per-pdpId as each bundle is
successfully fetched. If the remote becomes unreachable after a successful fetch, the
node continues serving the last-known bundle in DEGRADED state.

### Size Limit

Remote bundle responses are limited to 16 MB. Bundles exceeding this limit are rejected. This limit is enforced by the client and cannot be configured.

### Retry Behavior

On fetch failure, the node retries with exponential backoff (with jitter). The backoff
starts at `firstBackoff` and caps at `maxBackoff`. After recovery, the backoff resets
to the initial value.

### Graceful Shutdown

On application shutdown, all fetch loops are cancelled and HTTP connections are released.
No manual intervention is needed.

### Programmatic Configuration

For non-Spring environments, the builder API supports remote bundles directly:

```java
var securityPolicy = BundleSecurityPolicy.builder(publicKey).build();

var config = new RemoteBundleSourceConfig(
    "https://pap.example.com/bundles",
    List.of("production"),
    RemoteBundleSourceConfig.FetchMode.POLLING,
    Duration.ofSeconds(30),
    Duration.ofSeconds(30),
    "Authorization", "Bearer token",
    true, securityPolicy,
    Map.of(),
    Duration.ofMillis(500),
    Duration.ofSeconds(5),
    WebClient.builder());

var pdp = PolicyDecisionPointBuilder.withDefaults(mapper, clock)
    .withRemoteBundleSource(config)
    .build()
    .pdp();
```
