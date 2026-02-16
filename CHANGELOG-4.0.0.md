# SAPL 4.0.0 Changelog

---

## Overview

SAPL 4.0.0 is a **ground-up rewrite of the entire policy engine**. The old Xtext/EMF-based interpreter has been replaced by a new ANTLR4-based compiler that translates SAPL policies into an optimized voter-based evaluation model. This is not an incremental update -- virtually every component has been redesigned and rebuilt.
**Why a rewrite?** The 4.0 compiler produces a stratified voter graph that separates policies into three tiers -- foldable (compile-time constant), pure (synchronous, no streams), and streaming (reactive) -- so the engine only pays the cost of reactive evaluation for policies that actually need it. The result is significantly lower latency and memory footprint, especially for large policy sets.

**Key architectural changes:**
- **Parser:** Xtext/EMF/Guice -> ANTLR4 (eliminates ~30 transitive dependencies)
- **Evaluation:** Runtime AST interpretation -> compiled voter composition with stratified evaluation
- **Type system:** `Val` (mutable wrapper around Jackson `JsonNode`) -> sealed `Value` interface with immutable records (`TextValue`, `NumberValue`, `BooleanValue`, `ArrayValue`, `ObjectValue`, `NullValue`, `UndefinedValue`, `ErrorValue`). The internal data model no longer depends on Jackson. Values are type-safe, pattern-matchable, and immutable.
- **Module count:** 38 modules -> 24 modules
- **Spring integration:** 6 separate modules -> 1 unified `sapl-spring-boot-starter`
- **Eclipse IDE plugin:** Removed in favor of a universal Language Server Protocol (LSP) server

This release delivers the full-performance PDP engine, `sapl-node` HTTP server, and all function/attribute libraries.

**Minimum Requirements:**
- JDK 21+ (was JDK 17)
- Spring Boot 4.0.x (was 3.5.x)
- Jackson 3.x `tools.jackson.*` (was 2.x `com.fasterxml.jackson.*`)

---

## Breaking Changes

### Grammar & Syntax

**`where` Keyword Removed (CRITICAL):**

The `where` keyword for policy body conditions has been removed. Conditions now follow directly after the target expression, separated by semicolons.

```
-- v3.0.0
policy "example"
permit
    action == "read"
where
    subject == "admin";

-- v4.0.0
policy "example"
permit
    action == "read";
    subject == "admin";
```

Migration: Delete `where` line, add `;` to the end of the preceding target expression. Use `grep -r "where" *.sapl` to find all occurrences.

The stratified compilation now implicitly infers conditions that can be evaluated without IO to still enable fast elimination of irrelevant policies to avoid redundant IO for attribute lookups.

**Combining Algorithm Syntax - Complete Rewrite:**

Policy set combining algorithms use composable natural language: `<votingMode> or <defaultDecision> [errors <errorHandling>]`.

| 3.x Policy Set Syntax | 4.0.0 Policy Set Syntax |
|------------------------|-------------------------|
| `deny-overrides` | `priority deny or abstain errors propagate` |
| `permit-overrides` | `priority permit or abstain errors propagate` |
| `first-applicable` | `first or abstain errors propagate` |
| `only-one-applicable` | `unique or abstain errors propagate` |
| `deny-unless-permit` | `priority permit or deny` |
| `permit-unless-deny` | `priority deny or permit` |

New algorithms not available in 3.x: `unanimous or deny`, `unanimous strict or deny`, and all `errors propagate`/`errors abstain` variants.

**pdp.json Algorithm Format Changed (CRITICAL):**

The `algorithm` field changed from a flat string to a nested object.

```json
// v3.0.0
{ "algorithm": "DENY_UNLESS_PERMIT", "variables": {} }

// v4.0.0
{
  "algorithm": {
    "votingMode": "PRIORITY_PERMIT",
    "defaultDecision": "DENY",
    "errorHandling": "PROPAGATE"
  },
  "variables": {},
  "secrets": {}
}
```

| v3.0.0 String | votingMode | defaultDecision | errorHandling |
|----------------|------------|-----------------|---------------|
| `DENY_OVERRIDES` | `PRIORITY_DENY` | `ABSTAIN` | `PROPAGATE` |
| `PERMIT_OVERRIDES` | `PRIORITY_PERMIT` | `ABSTAIN` | `PROPAGATE` |
| `FIRST_APPLICABLE` | `FIRST` | `ABSTAIN` | `PROPAGATE` |
| `ONLY_ONE_APPLICABLE` | `UNIQUE` | `ABSTAIN` | `PROPAGATE` |
| `DENY_UNLESS_PERMIT` | `PRIORITY_PERMIT` | `DENY` | `ABSTAIN` |
| `PERMIT_UNLESS_DENY` | `PRIORITY_DENY` | `PERMIT` | `ABSTAIN` |

**Import Syntax Simplified:**

Wildcard imports (`import filter.*`) and library alias imports (`import time as t`) are removed. Use explicit function imports only:

```
-- v3.0.0
import filter.*
import time as t

-- v4.0.0
import filter.blacken
import time.now as t
```

**Number Literals:**
- `123.` (trailing dot) is now invalid -- use `123` or `123.0`

**Object Keys:**
- Object keys can now be unquoted identifiers: `{ type: "logAccess" }` in addition to `{ "type": "logAccess" }`

**New Operators/Syntax:**
- `#` (hash) operator for relative location context
- Attribute finder options: `<pip.attr(param)[options]>`
- `::` (subtemplate) as alternative array slicing syntax

**Lazy/Eager Boolean Operators:**
The lazy (`&&`, `||`) and eager (`&`, `|`) boolean operators now behave identically in 4.0.0. The distinction is preserved in the grammar for potential future use in selecting evaluation strategies (e.g., eager operators may force parallel evaluation of both operands), but currently both variants short-circuit in the same way.

### Extended Indeterminate Semantics (BEHAVIORAL CHANGE)

INDETERMINATE votes now carry an `Outcome` field (PERMIT, DENY, or PERMIT_OR_DENY) indicating what the decision "would have been" without the error. This changes behavior under priority-based algorithms:

- **Old:** Policy A = PERMIT, Policy B = ERROR -> always INDETERMINATE
- **New:** Policy A = PERMIT, Policy B = ERROR(Outcome.DENY) -> PERMIT (error would not have changed result)
- **New:** Policy A = PERMIT, Policy B = ERROR(Outcome.PERMIT) -> INDETERMINATE (error matters)

If your deployment relied on errors always causing INDETERMINATE, review all policies that can produce evaluation errors.

### Public API Changes

**Val Replaced by Sealed Value Interface:**

```java
public sealed interface Value
    permits UndefinedValue, ErrorValue, NullValue, BooleanValue,
            NumberValue, TextValue, ArrayValue, ObjectValue
```

| Old Pattern | New Pattern |
|-------------|-------------|
| `import io.sapl.api.interpreter.Val` | `import io.sapl.api.model.Value` (and subtypes) |
| `Val.of("text")` | `Value.of("text")` |
| `Val.TRUE` / `Val.FALSE` | `Value.TRUE` / `Value.FALSE` |
| `Val.UNDEFINED` | `Value.UNDEFINED` |
| `Val.NULL` | `Value.NULL` |
| `Val.ofEmptyObject()` | `Value.EMPTY_OBJECT` |
| `Val.ofEmptyArray()` | `Value.EMPTY_ARRAY` |
| `Val.error("msg")` | `Value.error("msg")` |
| `val.isError()` | `value instanceof ErrorValue` |
| `val.isTextual()` | `value instanceof TextValue` |
| `val.getBoolean()` | `((BooleanValue) value).value()` or pattern match |
| `val.getText()` | `((TextValue) value).value()` or pattern match |
| `val.get()` (JsonNode) | `ValueJsonMarshaller.toJsonNode(value)` |
| `Val.ofJson("...")` | `ValueJsonMarshaller.json("...")` |
| `val.withTrace(...)` | Removed -- tracing handled separately via `TracedValue` |
| `Flux<Val>` convenience methods | Removed entirely |

Behavioral differences:
- `NaN`/`Infinite` doubles throw `IllegalArgumentException` instead of creating values
- `ArrayValue.get(invalidIndex)` returns `ErrorValue` instead of throwing
- Number equality is numerical (`1.0 == 1.00`), not scale-sensitive
- No Jackson `JsonNode` dependency in value model

**AuthorizationSubscription - Now an Immutable Record:**

```java
public record AuthorizationSubscription(
    @NonNull Value subject,
    @NonNull Value action,
    @NonNull Value resource,
    @NonNull Value environment,  // NEVER null, Value.UNDEFINED instead
    @NonNull ObjectValue secrets)  // NEW field
```

| Old | New |
|-----|-----|
| `subscription.getSubject()` (JsonNode) | `subscription.subject()` (Value) |
| `subscription.getEnvironment()` (nullable) | `subscription.environment()` (never null; check `instanceof UndefinedValue`) |
| Mutable (`@Data` setters) | Immutable record |
| 4 fields | 5 fields (+ `secrets`) |
| `new AuthorizationSubscription()` | Must use `of()` factories or full constructor |
| `com.fasterxml.jackson.databind.ObjectMapper` | `tools.jackson.databind.ObjectMapper` |

**AuthorizationDecision - Now an Immutable Record:**

```java
public record AuthorizationDecision(
    @NonNull Decision decision,
    @NonNull ArrayValue obligations,  // NEVER null, empty array instead
    @NonNull ArrayValue advice,       // NEVER null, empty array instead
    @NonNull Value resource)          // NEVER null, Value.UNDEFINED instead
```

| Old | New |
|-----|-----|
| `decision.getDecision()` | `decision.decision()` |
| `decision.getResource()` -> `Optional<JsonNode>` | `decision.resource()` -> Value (check `instanceof UndefinedValue`) |
| `decision.getObligations()` -> `Optional<ArrayNode>` | `decision.obligations()` -> ArrayValue |
| `decision.withObligations(...)` | Removed -- construct new record |
| Field order: decision, resource, obligations, advice | Field order: decision, obligations, advice, resource |

**PolicyDecisionPoint Interface:**

| Change | Detail |
|--------|--------|
| `decideOnce()` uses `.next()` instead of `Mono.from()` | Less strict -- silently takes first instead of throwing on >1 |
| `decide(Multi...)` and `decideAll(Multi...)` | Now `default` methods (were abstract) |
| New `decideOnceBlocking()` | Synchronous/blocking API for non-reactive code |

**PIP Annotations Package Change:**
- `io.sapl.api.pip.*` -> `io.sapl.api.attributes.*`
- `@Attribute`, `@EnvironmentAttribute`, `@PolicyInformationPoint` annotations moved
- New `AttributeAccessContext` parameter required for `@EnvironmentAttribute` methods (as first parameter)
- Parameters changed from `Val` with validation annotations to concrete `Value` subtypes
- Validation annotations (`@Array`, `@Bool`, `@Int`, `@Long`, `@Number`, `@Text`, `@JsonObject`, `@Schema`) deleted

**Function Library Signature Changes:**
- Return type: `Val` -> `Value`
- Parameters: `Val` with annotations -> concrete `Value` subtypes (`ObjectValue`, `TextValue`, etc.)
- Methods are now explicitly `static`

**CombiningAlgorithm:**
- Was enum `PolicyDocumentCombiningAlgorithm` with values like `DENY_OVERRIDES`
- Now record `CombiningAlgorithm(VotingMode, DefaultDecision, ErrorHandling)`
- Default: `PRIORITY_DENY / DENY / PROPAGATE`

**IdentifiableAuthorizationDecision:**
- Accessor rename: `getAuthorizationSubscriptionId()` -> `subscriptionId()`, `getAuthorizationDecision()` -> `decision()`
- `INDETERMINATE` singleton: old had `null` subscription ID, new has `""`

**MultiAuthorizationSubscription:**
- Method rename: `addAuthorizationSubscription(String, JsonNode, ...)` -> `addSubscription(String, AuthorizationSubscription)`

**MultiAuthorizationDecision:**
- Method rename: `setAuthorizationDecisionForSubscriptionWithId(...)` -> `setDecision(...)`
- New methods: `getDecisionType(String)`, `isPermitted(String)`

**Serialization Version UID:**
- Changed from `03_00_00L` to `4_00_00L` (also fixed typo `VERISION` -> `VERSION`)
- All serialized objects are incompatible between versions

### Jackson 3.x Migration

Jackson namespace changed from `com.fasterxml.jackson.*` to `tools.jackson.*` (Jackson 3.x):

| Old Import | New Import |
|------------|------------|
| `com.fasterxml.jackson.databind.JsonNode` | `tools.jackson.databind.JsonNode` |
| `com.fasterxml.jackson.databind.ObjectMapper` | `tools.jackson.databind.ObjectMapper` |
| `com.fasterxml.jackson.databind.node.ArrayNode` | `tools.jackson.databind.node.ArrayNode` |
| `com.fasterxml.jackson.annotation.*` | `tools.jackson.annotation.*` |
| `com.fasterxml.jackson.core.JsonProcessingException` | `tools.jackson.core.JacksonException` |

The new `Value` type system largely eliminates direct Jackson usage. Use `ValueJsonMarshaller` for conversion.

### Spring Boot Integration -- PEP Libraries Merged Into One Starter

In v3.0.0, integrating SAPL with Spring required pulling in multiple separate modules depending on your use case. You needed `sapl-spring-pdp-embedded` or `sapl-spring-pdp-remote` for the PDP, `sapl-spring-security` for the Policy Enforcement Points (PEPs), and `sapl-spring-data-*` modules if using Spring Data. Each module had its own auto-configuration classes, and ordering between them was managed by `@AutoConfigureAfter` chains.

In v4.0.0, **all 6 Spring modules are merged into a single `sapl-spring-boot-starter`**:

| v3.0.0 Module | What it provided | Now in `sapl-spring-boot-starter` |
|----------------|-----------------|-----------------------------------|
| `sapl-spring-security` | `@PreEnforce`, `@PostEnforce`, `@EnforceTillDenied`, `@EnforceDropWhileDenied`, `@EnforceRecoverableIfDenied`, `@QueryEnforce` annotations; `SaplAuthorizationManager` and `ReactiveSaplAuthorizationManager` for HTTP filter chains; constraint handler infrastructure | Yes -- all PEP annotations and authorization managers |
| `sapl-spring-pdp-embedded` | 13 auto-config classes for embedded PDP (function contexts, attribute contexts, PRP, interpreter, interceptors, JWT extension) | Yes -- collapsed into 2 auto-config classes (`PDPAutoConfiguration` + `InterceptorAutoConfiguration`) |
| `sapl-spring-pdp-remote` | `RemotePDPAutoConfiguration` for remote PDP connection | Yes -- now guarded by `io.sapl.pdp.remote.enabled=true` |
| `sapl-spring-data-common` | Shared enforcement services, expression evaluators, subscription builders for Spring Data | Yes -- packages relocated to `io.sapl.spring.data.*` |
| `sapl-spring-data-r2dbc` | R2DBC repository enforcement with `@QueryEnforce` and dynamic WHERE clause injection | Yes -- R2DBC as `provided` scope dependency |
| `sapl-spring-data-mongo-reactive` | MongoDB reactive repository enforcement with `@QueryEnforce` | Yes -- MongoDB as `provided` scope dependency |

```xml
<!-- v3.0.0 -- needed multiple dependencies -->
<dependency><groupId>io.sapl</groupId><artifactId>sapl-spring-pdp-embedded</artifactId></dependency>
<dependency><groupId>io.sapl</groupId><artifactId>sapl-spring-security</artifactId></dependency>
<dependency><groupId>io.sapl</groupId><artifactId>sapl-spring-data-r2dbc</artifactId></dependency>

<!-- v4.0.0 -- single dependency replaces all -->
<dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-spring-boot-starter</artifactId>
</dependency>
```

The starter uses `@ConditionalOnClass` guards so Spring Data R2DBC and MongoDB beans are only created when the respective libraries are on the classpath. The embedded PDP is enabled by default (`io.sapl.pdp.embedded.enabled=true`), while the remote PDP requires explicit opt-in (`io.sapl.pdp.remote.enabled=true`).

All enforcement annotations (`@PreEnforce`, `@PostEnforce`, etc.) gain a new `secrets()` SpEL attribute for passing per-request secrets to the PDP. Two subscription builder services (`WebAuthorizationSubscriptionBuilderService` and `WebFluxAuthorizationSubscriptionBuilderService`) are unified into a single `AuthorizationSubscriptionBuilderService`.

**RSocket Support Removed:**
- Module `sapl-rsocket-endpoint` deleted entirely
- All RSocket dependencies removed
- Remote PDP is now HTTP-only
- `io.sapl.pdp.remote.type` default changed from `rsocket` to `http`
- `io.sapl.pdp.remote.enabled` must be explicitly set to `true` (default: `false`)
- Properties `rsocketHost` and `rsocketPort` removed

**NOTE**: RSocket will be reintroduced in a future release.

**Server Deprecation and Replacement:**
- `sapl-server-lt` is **deprecated** and replaced by `sapl-node`, a PicoCLI-based CLI tool with `bundle` and `generate` subcommands
- `sapl-server-ce` (Community Edition with Vaadin UI) is **no longer supported**. Its interactive features are partially replaced by `sapl-playground` (a standalone interactive policy testing application)
- `sapl-node` and `sapl-language-server` are available as **GraalVM native image binaries**, providing fast startup and low memory footprint for containerized and CLI deployments
- Property prefix: `io.sapl.server-lt.*` -> `io.sapl.node.*`
- Docker image: `ghcr.io/heutelbeck/sapl-server-lt` -> `ghcr.io/heutelbeck/sapl-node`

**Authentication Model Changed (sapl-node):**

Flat key/secret model replaced by multi-tenant user model:

```yaml
# v3.0.0
io.sapl.server-lt:
  key: xwuUaRD65G
  secret: $argon2id$v=19$...
  allowedApiKeys:
    - $argon2id$v=19$...

# v4.0.0
io.sapl.node:
  rejectOnMissingPdpId: false
  defaultPdpId: "default"
  users:
    - id: "client-1"
      pdpId: "default"
      basic:
        username: "xwuUaRD65G"
        secret: "$argon2id$v=19$..."
    - id: "client-2"
      pdpId: "production"
      apiKey: "$argon2id$v=19$..."
```

**Spring Data Package Relocations:**

| Old Package | New Package |
|-------------|-------------|
| `io.sapl.springdatacommon.*` | `io.sapl.spring.data.*` |
| `io.sapl.springdatar2dbc.*` | `io.sapl.spring.data.r2dbc.*` |
| `io.sapl.springdatamongoreactive.*` | `io.sapl.spring.data.mongo.*` |

**Authorization Subscription Builder Unification:**
- `WebAuthorizationSubscriptionBuilderService` and `WebFluxAuthorizationSubscriptionBuilderService` unified into `AuthorizationSubscriptionBuilderService`

**Property Changes:**

| Property | Change |
|----------|--------|
| `io.sapl.pdp.embedded.enabled` | NEW (default: `true`) - can disable embedded PDP |
| `io.sapl.pdp.embedded.pdp-config-type` | `FILESYSTEM` renamed to `DIRECTORY`. New: `MULTI_DIRECTORY`, `BUNDLES`, `REMOTE_BUNDLES` |
| `io.sapl.pdp.remote.enabled` | NEW (default: `false`) - must explicitly enable |
| `io.sapl.pdp.remote.type` | Default changed from `rsocket` to `http` |
| `io.sapl.pdp.remote.rsocketHost` | REMOVED |
| `io.sapl.pdp.remote.rsocketPort` | REMOVED |

**Embedded PDP Auto-Configuration:**
- 13 auto-configuration classes collapsed into 2 (`PDPAutoConfiguration` + `InterceptorAutoConfiguration`)
- If you had `@AutoConfigureAfter` references to removed classes, update to `PDPAutoConfiguration.class`

**Cache Provider:**
- Infinispan replaced by Caffeine: `spring.cache.type: caffeine`

### Module Changes

38 modules reduced to 24. Complete mapping:

| v3.0.0 Module | v4.0.0 Status |
|----------------|---------------|
| `sapl-pdp-api` | Merged into `sapl-api` |
| `sapl-extensions-api` | Merged into `sapl-api` |
| `sapl-lang` | Split into `sapl-parser` + `sapl-pdp` |
| `sapl-generator` | Removed (Xtext) |
| `sapl-ide` | Absorbed into `sapl-language-server` |
| `sapl-web` | Absorbed into `sapl-language-server` |
| `sapl-pdp-embedded` | Merged into `sapl-pdp` |
| `sapl-hamcrest` | Removed (migrate to AssertJ) |
| `sapl-assertj` | Merged into `sapl-test` |
| `sapl-coverage-api` | Merged into `sapl-test` |
| `sapl-test-junit` | Merged into `sapl-test` |
| `sapl-test-lang` | Replaced by `sapl-test-parser` |
| `sapl-test-ide` / `sapl-test-web` | Removed |
| `sapl-spring-pdp-embedded` | Merged into `sapl-spring-boot-starter` |
| `sapl-spring-pdp-remote` | Merged into `sapl-spring-boot-starter` |
| `sapl-spring-security` | Merged into `sapl-spring-boot-starter` |
| `sapl-spring-data-*` (3 modules) | Merged into `sapl-spring-boot-starter` |
| `sapl-rsocket-endpoint` | Removed entirely |
| `sapl-eclipse-plugin/*` (6 modules) | Removed entirely |
| `sapl-server-lt` / `sapl-server-ce` | Replaced by `sapl-node` + `sapl-playground` |
| `pdp-extensions/jwt` | Absorbed into `sapl-pdp` |
| `pdp-extensions/input-sanitization-functions` | Absorbed into `sapl-pdp` |

New modules: `sapl-api`, `sapl-parser`, `sapl-test-parser`, `sapl-spring-boot-starter`, `sapl-node`, `sapl-playground`, `sapl-documentation-generator`.

### Test Framework and Test DSL -- Complete Rewrite

The SAPL test infrastructure has been **completely rewritten**. The Xtext-based test DSL grammar (`SAPLTest.xtext`) is replaced by an ANTLR4 grammar (`SAPLTestParser.g4` + `SAPLTestLexer.g4`). Six test-related modules are consolidated into two (`sapl-test` + `sapl-test-parser`). The Java API is redesigned around a single `SaplTestFixture` class. Hamcrest matchers are dropped entirely in favor of custom `DecisionMatcher` assertions.

**Maven Dependencies:**
```xml
<!-- v3.0.0 (remove all of these) -->
<dependency><groupId>io.sapl</groupId><artifactId>sapl-hamcrest</artifactId></dependency>
<dependency><groupId>io.sapl</groupId><artifactId>sapl-assertj</artifactId></dependency>
<dependency><groupId>io.sapl</groupId><artifactId>sapl-coverage-api</artifactId></dependency>
<dependency><groupId>io.sapl</groupId><artifactId>sapl-test-junit</artifactId></dependency>

<!-- v4.0.0 (single dependency replaces everything) -->
<dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-test</artifactId>
    <scope>test</scope>
</dependency>
```

**Java API -- Complete Redesign:**

| v3.0.0 | v4.0.0 |
|--------|--------|
| `new SaplUnitTestFixture("name")` | `SaplTestFixture.createSingleTest()` |
| `new SaplIntegrationTestFixture("folder")` | `SaplTestFixture.createIntegrationTest()` |
| `fixture.constructTestCaseWithMocks()` | Direct chaining on fixture |
| `givenFunction("name", Val.of("x"))` | `givenFunction("name", args(), Value.of("x"))` |
| `givenAttribute("name", Val.of("x"))` | `givenEnvironmentAttribute("mockId", "name", args(), Value.of("x"))` |
| `.when(subscription)` | `.whenDecide(subscription)` |
| `.thenAttribute("name", value)` | `.thenEmit("mockId", value)` |
| `registerPIP(...)` | `withPolicyInformationPoint(...)` |
| `registerFunctionLibrary(...)` | `withFunctionLibrary(...)` |
| `registerVariable("key", val)` | `givenVariable("key", value)` |
| `io.sapl.test.Imports` | `io.sapl.test.Matchers` |
| Hamcrest matchers | `DecisionMatcher` (`isPermit()`, `isDeny()`, etc.) |
| (none) | `givenSecret("name", Value.of("x"))` **NEW** |
| (none) | `withBundle("path.saplbundle")` **NEW** |

Key conceptual change: Attribute mocks now require a **mock ID** string. The mock ID is used to reference the mock in `then` blocks for emitting subsequent values, decoupling the test wiring from the attribute's SAPL name:

```java
// v3.0.0 -- attribute referenced by SAPL name everywhere
.givenAttribute("time.now", Val.of("2025-01-06"))
.when(subscription)
.thenAttribute("time.now", Val.of("2025-01-07"))

// v4.0.0 -- mock ID decouples test wiring from attribute name
.givenEnvironmentAttribute("timeMock", "time.now", args(), Value.of("2025-01-06"))
.whenDecide(subscription)
.thenEmit("timeMock", Value.of("2025-01-07"))
```

**Full Java Test Example:**

```java
// v3.0.0
var fixture = new SaplUnitTestFixture("patientAccessPolicy");
fixture.registerFunctionLibrary(TemporalFunctionLibrary.class);
fixture.constructTestCaseWithMocks()
    .givenFunction("time.dayOfWeek", Val.of("MONDAY"))
    .givenAttribute("patient.treatingDoctor", Val.of("dr_smith"))
    .when(AuthorizationSubscription.of("dr_smith", "read", "patient_123"))
    .expectPermit()
    .verify();

// v4.0.0
SaplTestFixture.createSingleTest()
    .withPolicyFromResource("policies/patientAccessPolicy.sapl")
    .withFunctionLibrary(TemporalFunctionLibrary.class)
    .givenFunction("time.dayOfWeek", args(), Value.of("MONDAY"))
    .givenEnvironmentAttribute("doctorMock", "patient.treatingDoctor",
        args(), Value.of("dr_smith"))
    .whenDecide(AuthorizationSubscription.of("dr_smith", "read", "patient_123"))
    .expectPermit()
    .verify();
```

**Test DSL (`.sapltest`) -- Near-Complete Rewrite:**

The test DSL syntax has changed substantially. The overall structure (requirement/scenario blocks) is preserved, but the internal syntax aligns with the SAPL 4.0 grammar changes:

| v3.0.0 DSL | v4.0.0 DSL |
|------------|------------|
| `policy "name"` / `set "name"` | `document "name"` (unified for both) |
| `policies "a", "b"` | `documents "a", "b"` |
| `pdp combining-algorithm deny-overrides` | `priority deny or abstain errors propagate` |
| `pdp variables {...}` | `variables {...}` (no `pdp` prefix) |
| `function "time.dayOfWeek" of (any) maps to "x"` | `function time.dayOfWeek(any) maps to "x"` (unquoted, parens) |
| `function "f" maps to "x" is called once` | `function f() maps to "x"` + separate `verify` block |
| `attribute "time.now" emits "x"` | `attribute "mockId" <time.now> emits "x"` (mock ID + angle brackets) |
| In `then`: `attribute "time.now" emits "y"` | In `then`: `attribute "mockId" emits "y"` (reference by mock ID) |
| `notApplicable` | `not-applicable` |
| `virtual-time` | Removed |
| `with timing "PT1S"` | Removed |
| `pip "name"` / `static-pip "name"` | Removed from DSL (register via Java `getFixtureRegistrations()`) |

New DSL directives: `secrets {...}`, `configuration "path"`, `pdp-configuration "path"`.

**Full DSL Test Example:**

```
-- v3.0.0
requirement "Patient Access Control" {
    given
        - policy "patientAccessPolicy"
        - function "time.dayOfWeek" maps to "MONDAY" is called once
        - attribute "patient.treatingDoctor" emits "dr_smith"
        - pip "patientPip"

    scenario "doctor can read patient record"
        when subject "dr_smith" attempts action "read" on resource "patient_123"
        expect permit;

    scenario "nurse denied admin access"
        when subject "nurse_jane" attempts action "admin" on resource "patient_123"
        expect deny;
}

-- v4.0.0
requirement "Patient Access Control" {

    given
        - document "patientAccessPolicy"
        - function time.dayOfWeek() maps to "MONDAY"
        - attribute "doctorMock" <patient.treatingDoctor> emits "dr_smith"

    scenario "doctor can read patient record"
        when "dr_smith" attempts "read" on "patient_123"
        expect permit
        verify
            - function time.dayOfWeek() is called once;

    scenario "nurse denied admin access"
        when "nurse_jane" attempts "admin" on "patient_123"
        expect deny;
}
```

**Streaming Test Pattern (v4.0.0):**

```
requirement "Time-based Access" {
    given
        - document "timePolicy"
        - attribute "timeMock" <time.now> emits "2025-01-06T09:00:00Z"

    scenario "access revoked after hours"
        when "user" attempts "read" on "doc"
        expect permit
        then
            - attribute "timeMock" emits "2025-01-06T23:00:00Z"
        expect deny;
}
```

**JUnit Adapter Changes:**
- Import moved from `io.sapl.test.junit.JUnitTestAdapter` (in `sapl-test-junit`) to same package name but in `sapl-test` module
- `ImportType` enum moved from `io.sapl.test.grammar.sapltest.ImportType` (Xtext generated) to `io.sapl.test.junit.ImportType`
- New overridable methods: `getDefaultCombiningAlgorithm()`, `getPolicyDirectories()`

**Coverage Format:**
- File-based hit recording (separate directories per hit type) replaced by single NDJSON file: `target/sapl-coverage/coverage.ndjson`
- New `branchCoverageRatio` parameter in Maven plugin
- SonarQube report generation moved from JAXB to StAX
- Coverage data recorded via `CoverageAccumulator` hooking into PDP's `coverageStream`

---

## New Features

### Architecture Redesign

| Aspect | 3.x | 4.0.0 |
|--------|-----|-------|
| Evaluation Model | Runtime AST interpretation | Compile-time voter composition |
| Grammar Technology | Xtext/EMF/Guice | ANTLR4 (lightweight, no EMF/Guice) |
| Vote Structure | Decision only | Decision + trace + outcome + errors |
| Combining Algorithms | Runtime aggregation | Compiled stratified evaluation |
| Attribute Handling | Basic PIP invocation | Reactive streams with caching + hot-swap |
| Error Handling | Exceptions | Error values (never throws) |
| Type System | `Val` (Jackson JsonNode wrapper) | Sealed `Value` interface (type-safe, no Jackson) |

### Secrets Management (NEW)

v3.0.0 had no secrets management. Credentials for external services (HTTP APIs, MQTT brokers) had to be embedded in policy variables or hardcoded in policies. v4.0.0 introduces a comprehensive secrets system with strict separation of concerns.

**Three secrets sources, with precedence:**

1. **pdp.json `secrets` section** -- operator-configured, highest priority. Operators control credentials.
2. **Policy request settings** -- `secretsKey` field selects named credentials from pdp.json.
3. **Subscription `secrets` field** -- per-request secrets from the PEP (e.g., JWT bearer token).

```json
{
  "algorithm": { ... },
  "variables": {},
  "secrets": {
    "http": {
      "weather-api": { "headers": { "X-API-Key": "abc123" } },
      "headers": { "Authorization": "Bearer default-token" }
    },
    "mqtt": {
      "production": { "username": "prod-user", "password": "prod-secret" },
      "staging": { "username": "stage-user", "password": "stage-secret" }
    },
    "traccar": { "token": "...", "userName": "admin", "password": "..." }
  }
}
```

**How secrets reach PIPs -- the `AttributeAccessContext`:**

All PIP methods that need configuration or credentials now receive an `AttributeAccessContext` as their **first parameter**:

```java
public record AttributeAccessContext(
    @NonNull ObjectValue variables,          // from pdp.json "variables"
    @NonNull ObjectValue pdpSecrets,         // from pdp.json "secrets" (operator)
    @NonNull ObjectValue subscriptionSecrets) // from AuthorizationSubscription.secrets (application)
```

This is the mechanism by which secrets flow from the subscription and pdp.json into PIP methods. The `AuthorizationSubscription` record now has 5 fields:

```java
public record AuthorizationSubscription(
    Value subject, Value action, Value resource, Value environment,
    ObjectValue secrets)  // NEW -- per-request secrets from PEP
```

**Impact on PIP method signatures (BREAKING):**

Every PIP method that accesses external services has changed signature. The old pattern of `Flux<Val> method(Val entity, @Text Val arg)` is replaced by typed parameters with `AttributeAccessContext` as the first parameter:

```java
// v3.0.0 -- HTTP PIP
@EnvironmentAttribute
Flux<Val> get(Val requestSettings) { ... }

@Attribute
Flux<Val> get(Val resourceUrl, Val requestSettings) { ... }

// v4.0.0 -- HTTP PIP
@EnvironmentAttribute
Flux<Value> get(AttributeAccessContext ctx, ObjectValue requestSettings) { ... }

@Attribute
Flux<Value> get(AttributeAccessContext ctx, TextValue resourceUrl, ObjectValue requestSettings) { ... }
```

The HTTP PIP uses `ctx.pdpSecrets()` and `ctx.subscriptionSecrets()` internally to merge headers with three-tier precedence: pdp secrets (operator) > policy headers > subscription secrets. Policies reference named credential sets via `secretsKey`:

```
policy "check weather"
permit
  var request = { "baseUrl": "https://api.weather.com",
                  "path": "/v1/current",
                  "secretsKey": "weather-api" };
  <http.get(request)>.temperature > 0;
```

The JWT PIP reads tokens exclusively from `ctx.subscriptionSecrets()` and public key configuration from `ctx.variables()`. The MQTT and Traccar PIPs follow the same pattern with their respective `secrets.mqtt` and `secrets.traccar` sections.

**Note:** Not all PIPs use `AttributeAccessContext`. The Time PIP and X509 PIP have no need for secrets or variables, so their methods do not take the context parameter. Custom PIPs that do not need secrets can omit it.

**Security controls:**
- Secrets auto-redacted in `toString()` and logging for `AuthorizationSubscription`, `AttributeAccessContext`, `PdpData`
- pdp.json secrets (operator) always override subscription secrets (application)
- Fail-closed when named `secretsKey` not found -- no headers contributed
- JWT token injection requires explicit opt-in (`io.sapl.jwt.inject-token=true`)
- `secretsKey` metadata is stripped before HTTP requests are sent

### Remote Bundle Source (NEW)

Fetch policy bundles from remote HTTP endpoints with ETag-based change detection:

```yaml
io.sapl.pdp.embedded:
  pdp-config-type: REMOTE_BUNDLES
  remote-bundles:
    base-url: https://pap.example.com/bundles
    pdp-ids: [production, staging]
    mode: POLLING  # or LONG_POLL
    poll-interval: 30s
    auth-header-name: Authorization
    auth-header-value: "Bearer <token>"
    first-backoff: 500ms
    max-backoff: 5s
```

### Policy Bundles and Multi-Tenant PDP (NEW -- Major Feature)

v3.0.0 supported only two policy data source modes: `RESOURCES` (classpath) and `FILESYSTEM` (single directory). Every PDP instance served exactly one policy set. Running multiple tenants required deploying multiple PDP instances.

v4.0.0 introduces **policy bundles** and **multi-tenant PDP support**, enabling a single `sapl-node` instance (or embedded PDP) to serve multiple independent tenants, each with their own policies, combining algorithm, variables, and secrets.

**What is a bundle?** A `.saplbundle` file is a self-contained package containing a `pdp.json` configuration and all `.sapl` policy files for one tenant. Bundles can be optionally signed with Ed25519 for integrity verification. The bundle format enables:
- Atomic policy deployment (all-or-nothing updates)
- Cryptographic integrity verification
- Transport-agnostic distribution (file system, HTTP, CI/CD artifact)
- Per-tenant versioning via `configurationId` in `pdp.json`

**New data source modes (`io.sapl.pdp.embedded.pdp-config-type`):**

| Mode | Description |
|------|-------------|
| `RESOURCES` | Classpath resources (unchanged from v3.0.0) |
| `DIRECTORY` | Single directory with pdp.json + .sapl files (renamed from `FILESYSTEM`) |
| `MULTI_DIRECTORY` | **NEW.** Each subdirectory is an independent tenant. Directory name = `pdpId`. Each subdirectory contains its own `pdp.json` and `.sapl` files. Hot-reload detects file changes per tenant. |
| `BUNDLES` | **NEW.** Each `.saplbundle` file in the directory is a tenant. Filename (without extension) = `pdpId`. Supports Ed25519 signature verification per tenant. |
| `REMOTE_BUNDLES` | **NEW.** Bundles fetched from a remote HTTP server. Uses ETag-based change detection for efficient polling. Supports long-polling mode. Each `pdpId` maps to a remote bundle endpoint. |

**Multi-tenant routing in `sapl-node`:** When a client authenticates to `sapl-node`, the user entry specifies a `pdpId` that routes the request to the correct tenant's policy set:

```yaml
io.sapl.node:
  defaultPdpId: "default"
  rejectOnMissingPdpId: false
  users:
    - id: "hospital-a"
      pdpId: "hospital-a"
      apiKey: "$argon2id$v=19$..."
    - id: "hospital-b"
      pdpId: "hospital-b"
      apiKey: "$argon2id$v=19$..."
```

For OAuth2, the `pdpId` is extracted from a configurable JWT claim (`io.sapl.node.oauth.pdpIdClaim`, default: `"sapl_pdp_id"`).

**`MULTI_DIRECTORY` example file structure:**

```
tenants/
  hospital-a/
    pdp.json          # hospital-a's combining algorithm, variables, secrets
    patient-access.sapl
    emergency-override.sapl
  hospital-b/
    pdp.json          # hospital-b's combining algorithm, variables, secrets
    strict-access.sapl
```

```yaml
io.sapl.pdp.embedded:
  pdp-config-type: MULTI_DIRECTORY
  config-path: tenants
  policies-path: tenants
```

**`BUNDLES` example with signing:**

```yaml
io.sapl.pdp.embedded:
  pdp-config-type: BUNDLES
  config-path: bundles
  policies-path: bundles
  bundle-security:
    keys:
      prod-key: "MCowBQYDK2VwAyEA..."      # Ed25519 public key (base64)
    tenants:
      hospital-a: ["prod-key"]              # hospital-a bundles must be signed by prod-key
      hospital-b: ["prod-key"]
    unsigned-tenants: ["staging"]            # staging bundles may be unsigned
    allow-unsigned: false                    # global default: signatures required
    accept-risks: false                     # must be true alongside allow-unsigned
```

**`REMOTE_BUNDLES` example:**

```yaml
io.sapl.pdp.embedded:
  pdp-config-type: REMOTE_BUNDLES
  remote-bundles:
    base-url: https://pap.example.com/bundles
    pdp-ids: [hospital-a, hospital-b]
    mode: POLLING                            # or LONG_POLL
    poll-interval: 30s
    long-poll-timeout: 30s
    auth-header-name: Authorization
    auth-header-value: "Bearer <token>"
    first-backoff: 500ms
    max-backoff: 5s
    pdp-id-poll-intervals:                   # per-tenant polling intervals
      hospital-a: 10s                        # critical tenant polls faster
```

Health states for remote bundles: DOWN (no bundle fetched yet at startup), UP (bundle loaded, remote reachable), DEGRADED (bundle loaded, remote unreachable -- continues serving last-known bundle).

**Bundle CLI commands (`sapl-node`):**

```bash
# Create a signed bundle
java -jar sapl-node.jar bundle create \
  -i policies/hospital-a/ \
  -k keys/prod-key.pem --key-id prod-key \
  -o bundles/hospital-a.saplbundle

# Create an unsigned bundle (for development)
java -jar sapl-node.jar bundle create \
  -i policies/staging/ \
  -o bundles/staging.saplbundle

# Generate credentials for a client
java -jar sapl-node.jar generate --basic --id "hospital-a" --pdp-id "hospital-a"
java -jar sapl-node.jar generate --api-key --id "hospital-b" --pdp-id "hospital-b"
```

### Health Metrics & Observability (NEW)

- **PDP Health Indicator**: Spring Boot Actuator health endpoint (`PdpHealthIndicator`)
  - UP when all PDPs loaded, DOWN on error, warning on stale
- **Prometheus Metrics**: Decision metrics via Micrometer (`MetricsVoteInterceptor`)
- **Kubernetes Probes**: Readiness/liveness probe support in sapl-node
- **Subscription Lifecycle Logging**: Log new/ended subscriptions

```yaml
io.sapl.pdp.embedded:
  metrics-enabled: true
  print-subscription-events: true
  print-unsubscription-events: true

management:
  endpoint.health:
    show-details: when-authorized
    probes.enabled: true
  health:
    livenessstate.enabled: true
    readinessstate.enabled: true
  endpoints.web.exposure.include: health,info,prometheus
```

### JWT Auto-Injection (NEW)

Automatic injection of JWT bearer tokens into subscription secrets:

```yaml
io.sapl.jwt:
  inject-token: false  # must explicitly enable
  secrets-key: "jwt"   # key name in subscription secrets
```

### Combining Algorithm Enhancements

**New Voting Modes:**

| Mode | Behavior |
|------|----------|
| `FIRST` | First applicable policy wins (policy set level only, blocked at PDP level) |
| `PRIORITY_DENY` | Any DENY vote wins |
| `PRIORITY_PERMIT` | Any PERMIT vote wins |
| `UNANIMOUS` | All must agree on entitlement, constraints merged |
| `UNANIMOUS_STRICT` | All must return identical decisions |
| `UNIQUE` | Exactly one policy must be applicable |

**Stratified Evaluation (Performance):**
1. Foldable Votes -- static decisions evaluated at compile-time
2. Pure Policies -- synchronous evaluation for non-streaming policies
3. Stream Policies -- reactive evaluation with lazy chaining

### PolicyDecisionPoint Interface - Enhanced

```java
Flux<AuthorizationDecision> decide(AuthorizationSubscription sub);           // Abstract
Mono<AuthorizationDecision> decideOnce(AuthorizationSubscription sub);       // Default (.next())
AuthorizationDecision decideOnceBlocking(AuthorizationSubscription sub);     // NEW synchronous
Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription); // Now default
Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription);  // Now default
```

### Function Libraries Expansion

v3.0.0 had 6 built-in libraries (`standard`, `filter`, `temporal`, `logging`, `sanitization` as extension, `jwt` as extension) plus 3 extension libraries (`geo`, `mqtt`, `traccar`). v4.0.0 ships **32 built-in libraries** plus 3 extensions -- a massive expansion that eliminates the need for custom function code in many common scenarios.

All function library methods now use typed `Value` subtypes instead of `Val`:

```java
// v3.0.0
@Function
public static Val blacken(@Text Val input, @Number Val discloseLeft) { ... }

// v4.0.0
@Function
static Value blacken(Value... parameters) { ... }
```

Function library methods are **stateless** and do **not** receive `AttributeAccessContext` (unlike PIPs).

**Complete Library Inventory:**

| # | SAPL Namespace | Class | Functions | Status |
|---|----------------|-------|-----------|--------|
| 1 | `standard` | `StandardFunctionLibrary` | `length`, `toString`, `onErrorMap` | Existed (signatures changed) |
| 2 | `filter` | `FilterFunctionLibrary` | `blacken`, `replace`, `remove` | Existed (signatures changed) |
| 3 | `time` | `TemporalFunctionLibrary` | 50+ functions (see below) | Existed (massively expanded) |
| 4 | `string` | `StringFunctionLibrary` | `toLowerCase`, `toUpperCase`, `equalsIgnoreCase`, `trim`, `trimStart`, `trimEnd`, `isBlank`, `contains`, `startsWith`, `endsWith`, `length`, `isEmpty`, `substring`, `substringRange`, `indexOf`, `lastIndexOf`, `join`, `concat`, `replace`, `replaceFirst`, `leftPad`, `rightPad`, `repeat`, `reverse` (24) | **NEW** |
| 5 | `array` | `ArrayFunctionLibrary` | `concatenate`, `difference`, `union`, `toSet`, `intersect`, `containsAny`, `containsAll`, `containsAllInOrder`, `sort`, `flatten`, `size`, `reverse`, `isSet`, `isEmpty`, `head`, `last`, `max`, `min`, `sum`, `multiply`, `avg`, `median`, `range`, `rangeStepped`, `crossProduct`, `zip` (25+) | **NEW** |
| 6 | `object` | `ObjectFunctionLibrary` | `keys`, `values`, `size`, `hasKey`, `isEmpty` (5) | **NEW** |
| 7 | `json` | `JsonFunctionLibrary` | `jsonToVal`, `valToJson` (2) | **NEW** |
| 8 | `math` | `MathFunctionLibrary` | `min`, `max`, `abs`, `ceil`, `floor`, `round`, `pow`, `sqrt`, `sign`, `clamp`, `randomInteger`, `randomIntegerSeeded`, `randomFloat`, `randomFloatSeeded`, `pi`, `e`, `log`, `log10`, `logb` (19) | **NEW** |
| 9 | `numeral` | `NumeralFunctionLibrary` | `fromHex`, `fromBinary`, `fromOctal`, `toHex`, `toBinary`, `toOctal`, `toHexPrefixed`, `toBinaryPrefixed`, `toOctalPrefixed`, `toHexPadded`, `toBinaryPadded`, `toOctalPadded`, `isValidHex`, `isValidBinary`, `isValidOctal` (15) | **NEW** |
| 10 | `bitwise` | `BitwiseFunctionLibrary` | `bitwiseAnd`, `bitwiseOr`, `bitwiseXor`, `bitwiseNot`, `testBit`, `setBit`, `clearBit`, `toggleBit`, `bitCount`, `leftShift`, `rightShift`, `unsignedRightShift`, `rotateLeft`, `rotateRight`, `leadingZeros`, `trailingZeros`, `reverseBits`, `isPowerOfTwo` (18) | **NEW** |
| 11 | `units` | `UnitsFunctionLibrary` | `parse`, `parseBytes` (2) | **NEW** |
| 12 | `cidr` | `CidrFunctionLibrary` | `contains`, `expand`, `intersects`, `isValid`, `merge`, `isPrivateIpv4`, `isLoopback`, `isLinkLocal`, `isMulticast`, `isDocumentation`, `isCgnat`, `isBenchmark`, `isReserved`, `isBroadcast`, `isPublicRoutable`, `anonymizeIp`, `hashIpPrefix`, `getNetworkAddress`, `getBroadcastAddress`, `getAddressCount`, `getUsableHostCount`, `sameSubnet`, `canSubdivide`, ... (24+) | **NEW** |
| 13 | `graph` | `GraphFunctionLibrary` | `reachable`, `reachablePaths` (2) | **NEW** |
| 14 | `graphql` | `GraphQLFunctionLibrary` | `validateQuery`, `analyzeQuery`, `complexity`, `parseSchema` | **NEW** |
| 15 | `encoding` | `EncodingFunctionLibrary` | `base64Encode`, `base64Decode`, `base64DecodeStrict`, `isValidBase64`, `isValidBase64Strict`, `base64UrlEncode`, `base64UrlDecode`, `base64UrlDecodeStrict`, `isValidBase64Url`, `isValidBase64UrlStrict`, `hexEncode`, `hexDecode`, `isValidHex` (13) | **NEW** |
| 16 | `digest` | `DigestFunctionLibrary` | `sha256`, `sha384`, `sha512`, `sha3_256`, `sha3_384`, `sha3_512`, `md5`, `sha1` (9) | **NEW** |
| 17 | `mac` | `MacFunctionLibrary` | `hmacSha256`, `hmacSha384`, `hmacSha512`, `timingSafeEquals`, `isValidHmac` (5) | **NEW** |
| 18 | `signature` | `SignatureFunctionLibrary` | `isValidRsaSha256`, `isValidRsaSha384`, `isValidRsaSha512`, `isValidEcdsaP256`, `isValidEcdsaP384`, `isValidEcdsaP521`, `isValidEd25519` (7) | **NEW** |
| 19 | `keys` | `KeysFunctionLibrary` | Key parsing/management | **NEW** |
| 20 | `x509` | `X509FunctionLibrary` | `parseCertificate`, `extractSubjectDn`, `extractIssuerDn`, `extractCommonName`, `extractSerialNumber`, `extractNotBefore`, `extractNotAfter`, `extractFingerprint`, `matchesFingerprint`, `extractSubjectAltNames`, `hasDnsName`, `hasIpAddress`, `isValidAt` (13) | **NEW** |
| 21 | `csv` | `CsvFunctionLibrary` | `csvToVal`, `valToCsv` (2) | **NEW** |
| 22 | `xml` | `XmlFunctionLibrary` | `xmlToVal`, `valToXml` (2) | **NEW** |
| 23 | `yaml` | `YamlFunctionLibrary` | `yamlToVal`, `valToYaml` (2) | **NEW** |
| 24 | `toml` | `TomlFunctionLibrary` | `tomlToVal`, `valToToml` (2) | **NEW** |
| 25 | `patterns` | `PatternsFunctionLibrary` | `matchGlob`, `matchGlobWithoutDelimiters`, `escapeGlob`, `isValidRegex`, `findMatches`, `findMatchesLimited`, `findAllSubmatch`, `findAllSubmatchLimited`, `replaceAll`, `split`, `matchTemplate` (11) | **NEW** |
| 26 | `sanitize` | `SanitizationFunctionLibrary` | `assertNoSqlInjection`, `assertNoSqlInjectionStrict` | **Moved** from extension |
| 27 | `jsonschema` | `SchemaValidationLibrary` | `isCompliant`, `isCompliantWithExternalSchemas`, `validate`, `validateWithExternalSchemas` | **NEW** |
| 28 | `semver` | `SemVerFunctionLibrary` | `parse`, `isValid`, `compare`, `equals`, `isLower`, `isHigher`, `isLowerOrEqual`, `isHigherOrEqual`, `haveSameMajor`, `haveSameMinor`, `haveSamePatch`, `isCompatibleWith`, `isAtLeast`, `isAtMost`, `isBetween`, `isPreRelease`, `isStable`, `getMajor`, `getMinor`, `getPatch`, `satisfies`, `maxSatisfying`, `minSatisfying`, `coerce`, `diff` (24+) | **NEW** |
| 29 | `permissions` | `PermissionsFunctionLibrary` | `hasAll`, `hasAny`, `hasNone`, `hasExact`, `combine`, `combineAll`, `isSubsetOf`, `unixOwner`, `unixGroup`, `unixOther`, `unixMode`, `unixCanRead`, `unixCanWrite`, `unixCanExecute`, `posixRead`, `posixWrite`, `posixExecute`, `posixAll`, `posixNone`, `posixRW`, `posixRX`, ... (29+) | **NEW** |
| 30 | `reflect` | `ReflectionFunctionLibrary` | `isArray`, `isObject`, `isText`, `isNumber`, `isInteger`, `isBoolean`, `isNull`, `isUndefined`, `isDefined`, `isError`, `isEmpty`, `typeOf` (13) | **NEW** |
| 31 | `uuid` | `UuidFunctionLibrary` | `parse`, `random`, `seededRandom` (3) | **NEW** |
| 32 | `sapl` | `SaplFunctionLibrary` | `info` (1) | **NEW** |

**Temporal Library (`time`) -- Massively Expanded:**

The `time` function library grew from a handful of functions to 50+, organized by category:
- **Duration:** `durationOfSeconds`, `durationOfMinutes`, `durationOfHours`, `durationOfDays`, `durationFromISO`, `durationToISOCompact`, `durationToISOVerbose`
- **Comparison:** `before`, `after`, `between`, `timeBetween`
- **Arithmetic:** `plusDays`, `plusMonths`, `plusYears`, `minusDays`, `minusMonths`, `minusYears`, `plusNanos`, `plusMillis`, `plusSeconds`, `minusNanos`, `minusMillis`, `minusSeconds`
- **Epoch:** `epochSecond`, `epochMilli`, `ofEpochSecond`, `ofEpochMilli`
- **Extraction:** `weekOfYear`, `dayOfYear`, `dayOfWeek`, `dateOf`, `timeOf`, `hourOf`, `minuteOf`, `secondOf`
- **Boundaries:** `startOfDay`, `endOfDay`, `startOfWeek`, `endOfWeek`, `startOfMonth`, `endOfMonth`, `startOfYear`, `endOfYear`
- **Truncation:** `truncateToHour`, `truncateToDay`, `truncateToWeek`, `truncateToMonth`, `truncateToYear`
- **Zones/Offsets:** `toZone`, `toOffset`, `dateTimeAtOffset`, `dateTimeAtZone`, `offsetDateTime`, `offsetTime`, `timeAtOffset`, `timeInZone`
- **Parsing:** `localIso`, `localDin`, `timeAMPM`, `validUTC`, `validRFC3339`
- **Age:** `ageInYears`, `ageInMonths`

**Extension Libraries (separate Maven modules):**

| # | SAPL Namespace | Module | Functions |
|---|----------------|--------|-----------|
| 33 | `geo` | `geo-functions` | 30+ geospatial functions (`within`, `contains`, `intersects`, `distance`, `geodesicDistance`, `buffer`, `centroid`, `union`, `intersection`, `wktToGeoJSON`, `kmlToGeoJSON`, ...) |
| 34 | `traccar` | `geo-traccar` | Traccar GPS integration functions |
| 35 | `mqtt` | `mqtt-functions` | MQTT message functions |

**Removed:** `LoggingFunctionLibrary` (trace/debug/info/warn/error spy functions)

**Absorbed into core `sapl-pdp`:** JWT functions/PIP (was separate `pdp-extensions/jwt`), Sanitization functions (was `pdp-extensions/input-sanitization-functions`). No extra Maven dependency needed.

### PIPs -- Complete Inventory with Signatures

All core PIPs now return `Flux<Value>` instead of `Flux<Val>`. PIPs that access external services or configuration receive `AttributeAccessContext ctx` as the first parameter for secrets/variables access.

**`time` PIP** (environment attributes, no secrets needed):

| SAPL Syntax | Java Signature |
|-------------|----------------|
| `<time.now>` | `Flux<Value> now()` |
| `<time.now(1000)>` | `Flux<Value> now(NumberValue updateIntervalInMillis)` |
| `<time.systemTimeZone>` | `Flux<Value> systemTimeZone()` |
| `<time.nowIsAfter("...")>` | `Flux<Value> nowIsAfter(TextValue checkpoint)` |
| `<time.nowIsBefore("...")>` | `Flux<Value> nowIsBefore(TextValue time)` |
| `<time.nowIsBetween("...", "...")>` | `Flux<Value> nowIsBetween(TextValue start, TextValue end)` |
| `<time.localTimeIsAfter("...")>` | `Flux<Value> localTimeIsAfter(TextValue checkpoint)` |
| `<time.localTimeIsBefore("...")>` | `Flux<Value> localTimeIsBefore(TextValue checkpoint)` |
| `<time.localTimeIsBetween("...", "...")>` | `Flux<Value> localTimeIsBetween(TextValue start, TextValue end)` |
| `<time.toggle(5000, 3000)>` | `Flux<Value> toggle(NumberValue trueDurationMs, NumberValue falseDurationMs)` |

**`http` PIP** (environment + entity attributes, uses secrets):

| SAPL Syntax | Java Signature |
|-------------|----------------|
| `<http.get(request)>` | `Flux<Value> get(AttributeAccessContext ctx, ObjectValue requestSettings)` |
| `<http.post(request)>` | `Flux<Value> post(AttributeAccessContext ctx, ObjectValue requestSettings)` |
| `<http.put(request)>` | `Flux<Value> put(AttributeAccessContext ctx, ObjectValue requestSettings)` |
| `<http.patch(request)>` | `Flux<Value> patch(AttributeAccessContext ctx, ObjectValue requestSettings)` |
| `<http.delete(request)>` | `Flux<Value> delete(AttributeAccessContext ctx, ObjectValue requestSettings)` |
| `<http.websocket(request)>` | `Flux<Value> websocket(AttributeAccessContext ctx, ObjectValue requestSettings)` |
| `"url".<http.get(request)>` | `Flux<Value> get(AttributeAccessContext ctx, TextValue url, ObjectValue requestSettings)` |
| `"url".<http.post(request)>` | `Flux<Value> post(AttributeAccessContext ctx, TextValue url, ObjectValue requestSettings)` |
| (same pattern for put, patch, delete, websocket) | |

Header merge precedence: `ctx.pdpSecrets()` > policy `requestSettings.headers` > `ctx.subscriptionSecrets()`. Named credential sets via `secretsKey` in request settings.

**`jwt` PIP** (NEW, environment attributes, uses secrets):

| SAPL Syntax | Java Signature |
|-------------|----------------|
| `<jwt.token>` | `Flux<Value> token(AttributeAccessContext ctx)` |
| `<jwt.token("keyName")>` | `Flux<Value> token(AttributeAccessContext ctx, TextValue secretsKeyName)` |

Reads JWT from `ctx.subscriptionSecrets()`, validates against public keys from `ctx.variables().get("jwt")`. Reactively transitions validity states (IMMATURE -> VALID -> EXPIRED), triggering automatic policy re-evaluation. Supports RSA, EC, HMAC, and EdDSA signature algorithms.

**`x509` PIP** (NEW, entity attributes, no secrets needed):

| SAPL Syntax | Java Signature |
|-------------|----------------|
| `certPem.<x509.isCurrentlyValid>` | `Flux<Value> isCurrentlyValid(TextValue certPem)` |
| `certPem.<x509.isExpired>` | `Flux<Value> isExpired(TextValue certPem)` |

Reactively monitors certificate validity with zero-polling transitions at notBefore/notAfter boundaries.

**Extension PIPs:**

| PIP | SAPL Syntax | Notes |
|-----|-------------|-------|
| `mqtt` | `"topic".<mqtt.messages>`, `"topic".<mqtt.messages(qos)>` | Credentials from `secrets.mqtt` via `AttributeAccessContext` |
| `traccar` | `<traccar.server>`, `deviceId.<traccar.device>`, `deviceId.<traccar.position>`, etc. | Credentials from `secrets.traccar` via `AttributeAccessContext` |

### Attribute Broker (NEW -- Major Feature)

In v3.0.0, every attribute access in a policy triggered an independent call to the corresponding PIP. If multiple policies (or the same policy evaluated for different subscriptions) accessed the same external attribute, each access opened its own connection and fetched its own data. There was no caching, no connection reuse, and no way to share attribute streams across evaluations.

v4.0.0 introduces the **Attribute Broker** (`CachingAttributeStreamBroker`), which sits between the policy evaluation engine and the PIPs. It is one of the most significant new features for production deployments, particularly in environments with expensive external attribute lookups (HTTP APIs, MQTT brokers, databases).

**What the broker provides:**

- **Stream caching with grace periods**: When a policy requests an attribute that was recently fetched, the broker returns the cached stream instead of calling the PIP again. A configurable grace period (default 3000ms) keeps streams alive briefly after the last subscriber disconnects, so a rapid re-subscription (e.g., during policy re-evaluation) reuses the existing connection instead of opening a new one.
- **Connection reuse via multicast**: Attribute streams are multicast with `replay(1)`, meaning multiple concurrent policy evaluations sharing the same attribute see the same underlying reactive stream. For streaming PIPs (HTTP SSE, MQTT, WebSocket), this means a single connection serves all subscribers.
- **Bounded backpressure buffering** (128 elements): Prevents unbounded memory growth when consumers are slower than producers.
- **Hot-swap capability**: PIPs can be replaced at runtime without restarting the PDP. The broker handles atomic PIP registration (all-or-nothing) and re-wires attribute streams to the new PIP.
- **Configurable from policies**: Attribute broker parameters (timeouts, cache behavior) can be configured from policy variables and per-attribute-access via the new `[options]` bracket syntax: `<pip.attr(param)[options]>`.

**InMemoryAttributeRepository:**
- TTL support with configurable timeout strategies: `REMOVE` (attribute disappears) or `BECOME_UNDEFINED` (attribute becomes `UndefinedValue`)
- Thread-safe with ConcurrentHashMap
- Enables push-based attribute injection from application code into the PDP

### Policy Evaluation Reporting

**Vote-Based Tracing:**

```java
public record Vote(
    AuthorizationDecision authorizationDecision,
    List<ErrorValue> errors,
    List<AttributeRecord> contributingAttributes,
    List<Vote> contributingVotes,
    VoterMetadata voter,
    Outcome outcome)
```

Three output formats: Full Trace (hierarchical JSON), JSON Report, Text Report.

**VoteInterceptor Framework:**
- Priority-ordered interceptor chain
- Replaces `TracedDecisionInterceptor`

### Spring Boot Enhancements

- All enforcement annotations (`@PreEnforce`, `@PostEnforce`, `@EnforceTillDenied`, `@EnforceDropWhileDenied`, `@EnforceRecoverableIfDenied`, `@QueryEnforce`) gain `secrets()` attribute
- Spring Data: MongoDB reactive + R2DBC with `@QueryEnforce` and dynamic WHERE clause injection
- Jackson 3.x module registration via `SaplJacksonModule` beans (auto-discovered by Spring Boot 4)

### Eclipse Plugin Removed -- Universal LSP Server Instead

The entire `sapl-eclipse-plugin` directory (6 Eclipse/Tycho modules: `sapl-eclipse-ui`, `sapl-test-eclipse-ui`, `sapl-eclipse-target`, `sapl-eclipse-thirdparty`, `sapl-eclipse-feature`, `sapl-eclipse-repository`) has been **removed**. The Eclipse-specific IDE integration is replaced by the `sapl-language-server` module, which implements the Language Server Protocol (LSP).

LSP is an editor-agnostic protocol supported by VS Code, Neovim, Emacs, IntelliJ, Sublime Text, and virtually every modern editor. This means SAPL policy editing, validation, code completion, and semantic highlighting now work in **any LSP-capable editor** instead of being tied to Eclipse.

The language server itself has been rewritten from Xtext-based LSP to a native ANTLR4-based implementation, which is lighter-weight and starts faster.

**Editor-specific integrations provided:**
- Vaadin-based SAPL editor component (`sapl-vaadin-editor`) for web applications
- Neovim LSP integration (`sapl-nvim.nix`) for NixOS-based Neovim setups

### Editor Feature Enhancements

- Code coverage visualization in SAPL editor
- Merge view for sapl-test editor and JSON editor
- NPE fix in code completion
- Select-text-deleted bug fix

### SAPL Playground (NEW MODULE)

Interactive policy testing application with:
- Permalink sharing for playground state
- JSON Graph visualization
- Follow box and subscription resizing
- Built-in example policies

### Documentation Generator (NEW MODULE)

`sapl-documentation-generator` for generating SAPL function/PIP documentation from annotations.

---

## Security Fixes

- **CVE-2025-53864** (nimbus-jose-jwt DoS via deeply nested JSON, CVSS 5.8): Fixed by upgrading nimbus-jose-jwt to 10.7
- **CVE-2025-48924** (Apache Commons Lang DoS via `ClassUtils.getClass` recursion, CVSS 5.3): Fixed by pinning commons-lang3 to 3.18.0 and replacing `StringUtils.indexOf()` with `Strings.CS.indexOf()` in `SaplConditionOperation`
- **SpEL Interpreter Isolation**: Fixed risk of confusing SpEL interpreters between SAPL and Spring in `ReactiveSaplMethodSecurityConfiguration`. Prevents potential authorization bypass.
- **API Key Authentication Rework**: Complete rework of `ApiKeyAuthenticationToken` with role-based access control
- **X509 Library Impurity Fix**: X509 functions with side effects split into pure `X509FunctionLibrary` + stateful `X509PolicyInformationPoint`
- **Configuration Integrity**: Auto-generated configuration IDs include `sha256:<hash>` for audit verification
- **TOCTOU Mitigation**: Atomic file read + size validation in `PDPConfigurationLoader` (max 1000 files, 10MB total)
- **Insecure SSL Warning**: `withUnsecureSSL()` on remote PDP has prominent warnings

---

## Behavioral Changes

These changes may silently affect authorization outcomes:

1. **Empty config rejected at startup**: v3.0.0 silently used default combining algorithm when `pdp.json` was absent. v4.0.0 requires `pdp.json` with a valid `algorithm` field for all source types (Directory, Resources, Bundle). Missing or invalid `pdp.json` fails at startup.
2. **Constraint handler matching**: `TypeSupport.java` updated for more robust matching. Some handlers that previously matched may not match (or vice versa).
3. **Deletion detection**: Policy file deletion now detected differently in `DirectoryPDPConfigurationSource`. Affects hot-reload timing.
4. **Timeout handling**: `TimeOutWrapper` rewritten to use `Sinks.many().unicast()` with explicit subscription management. Timeout before first value now emits `Value.UNDEFINED` but continues emitting subsequent values from the source (rather than terminating). Empty source flux emits `Value.UNDEFINED` immediately. Early completion terminates without waiting for timeout.
5. **X509 impurity**: Functions that had side effects split into function + PIP. Policies using stateful X509 operations via function calls need PIP attributes instead.
6. **Digest function names**: Function names in `DigestFunctionLibrary` changed.
7. **Secrets masking**: `toString()` on `AuthorizationSubscription` and `AttributeAccessContext` now masks secrets. Affects log output.
8. **Node startup robustness**: `DynamicPolicyDecisionPoint` error handling improved for invalid configurations at startup.

---

## Migration Guide

### Step 1: Environment

- [ ] Update JDK to 21+
- [ ] Update Spring Boot to 4.0.x
- [ ] Update Jackson imports: `com.fasterxml.jackson.*` -> `tools.jackson.*`

### Step 2: Maven Dependencies

```xml
<!-- Replace all of these... -->
<artifactId>sapl-pdp-api</artifactId>         <!-- or sapl-extensions-api -->
<artifactId>sapl-pdp-embedded</artifactId>     <!-- or sapl-lang -->
<artifactId>sapl-spring-pdp-embedded</artifactId>
<artifactId>sapl-spring-pdp-remote</artifactId>
<artifactId>sapl-spring-security</artifactId>
<artifactId>sapl-spring-data-*</artifactId>
<artifactId>jwt</artifactId>
<artifactId>input-sanitization-functions</artifactId>
<artifactId>sapl-hamcrest</artifactId>
<artifactId>sapl-assertj</artifactId>
<artifactId>sapl-coverage-api</artifactId>
<artifactId>sapl-test-junit</artifactId>

<!-- ...with these -->
<artifactId>sapl-api</artifactId>
<artifactId>sapl-pdp</artifactId>
<artifactId>sapl-spring-boot-starter</artifactId>
<artifactId>sapl-test</artifactId>            <!-- test scope -->
<!-- JWT and sanitization are now built into sapl-pdp -->
```

### Step 3: Policy Files (`.sapl`)

- [ ] Remove all `where` keywords; add `;` to preceding target expression
- [ ] Replace combining algorithm syntax in policy sets (see mapping table above)
- [ ] Replace wildcard imports (`import X.*`) with explicit function imports
- [ ] Replace library alias imports (`import X as alias`) with `import X.func as alias`
- [ ] Add `@` target to extended filter statements if missing

### Step 4: pdp.json

- [ ] Convert `"algorithm": "STRING"` to nested object format with `votingMode`, `defaultDecision`, `errorHandling`
- [ ] Move any credentials from `variables` to new `secrets` section
- [ ] Add `configurationId` if using BUNDLES mode

### Step 5: Java Code

- [ ] Replace `Val` with `Value` types throughout
- [ ] Update `AuthorizationSubscription` usage to record accessors (`.subject()` not `.getSubject()`)
- [ ] Update `AuthorizationDecision` usage (no more Optional, no more with*() methods)
- [ ] Update PIP annotations: `io.sapl.api.pip.*` -> `io.sapl.api.attributes.*`
- [ ] Add `AttributeAccessContext` as first parameter to `@EnvironmentAttribute` methods
- [ ] Replace `Val` parameter types with concrete `Value` subtypes
- [ ] Remove validation annotations (`@Array`, `@Bool`, `@Number`, etc.)
- [ ] Update Spring Data imports (see package relocation table)
- [ ] Replace builder service references with `AuthorizationSubscriptionBuilderService`

### Step 6: Application Properties

- [ ] Rename `FILESYSTEM` to `DIRECTORY` for `pdp-config-type`
- [ ] Add `io.sapl.pdp.remote.enabled=true` if using remote PDP
- [ ] Rename `io.sapl.server-lt.*` to `io.sapl.node.*` (if running server)
- [ ] Migrate flat key/secret to unified `users` list (if running server)
- [ ] Remove all RSocket configuration
- [ ] Replace Infinispan with Caffeine cache configuration

### Step 7: Test Code

- [ ] Replace test fixture classes (see test framework section above)
- [ ] Replace `Val.of(...)` with `Value.of(...)`
- [ ] Update attribute mocking to use mock ID system
- [ ] Update `.sapltest` DSL files (see DSL migration table above)
- [ ] Update JUnit adapter imports

### Step 8: Verify

- [ ] Grep for `where` in all `.sapl` files -- should be zero occurrences
- [ ] Grep for `com.fasterxml.jackson` -- should be zero occurrences
- [ ] Grep for `Val.of\|Val.TRUE\|Val.FALSE\|Val.UNDEFINED` -- should be zero occurrences
- [ ] Run full test suite: `mvn verify -q`
- [ ] Run integration tests: `mvn verify -Pit -q`

---

## Dependency Version Changes

| Dependency | v3.0.0 | v4.0.0 |
|------------|--------|--------|
| JDK | 17 | **21** |
| Spring Boot | 3.5.3 | **4.0.2** |
| Jackson | 2.x (`com.fasterxml`) | **3.x (`tools.jackson`)** |
| JUnit Jupiter | 5.11.3 | **6.0.2** |
| Vaadin | 24.6.12 | **25.0.4** |
| OkHttp | 4.12.0 | **5.3.2** |
| Parser Technology | Xtext 2.37.0 / EMF / Guice | **ANTLR4 4.13.2** |
| Lombok | 1.18.38 | 1.18.42 |
| AssertJ | 3.26.3 | 3.27.7 |
| json-schema-validator | 1.5.4 | **3.0.0** |
| JaCoCo | 0.8.12 | 0.8.14 |
| Nullness Annotations | reactor.util.annotation | **JSpecify 1.0.0** |
| SBOM Format | SPDX | **CycloneDX** |

**Added:** PicoCLI 4.7.7, classgraph, nimbus-jose-jwt 10.7, BouncyCastle 1.83, semver4j 6.0.0, ipaddress 5.5.1, Testcontainers 2.0.3

**Removed:** Xtext/EMF/Guice, RSocket, Hamcrest matchers (spotify), reactor-extra, Infinispan, Tycho/Eclipse build

---

## Compatibility Matrix

| Component | Minimum | Recommended | Maximum |
|-----------|---------|-------------|---------|
| JDK | 21 | 21 | 25 |
| Spring Boot | 4.0.x | 4.0.2 | 4.0.x |
| Vaadin | 25.x | 25.0.4 | 25.x |
| Jackson | 3.x | (managed by Spring Boot) | 3.x |
| JUnit | 6.x | 6.0.2 | 6.x |

---

## BOM Artifacts (sapl-bom)

v3.0.0 had 25 managed artifacts. v4.0.0 has 12:

`sapl-api`, `sapl-pdp`, `sapl-test`, `sapl-pdp-remote`, `sapl-spring-boot-starter`, `mqtt-pip`, `mqtt-functions`, `sapl-vaadin-theme`, `sapl-vaadin-editor`, `sapl-code-style`, `geo-functions`, `geo-traccar`
