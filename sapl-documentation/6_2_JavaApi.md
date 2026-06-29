---
layout: default
title: Java API
parent: SDKs and APIs
nav_order: 602
---

## Java API

The core SAPL decision types are defined in the `sapl-api` module:

```xml
<dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-api</artifactId>
    <version>4.1.1</version>
</dependency>
```

An application reaches a PDP in one of two ways. An **embedded PDP** runs in process and evaluates policies locally. It is Reactor-free and exposes decisions through the SAPL `Stream` primitive and synchronous one-shot calls. A **remote PDP client** connects to a SAPL Node (or any SAPL-compatible server) over HTTP or RSocket, and exposes decisions reactively as `Flux` and `Mono`. Both speak the same authorization semantics as the HTTP API, with single subscriptions (streaming and one-shot) and multi-subscriptions (streaming and batch).

### Authorization Decisions

A decision is an `io.sapl.api.pdp.AuthorizationDecision`, a record with four components.

| Component     | Type                  | Description                                              |
|---------------|-----------------------|---------------------------------------------------------|
| `decision`    | `Decision`            | One of the five decision verbs below.                   |
| `obligations` | `ArrayValue`          | Constraints the PEP must fulfil, or it denies access.   |
| `advice`      | `ArrayValue`          | Constraints the PEP should fulfil on a best-effort basis.|
| `resource`    | `Value`               | A replacement resource, when the policy supplies one.    |

The `decision` is always present and carries one of five verbs.

| Verb             | Meaning                                                                                                            |
|------------------|--------------------------------------------------------------------------------------------------------------------|
| `PERMIT`         | Access is granted.                                                                                                  |
| `DENY`           | Access is denied.                                                                                                   |
| `SUSPEND`        | Access is paused. The subscription stays alive and may resume on a later `PERMIT`. A one-shot PEP that cannot suspend treats `SUSPEND` as `DENY`. |
| `INDETERMINATE`  | An error prevented a decision.                                                                                      |
| `NOT_APPLICABLE` | No policy matched the subscription.                                                                                 |

Singletons exist for the simple cases, for example `AuthorizationDecision.PERMIT` and `AuthorizationDecision.SUSPEND`. See [Authorization Decisions](../2_3_AuthorizationDecisions/) for the full decision-verb semantics.

### The PDP Interfaces

The two access styles correspond to two interfaces.

**Embedded** uses `io.sapl.api.pdp.StreamingPolicyDecisionPoint` (the concrete embedded PDP, `BlockingPolicyDecisionPoint`, implements it). It is Reactor-free.

| Method                                      | Returns                                  | Behaviour                                                              |
|---------------------------------------------|------------------------------------------|-----------------------------------------------------------------------|
| `decideOnce(AuthorizationSubscription)`     | `AuthorizationDecision`                   | One-shot, synchronous. No Reactor on the call path.                    |
| `decide(AuthorizationSubscription)`         | `Stream<AuthorizationDecision>`           | Streaming. A SAPL `Stream`, consumed with `awaitNext()` and closed.    |
| `decide(MultiAuthorizationSubscription)`    | `Stream<IdentifiableAuthorizationDecision>` | Streaming individual. Each decision is tagged with its subscription ID.|
| `decideAll(MultiAuthorizationSubscription)` | `Stream<MultiAuthorizationDecision>`      | Streaming batch. All decisions in one object whenever any changes.     |

**Remote** uses `io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint`, based on Project Reactor (<https://projectreactor.io/>).

| Method                                      | Returns                                | Behaviour                            |
|---------------------------------------------|----------------------------------------|--------------------------------------|
| `decideOnce(AuthorizationSubscription)`     | `Mono<AuthorizationDecision>`           | One-shot reactive.                   |
| `decide(AuthorizationSubscription)`         | `Flux<AuthorizationDecision>`           | Streaming.                           |
| `decide(MultiAuthorizationSubscription)`    | `Flux<IdentifiableAuthorizationDecision>` | Streaming individual.              |
| `decideAll(MultiAuthorizationSubscription)` | `Flux<MultiAuthorizationDecision>`      | Streaming batch.                     |

Every method also has an overload taking a `String pdpId` for routing to a named PDP in a multi-tenant deployment.

### Embedded PDP

SAPL requires Java 21 or newer and is compatible with Java 25. Configure a Java version in your project:

```xml
<properties>
  <java.version>21</java.version>
  <maven.compiler.source>${java.version}</maven.compiler.source>
  <maven.compiler.target>${java.version}</maven.compiler.target>
</properties>
```

Add the embedded PDP dependency:

```xml
<dependency>
  <groupId>io.sapl</groupId>
  <artifactId>sapl-pdp</artifactId>
  <version>4.1.1</version>
</dependency>
```

For snapshot builds, add the Maven Central snapshot repository:

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

For projects using multiple SAPL dependencies, import the bill of materials:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.sapl</groupId>
      <artifactId>sapl-bom</artifactId>
      <version>4.1.1</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Build a PDP with `PolicyDecisionPointBuilder` (package `io.sapl.pdp`). For policies bundled in your application resources (the `src/main/resources/policies` folder), use `withResourcesSource()`. For policies on the filesystem, with live-reload on changes, use `withDirectorySource()`. Custom Policy Information Points and function libraries bind through `withPolicyInformationPoint(...)` and `withFunctionLibrary(...)`:

```java
import io.sapl.pdp.PDPComponents;
import io.sapl.pdp.PolicyDecisionPointBuilder;

// Option A: load policies from application resources (src/main/resources/policies)
var components = PolicyDecisionPointBuilder.withDefaults()
        .withPolicyInformationPoint(new MyCustomPip())
        .withFunctionLibrary(new MyFunctionLibrary())
        .withResourcesSource()
        .build();

// Option B: load policies from a filesystem directory (with live-reload)
var components = PolicyDecisionPointBuilder.withDefaults()
        .withDirectorySource(Path.of("/etc/sapl/policies"))
        .build();
```

`build()` returns a `PDPComponents` record, which is `AutoCloseable`. Obtain the PDP with `components.pdp()`. It is a `BlockingPolicyDecisionPoint`.

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

For a single synchronous decision, use `decideOnce`. It returns the `AuthorizationDecision` directly, with no Reactor on the call path:

```java
try (var components = PolicyDecisionPointBuilder.withDefaults().withResourcesSource().build()) {
    var pdp          = components.pdp();
    var subscription = AuthorizationSubscription.of("willi", "read", "something");
    var decision     = pdp.decideOnce(subscription);
    System.out.println(decision.decision()); // PERMIT, DENY, SUSPEND, INDETERMINATE, or NOT_APPLICABLE
}
```

For continuous decisions that update when policies or attributes change, `decide` returns a SAPL `Stream`. Read from it with `awaitNext()` and close it when finished. The `try`-with-resources block closes both the stream and the `PDPComponents`:

```java
try (var components = PolicyDecisionPointBuilder.withDefaults().withResourcesSource().build()) {
    var pdp = components.pdp();
    try (var stream = pdp.decide(subscription)) {
        var decision = stream.awaitNext();
        System.out.println(decision.decision());
    }
}
```

`PDPComponents` owns the policy sources, the attribute broker, and their background threads. Always close it (directly via `close()` or through `try`-with-resources) so those resources are released.

The [Embedded PDP Demo](https://github.com/heutelbeck/sapl-demos/tree/master/embedded-pdp) shows this end to end, including a custom PIP and function library.

### Remote PDP Client

For a non-Spring application that connects to a SAPL Node or other remote PDP server. Both HTTP/JSON and RSocket/protobuf transports are supported:

```xml
<dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-pdp-remote</artifactId>
    <version>4.1.1</version>
</dependency>
```

`RemotePolicyDecisionPoint.builder()` selects the transport with `.http()` or `.rsocket()`, and returns a `ReactivePolicyDecisionPoint`:

```java
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;

// HTTP
ReactivePolicyDecisionPoint pdp = RemotePolicyDecisionPoint.builder().http()
        .baseUrl("https://localhost:8443")
        .basicAuth("clientKey", "clientSecret")
        .build();

// RSocket (high-throughput protobuf transport)
ReactivePolicyDecisionPoint pdp = RemotePolicyDecisionPoint.builder().rsocket()
        .host("localhost").port(7000)
        .secure()
        .apiKey("sapl_7f3a...")
        .build();
```

Both builders expose `basicAuth(key, secret)`, `apiKey(key)`, and `oauth2(...)` for authentication. TLS differs by transport. The HTTP builder gets TLS from an `https://` base URL (it defaults to `https://localhost:8443`); use `secure(SslContext)` or `withUnsecureSSL()` only to customize certificate trust. The RSocket builder has no URL scheme, so it enables TLS via `secure()` (or `secure(SslContext)` / `withUnsecureSSL()`); it defaults `port` to `7000` and also accepts `socketPath(...)` and `keepAlive(...)`.

Sending credentials over a plaintext connection (an `http://` base URL, or an RSocket connection without TLS) is refused at `build()` time. Call `allowInsecureTransport()` to accept that risk for local development, or use an `https://` URL (or RSocket `secure()`) in production.

Consume decisions reactively. A streaming subscription keeps receiving updated decisions until you unsubscribe. Use `blockFirst()` or `take(1)` to consume just the first:

```java
var subscription = AuthorizationSubscription.of("willi", "read", "something");

// Reactive streaming
pdp.decide(subscription)
   .doOnNext(decision -> System.out.println(decision.decision()))
   .subscribe();

// One-shot, blocking on the reactive result
var decision = pdp.decideOnce(subscription).block();
```

The [Remote PDP Demo](https://github.com/heutelbeck/sapl-demos/tree/master/remote-pdp) shows HTTP and multi-subscription usage. For the RSocket wire protocol, see [RSocket API](../6_1_HTTPApi/#rsocket-api).

### Spring Boot Applications

For Spring Boot applications, use the unified starter. It includes the embedded PDP, the remote PDP client, Spring Security integration, and autoconfigures the PDP:

```xml
<dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-spring-boot-starter</artifactId>
    <version>4.1.1</version>
</dependency>
```

By default the embedded PDP is active. To connect to a remote PDP server instead, configure the remote PDP properties (prefix `io.sapl.pdp.remote`):

| Property             | Type      | Default  | Description                                                              |
|----------------------|-----------|----------|-------------------------------------------------------------------------|
| `enabled`            | `boolean` | `false`  | Activates the remote PDP client and disables the embedded PDP.          |
| `type`               | `String`  | `"http"` | Connection transport, `http` or `rsocket`.                              |
| `host`               | `String`  |          | Host of the remote PDP. An HTTP base URL, or a hostname for RSocket.    |
| `port`               | `int`     | `7000`   | RSocket port.                                                           |
| `socketPath`         | `String`  |          | RSocket Unix domain socket path, as an alternative to host and port.   |
| `tls`                | `boolean` | `false`  | Enables TLS on the RSocket transport.                                   |
| `key`                | `String`  |          | Client key for basic authentication. Requires `secret`.                 |
| `secret`             | `String`  |          | Client secret for basic authentication. Requires `key`.                 |
| `bearerToken`        | `String`  |          | A SAPL API key or bearer token sent as `Authorization: Bearer`.         |
| `tokenRelay`         | `boolean` | `false`  | Forwards the incoming user's OAuth2 token to the PDP (HTTP only).       |
| `oauth2`             | object    |          | OAuth2 client-credentials configuration (`clientRegistrationId`, etc.). |
| `keepAlive`          | duration  | `20s`    | RSocket keep-alive interval.                                            |
| `maxLifeTime`        | duration  | `90s`    | RSocket connection maximum lifetime.                                    |
| `ignoreCertificates` | `boolean` | `false`  | Disables TLS certificate verification. For development only.            |

Configure exactly one authentication method: `key` and `secret` together, `bearerToken` alone, `tokenRelay`, or `oauth2`.

Example using basic authentication over HTTP:

```properties
io.sapl.pdp.remote.enabled=true
io.sapl.pdp.remote.type=http
io.sapl.pdp.remote.host=https://pdp.example.com:8443
io.sapl.pdp.remote.key=your-client-key
io.sapl.pdp.remote.secret=your-client-secret
```

Example using a bearer token over RSocket:

```properties
io.sapl.pdp.remote.enabled=true
io.sapl.pdp.remote.type=rsocket
io.sapl.pdp.remote.host=pdp.example.com
io.sapl.pdp.remote.port=7000
io.sapl.pdp.remote.tls=true
io.sapl.pdp.remote.bearerToken=sapl_7f3a...
```

#### Reducing Application Footprint

When using only a remote PDP, exclude the embedded PDP dependency to reduce the application size:

```xml
<dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-spring-boot-starter</artifactId>
    <version>4.1.1</version>
    <exclusions>
        <exclusion>
            <groupId>io.sapl</groupId>
            <artifactId>sapl-pdp</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

### Deployment Options

SAPL provides three ways to deploy a PDP:

- **Embedded PDP**: Runs inside a JVM application with policies loaded from the classpath, a filesystem directory, or signed bundles. Suitable for single-instance applications or microservices where policies are deployed alongside the application.
- **SAPL Node**: A standalone, headless PDP server that exposes the PDP over HTTP and RSocket. Supports filesystem directories, signed bundles, and remote bundle fetching. Designed for centralized policy management across multiple applications.
- **Remote PDP client**: A lightweight client library that connects to a SAPL Node (or any SAPL-compatible server) over HTTP or RSocket. Applications use this when policies are managed centrally rather than embedded.
</content>
