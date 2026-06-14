# SAPL 4.1.0

4.1.0 has three big changes. `SUSPEND` is a new decision effect that pauses access instead of terminating the subscription. The PDP core no longer uses Reactor internally; it runs on a small `Stream` type instead, though the reactive Spring PEP still uses Reactor at its boundary. And the PEP was rebuilt around a plan-and-signal constraint model. The attribute layer also moved to a new broker contract. Policy syntax for `permit` and `deny` is unchanged; everything else below is new.

The full architecture and worked examples are in [`sapl-documentation/6_3_Spring.md`](sapl-documentation/6_3_Spring.md).

## SUSPEND decision verb

A new third effect joins `permit` and `deny`. A policy with effect `suspend` casts a vote to **pause** access without terminating the subscription. Streaming PEPs that honour `SUSPEND` stop forwarding data while keeping the subscription alive; a later `permit` resumes flow. One-shot PEPs (`@PreEnforce`, `@PostEnforce`) treat `SUSPEND` exactly like `DENY`: the protected call is denied, and decision-attached obligations / advice / resource handlers run identically to the `DENY` path.

```sapl
policy "suspend during maintenance window"
suspend
    resource.type == "patient_record";
    <maintenance.isActive>;
```

The `AuthorizationDecision.decision` enum now carries five values: `PERMIT`, `DENY`, `SUSPEND`, `INDETERMINATE`, `NOT_APPLICABLE`. The HTTP and RSocket wire encodings serialise the new value as `"SUSPEND"`. CLI exit code 5 distinguishes SUSPEND from DENY (exit code 2) for shell scripts.

### Combining algorithms

`SUSPEND` is a vote alongside `PERMIT` and `DENY`. A new priority algorithm is added and the existing two are extended:

| Algorithm | Behaviour |
|---|---|
| `priority deny` | Any `DENY` wins over any number of `PERMIT`s or `SUSPEND`s. |
| `priority permit` | Any `PERMIT` wins over any number of `DENY`s or `SUSPEND`s. |
| `priority suspend` | Any `SUSPEND` wins over any number of `PERMIT`s or `DENY`s. |
| `unanimous` | All applicable policies must agree on effect; constraints are merged. |
| `first applicable` | First applicable policy's vote is taken; constraints are merged. |

The `errors abstain` / `errors propagate` clause now governs the **final** disposition of an `INDETERMINATE` accumulation. `errors abstain` converts a final `INDETERMINATE` to `NOT_APPLICABLE` (and the configured default decision then applies); `errors propagate` returns `INDETERMINATE` as-is. Erroring policies still participate as `INDETERMINATE` votes inside the algorithm where they may block a priority decision; reading the clause as "errors are invisible" is a misread.

Each `INDETERMINATE` vote now carries an `Outcome` field recording which decisions the errored policy could have produced (XACML 3.0 extended-indeterminate marker). Priority algorithms use this to decide whether an error blocks an otherwise-winning concrete decision: an error is **critical** if its outcome includes the priority decision.

### Vocabulary rename

The internal vocabulary "entitlement" (the result a policy casts) is renamed to "effect" everywhere it surfaced: documentation, AST node names, LSP completions, error messages. Policy syntax was never affected.

See [Authorization Decisions](sapl-documentation/2_3_AuthorizationDecisions.md), [Policy Structure](sapl-documentation/2_4_PolicyStructure.md), and [Combining Algorithms](sapl-documentation/2_5_CombiningAlgorithms.md) for full semantics.

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

Per-item obligation failure also terminates the subscription unconditionally with an `AccessDeniedException`. This is the strict fail-closed default; matching strict `@PreEnforce` semantics on a per-item timeline.

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

`pauseRapDuringSuspend` (default `false`) controls whether the upstream subscription is disposed during a suspend window: the default keeps it connected with items dropped silently; set `true` for upstream sources with expensive side effects that must not run while denied.

## HTTP authorization

Both the servlet and reactive authorization managers now run on the plan-and-signal model. One HTTP exchange produces five signals. Policy obligations can shape the request, the response, and the denial response. The configurer entry point is identical across servlet and reactive, and the same policy text works against either backend.

```
// servlet
http.with(saplHttp(), withDefaults())

// reactive
SaplServerHttpSecurityConfigurer.apply(http, context)
```

4.0's HTTP authorization managers fired only `DecisionSignal` and a misnamed shim signal; they could not enforce response-level or denial-level obligations at all.

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

New fields: `applicationPath` (path with `contextPath` stripped); `forwarded` (parsed RFC 7239 `Forwarded` plus legacy `X-Forwarded-*`); `contentLength`.

Header keys are now lowercased on both stacks (matches HTTP/2 and Spring's case-insensitive `HttpHeaders` contract). Read `headers["user-agent"]` instead of `headers["User-Agent"]`. Custom subscription factories that hand-built fields like `Map.of("requestedURI", req.getRequestURI())` should rename keys to match the unified shape.

## Provider API

`ConstraintHandlerProvider` returns a list.

```java
// 4.0
Optional<ScopedConstraintHandler> getConstraintHandler(Value, Set<SignalType>);

// 4.1
List<ScopedConstraintHandler> getConstraintHandlers(Value, Set<SignalType>);
```

An empty list means the provider is not responsible. A non-empty list means one or more handlers will run. One obligation may now drive several coordinated handlers across different signals.

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

## Stream abstraction (Reactor out of the PDP core)

The PDP core no longer uses Reactor internally. Policy evaluation, the attribute broker, and multi-subscription now run on a small `io.sapl.api.stream.Stream` type instead of `Flux` and `Mono`. The reason was `Flux.combineLatest`: while several attributes updated in a cascade it would emit intermediate tuples, pairing one input's new value with the others' stale values before settling on the correct one. A snapshot-driven evaluator took its place and emits a consistent tuple every time; `MultiSubscriptionDeglitchTests` pin that down for both the blocking and reactive PDPs.

Reactor has not gone away as a PEP target. The reactive Spring PEP (WebFlux) still talks to the PDP through the adapter in `sapl-pdp-reactive`, which turns the core `Stream` back into `Flux` and `Mono` at the boundary. The change is internal; the only place it shows up directly is when you write a PIP, where attribute methods now return `io.sapl.api.stream.Stream<Value>` rather than `Flux` or `Mono` (see the AttributeBroker section below).

## AttributeBroker

The 4.0 Reactor-based attribute layer is replaced with a callback-driven broker.

- **Consumer interface**: `AttributeBroker` in `io.sapl.attributes.broker`. `open(id, deps, onUpdate) → Subscription` with no Reactor at the boundary.
- **Repository interface**: `AttributeRepository` with `publish(key, value)`, `publish(key, value, ttl)`, `remove(key)`, and `observe(invocation, onValue)`. The first three are the producer surface; `observe` is the single-key listener surface used by the broker's fallback path.
- **Default top-level**: `PolicyInformationPointAttributeBroker(Duration gracePeriodDuration, AttributeRepository fallback)` with `InMemoryAttributeRepository` as the fallback. PIP match routes through the PIP exclusively; non-match goes through the fallback; no fallback yields `UNDEFINED`. Catalog mutations (`load` / `swap` / `unload`) migrate routing atomically.
- **PIP method signatures**: BREAKING. Attribute methods now return `io.sapl.api.stream.Stream<Value>` (for streaming attributes) or a plain `Value` subtype (for one-shot attributes). The 4.0 `Flux<Value>` / `Mono<Value>` returns are no longer accepted. The annotations (`@PolicyInformationPoint`, `@Attribute`, `@EnvironmentAttribute`) and the parameter-order rules are unchanged. Migrate by replacing `Flux.interval(...)` / `Flux.just(...)` / `Flux.error(...)` with the corresponding `Streams.scheduledPoll(...)` / `Streams.just(...)` / `Streams.error(...)` constructors from `io.sapl.api.stream.Streams`. `Mono<Value>` callers usually drop down to a direct `Value` return; if they must stay async, use `Streams.fromCallback(...)` or `Streams.fromBlockingSource(...)`. See [Custom Attribute Finders](sapl-documentation/8_3_CustomAttributeFinders.md).
- **Builder**: `withAttributeBroker(...)` for full override; `withRepository(...)` swaps just the fallback.
- **Spring beans**: `policyInformationPointAttributeBroker`, `inMemoryAttributeRepository`, `attributeRepository`, and `attributeBroker` (`@Primary`).
- **Extension authors writing their own broker / repository implementations**: new package `io.sapl.attributes.broker.*`; new callback-driven contract `open(id, deps, onUpdate) → Subscription`; no Reactor at the boundary.

### Behavioural changes versus 4.0

- A slow PIP whose initial-value timeout fires now publishes `UNDEFINED` (absence). 4.0 published an `ErrorValue("timeout...")`.
- A PIP that returns an empty-completion stream now publishes `UNDEFINED`. 4.0 silently hung.
- A `RuntimeException` thrown by a PIP method during invocation recovers under backoff-bounded retries (jittered exponential), not pollInterval-bounded. Transient connect-time and send-time failures heal on the same schedule as mid-stream failures.
- Hot-swap of a PIP no longer surfaces transient `UNDEFINED` to consumers during the rebind window if a real prior value was observed; the prior value persists until the replacement emits.

## Plugins source

The PDP runtime is driven by an observable `PluginsSource` that emits immutable `PluginsBundle` snapshots. Each bundle carries the function broker, decision interceptors, and subscription lifecycle listeners as one atomic unit. New snapshots drive recompilation of every retained PDP configuration against the new bundle. The compiled artefact carries the bundle it was compiled against, so folded constants and live function calls go to the same broker for any given evaluation.

The 4.1 implementation is `StaticPluginsSource`: one snapshot for the life of the source. A future plugin engine emits new bundles on every catalog change against the same interface. Plugin authors keep implementing the same leaf contracts (`FunctionLibrary`, `DecisionInterceptor`, `SubscriptionLifecycleListener`) in `sapl-api`.

Configurations that arrive before the plugins source has delivered an initial snapshot are retained and surface in the PDP's status as `AWAITING_PLUGINS`. They compile automatically when the snapshot arrives; `loadConfiguration` no longer throws in this case.

`PolicyDecisionPointBuilder.withFunctionBroker(...)` from 4.0 keeps working unchanged. The builder wraps the broker in a `StaticPluginsSource` internally. Code driving recompile from an external source uses the new `withPluginsSource(...)` method. The Spring starter's auto-configuration publishes a `PluginsSource` bean.

## SAPL Node

Changes to the runnable PDP distribution (sapl-node).

- **HTTP throughput.** The PDP HTTP path runs on Spring MVC on Jetty with virtual threads, bypassing the reactive request pipeline on the hot path. RSocket is the highest-throughput transport.
- **RSocket transport in the Spring starter.** `RemotePDP` configuration accepts `type: rsocket` with TLS via a shared SSL bundle, alongside the existing HTTP transport. `tokenRelay` is HTTP-only (RSocket authenticates once at connection setup).
- **OpenID Authorization API endpoint.** An OpenID-style authorization API ships alongside the native SAPL HTTP API. Documented in the OpenAPI spec the node exposes.
- **Scalar OpenAPI UI** at `/scalar`, generated from the OpenAPI definition.
- **Status page** at `/` reports version, build metadata, and health.
- **Startup failures.** Configuration errors are reported as messages with remediation advice instead of Spring stack traces.
- **`--no-auth` CLI shortcut** sets `io.sapl.node.allow-no-auth=true` for first-run development.
- **Boot logging.** Framework log noise is suppressed; the ready banner reports endpoints, ports, and active authentication methods.
- **Active SSE drain on shutdown.** Open streaming subscriptions receive `event: shutdown` and complete cleanly before the HTTP listener disposes.
