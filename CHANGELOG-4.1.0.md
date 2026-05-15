# SAPL 4.1.0

4.1.0 is a rewrite of the Spring PEP. The ad hoc enforcement points, constraint handlers, and query-rewriting code that grew over the 3.x and 4.0 series collapses into one model from the upcoming PEP Patterns paper.

`@PreEnforce` and `@PostEnforce` keep their external surface. The four legacy streaming annotations (`@EnforceTillDenied`, `@EnforceDropWhileDenied`, `@EnforceAccessAware`, `@EnforceRecoverableIfDenied`) become `@StreamEnforce`, driven by the new `SUSPEND` decision verb. The HTTP authorization managers stay at the configurer level. The internals have changed.

For the full architecture and worked examples, see [`sapl-documentation/6_3_Spring.md`](sapl-documentation/6_3_Spring.md).

## Streaming PEP

A continuous stream of authorization decisions drives the subscription. Each PDP decision verb maps to one observable effect.

| PDP decision | Effect on the subscription |
|---|---|
| `PERMIT` | Items from the protected method flow through to the subscriber. |
| `SUSPEND` | Items are silently dropped. The subscription stays open. A later `PERMIT` resumes the flow. |
| `INDETERMINATE` | Same as `SUSPEND`. Streaming subscriptions survive transient PDP errors. |
| `NOT_APPLICABLE` | Same as `SUSPEND`. Streaming subscriptions survive transient policy gaps. |
| `DENY` | The subscription terminates with an `AccessDeniedException`. |

Mapping `INDETERMINATE` and `NOT_APPLICABLE` to silent drop is deliberate. Operators who want hard fail-closed semantics set the combining algorithm's `defaultDecision` to `DENY` (so `NOT_APPLICABLE` collapses to `DENY`) or its `errorHandling` to `PROPAGATE` (so `INDETERMINATE` collapses to `DENY`) at the PDP level. The streaming PEP honours whatever decision the PDP produces.

The four 4.0 streaming annotations collapse into a single `@StreamEnforce` with three orthogonal boolean flags, `signalTransitions`, `terminateOnItemEnforcementFailure`, and `pauseRapDuringSuspend`. The `survivesDeny` parameter is gone. Whether a deny terminates the subscription or merely suspends it moved into the policy. Use `deny` for terminate, `suspend` for survive. The `StreamMode` enum is removed.

### Migration from 4.0

| 4.0 annotation | 4.1 replacement | Notes |
|---|---|---|
| `@EnforceTillDenied` | `@StreamEnforce` (defaults) | The policy uses `deny` for windows that should terminate the subscription, `suspend` for windows that should drop items but keep the subscription open. |
| `@EnforceDropWhileDenied` | `@StreamEnforce` (defaults) plus `suspend` verb in policy | The annotation no longer encodes the survives-deny choice. Use the `suspend` verb in policy text for deny windows that should leave the subscription alive. Use `deny` for windows that should terminate. |
| `@EnforceAccessAware` | `@StreamEnforce(signalTransitions = true)` plus `suspend` verb in policy | Subscriber receives non-terminal `AccessDeniedException` and `AccessGrantedException` events on every transition. |
| `@EnforceRecoverableIfDenied` | `@StreamEnforce(signalTransitions = true)` plus `suspend` verb in policy plus `TransitionSignals.onTransitions(...)` at the call site | `TransitionSignals` translates the non-terminal events into application-level callbacks. |

Two new flags address what the 4.0 aliases could not express.

`terminateOnItemEnforcementFailure` (default `false`) controls what happens when a per-item obligation handler fails. The default suspends. Set to `true` for protected methods whose per-item side effects are unsafe to leave unenforced.

`pauseRapDuringSuspend` (default `false`) controls whether the upstream subscription is disposed during a suspend window. The default keeps the upstream connected with items dropped silently. Set to `true` for upstream sources with expensive side effects that must not run while denied.

## HTTP authorization

Both the servlet and reactive authorization managers run on the plan-and-signal model. One HTTP exchange now produces five signals to the planner. Policy obligations can shape the request (header injection, attribute set), the response (status, headers, body rewrite), and the denial response (custom error page, redirect). The five-signal lifecycle and the configurer entry point are identical across servlet and reactive. The same SAPL policy text works against either backend.

```
// servlet
http.with(saplHttp(), withDefaults())

// reactive
SaplServerHttpSecurityConfigurer.apply(http, context)
```

4.0's HTTP authorization managers fired only `DecisionSignal` and a misnamed `HttpRequestShimSignal`. They could not enforce response-level or denial-level obligations at all.

### Unified request shape

Both stacks now emit the same shape under `action.http` and `resource.http`. One policy works against either backend.

```sapl
// 4.0 (servlet)
deny resource.requestedURI == "/secret"

// 4.0 (reactive, different shape)
deny resource.contextPath == "/secret"

// 4.1 (servlet and reactive, same text)
deny resource.path == "/secret"
```

Field migration:

| 4.0 | 4.1 | Notes |
|---|---|---|
| `requestedURI` | `path` | The old name was a Servlet-API misnomer. The value is the request path, not a URI. |
| `requestURL` | `url` | Now includes the query string for parity across stacks. |
| `serverName` | `host` | The value is host-as-seen-here, not the JVM hostname. |
| `serverPort` | `port` | Same reasoning. |
| `remoteAddress` (`"/127.0.0.1:54402"`) | `client.address` (`"127.0.0.1"`) | Split into `client.host` and `client.port`. |
| `remoteHost` | `client.host` | Grouped under the `client` peer. |
| `remotePort` | `client.port` | Grouped under the `client` peer. |
| `localAddress`, `localName`, `localPort` | `server.address`, `server.host`, `server.port` | Grouped under the `server` bind interface. |
| `parameters` | `queryParameters` | Now query-only. Servlet-side form-body parameters are no longer mixed in. |
| `protocol`, `requestedSessionId`, `authType`, `locale`, `locales`, `servletPath` | removed | Use `subject.authentication`, the `accept-language` header, or `applicationPath` instead. |

New fields:

- `applicationPath`. The path with `contextPath` stripped. Useful when the app is mounted under a context root.
- `forwarded`. A parsed view of RFC 7239 `Forwarded` (or legacy `X-Forwarded-{For,Host,Proto,Port}`) so policies can read the original client, host, and scheme behind a proxy without splitting headers manually. The block is omitted when no relevant header is present.
- `contentLength`. Request body length in bytes when known.

Header keys are now lowercased on both stacks. This matches HTTP/2 and Spring's case-insensitive `HttpHeaders` contract. Policies that read `headers["User-Agent"]` must read `headers["user-agent"]` instead.

Custom subscription factories that hand-built fields like `Map.of("requestedURI", req.getRequestURI())` should rename the key to `"path"` so they match the policy and the default factory.

### `MutableHttpResponse.setStatusCode`

Now returns `boolean` to match the reactive `ServerHttpResponse` contract. Most callers ignore the return value.

## Provider API

A single `ConstraintHandlerProvider` interface returns a list.

```java
// 4.0
Optional<ScopedConstraintHandler> getConstraintHandler(Value, Set<SignalType>);

// 4.1
List<ScopedConstraintHandler> getConstraintHandlers(Value, Set<SignalType>);
```

An empty list means the provider is not responsible. A non-empty list means one or more handlers will run. One obligation may now drive several coordinated handlers across different signals.

## Authorization subscription factory

The two HTTP authorization managers delegate subscription construction to an `AuthorizationSubscriptionFactory` bean, or `ReactiveAuthorizationSubscriptionFactory` for reactive. The starter registers a default factory under `@ConditionalOnMissingBean` that preserves 4.0 behaviour. Override the factory globally with a `@Bean`, per chain via `http.with(saplHttp(), c -> c.subscriptionFactory(...))`, or replace the manager entirely with `c.authorizationManager(...)`.

The manager constructors changed.

```java
// 4.0
new SaplAuthorizationManager(pdp, planner, mapper)
new ReactiveSaplAuthorizationManager(pdp, planner, mapper)

// 4.1
new SaplAuthorizationManager(pdp, planner, subscriptionFactory)
new ReactiveSaplAuthorizationManager(pdp, planner, subscriptionFactory)
```

Code that relies on the auto-configured beans is unaffected.

## Package relocations

The `manager` and `config` packages fold into `pep.http.{servlet,reactive}`.

| 4.0 | 4.1 |
|---|---|
| `io.sapl.spring.manager.SaplAuthorizationManager` | `io.sapl.spring.pep.http.servlet.SaplAuthorizationManager` |
| `io.sapl.spring.manager.SaplAccessDeniedHandler` | `io.sapl.spring.pep.http.servlet.SaplAccessDeniedHandler` |
| `io.sapl.spring.manager.ReactiveSaplAuthorizationManager` | `io.sapl.spring.pep.http.reactive.ReactiveSaplAuthorizationManager` |
| `io.sapl.spring.config.SaplHttpSecurityConfigurer` | `io.sapl.spring.pep.http.servlet.SaplHttpSecurityConfigurer` |

## Removed

The legacy `io.sapl.spring.data` subtree (the old `@QueryEnforce`-based query rewriting) is gone. Spring Data query manipulation now travels as an ordinary obligation on a `@PreEnforce` decision. A shim `BeanPostProcessor` intercepts the query as Spring Data dispatches it.

## Correctness

### Glitch-free multi-subscription

`Flux.combineLatest` is gone from main src across the engine. The previous combiner produced intermediate tuples during cascading updates, pairing one input's new value with stale previous values from the others before the correct tuple appeared. Multi-subscription evaluation now uses a snapshot-driven evaluator that delivers consistent tuples on every emission. `MultiSubscriptionDeglitchTests` lock the invariant for both the reactive and blocking PDPs.

### AttributeStore

A single `AttributeStore` replaces `AttributeBroker` and `FunctionBroker`. Custom PIPs and function libraries register against the store directly. The change is transparent to policy authors. Extension authors who built broker-style abstractions against 4.0 need to switch to the new interface.

## SAPL Node

The runnable PDP distribution gains operator-facing improvements.

- **Higher HTTP throughput.** The PDP HTTP path runs on Spring MVC on Jetty with virtual threads, bypassing the reactive request pipeline on the hot path. The RSocket transport remains the top-throughput option.
- **RSocket transport in the Spring starter.** `RemotePDP` configuration accepts `type: rsocket` with TLS via a shared SSL bundle, alongside the existing HTTP transport. The Spring docs cover the new property keys and the rationale for `tokenRelay` staying HTTP-only (RSocket authenticates once at connection setup, so per-request user credential relay is not on the table).
- **OpenID Authorization API endpoint.** An OpenID-style authorization API ships alongside the native SAPL HTTP API. The shape is documented in the OpenAPI spec exposed by the node.
- **Scalar OpenAPI UI.** A live API explorer mounts at `/scalar`, generated from the OpenAPI definition the node exposes.
- **Status page.** A root index page at `/` reports node version, build metadata, and health.
- **Readable startup failures.** Configuration errors (missing auth mechanism, JWT issuer not configured, payload size out of range, duplicate api-key-id, others) surface as messages with concrete remediation advice instead of Spring stack traces. Spring Boot's `FailureAnalyzer` mechanism does the work.
- **`--no-auth` CLI shortcut.** Sets `io.sapl.node.allow-no-auth=true` from the command line for first-run development without YAML editing.
- **Clean boot logging.** Framework noise is suppressed via `logback-spring.xml`. The ready banner reports endpoints, ports, and active authentication methods at a glance.
- **Active SSE drain on shutdown.** Open streaming subscriptions receive a server-initiated `event: shutdown` and are cleanly completed before the HTTP listener disposes.
- **Internal package layout cleaned up** under `io.sapl.node.*` (`auth/`, `boot/`, `observability/`, `http/openapi/`, `rsocket/pdp/`). No supported public API affected.
