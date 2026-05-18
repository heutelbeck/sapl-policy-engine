---
layout: default
title: Custom Attribute Finders
parent: Extending SAPL
nav_order: 803
---

## Custom Attribute Finders

A Policy Information Point (PIP) is a class that provides attribute finders to the PDP. Custom PIPs allow policies to access external data sources such as databases, APIs, or message brokers.

### Declaring a PIP

A class annotated with `@PolicyInformationPoint` is recognized as a PIP:

| Attribute            | Description                                              | Default          |
|----------------------|----------------------------------------------------------|------------------|
| `name`               | PIP namespace as used in SAPL policies (e.g., `"user"`)  | Java class name  |
| `description`        | Short description for documentation                      | `""`             |
| `pipDocumentation`   | Detailed documentation (supports Markdown)               | `""`             |

```java
@PolicyInformationPoint(name = "user", description = "User profile attributes")
public class UserPIP {
    ...
}
```

### Entity Attributes vs. Environment Attributes

SAPL distinguishes two kinds of attribute access:

- **Entity attributes** are called on a value: `subject.cert.<x509.isValid>`. The left-hand value (`subject.cert`) is passed to the method as the first parameter. Use the `@Attribute` annotation.

- **Environment attributes** are called without a left-hand value: `<time.now>`. Use the `@EnvironmentAttribute` annotation.

A method can carry both `@Attribute` and `@EnvironmentAttribute` to support both calling conventions from a single implementation.

### Declaring Attributes

Both `@Attribute` and `@EnvironmentAttribute` support the same annotation attributes:

| Attribute      | Description                                           | Default          |
|----------------|-------------------------------------------------------|------------------|
| `name`         | Attribute name in SAPL (overrides Java method name)   | Method name      |
| `docs`         | Attribute documentation                               | `""`             |
| `schema`       | Inline JSON schema for the return value               | `""`             |
| `pathToSchema` | Classpath path to a JSON schema file                  | `""`             |

### Return Types

Attribute methods return either `io.sapl.api.stream.Stream<Value>` (for attributes that emit a sequence of values) or a `Value` subtype (for one-shot attributes that resolve to a single value). The PDP wraps a one-shot `Value` return as `Streams.just(value)` internally.

Use `Stream<Value>` for attributes whose value changes over time (periodic sensor readings, message streams, certificate expiry watchers). Use a `Value` return when the attribute resolves once per invocation (database lookup, deterministic computation, single-shot HTTP GET).

`Stream<Value>` here is the SAPL stream primitive in `sapl-api`, not `java.util.stream.Stream`. The `io.sapl.api.stream.Streams` utility class provides constructors: `Streams.just(value)`, `Streams.error(message)`, `Streams.empty()`, `Streams.scheduledPoll(interval, supplier, clock, scheduler)`, `Streams.scheduledAt(value, instant, scheduler)`, `Streams.concat(...)`, `Streams.map(stream, fn)`, `Streams.distinctUntilChanged(stream, errorMapper)`, `Streams.fromCallback(producer)`, `Streams.fromBlockingSource(callable)`. PIP authors compose these in the same shape as the built-in libraries.

Reactor types (`Flux<Value>`, `Mono<Value>`) are no longer accepted from PIP methods. The 4.1 attribute broker contract drops Reactor at the boundary.

### Parameter Order

Attribute method parameters follow a fixed order:

**For `@Attribute` (entity attributes):**

| Position | Type | Description |
|----------|------|-------------|
| 1st | `Value` subtype | Entity (left-hand value from SAPL) |
| 2nd (optional) | `AttributeAccessContext` | Variables and secrets |
| Remaining | `Value` subtypes | Policy parameters (bracket arguments) |
| Last (optional) | `Value[]` subtype | Variable arguments |

**For `@EnvironmentAttribute`:**

| Position | Type | Description |
|----------|------|-------------|
| 1st (optional) | `AttributeAccessContext` | Variables and secrets |
| Remaining | `Value` subtypes | Policy parameters (bracket arguments) |
| Last (optional) | `Value[]` subtype | Variable arguments |

Policy parameters use concrete `Value` subtypes for type safety, following the same type mapping as [functions](../8_2_CustomFunctionLibraries#declaring-functions). The PDP validates parameter types before calling the method.

### Examples

**Environment attribute with no parameters:**

```java
/* <time.now> */
@EnvironmentAttribute(docs = "Returns the current UTC time, updating periodically.")
public Stream<Value> now() {
    return Streams.scheduledPoll(Duration.ofSeconds(1),
        () -> Value.of(Instant.now(clock).toString()),
        clock, scheduler);
}
```

**Environment attribute with `AttributeAccessContext`:**

```java
/* <jwt.token> */
@EnvironmentAttribute(docs = "Extracts a JWT from the subscription secrets.")
public Stream<Value> token(AttributeAccessContext ctx) {
    var secretsKey = resolveSecretsKey(ctx);
    return tokenFromSecrets(secretsKey, ctx);
}
```

The `AttributeAccessContext` provides access to:

| Method                 | Description                                                      |
|------------------------|------------------------------------------------------------------|
| `variables()`          | PDP environment variables (from `pdp.json`)                      |
| `pdpSecrets()`         | Operator-level secrets configured in `pdp.json`                  |
| `subscriptionSecrets()`| Per-request secrets provided by the application                  |

The context is injected automatically by the PDP and is invisible to policy authors.

**Entity attribute:**

```java
/* subject.clientCertificate.<x509.isCurrentlyValid> */
@Attribute(docs = "Checks if the certificate is currently valid.")
public Stream<Value> isCurrentlyValid(TextValue certPem) {
    try {
        var certificate = CertificateUtils.parseCertificate(certPem.value());
        var notBefore   = certificate.getNotBefore().toInstant();
        var notAfter    = certificate.getNotAfter().toInstant();
        return Streams.scheduledPoll(Duration.ofMinutes(1),
            () -> Value.of(clock.instant().isAfter(notBefore)
                        && clock.instant().isBefore(notAfter)),
            clock, scheduler);
    } catch (CertificateException e) {
        return Streams.just(Value.error("Invalid certificate."));
    }
}
```

The first parameter (`TextValue certPem`) receives the left-hand value from the SAPL expression.

**Entity attribute with context and policy parameters:**

```java
/* "sensors/#".<mqtt.messages(1)> */
@Attribute(name = "messages", docs = "Subscribes to MQTT messages on a topic.")
public Stream<Value> messages(Value topic, AttributeAccessContext ctx, Value qos) {
    return mqttClient.subscribe(topic, ctx, qos);
}
```

The entity (`topic`) is the left-hand value, `ctx` is injected, and `qos` is the bracket argument from the policy.

**Dual annotation (works as both entity and environment attribute):**

```java
/* Environment: <http.get(requestSettings)> */
/* Entity:      "https://api.example.com".<http.get(requestSettings)> */
@Attribute
@EnvironmentAttribute(docs = "Performs an HTTP GET request.")
public Stream<Value> get(AttributeAccessContext ctx, ObjectValue requestSettings) {
    return webClient.httpRequest(HttpMethod.GET, mergeHeaders(ctx, requestSettings));
}
```

When both annotations are present, the method is registered for both calling conventions. The entity value, if present, is passed as the first policy parameter.

**Variable arguments:**

```java
/* subject.<user.attribute("AA", "BB", "CC")> */
@Attribute(name = "attribute", docs = "Accepts a variable number of arguments.")
public Stream<Value> attribute(Value leftHand, AttributeAccessContext ctx, TextValue... params) {
    ...
}
```

**One-shot attribute (single `Value` return):**

```java
/* <user.lookup(id)> */
@EnvironmentAttribute(docs = "Looks up a user record by id; returns once per invocation.")
public Value lookup(TextValue id) {
    var record = userRepository.findById(id.value());
    if (record == null) {
        return Value.error("User not found.");
    }
    return Value.of(record.toJson());
}
```

The PDP wraps a `Value` return as a single-element stream automatically. Use this shape for deterministic, side-effect-free attribute resolutions.

If an attribute is overloaded, an implementation with an exact match of the number of arguments takes precedence over a variable arguments implementation.

### Attribute Name Overloading

SAPL allows multiple implementations for the same attribute name with different signatures. For example, a PIP can provide:

- `<user.profile>` (environment, no parameters)
- `subject.<user.profile>` (entity)
- `<user.profile("department")>` (environment with parameter)
- `subject.<user.profile("department")>` (entity with parameter)

The PDP disambiguates at runtime based on the calling convention and argument count.

### Error Handling

Attribute methods should not throw checked exceptions and should treat thrown `RuntimeException`s as a last resort. Prefer publishing an `ErrorValue` into the stream so the PDP and the consuming policy can handle it deterministically. Use `Value.error("...")` (or `Streams.error("...")` for a one-shot error stream).

```java
@EnvironmentAttribute(docs = "Fetches data from an external API.")
public Stream<Value> fetchData(AttributeAccessContext ctx, TextValue endpoint) {
    try {
        var body = webClient.get(endpoint.value()).bodyAsString();
        return Streams.just(Value.of(body));
    } catch (Exception e) {
        return Streams.error("API request failed: " + e.getMessage());
    }
}
```

A `RuntimeException` thrown by the attribute method is not silently captured: the attribute broker treats it as a failed attempt and drives the retry burst (jittered exponential backoff up to the policy-configured retry count). Transient connect-time or send-time failures recover on the same schedule as transient mid-stream errors. After retries are exhausted, the broker publishes a transient `ErrorValue` summarising the last cause and waits one `pollInterval` before the next cycle. An error value reaching a policy condition causes the enclosing condition to evaluate to `INDETERMINATE`.

### Credential Management

PIP methods frequently need credentials to access external services. These credentials should never be hardcoded or stored in policies.

Use `AttributeAccessContext` to access secrets:

- **`pdpSecrets()`** for operator-level credentials configured in `pdp.json` (e.g., database connection strings, API keys shared across all requests)
- **`subscriptionSecrets()`** for per-request credentials provided by the application (e.g., the current user's OAuth token)
- **`variables()`** for non-sensitive PDP configuration (e.g., service URLs, timeout settings)

See [Authorization Subscriptions](../2_1_AuthorizationSubscriptions/) for details on the secrets field.

### Registering Custom PIPs

Custom PIPs are registered with the PDP through the builder API:

```java
var pdp = PolicyDecisionPointBuilder.builder()
    .withDefaults()
    .withPolicyInformationPoint(new UserPIP(userService))
    .build();
```

In a Spring Boot application, any bean annotated with `@PolicyInformationPoint` is automatically discovered and registered with the PDP.

{: .note }
> Add the `-parameters` flag to the Java compiler to ensure that automatically generated documentation includes parameter names from the source code.
