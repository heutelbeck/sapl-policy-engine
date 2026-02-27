---
layout: default
title: Java API
parent: Integration
nav_order: 602
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

| Method                                          | Returns                                   | Behavior                                                                                                                                           |
|-------------------------------------------------|-------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| `decide(AuthorizationSubscription)`             | `Flux<AuthorizationDecision>`             | Streaming. Returns a continuous stream of decisions that updates whenever policies, attributes, or conditions change.                              |
| `decideOnce(AuthorizationSubscription)`         | `Mono<AuthorizationDecision>`             | One-shot reactive. Returns a single decision.                                                                                                      |
| `decideOnceBlocking(AuthorizationSubscription)` | `AuthorizationDecision`                   | One-shot synchronous. When no policy accesses external attributes, the PDP uses an optimized evaluation path that bypasses all reactive machinery. |
| `decide(MultiAuthorizationSubscription)`        | `Flux<IdentifiableAuthorizationDecision>` | Streaming individual. Each decision is tagged with the subscription ID for correlation.                                                            |
| `decideAll(MultiAuthorizationSubscription)`     | `Flux<MultiAuthorizationDecision>`        | Streaming batch. Emits all decisions as a single object whenever any decision changes.                                                             |

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

| Property             | Type      | Default  | Description                                                                       |
|----------------------|-----------|----------|-----------------------------------------------------------------------------------|
| `enabled`            | `boolean` | `false`  | Activates the remote PDP client and disables the embedded PDP.                    |
| `type`               | `String`  | `"http"` | Connection protocol. Currently only `http` is supported.                          |
| `host`               | `String`  |          | Base URL of the remote PDP server (e.g., `https://pdp.example.com:8443`).         |
| `key`                | `String`  |          | Client key for basic authentication. Requires `secret`.                           |
| `secret`             | `String`  |          | Client secret for basic authentication. Requires `key`.                           |
| `apiKey`             | `String`  |          | API key for API-key-based authentication. Mutually exclusive with `key`/`secret`. |
| `ignoreCertificates` | `boolean` | `false`  | Disables TLS certificate verification. For development only.                      |

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

### Integrating SAPL into Applications

Applications can integrate SAPL authorization either through an embedded PDP or by connecting to a remote SAPL server via HTTP. The embedded approach works well for single-instance applications or microservices, while the remote approach supports centralized policy management across multiple applications.

#### Embedded PDP for Java Applications

SAPL requires Java 21 or newer and is compatible with Java 25.

Configure a Java version in your project:

```xml
<properties>
  <java.version>21</java.version>
  <maven.compiler.source>${java.version}</maven.compiler.source>
  <maven.compiler.target>${java.version}</maven.compiler.target>
</properties>
```

Add the SAPL embedded PDP dependency:

```xml
<dependency>
  <groupId>io.sapl</groupId>
  <artifactId>sapl-pdp-embedded</artifactId>
  <version>4.0.0-SNAPSHOT</version>
</dependency>
```

Add the Maven Central snapshot repository:

```xml
<repositories>
  <repository>
    <name>Central Portal Snapshots</name>
    <id>central-portal-snapshots</id>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <releases>
      <enabled>false</enabled>
    </releases>
    <snapshots>
      <enabled>true</enabled>
    </snapshots>
  </repository>
</repositories>
```

For projects using multiple SAPL dependencies, use the bill of materials POM:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.sapl</groupId>
      <artifactId>sapl-bom</artifactId>
      <version>4.0.0-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Build a PDP using `PolicyDecisionPointBuilder`. For policies bundled in your application's resources (the `src/main/resources/policies` folder), use `withResourcesSource()`. For policies on the filesystem (with live-reload on changes), use `withDirectorySource()`:

```java
import io.sapl.pdp.PolicyDecisionPointBuilder;

// Option A: Load policies from application resources (src/main/resources/policies)
var components = PolicyDecisionPointBuilder.withDefaults()
        .withResourcesSource()
        .build();
var pdp = components.pdp();

// Option B: Load policies from a filesystem directory (with live-reload)
var components = PolicyDecisionPointBuilder.withDefaults()
        .withDirectorySource(Path.of("~/sapl/policies"))
        .build();
var pdp = components.pdp();
```

Create the configuration file `pdp.json` in the policies directory:

```json
{
  "algorithm": {
    "votingMode": "PRIORITY_PERMIT",
    "defaultDecision": "DENY",
    "errorHandling": "ABSTAIN"
  },
  "variables": {}
}
```

Add a policy file `test_policy.sapl` in the same directory:

```sapl
policy "permit reading"
permit
  action == "read";
  subject == "willi" & resource =~ "some.+";
```

Request authorization decisions using the PDP. For a single blocking decision:

```java
var subscription = AuthorizationSubscription.of("willi", "read", "something");
var decision     = pdp.decideOnceBlocking(subscription);
System.out.println(decision.decision()); // PERMIT
```

For reactive streaming decisions that update when policies or attributes change:

```java
pdp.decide(subscription).subscribe(decision ->
    System.out.println(decision.decision())
);
```

When the PDP is no longer needed, release its resources:

```java
components.dispose();
```

Example applications demonstrating different integration patterns are available in the [SAPL demos repository](https://github.com/heutelbeck/sapl-demos). Start with the [Embedded PDP Demo](https://github.com/heutelbeck/sapl-demos/tree/master/embedded-pdp) for basic usage, or explore the [Spring MVC Project](https://github.com/heutelbeck/sapl-demos/tree/master/web-mvc-app) and [Webflux Application](https://github.com/heutelbeck/sapl-demos/tree/master/webflux) for framework integration.

### Deployment Options

SAPL provides three ways to deploy a PDP:

- **Embedded PDP**: Runs inside a Java (or any other JVM language) application with policies loaded from the classpath, a filesystem directory, or signed bundles. Suitable for single-instance applications or microservices where policies are deployed alongside the application.
- **SAPL Node**: A standalone, headless PDP server that exposes the PDP via an HTTP API. Supports filesystem directories, signed bundles, and remote bundle fetching. Designed for centralized policy management across multiple applications.
- **Remote PDP client**: A lightweight client library that connects to a SAPL Node (or any SAPL-compatible server) via HTTP. Applications use this when policies are managed centrally rather than embedded.
