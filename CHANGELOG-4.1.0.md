# SAPL 4.1.0

4.1.0 has several big changes. `SUSPEND` is a new decision effect that pauses access instead of terminating the subscription. Boolean evaluation moved to Kleene three-valued logic, which changes how errors and `undefined` propagate. The PDP core no longer uses Reactor internally. It runs on a small `Stream` type instead, though the reactive Spring PEP still uses Reactor at its boundary. And the PEP was rebuilt around a plan-and-signal constraint model. The attribute layer also moved to a new broker contract. Policy syntax for `permit` and `deny` is unchanged. Everything else below is new.

The full architecture and worked examples are in [`sapl-documentation/6_3_Spring.md`](sapl-documentation/6_3_Spring.md).

## Release hardening

- Multi-subscription correlation IDs must be non-blank, and JSON multi-subscription input reports blank or duplicate IDs as clean databind errors.
- Strict `Value` JSON serialization rejects nested `undefined` values instead of writing invalid JSON tokens.
- Blocking multi-subscription streams keep only the latest pending decision per ID while lagging consumers catch up.
- SAPL Node HTTP and RSocket PDP endpoints reject multi-subscriptions above `io.sapl.node.max-multi-subscription-count` (default 256) before PDP fan-out.
- HTTP PIP `maxResponseBytes` limits are enforced across split SSE `data:` fields and fragmented WebSocket messages.
- MVC OAuth2 authentication now requires JWT `exp` by default, matching PDP HTTP and RSocket authentication. Non-expiring JWTs require the explicit `io.sapl.node.oauth.allow-jwt-without-expiry=true` opt-in.
- OpenID Authorization API requests that exceed the configured body limit during chunked reads now return 413.
- Geo `geometryBag` and `flattenGeometryBag` enforce the configured geometry collection member cap.
- Geo CRS lookup now uses GeoTools' WKT-file EPSG CRS authority service, focusing bundled EPSG support on CRS definitions instead of HSQL-backed coordinate system, datum, and coordinate operation authority factories.
- MQTT topic matching functions reject excessive topic filter arrays before parsing them.
- MQTT PIP subscriptions bound topic filter count and total topic-filter bytes via `maxTopicFilters` and `maxTopicFilterBytes`.
- MQTT PIP messages reject binary payloads instead of expanding them into SAPL number arrays. Publish text, JSON, or an explicit encoded string.
- Remote bundle polling now defaults to `5s`, and per-PDP poll interval overrides must use configured PDP IDs and positive durations.
- Bundle fetches and inspection now accept entries up to 256 MiB, while directory `pdp.json` is bounded to 1 GiB before parsing.
- Array, graph, CIDR, and text-format functions now reject oversized materialized outputs instead of allocating unbounded results.
- Remote bundle auth headers require `https` by default. Plaintext HTTP requires `remote-bundles.allow-insecure-http=true`.
- Remote HTTP PDP Basic-auth constructors reject plaintext HTTP. Use the builder's explicit `allowInsecureTransport()` only for trusted local or proxied hops.
- OpenID Authorization API disable switch is now `io.sapl.node.openid-authz-api.enabled`. The prerelease `io.sapl.server.openid-authz-api.enabled` key is no longer read.
- The embedded SBOM no longer reports provided build-time helper artifacts that are not packaged in the node runtime.

## SUSPEND decision verb

A new third effect joins `permit` and `deny`. A policy with effect `suspend` casts a vote to **pause** access without terminating the subscription. Streaming PEPs that honour `SUSPEND` stop forwarding data while keeping the subscription alive. A later `permit` resumes flow. One-shot PEPs (`@PreEnforce`, `@PostEnforce`) treat `SUSPEND` exactly like `DENY`: the protected call is denied, and decision-attached obligations / advice / resource handlers run identically to the `DENY` path.

```sapl
policy "suspend during maintenance window"
suspend
    resource.type == "patient_record";
    <maintenance.isActive>;
```

The `AuthorizationDecision.decision` enum now carries five values: `PERMIT`, `DENY`, `SUSPEND`, `INDETERMINATE`, `NOT_APPLICABLE`. The HTTP and RSocket wire encodings serialise the new value as `"SUSPEND"`. CLI exit code 5 distinguishes SUSPEND from DENY (exit code 2) for shell scripts.

Generated protobuf clients must regenerate from the 4.1 schema. `ErrorValue.arguments` is removed and reserved, and `AuthorizationSubscription.secrets` is now an object value instead of an arbitrary `Value`.

### Combining algorithms

`SUSPEND` is a vote alongside `PERMIT` and `DENY`. A new priority algorithm is added and the existing two are extended:

| Algorithm | Behaviour |
|---|---|
| `priority deny` | Any `DENY` wins over any number of `PERMIT`s or `SUSPEND`s. |
| `priority permit` | Any `PERMIT` wins over any number of `DENY`s or `SUSPEND`s. |
| `priority suspend` | Any `SUSPEND` wins over any number of `PERMIT`s or `DENY`s. |
| `unanimous` | All applicable policies must agree on effect, and constraints are merged. |
| `first applicable` | First applicable policy's vote is taken, and constraints are merged. |

The `errors abstain` / `errors propagate` clause now governs the **final** disposition of an `INDETERMINATE` accumulation. `errors abstain` converts a final `INDETERMINATE` to `NOT_APPLICABLE` (and the configured default decision then applies). `errors propagate` returns `INDETERMINATE` as-is. Erroring policies still participate as `INDETERMINATE` votes inside the algorithm where they may block a priority decision. Reading the clause as "errors are invisible" is a misread.

Each `INDETERMINATE` vote now carries an `Outcome` field recording which decisions the errored policy could have produced (XACML 3.0 extended-indeterminate marker). Priority algorithms use this to decide whether an error blocks an otherwise-winning concrete decision: an error is **critical** if its outcome includes the priority decision.

### Vocabulary rename

The internal vocabulary "entitlement" (the result a policy casts) is renamed to "effect" everywhere it surfaced: documentation, AST node names, LSP completions, error messages. Policy syntax was never affected.

See [Authorization Decisions](sapl-documentation/2_3_AuthorizationDecisions.md), [Policy Structure](sapl-documentation/2_4_PolicyStructure.md), and [Combining Algorithms](sapl-documentation/2_5_CombiningAlgorithms.md) for full semantics.

## Kleene logic for errors and undefined

Boolean operators (`&`, `&&`, `|`, `||`) and policy body conditions now follow Kleene strong three-valued logic. A value that is not `true` or `false`, an error, `undefined`, or any other non-boolean, acts as a third value, unknown. Errors and `undefined` are treated alike.

AND is `false` if any operand is `false`, otherwise unknown if any operand is unknown, otherwise `true`. OR is `true` if any operand is `true`, otherwise unknown if any operand is unknown, otherwise `false`. An error or `undefined` operand no longer short-circuits the expression. A dominating value (`false` for AND, `true` for OR) wins regardless of operand position or cost stratum, so the result no longer depends on evaluation order, and a reachable dominating operand rescues an error. The error becomes the result only when no operand carries the dominating value, in which case the operator yields the original error (or a type-mismatch error for `undefined` or another non-boolean).

See [Evaluation Semantics](sapl-documentation/2_11_EvaluationSemantics.md) and [Expressions](sapl-documentation/2_7_Expressions.md).

### Other policy language changes

Identifiers that need escaping now use backticks, for example `` `subject-id` ``, instead of the old caret escaping form. Decimal arithmetic now uses bounded decimal math (`MathContext.DECIMAL128`) to avoid unbounded intermediate precision.

## Streaming PEP

A continuous stream of authorization decisions drives every protected subscription. Under the strict fail-closed discipline, each PDP decision verb maps to one observable effect.

| PDP decision | Effect on the subscription |
|---|---|
| `PERMIT` | Items from the protected method flow through to the subscriber. |
| `SUSPEND` | Items are silently dropped. The subscription stays open. A later `PERMIT` resumes the flow. |
| `INDETERMINATE` | The subscription terminates with an `AccessDeniedException`. |
| `NOT_APPLICABLE` | The subscription terminates with an `AccessDeniedException`. |
| `DENY` | The subscription terminates with an `AccessDeniedException`. |

Only an explicit `SUSPEND` from the PDP silences (rather than terminates) the subscription. Operators who want `NOT_APPLICABLE` to silence rather than terminate set the combining algorithm's `defaultDecision` to `SUSPEND` at the PDP level, producing a real `SUSPEND` decision the streaming PEP then routes through suspension.

Per-item obligation failure also terminates the subscription unconditionally with an `AccessDeniedException`. This is the strict fail-closed default. It matches strict `@PreEnforce` semantics on a per-item timeline.

The four 4.0 streaming annotations collapse into a single `@StreamEnforce` with two orthogonal boolean flags: `signalTransitions` and `pauseRapDuringSuspend`. The `survivesDeny` parameter and the `StreamMode` enum are gone. Whether a deny terminates the subscription or merely suspends it moved into the policy: use `deny` for terminate, `suspend` for survive.

### Migration from 4.0

| 4.0 annotation | 4.1 replacement |
|---|---|
| `@EnforceTillDenied` | `@StreamEnforce` (defaults) |
| `@EnforceDropWhileDenied` | `@StreamEnforce` plus `suspend` verb in policy |
| `@EnforceAccessAware` | `@StreamEnforce(signalTransitions = true)` plus `suspend` verb in policy |
| `@EnforceRecoverableIfDenied` | `@StreamEnforce(signalTransitions = true)` plus `suspend` verb in policy, then `TransitionSignals.onTransitions(...)` at the call site |

### Breaking change: `terminateOnItemEnforcementFailure` removed

`@StreamEnforce(..., terminateOnItemEnforcementFailure = ...)` is a compile error in 4.1. Under the strict fail-closed default, per-item enforcement failure is unconditionally terminal, which is what the prior `true` setting produced. Migration: remove the parameter from every `@StreamEnforce` annotation. The new default matches the prior `true` semantics.

`pauseRapDuringSuspend` (default `false`) controls whether the upstream subscription is disposed during a suspend window: the default keeps it connected with items dropped silently. Set `true` for upstream sources with expensive side effects that must not run while denied.

## Spring starter artifacts

The embedded PDP auto-configuration moved out of `sapl-spring-boot-starter` into the new `sapl-spring-pdp` artifact. Applications that rely on an embedded PDP must add `sapl-spring-pdp`. PEP-only applications using a remote PDP no longer pull embedded PDP dependencies through the starter.

The BOM now manages the new 4.1 artifacts `sapl-pdp-reactive`, `sapl-spring-pdp`, and `sapl-attribute-utils`.

## HTTP authorization

Both the servlet and reactive authorization managers now run on the plan-and-signal model. One HTTP exchange produces five signals. Policy obligations can shape the request, the response, and the denial response. The configurer entry point is identical across servlet and reactive, and the same policy text works against either backend.

```
// servlet
http.with(saplHttp(), withDefaults())

// reactive
SaplServerHttpSecurityConfigurer.apply(http, context)
```

4.0's HTTP authorization managers fired only `DecisionSignal` and a misnamed shim signal. They could not enforce response-level or denial-level obligations at all.

### Unified request shape

Both stacks emit the same shape under `action.http` and `resource.http`. One policy works against either backend.

```sapl
// 4.0 (servlet)
deny resource.requestedURI == "/secret"

// 4.0 (reactive, different shape)
deny resource.contextPath == "/secret"

// 4.1 (servlet and reactive)
deny resource.path == "/secret"
```

Field migration:

| 4.0 | 4.1 |
|---|---|
| `requestedURI` | `path` |
| `requestURL` | `url` (includes query string) |
| `serverName` / `serverPort` | `host` / `port` |
| `remoteAddress` (host:port) | `client.address`, `client.host`, `client.port` |
| `remoteHost` / `remotePort` | `client.host` / `client.port` |
| `localAddress` / `localName` / `localPort` | `server.address` / `server.host` / `server.port` |
| `parameters` | `queryParameters` (form-body parameters no longer mixed in) |
| `protocol`, `requestedSessionId`, `authType`, `locale`, `locales`, `servletPath` | removed (use `subject.authentication`, `accept-language`, or `applicationPath`) |

New fields: `applicationPath` (path with `contextPath` stripped), `forwarded` (parsed RFC 7239 `Forwarded` plus legacy `X-Forwarded-*`), and `contentLength`.

Header keys are now lowercased on both stacks (matches HTTP/2 and Spring's case-insensitive `HttpHeaders` contract). Read `headers["user-agent"]` instead of `headers["User-Agent"]`. Custom subscription factories that hand-built fields like `Map.of("requestedURI", req.getRequestURI())` should rename keys to match the unified shape.

### Subscription data, tenants, and transactions

Default method and HTTP subscription builders redact credential-like values from generated subject, action, and resource projections. Policies that need raw credentials must supply them explicitly, for example through SpEL, secrets, or a custom subscription factory.

Multi-tenant Spring applications now resolve PDP IDs through the configured blocking or reactive tenant resolver. The default blocking path reads from the `SecurityContextHolder`. The reactive path uses Reactor context.

`@EnableReactiveSaplMethodSecurity.order()` is removed. Reactive method-security advisor order is fixed so denials and constraint failures propagate as `AccessDeniedException` and roll back surrounding Spring transactions.

## Provider API

`ConstraintHandlerProvider` returns a list.

```java
// 4.0
Optional<ScopedConstraintHandler> getConstraintHandler(Value, Set<SignalType>);

// 4.1
List<ScopedConstraintHandler> getConstraintHandlers(Value, Set<SignalType>);
```

An empty list means the provider is not responsible. A non-empty list means one or more handlers will run. One obligation may now drive several coordinated handlers across different signals.

The old `io.sapl.spring.constraints.api.*` provider taxonomy is gone. Custom constraint code moves to `io.sapl.spring.pep.constraints` and the new signal-scoped handler types.

## Authorization subscription factory

HTTP authorization managers delegate subscription construction to an `AuthorizationSubscriptionFactory` (`ReactiveAuthorizationSubscriptionFactory` for reactive). The starter registers a default factory under `@ConditionalOnMissingBean` that preserves 4.0 behaviour. Override globally with a `@Bean`, per chain via `http.with(saplHttp(), c -> c.subscriptionFactory(...))`, or replace the manager entirely with `c.authorizationManager(...)`.

Manager constructors changed from `(pdp, planner, mapper)` to `(pdp, planner, subscriptionFactory)`. Code that relies on the auto-configured beans is unaffected.

## Package relocations

The `manager` and `config` packages fold into `pep.http.{servlet,reactive}`.

| 4.0 | 4.1 |
|---|---|
| `io.sapl.spring.manager.SaplAuthorizationManager` | `io.sapl.spring.pep.http.servlet.SaplAuthorizationManager` |
| `io.sapl.spring.manager.SaplAccessDeniedHandler` | `io.sapl.spring.pep.http.servlet.SaplAccessDeniedHandler` |
| `io.sapl.spring.manager.ReactiveSaplAuthorizationManager` | `io.sapl.spring.pep.http.reactive.ReactiveSaplAuthorizationManager` |
| `io.sapl.spring.config.SaplHttpSecurityConfigurer` | `io.sapl.spring.pep.http.servlet.SaplHttpSecurityConfigurer` |

## Removed

The legacy `io.sapl.spring.data` subtree (the old `@QueryEnforce`-based query rewriting) is gone. Spring Data query rewriting now travels as an ordinary obligation on a `@PreEnforce` decision. A shim `BeanPostProcessor` intercepts the query as Spring Data dispatches it.

Query rewriting obligations use the `sql:queryRewriting` / `relational:queryRewriting` shape with typed criteria and projection narrowing. Mongo and R2DBC enforcement is implemented through template and `DatabaseClient` shims instead of annotation-specific repositories.

## Stream abstraction (Reactor out of the PDP core)

The PDP core no longer uses Reactor internally. Policy evaluation, the attribute broker, and multi-subscription now run on a small `io.sapl.api.stream.Stream` type instead of `Flux` and `Mono`. The reason was `Flux.combineLatest`: while several attributes updated in a cascade it would emit intermediate tuples, pairing one input's new value with the others' stale values before settling on the correct one. A snapshot-driven evaluator took its place and emits a consistent tuple every time. `MultiSubscriptionDeglitchTests` pin that down for both the blocking and reactive PDPs.

Reactor has not gone away as a PEP target. The reactive Spring PEP (WebFlux) still talks to the PDP through the adapter in `sapl-pdp-reactive`, which turns the core `Stream` back into `Flux` and `Mono` at the boundary. The change is internal. The only place it shows up directly is when you write a PIP, where attribute methods now return `io.sapl.api.stream.Stream<Value>` rather than `Flux` or `Mono` (see the AttributeBroker section below).

BREAKING for Java integrations: `io.sapl.api.pdp.PolicyDecisionPoint` and `MultiTenantPolicyDecisionPoint` are gone. Core integrations use `StreamingPolicyDecisionPoint`. Reactor integrations use `ReactivePolicyDecisionPoint` from `sapl-pdp-reactive`. PDP configuration types such as `CombiningAlgorithm`, `PDPConfiguration`, and `PdpData` moved to `io.sapl.api.pdp.configuration`.

## AttributeBroker

The 4.0 Reactor-based attribute layer is replaced with a callback-driven broker.

- **Consumer interface**: `AttributeBroker` in `io.sapl.attributes.broker`. `open(id, deps, onUpdate)` returns `Subscription` with no Reactor at the boundary.
- **Repository interface**: `AttributeRepository` with `publish(key, value)`, `publish(key, value, ttl)`, `remove(key)`, and `observe(invocation, onValue)`. The first three are the producer surface. `observe` is the single-key listener surface used by the broker's fallback path.
- **Default top-level**: `PolicyInformationPointAttributeBroker(Duration gracePeriodDuration, AttributeRepository fallback)` with `InMemoryAttributeRepository` as the fallback. PIP match routes through the PIP exclusively. Non-match goes through the fallback. No fallback yields `UNDEFINED`. Catalog mutations (`load` / `swap` / `unload`) migrate routing atomically.
- **PIP method signatures**: BREAKING. Attribute methods now return `io.sapl.api.stream.Stream<Value>` (for streaming attributes) or a plain `Value` subtype (for one-shot attributes). The 4.0 `Flux<Value>` / `Mono<Value>` returns are no longer accepted. The annotations (`@PolicyInformationPoint`, `@Attribute`, `@EnvironmentAttribute`) and the parameter-order rules are unchanged. Migrate by replacing `Flux.interval(...)` / `Flux.just(...)` / `Flux.error(...)` with the corresponding `Streams.scheduledPoll(...)` / `Streams.just(...)` / `Streams.error(...)` constructors from `io.sapl.api.stream.Streams`. `Mono<Value>` callers usually drop down to a direct `Value` return. If they must stay async, use `Streams.fromCallback(...)` or `Streams.fromBlockingSource(...)`. See [Custom Attribute Finders](sapl-documentation/8_3_CustomAttributeFinders.md).
- **Invocation context**: `AttributeFinderInvocation` now carries the PDP ID. Custom brokers, repositories, and tests that construct invocations directly must pass the configured PDP ID.
- **Builder**: `withAttributeBroker(...)` for full override, and `withRepository(...)` swaps just the fallback.
- **Spring beans**: `policyInformationPointAttributeBroker`, `inMemoryAttributeRepository`, `attributeRepository`, and `attributeBroker` (`@Primary`).
- **Extension authors writing their own broker / repository implementations**: new package `io.sapl.attributes.broker.*`, and new callback-driven contract `open(id, deps, onUpdate)` returning `Subscription`, with no Reactor at the boundary.

### Behavioural changes versus 4.0

- A slow PIP whose initial-value timeout fires now publishes `UNDEFINED` (absence). 4.0 published an `ErrorValue("timeout...")`.
- A PIP that returns an empty-completion stream now publishes `UNDEFINED`. 4.0 silently hung.
- A `RuntimeException` thrown by a PIP method during invocation recovers under backoff-bounded retries (jittered exponential), not pollInterval-bounded. Transient connect-time and send-time failures heal on the same schedule as mid-stream failures.
- Hot-swap of a PIP no longer surfaces transient `UNDEFINED` to consumers during the rebind window if a real prior value was observed. The prior value persists until the replacement emits.

## Clock and timestamp sources

The single PDP clock splits into a temporal-reasoning `Clock` for accurate readings, used by the time attribute finder, JWT validation, and the scheduler, and an observability `InstantSource` that stamps the decision trace and attribute arrival times and may be coarse. The builder exposes `withClock(...)`, `withTimestampSource(...)`, and `withCoarseTimestamps(...)`.

## Plugins source

The PDP runtime is driven by an observable `PluginsSource` that emits immutable `PluginsBundle` snapshots. Each bundle carries the function broker, decision interceptors, and subscription lifecycle listeners as one atomic unit. New snapshots drive recompilation of every retained PDP configuration against the new bundle. The compiled artefact carries the bundle it was compiled against, so folded constants and live function calls go to the same broker for any given evaluation.

The 4.1 implementation is `StaticPluginsSource`: one snapshot for the life of the source. A future plugin engine emits new bundles on every catalog change against the same interface. Plugin authors keep implementing the same leaf contracts (`FunctionLibrary`, `DecisionInterceptor`, `SubscriptionLifecycleListener`) in `sapl-api`.

Configurations that arrive before the plugins source has delivered an initial snapshot are retained and surface in the PDP's status as `AWAITING_PLUGINS`. They compile automatically when the snapshot arrives. `loadConfiguration` no longer throws in this case.

`PolicyDecisionPointBuilder.withFunctionBroker(...)` from 4.0 keeps working unchanged. The builder wraps the broker in a `StaticPluginsSource` internally. Code driving recompile from an external source uses the new `withPluginsSource(...)` method. The Spring starter's auto-configuration publishes a `PluginsSource` bean.

Function library registration now uses `FunctionLibraryProvider`, returning library instances. The old `FunctionLibraryClassProvider` class-returning SPI is gone. Builder callers use `withFunctionLibrary(Object)` for custom libraries.

## Remote PDP clients and starters

Direct `sapl-pdp-remote` clients were renamed around the reactive split: use `RemoteHttpReactivePolicyDecisionPoint` / `ProtobufRemoteReactivePolicyDecisionPoint` for reactive clients, or `DelegatingBlockingPolicyDecisionPoint` for blocking access.

Spring remote PDP authentication properties changed. Use `bearer-token` for static bearer credentials. OAuth2 `client_credentials` is supported. Plaintext credential transport for HTTP or RSocket requires the explicit insecure-transport opt-in.

## SAPL Node

Changes to the runnable PDP distribution (sapl-node).

- **HTTP throughput.** The PDP HTTP path runs on Spring MVC on Jetty with virtual threads, bypassing the reactive request pipeline on the hot path. RSocket is the highest-throughput transport.
- **RSocket transport in the Spring starter.** `RemotePDP` configuration accepts `type: rsocket` with TLS via a shared SSL bundle, alongside the existing HTTP transport. `tokenRelay` is HTTP-only (RSocket authenticates once at connection setup).
- **OpenID Authorization API endpoint.** An OpenID-style authorization API ships alongside the native SAPL HTTP API. Documented in the OpenAPI spec the node exposes.
- **Scalar OpenAPI UI** at `/scalar`, generated from the OpenAPI definition.
- **Status page** at `/` reports version, build metadata, and health.
- **Startup failures.** Configuration errors are reported as messages with remediation advice instead of Spring stack traces.
- **Startup security warnings.** The node warns loudly at startup on insecure configuration, such as running with authentication disabled or an HTTP transport that exposes it without protection.
- **`--no-auth` CLI shortcut** sets `io.sapl.node.allow-no-auth=true` for first-run development.
- **API keys.** API keys are now indexed credentials of the form `sapl_<id>_<secret>`. Configurations must include the matching `api-key-id`. Duplicate or missing IDs fail startup.
- **Default listener.** The packaged node defaults to HTTP on `127.0.0.1:8080`. Review reverse proxies, container ports, health checks, and old `8443` assumptions when migrating.
- **Boot logging.** Framework log noise is suppressed. The ready banner reports endpoints, ports, and active authentication methods.
- **SSE client liveness.** Keepalive on the streaming endpoints detects clients that vanish without closing the connection and releases their subscriptions, closing a subscription leak path.
- **Active SSE drain on shutdown.** Open streaming subscriptions receive `event: shutdown` and complete cleanly before the HTTP listener disposes.

## Extension PIPs

- **MQTT PIP.** Broker definitions move to operator-owned `variables.mqtt` configuration. Policy-supplied broker objects are rejected, TLS is supported, and plaintext credential transport requires the explicit insecure opt-in.
- **Traccar PIP.** Policy calls select named configured servers from `TRACCAR_CONFIG`. Credentials come from secrets, policy-supplied connection objects are rejected, path segments are encoded, and credentialed HTTP requires the explicit insecure opt-in.
- **Attribute utilities.** The new `sapl-attribute-utils` artifact provides shared HTTP/WebSocket helper code such as `BlockingWebClient` for extension authors.

## SAPL Test and editor tooling

- SAPL Test supports `suspend` expectations, `priority suspend`, `default suspend`, low-latency mode, `never called`, stricter count / length grammar, `SaplTestFixture.expectSuspend()`, and streaming `thenEmit()`.
- The language server reports oversized documents, bidi control characters, and excessive nesting as diagnostics instead of letting them proceed through normal parsing.
- The Vaadin editor's embedded LSP WebSocket is same-origin by default and is bounded by explicit session and queue limits. Configure `sapl.editor.lsp.*` when embedding the editor across origins or behind a proxy.

## Function libraries

The bundled function libraries gained stricter input validation, including explicit size and complexity limits, and fail closed on invalid input. No function library performs network or filesystem I/O, which keeps policy evaluation deterministic and free of side effects.
