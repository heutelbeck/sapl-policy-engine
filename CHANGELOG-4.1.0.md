# SAPL 4.1.0 Changelog

4.1.0 is a ground-up rewrite of the Spring PEP. It replaces the ad hoc
collection of enforcement points, constraint handlers, and query
rewriting machinery that grew over the 3.x and 4.0 series with a single
coherent model derived from the upcoming **PEP Patterns** paper. The
external surface for the request-response PEPs (`@PreEnforce`,
`@PostEnforce`) is preserved. The four legacy streaming annotations
(`@EnforceTillDenied`, `@EnforceDropWhileDenied`, `@EnforceAccessAware`,
`@EnforceRecoverableIfDenied`) are replaced by a single
`@StreamEnforce` annotation with three orthogonal flags, driven by the
new `SUSPEND` decision verb. The HTTP authorization managers are
preserved at the configurer level but redesigned underneath.

For the full architecture and worked examples, see
[`sapl-documentation/6_3_Spring.md`](sapl-documentation/6_3_Spring.md).

## What's unified

- **One enforcement model.** `EnforcementPlanner` builds a `Plan` once
  per decision and `EnforcementPlan.execute` discharges it against typed
  `Signal`s at well-defined lifecycle points. Pre, post, HTTP, and shim
  PEPs all share the same plan, the same admissibility invariants, and
  the same failure semantics.
- **One provider model.** A single `ConstraintHandlerProvider` interface
  returning `List<ScopedConstraintHandler>` replaces the previous
  collection of typed provider interfaces. One obligation can drive
  several coordinated handlers across different signals.
- **One mechanism for query rewriting.** R2DBC and reactive MongoDB
  query manipulation now travel as ordinary obligations on a
  `@PreEnforce` decision; a shim `BeanPostProcessor` intercepts the
  query as Spring Data dispatches it. The legacy `@QueryEnforce`
  annotation and the `io.sapl.spring.data` subtree are gone.
- **One way to wire HTTP PEPs.** Servlet:
  `http.with(saplHttp(), withDefaults())`. Reactive:
  `SaplServerHttpSecurityConfigurer.apply(http, context)`. One call
  installs the manager, the HTTP PEP filter, and the access-denied
  handler.
- **One streaming PEP.** `@StreamEnforce` replaces the four legacy
  alias annotations. Behaviour is selected via three orthogonal boolean
  flags (`signalTransitions`, `terminateOnItemEnforcementFailure`,
  `pauseRapDuringSuspend`) and the policy author's choice of the
  `suspend` vs `deny` decision verb, rather than by picking an alias
  upfront. Routing is driven by the PDP decision verb directly: PERMIT
  lets items flow, SUSPEND silences them while keeping the subscription
  open, DENY terminates.

## Proper obligation handling for HTTP authorization

Both the servlet and reactive authorization managers now participate in
the plan-and-signal model. Five signals reach the planner over one HTTP
exchange. Policy obligations can shape the request (header injection,
attribute set), the response (status, headers, body rewrite), and the
denial response (custom error page, redirect). The five-signal lifecycle
and the configurer entry point are identical across servlet and
reactive — the same SAPL policy text works against both backends.

4.0's HTTP authorization managers fired only `DecisionSignal` and a
misnamed `HttpRequestShimSignal` and could not enforce response- or
denial-level obligations at all.

## Streaming PEP redesigned around the SUSPEND verb

The streaming PEP path has been redesigned ground-up to consume a
continuous stream of authorization decisions. Each decision the PDP
emits during the lifetime of the subscription has one of five verbs,
and each maps to a single observable effect on the subscriber's stream.

| PDP decision | Effect on the subscription |
|---|---|
| `PERMIT` | Items from the protected method flow through to the subscriber. |
| `SUSPEND` | Items are silently dropped. The subscription stays open. A later `PERMIT` resumes the flow. |
| `INDETERMINATE` | Same as `SUSPEND`. Streaming subscriptions are kept open across transient PDP errors. |
| `NOT_APPLICABLE` | Same as `SUSPEND`. Streaming subscriptions are kept open across transient policy gaps. |
| `DENY` | The subscription terminates with an `AccessDeniedException`. |

The mapping of `INDETERMINATE` and `NOT_APPLICABLE` to silent-drop is
deliberate: streaming subscriptions survive transient PDP errors and
policy gaps. Operators who want hard fail-closed semantics on these
set the combining algorithm's `defaultDecision` to `DENY` (so
`NOT_APPLICABLE` collapses to `DENY`) or its `errorHandling` to
`PROPAGATE` (so `INDETERMINATE` collapses to `DENY`) at the PDP
level. The streaming PEP honours whatever decision the PDP produces.

The combination of verb-driven routing plus three orthogonal flags
(`signalTransitions`, `terminateOnItemEnforcementFailure`,
`pauseRapDuringSuspend`) replaces the four legacy alias annotations
that hard-coded each combination. See the
[`@StreamEnforce`](sapl-documentation/6_3_Spring.md#streaming-enforcement-with-streamenforce)
section in `6_3_Spring.md` for usage and worked patterns.

## Breaking changes

### Streaming PEP annotations

The four 4.0 streaming annotations are replaced by a single
`@StreamEnforce`. Migration:

| 4.0 annotation | 4.1 replacement | Notes |
|---|---|---|
| `@EnforceTillDenied` | `@StreamEnforce` (defaults) | Behavioural difference moves into the policy. The policy uses `deny` for windows that should terminate the subscription (matching the old TillDenied semantics) and `suspend` for windows that should drop items but keep the subscription open. |
| `@EnforceDropWhileDenied` | `@StreamEnforce` (defaults) plus `suspend` verb in policy | The annotation no longer encodes the survives-deny choice. Use the `suspend` verb in the policy text for the deny windows that should leave the subscription alive; use `deny` for the windows that should terminate. |
| `@EnforceAccessAware` | `@StreamEnforce(signalTransitions = true)` plus `suspend` verb in policy | Subscriber receives non-terminal `AccessDeniedException` / `AccessGrantedException` events on every transition. Same `suspend`-vs-`deny` policy distinction as DropWhileDenied. |
| `@EnforceRecoverableIfDenied` | `@StreamEnforce(signalTransitions = true)` plus `suspend` verb in policy plus `TransitionSignals.onTransitions(...)` at the call site | The annotation does the transition signalling; `TransitionSignals` translates the non-terminal events into application-level callbacks. |

The `survivesDeny` annotation parameter is gone. The choice of whether
a deny condition terminates the subscription or merely suspends it
moved into the policy: use `deny` for terminate, `suspend` for survive.

The `StreamMode` enum is removed.

Two new flags surface concerns the 4.0 aliases could not express:

- `terminateOnItemEnforcementFailure` — when a per-item obligation
  handler fails, terminate the subscription (default `false` —
  suspend instead). Set to `true` for protected methods whose per-item
  side effects are unsafe to leave unenforced.
- `pauseRapDuringSuspend` — dispose the upstream subscription whenever
  the stream is silenced, and re-subscribe when it resumes (default
  `false` — upstream stays connected, items dropped silently). Set to
  `true` for upstream sources with expensive side effects that must
  not run while denied.

### `ConstraintHandlerProvider`

Custom providers must change the method signature and return type:

```java
// 4.0
Optional<ScopedConstraintHandler> getConstraintHandler(Value, Set<SignalType>);

// 4.1
List<ScopedConstraintHandler> getConstraintHandlers(Value, Set<SignalType>);
```

A single obligation may now drive several handlers — return multiple
entries from one provider call.

### Authorization subscription factory

The two HTTP authorization managers now delegate subscription
construction to an `AuthorizationSubscriptionFactory` (servlet) /
`ReactiveAuthorizationSubscriptionFactory` (reactive) bean. The starter
registers a default factory under `@ConditionalOnMissingBean` that
preserves 4.0 behaviour. Override the factory globally with a `@Bean`,
per chain via `http.with(saplHttp(), c -> c.subscriptionFactory(...))`,
or replace the manager entirely with `c.authorizationManager(...)`.

The constructor signatures changed:

```java
// 4.0
new SaplAuthorizationManager(pdp, planner, mapper);
new ReactiveSaplAuthorizationManager(pdp, planner, mapper);

// 4.1
new SaplAuthorizationManager(pdp, planner, subscriptionFactory);
new ReactiveSaplAuthorizationManager(pdp, planner, subscriptionFactory);
```

Code that relies on the auto-configured beans is unaffected.

### Package relocations

The `manager` and `config` packages have been folded into a unified
`pep.http.{servlet,reactive}` layout:

| 4.0                                                               | 4.1                                                                       |
|-------------------------------------------------------------------|---------------------------------------------------------------------------|
| `io.sapl.spring.manager.SaplAuthorizationManager`                 | `io.sapl.spring.pep.http.servlet.SaplAuthorizationManager`                |
| `io.sapl.spring.manager.SaplAccessDeniedHandler`                  | `io.sapl.spring.pep.http.servlet.SaplAccessDeniedHandler`                 |
| `io.sapl.spring.manager.ReactiveSaplAuthorizationManager`         | `io.sapl.spring.pep.http.reactive.ReactiveSaplAuthorizationManager`       |
| `io.sapl.spring.config.SaplHttpSecurityConfigurer`                | `io.sapl.spring.pep.http.servlet.SaplHttpSecurityConfigurer`              |

### HTTP request shape

Both serializers (servlet and reactive) now emit the same unified
shape under `action.http` and `resource.http`. The same SAPL policy
text works against either backend. The most common rename is
`requestedURI` -> `path`; the full schema is documented in
[`sapl-documentation/6_3_Spring.md` -> "The HTTP Request Shape"](sapl-documentation/6_3_Spring.md).

```sapl
// 4.0 (servlet) and 4.0 (reactive, two different shapes)
deny resource.requestedURI == "/secret";
deny resource.contextPath == "/secret";

// 4.1 (servlet and reactive, same text)
deny resource.path == "/secret";
```

Field-by-field migration:

| 4.0 | 4.1 | Notes |
|---|---|---|
| `requestedURI` | `path` | The old name was a Servlet-API misnomer; the value is the request path, not a URI |
| `requestURL` | `url` | Now also includes the query string for parity across stacks |
| `serverName` | `host` | Plain noun; was misleading because the value is host-as-seen-here, not the JVM hostname |
| `serverPort` | `port` | Same reasoning |
| `remoteAddress` (string `"/127.0.0.1:54402"`) | `client.address` (`"127.0.0.1"`) | Also `client.host`, `client.port` split out |
| `remoteHost` | `client.host` | Grouped under the `client` peer |
| `remotePort` | `client.port` | Grouped under the `client` peer |
| `localAddress` / `localName` / `localPort` | `server.address` / `server.host` / `server.port` | Grouped under the `server` bind interface |
| `parameters` | `queryParameters` | Now query-only; servlet-side form-body parameters are no longer mixed in |
| `protocol`, `requestedSessionId`, `authType`, `locale`, `locales`, `servletPath` | removed | Use `subject.authentication`, the `accept-language` header, or `applicationPath` instead |

Additions:

- `applicationPath` -- the path with `contextPath` stripped, useful when the app is mounted under a context root.
- `forwarded` -- a parsed view of RFC 7239 `Forwarded` (or legacy `X-Forwarded-{For,Host,Proto,Port}`) so policies can read the original client / host / scheme behind a proxy without splitting headers manually. Block omitted when no relevant header is present.
- `contentLength` -- request body length in bytes when known.

Header keys are now lowercased on both stacks (matches HTTP/2 and
Spring's case-insensitive `HttpHeaders` contract). Policies that read
`headers["User-Agent"]` must read `headers["user-agent"]` instead.

Custom subscription factories that hand-built fields like
`Map.of("requestedURI", req.getRequestURI())` should rename the key to
`"path"` so they match the policy and the default factory.

### `MutableHttpResponse.setStatusCode`

Returns `boolean` (was `void`) to match the reactive `ServerHttpResponse`
contract. Most callers ignore the return value.

### Removed

The legacy `io.sapl.spring.data` subtree (old `@QueryEnforce`-based
query rewriting). Spring Data query manipulation now goes through
`@PreEnforce` plus a query manipulation obligation.
