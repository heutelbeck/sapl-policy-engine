---
layout: default
title: Java API
#permalink: /reference/Java-API/
parent: Language SDKs and Clients
grand_parent: SAPL Reference
nav_order: 651
---

## Java API

The Java API is based on Project Reactor (<https://projectreactor.io/>). It is defined in the `sapl-api` module:

```xml
<dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-api</artifactId>
    <version>4.0.0-SNAPSHOT</version>
</dependency>
```

The central interface is `PolicyDecisionPoint`. It exposes the same authorization semantics as the HTTP API: single subscriptions (streaming and one-shot) and multi-subscriptions (streaming and batch). Only the streaming single-subscription method is abstract; all others have default implementations that PDP implementations may override with optimized evaluation paths.

| Method | Returns | Behavior |
|--------|---------|----------|
| `decide(AuthorizationSubscription)` | `Flux<AuthorizationDecision>` | Streaming. Returns a continuous stream of decisions that updates whenever policies, attributes, or conditions change. |
| `decideOnce(AuthorizationSubscription)` | `Mono<AuthorizationDecision>` | One-shot reactive. Returns a single decision. |
| `decideOnceBlocking(AuthorizationSubscription)` | `AuthorizationDecision` | One-shot synchronous. When no policy accesses external attributes, the PDP uses an optimized evaluation path that bypasses all reactive machinery. |
| `decide(MultiAuthorizationSubscription)` | `Flux<IdentifiableAuthorizationDecision>` | Streaming individual. Each decision is tagged with the subscription ID for correlation. |
| `decideAll(MultiAuthorizationSubscription)` | `Flux<MultiAuthorizationDecision>` | Streaming batch. Emits all decisions as a single object whenever any decision changes. |

### Embedded PDP (Non-Spring)

For non-Spring JVM applications, an embedded PDP can be used directly:

```xml
<dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-pdp</artifactId>
    <version>4.0.0-SNAPSHOT</version>
</dependency>
```

### Remote PDP Client (Non-Spring)

For non-Spring JVM applications connecting to a SAPL Node or other remote PDP server:

```xml
<dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-pdp-remote</artifactId>
    <version>4.0.0-SNAPSHOT</version>
</dependency>
```

### Spring Boot Applications

For Spring Boot applications, use the unified starter. It includes the embedded PDP, the remote PDP client, Spring Security integration, and uses autoconfiguration to bootstrap the PDP:

```xml
<dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-spring-boot-starter</artifactId>
    <version>4.0.0-SNAPSHOT</version>
</dependency>
```

By default, the embedded PDP is active. To connect to a remote PDP server instead, configure the remote PDP properties (prefix `io.sapl.pdp.remote`):

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `false` | Activates the remote PDP client and disables the embedded PDP. |
| `type` | `String` | `"http"` | Connection protocol. Currently only `http` is supported. |
| `host` | `String` | | Base URL of the remote PDP server (e.g., `https://pdp.example.com:8443`). |
| `key` | `String` | | Client key for basic authentication. Requires `secret`. |
| `secret` | `String` | | Client secret for basic authentication. Requires `key`. |
| `apiKey` | `String` | | API key for API-key-based authentication. Mutually exclusive with `key`/`secret`. |
| `ignoreCertificates` | `boolean` | `false` | Disables TLS certificate verification. For development only. |

Exactly one authentication method must be configured: either `key` and `secret` together, or `apiKey` alone.

Example using basic authentication:

```properties
io.sapl.pdp.remote.enabled=true
io.sapl.pdp.remote.host=https://pdp.example.com:8443
io.sapl.pdp.remote.key=your-client-key
io.sapl.pdp.remote.secret=your-client-secret
```

Example using API key authentication:

```properties
io.sapl.pdp.remote.enabled=true
io.sapl.pdp.remote.host=https://pdp.example.com:8443
io.sapl.pdp.remote.apiKey=your-api-key
```

#### Reducing Application Footprint

When using only a remote PDP, you can exclude the embedded PDP dependency to reduce the application size:

```xml
<dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-spring-boot-starter</artifactId>
    <version>4.0.0-SNAPSHOT</version>
    <exclusions>
        <exclusion>
            <groupId>io.sapl</groupId>
            <artifactId>sapl-pdp</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```
