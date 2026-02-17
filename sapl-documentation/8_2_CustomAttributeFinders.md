---
layout: default
title: Custom Attribute Finders
#permalink: /reference/Custom-Attribute-Finders/
parent: Attribute Finders
grand_parent: SAPL Reference
nav_order: 250
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

Attribute methods must return `Flux<Value>` or `Mono<Value>`. A `Mono<Value>` return is automatically converted to a `Flux<Value>` by the PDP.

Use `Flux<Value>` when the attribute value can change over time (e.g., periodic sensor readings, message streams). Use `Mono<Value>` when the attribute is fetched once (e.g., a database lookup).

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

Policy parameters use concrete `Value` subtypes for type safety, following the same type mapping as [functions](../7_2_CustomFunctionLibraries#declaring-functions). The PDP validates parameter types before calling the method.

### Examples

**Environment attribute with no parameters:**

```java
/* <time.now> */
@EnvironmentAttribute(docs = "Returns the current UTC time, updating periodically.")
public Flux<Value> now() {
    return Flux.interval(Duration.ofSeconds(1))
        .map(i -> Value.of(Instant.now(clock).toString()));
}
```

**Environment attribute with `AttributeAccessContext`:**

```java
/* <jwt.token> */
@EnvironmentAttribute(docs = "Extracts a JWT from the subscription secrets.")
public Flux<Value> token(AttributeAccessContext ctx) {
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
public Flux<Value> isCurrentlyValid(TextValue certPem) {
    try {
        var certificate = CertificateUtils.parseCertificate(certPem.value());
        var notBefore   = certificate.getNotBefore().toInstant();
        var notAfter    = certificate.getNotAfter().toInstant();
        return Flux.interval(Duration.ofMinutes(1))
            .map(i -> Value.of(clock.instant().isAfter(notBefore)
                            && clock.instant().isBefore(notAfter)));
    } catch (CertificateException e) {
        return Flux.just(Value.error("Invalid certificate.", e));
    }
}
```

The first parameter (`TextValue certPem`) receives the left-hand value from the SAPL expression.

**Entity attribute with context and policy parameters:**

```java
/* "sensors/#".<mqtt.messages(1)> */
@Attribute(name = "messages", docs = "Subscribes to MQTT messages on a topic.")
public Flux<Value> messages(Value topic, AttributeAccessContext ctx, Value qos) {
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
public Flux<Value> get(AttributeAccessContext ctx, ObjectValue requestSettings) {
    return webClient.httpRequest(HttpMethod.GET, mergeHeaders(ctx, requestSettings));
}
```

When both annotations are present, the method is registered for both calling conventions. The entity value, if present, is passed as the first policy parameter.

**Variable arguments:**

```java
/* subject.<user.attribute("AA", "BB", "CC")> */
@Attribute(name = "attribute", docs = "Accepts a variable number of arguments.")
public Flux<Value> attribute(Value leftHand, AttributeAccessContext ctx, TextValue... params) {
    ...
}
```

If an attribute is overloaded, an implementation with an exact match of the number of arguments takes precedence over a variable arguments implementation.

### Attribute Name Overloading

SAPL allows multiple implementations for the same attribute name with different signatures. For example, a PIP can provide:

- `<user.profile>` (environment, no parameters)
- `subject.<user.profile>` (entity)
- `<user.profile("department")>` (environment with parameter)
- `subject.<user.profile("department")>` (entity with parameter)

The PDP disambiguates at runtime based on the calling convention and argument count.

### Error Handling

Attribute methods must never throw exceptions. Return error values using `Value.error()`:

```java
@EnvironmentAttribute(docs = "Fetches data from an external API.")
public Mono<Value> fetchData(AttributeAccessContext ctx, TextValue endpoint) {
    return webClient.get(endpoint.value())
        .map(Value::of)
        .onErrorResume(e -> Mono.just(Value.error("API request failed.", e)));
}
```

An error value propagates through the policy evaluation and causes the enclosing condition to evaluate to `INDETERMINATE`.

### Credential Management

PIP methods frequently need credentials to access external services. These credentials should never be hardcoded or stored in policies.

Use `AttributeAccessContext` to access secrets:

- **`pdpSecrets()`** for operator-level credentials configured in `pdp.json` (e.g., database connection strings, API keys shared across all requests)
- **`subscriptionSecrets()`** for per-request credentials provided by the application (e.g., the current user's OAuth token)
- **`variables()`** for non-sensitive PDP configuration (e.g., service URLs, timeout settings)

See [Authorization Subscriptions](../1_2_AuthorizationSubscriptions/) for details on the secrets field.

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
