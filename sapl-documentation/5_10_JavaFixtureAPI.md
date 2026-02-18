---
layout: default
title: Java Fixture API
parent: Testing SAPL Policies
grand_parent: SAPL Reference
nav_order: 310
---

## Java Fixture API

The `SaplTestFixture` class provides a programmatic API for testing SAPL policies from Java. It uses a fluent Given-When-Then builder pattern. Use this API when the test DSL does not cover your use case, for example when you need custom assertion predicates, specific timing control, or integration with Java test infrastructure.

### Creating a Test Fixture

The fixture API has two entry points. `createSingleTest()` creates a unit test that evaluates a single policy document. `createIntegrationTest()` loads multiple documents and combines their decisions through a combining algorithm. Both follow the same fluent builder chain: load policies, set up mocks, submit a subscription, assert the decision.

```java
// Unit test (single document)
var result = SaplTestFixture.createSingleTest()
    .withPolicyFromResource("policies/patient-access.sapl")
    .givenFunction("time.dayOfWeek", args(any()), Value.of("MONDAY"))
    .whenDecide(AuthorizationSubscription.of("Dr. Smith", "read", "patient_record"))
    .expectPermit()
    .verify();

// Integration test (multiple documents)
var result = SaplTestFixture.createIntegrationTest()
    .withConfigurationFromResources("policiesIT")
    .withCombiningAlgorithm(CombiningAlgorithm.of(PRIORITY_DENY, DENY, ABSTAIN))
    .whenDecide(AuthorizationSubscription.of("user", "read", "resource"))
    .expectPermit()
    .verify();
```

### Loading Policies

Unit tests load a single document by path or inline source. Integration tests load a full configuration directory containing multiple `.sapl` files and a `pdp.json`.

| Method                                   | Description                                            |
|------------------------------------------|--------------------------------------------------------|
| `withPolicyFromResource(path)`           | Load from classpath resource                           |
| `withPolicyFromFile(path)`               | Load from filesystem                                   |
| `withPolicy(source)`                     | Inline policy source                                   |
| `withConfigurationFromResources(path)`   | Load all policies and pdp.json from classpath directory |
| `withConfigurationFromDirectory(path)`   | Load all policies and pdp.json from filesystem directory |

### Mocking

The fixture provides the same mocking capabilities as the test DSL. Functions are mocked with argument matchers from `io.sapl.test.Matchers`. Attribute mocks are identified by a mock ID string and an attribute name. Variables and secrets configure PDP-level state.

```java
import static io.sapl.test.Matchers.*;

// Function mock
.givenFunction("time.dayOfWeek", args(any()), Value.of("MONDAY"))

// Environment attribute mock with initial value
.givenEnvironmentAttribute("timeMock", "time.now", args(), Value.of("2026-01-15T10:00:00Z"))

// Entity attribute mock
.givenAttribute("upperMock", "string.upper", any(), args(), Value.of("HELLO"))

// PDP variables and secrets
.givenVariable("tenant", Value.of("hospital-north"))
.givenSecret("api_key", Value.of("sk-test-key"))
```

### Decision Expectations

Simple expectations check only the decision type. For policies that attach obligations, advice, or a transformed resource, use `expectDecisionMatches()` with a decision matcher. Custom predicates give full control for assertions that the built-in matchers do not cover.

```java
// Simple decisions
.expectPermit()
.expectDeny()
.expectIndeterminate()
.expectNotApplicable()

// Decision matcher with obligations and resource
.expectDecisionMatches(isPermit()
    .containsObligation(Value.of(Map.of("type", "logAccess")))
    .withResource(expectedResource))

// Custom predicate
.expectDecisionMatches(isPermit()
    .containsObligationMatching(obligation ->
        obligation instanceof ObjectValue obj
        && obj.get("type") instanceof TextValue(var type)
        && "audit".equals(type)))
```

### Streaming

Streaming tests emit new values to attribute mocks between expectations, just like `then` blocks in the DSL. The `thenEmit()` method takes the mock ID and the new value. The fixture waits for the PDP to re-evaluate and checks the next expectation.

```java
.givenEnvironmentAttribute("timeMock", "time.now", args(), Value.of("morning"))
.whenDecide(subscription)
.expectPermit()
.thenEmit("timeMock", Value.of("night"))
.expectDeny()
.verify();
```

### Registering Libraries and PIPs

When your policies use custom function libraries or PIPs, register them with the fixture. Function libraries are registered by class (the fixture instantiates them). PIPs are registered as instances, which allows injecting dependencies like service clients.

```java
// Static function library
.withFunctionLibrary(TemporalFunctionLibrary.class)

// All default function libraries
.withDefaultFunctionLibraries()

// PIP instance
.withPolicyInformationPoint(new UserPIP(userService))
```

### Execution

The `verify()` method executes the test with a default timeout of 10 seconds:

```java
TestResult result = fixture.verify();
```

A custom timeout can be specified:

```java
TestResult result = fixture.verify(Duration.ofSeconds(30));
```
