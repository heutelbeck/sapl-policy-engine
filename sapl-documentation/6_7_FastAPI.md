---
layout: default
title: FastAPI
#permalink: /python-fastapi/
has_children: false
parent: SDKs and APIs
nav_order: 607
---

## FastAPI SDK

Attribute-Based Access Control (ABAC) for FastAPI using SAPL (Streaming Attribute Policy Language). Provides decorator-driven policy enforcement with a constraint handler architecture for obligations, advice, and response transformation.

The `sapl-fastapi` library integrates SAPL policy enforcement into FastAPI and Starlette applications. It works on both synchronous (`def`) and asynchronous (`async def`) endpoints, supports Server-Sent Events streaming for continuous authorization, and works with FastAPI's dependency injection system.

### What is SAPL?

SAPL is a policy language and Policy Decision Point (PDP) for attribute-based access control. Policies are written in a dedicated language and evaluated by the PDP, which streams authorization decisions based on subject, action, resource, and environment attributes.

Three core concepts:

1. **Authorization subscription**: your app sends `{ subject, action, resource, environment }` to the PDP.
2. **PDP decision**: the PDP evaluates policies and returns `PERMIT` or `DENY`, optionally with obligations, advice, or a replacement resource.
3. **Constraint handlers**: registered handlers execute the policy's instructions (log, filter, transform, cap values, etc.).

A PDP decision looks like this:

```json
{
  "decision": "PERMIT",
  "obligations": [{ "type": "logAccess", "message": "Patient record accessed" }],
  "advice": [{ "type": "notifyAdmin" }]
}
```

`decision` is always present (`PERMIT`, `DENY`, `SUSPEND`, `INDETERMINATE`, or `NOT_APPLICABLE`). The other fields are optional. `obligations` and `advice` are arrays of arbitrary JSON objects (by convention with a `type` field for handler dispatch), and `resource` (when present) replaces the endpoint's return value entirely.

For a deeper introduction to SAPL's subscription model and policy language, see the [SAPL documentation](https://sapl.io/docs/latest/).

### Installation

Install the library and the base dependency:

```bash
pip install sapl-fastapi
```

This also installs `sapl-base`, which provides the PDP client, constraint engine, and content filtering. The library requires Python 3.12 or later and FastAPI 0.100+.

A complete working demo with JWT authentication, constraint handlers, content filtering, and streaming enforcement is available at [sapl-python-demos/fastapi_demo](https://github.com/heutelbeck/sapl-python-demos/tree/main/fastapi_demo).

### Setup

#### Lifespan Configuration

Configure SAPL during application startup using FastAPI's lifespan context manager:

```python
import os
from contextlib import asynccontextmanager
from collections.abc import AsyncIterator

from fastapi import FastAPI
from sapl_fastapi import SaplConfig, configure_sapl, cleanup_sapl

@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    config = SaplConfig(
        base_url=os.getenv("SAPL_PDP_URL", "https://localhost:8443"),
        token=os.getenv("SAPL_PDP_TOKEN"),
    )
    configure_sapl(config)
    yield
    await cleanup_sapl()

app = FastAPI(lifespan=lifespan)
```

For basic authentication instead of an API key:

```python
config = SaplConfig(
    base_url="https://localhost:8443",
    username="myPdpClient",
    secret="myPassword",
)
```

`token` (API key) and `username`/`secret` (Basic Auth) are mutually exclusive. Configure one or the other.

#### Local Development (HTTP)

For local development without TLS, point `base_url` at a loopback host. A plain `http://` URL is accepted only when the host is `localhost`, `127.0.0.1`, or `::1`. Any plain-HTTP URL targeting a remote host is refused at construction time, so plaintext authorization decisions never leave the machine.

```python
config = SaplConfig(
    base_url="http://localhost:8443",
)
```

#### What configure_sapl Registers

`configure_sapl()` creates the module-level singleton PDP client and the `EnforcementPlanner`. It automatically registers the built-in `ContentFilteringProvider` and `ContentFilterPredicateProvider` for content filtering support. Custom constraint handler providers are registered separately via `register_provider()`.

`cleanup_sapl()` closes the PDP client and releases HTTP connections. Always call it during shutdown (in the lifespan `yield` teardown block).

### Enforcement Decorators

The decorators work on both synchronous (`def`) and asynchronous (`async def`) FastAPI endpoint functions. The decorated endpoint **must** include `request: Request` as a parameter (either positional or keyword) so the decorator can extract request context.

The decorators auto-detect the endpoint kind, so you write the endpoint in whichever style suits it. An async endpoint runs on the async enforcement core. A sync endpoint runs on the blocking core, which executes the endpoint off the event loop, so synchronous database and IO access works normally. FastAPI and Starlette already run sync `def` endpoints in a threadpool, so the blocking core runs cleanly there. When you configure a transaction provider (see [Database Transactions](#database-transactions)) it must match the endpoint kind: a sync context-manager factory for sync endpoints, an async one for async endpoints.

#### @pre_enforce

Authorizes **before** the endpoint executes. The endpoint only runs on PERMIT.

```python
from fastapi import FastAPI, Request
from sapl_fastapi import pre_enforce

app = FastAPI()


@app.get("/patient/{patient_id}")
@pre_enforce(action="readPatient", resource="patient")
async def get_patient(request: Request, patient_id: str):
    return {"id": patient_id, "name": "Jane Doe", "ssn": "123-45-6789"}
```

Use `@pre_enforce` for endpoints with side effects (database writes, emails) that should not execute when access is denied. On denial, an `HTTPException` with status 403 is raised.

#### @post_enforce

Authorizes **after** the endpoint executes. The endpoint always runs. Its return value is available to the subscription builder via the `return_value` argument of callable fields.

```python
from fastapi import Request
from sapl_fastapi import post_enforce


@app.get("/record/{record_id}")
@post_enforce(
    action="read",
    resource=lambda ctx: {"type": "record", "data": ctx.return_value},
)
async def get_record(request: Request, record_id: str):
    return {"id": record_id, "value": "sensitive-data"}
```

Use `@post_enforce` when the policy needs to see the actual return value to make its authorization decision (e.g., deny based on the data's classification). On denial, the return value is discarded and `HTTPException(403)` is raised.

#### Building the Authorization Subscription

Each decorator accepts keyword arguments to customize the authorization subscription fields: `subject`, `action`, `resource`, `environment`, and `secrets`.

**Default Values**

When not explicitly provided, the subscription fields are derived from the Starlette `Request`:

| Field         | Default                                                                       |
| ------------- | ----------------------------------------------------------------------------- |
| `subject`     | `request.state.user` or `request.scope["user"]`, or `"anonymous"`             |
| `action`      | `{"method": request.method, "handler": function_name}`                        |
| `resource`    | `{"path": request.url.path, "params": dict(request.path_params)}`            |
| `environment` | `{"ip": request.client.host}` (when available)                                |
| `secrets`     | Not sent unless explicitly specified                                          |

The `subject` default integrates with FastAPI/Starlette authentication middleware. If you set `request.state.user` in an authentication dependency or middleware, it is automatically used as the subject.

**Static Values**

Pass a string or dict directly:

```python
@pre_enforce(action="read", resource="patient")
```

**Dynamic Values (Callables)**

Pass a callable that receives a `SubscriptionContext` and returns the field value. The context provides `request`, `return_value` (`None` for `@pre_enforce`), `params` (path parameters), `query` (query string), and `args` (resolved function arguments):

```python
@pre_enforce(
    subject=lambda ctx: getattr(ctx.request.state, "user", "anonymous") if ctx.request else "anonymous",
    resource=lambda ctx: {"pilotId": ctx.params.get("pilot_id")},
)
```

**Secrets**

The `secrets` field carries sensitive data (tokens, API keys) that the PDP needs for policy evaluation but that must not appear in logs. It is excluded from debug logging automatically. Use it when a policy needs to inspect credentials, for example passing a raw JWT so the PDP can read its claims:

```python
@pre_enforce(
    action="exportData",
    resource=lambda ctx: {"pilotId": ctx.params.get("pilot_id")},
    secrets=lambda ctx: {"jwt": getattr(ctx.request.state, "token", None)} if ctx.request and getattr(ctx.request.state, "token", None) else None,
)
```

#### @stream_enforce

Streaming enforcement applies an authorization decision continuously to a stream of items your endpoint produces. The decorated endpoint returns an **async iterator** of data items. SAPL opens a streaming PDP subscription and applies each decision to the stream as it runs: `PERMIT` passes items through, `SUSPEND` pauses, `DENY` ends it. The enforced result is **itself an async iterator** of authorised items, so it is independent of how you deliver them.

`@stream_enforce` is the ready-made binding for **Server-Sent Events**: it wraps the enforced iterator in a Starlette `StreamingResponse` that renders each item as an SSE `data:` frame on `text/event-stream`. SSE is the delivery shown here. For another delivery mode (a WebSocket, a gRPC stream, or consuming the stream in-process) drive the enforcement directly with `run_pipeline` from `sapl_base.pep.streaming`: it takes your async iterator and returns the enforced async iterator, with no transport assumptions.

```python
import asyncio
from datetime import datetime, timezone
from fastapi import Request
from sapl_fastapi import stream_enforce


@app.get("/stream/heartbeat")
@stream_enforce(action="stream:heartbeat", resource="heartbeat")
async def heartbeat(request: Request):
    seq = 0
    while True:
        yield {"seq": seq, "ts": datetime.now(timezone.utc).isoformat()}
        seq += 1
        await asyncio.sleep(2)
```

A single decorator now covers every streaming case. The behaviour is driven by the policy verbs and by two boolean flags, both defaulting to `False`.

```python
@stream_enforce(
    action="stream:heartbeat",
    resource="heartbeat",
    signal_transitions=False,       # default
    pause_rap_during_suspend=False,  # default
)
```

**Verb routing.** Every decision the PDP emits during the lifetime of the subscription maps to one observable effect.

| PDP decision     | Effect on the stream                                                                                  |
| ---------------- | ----------------------------------------------------------------------------------------------------- |
| `PERMIT`         | Items flow through to the consumer.                                                                    |
| `SUSPEND`        | Items are silently dropped. The subscription stays open. A later `PERMIT` resumes the flow.            |
| `DENY`           | The stream terminates. The SSE binding emits a final `ACCESS_DENIED` frame before closing.             |
| `INDETERMINATE`  | The subscription terminates, the same way `DENY` does.                                                 |
| `NOT_APPLICABLE` | The subscription terminates, the same way `DENY` does.                                                 |

Under the strict fail-closed discipline only an explicit `SUSPEND` keeps the subscription alive while pausing it. `DENY`, `INDETERMINATE`, and `NOT_APPLICABLE` all terminate. For keep-alive semantics where access pauses and later resumes, the policy must emit `SUSPEND` rather than `DENY`. Operators who want `NOT_APPLICABLE` to pause rather than terminate set the combining algorithm's `defaultDecision` to `SUSPEND` at the PDP level.

**signal_transitions.** With the default `False`, suspend and resume boundaries are silent. The consumer sees items while permitted and a gap while suspended, with no boundary item. With `True`, the enforced stream carries an `ACCESS_SUSPENDED` boundary item each time it is suspended and an `ACCESS_GRANTED` boundary item each time it resumes (the SSE binding renders these as frames). Use this when the consumer should show a paused/resumed status.

**pause_rap_during_suspend.** With the default `False`, the protected async iterator stays subscribed during suspension. Items keep arriving from upstream and are dropped on the way to the client, giving lower latency on resume. With `True`, the upstream iterator is cancelled on entry to the suspended state and re-subscribed on resume. Use this for upstream sources with expensive side effects that must not run while access is paused.

| Scenario                                       | Configuration                                                |
| ---------------------------------------------- | ------------------------------------------------------------ |
| Access loss is permanent (revoked credentials)  | policy emits `deny`; defaults                                |
| Client does not need to know about gaps         | policy emits `suspend`; defaults                             |
| Client should show suspended/restored status    | policy emits `suspend`; `signal_transitions=True`            |

### How Enforcement Works

The decorators above are convenient, but to use them well it helps to understand what actually happens behind the scenes. This section walks through the enforcement lifecycle so you can reason about behavior.

#### The Deny Invariant

Only `PERMIT` grants access. The PDP can return five possible decisions (`PERMIT`, `DENY`, `SUSPEND`, `INDETERMINATE`, `NOT_APPLICABLE`), and only `PERMIT` ever results in your endpoint running or your stream forwarding data. Everything else means denial. The streaming PEP honours `SUSPEND` by pausing the stream while keeping the subscription alive, so a later `PERMIT` resumes it. One-shot enforcement (`@pre_enforce`, `@post_enforce`) treats `SUSPEND` as a denial. See [Authorization Decisions](../2_3_AuthorizationDecisions/) for details.

A `PERMIT` with obligations is not a free pass. The PEP checks that every obligation in the decision has a registered handler. If even one obligation cannot be fulfilled, the PEP treats the decision as a denial. If a handler accepts responsibility but fails during execution, that also results in denial. Advice is softer: if an advice handler fails, the PEP logs the failure and moves on. Advice never causes denial.

| Aspect          | Obligation                                                          | Advice                                         |
|-----------------|---------------------------------------------------------------------|-------------------------------------------------|
| All handled?    | Required. Unhandled obligations deny access (HTTPException 403).    | Optional. Unhandled advice is silently ignored. |
| Handler failure | Denies access (HTTPException 403).                                  | Logs a warning and continues.                   |

This means you can always trust that if your endpoint runs, every obligation attached to the decision has been successfully enforced.

#### Enforcement Locations

Depending on the decorator, constraint handlers can intervene at different points in the lifecycle of a request or stream.

For request-response endpoints (`@pre_enforce` and `@post_enforce`), constraints can run at four points:

| Location              | When it happens                         | What constraints do here                            |
|-----------------------|-----------------------------------------|-----------------------------------------------------|
| On decision           | Authorization decision arrives          | Side effects like logging, audit, or notification    |
| Pre-method invocation | Before the protected endpoint executes  | Modify endpoint arguments (`@pre_enforce` only)     |
| On return value       | After the endpoint returns              | Transform, filter, or replace the result            |
| On error              | If the endpoint throws                  | Transform or observe the error                      |

For streaming endpoints (`@stream_enforce`), constraints can run at five points:

| Location           | When it happens                              | What constraints do here                |
|--------------------|----------------------------------------------|-----------------------------------------|
| On decision        | Each new decision from the PDP stream        | Side effects like logging, audit        |
| On each data item  | Each element yielded by the async iterator   | Transform, filter, or replace items     |
| On stream error    | The iterator produces an error               | Transform or observe the error          |
| On stream complete | The iterator finishes normally               | Cleanup and finalization                |
| On cancel          | Client disconnects or enforcement terminates | Release resources and close connections |

SAPL models each of these points as a named signal, and a handler attaches to whichever signal fits the work it does. A handler that fires once when the decision arrives attaches to the decision signal. A handler that processes each emitted item attaches to the output signal. The signal a handler attaches to determines when it runs. The same `ConstraintHandlerProvider` mechanism is used for one-shot and streaming enforcement alike.

#### PreEnforce Lifecycle

When you decorate an endpoint with `@pre_enforce`, here is what happens step by step.

First, the PEP builds an authorization subscription from the decorator options (or from defaults if you left them out) and sends it to the PDP as a one-shot request. The PDP evaluates the subscription against all matching policies and returns a single decision.

If the decision is anything other than `PERMIT`, the PEP raises `HTTPException(403)` immediately. Your endpoint never runs.

If the decision is `PERMIT`, the PEP resolves all constraint handlers. It walks through the obligations and advice attached to the decision and checks which registered handlers claim responsibility for each one. If any obligation has no matching handler, the PEP denies access right there, because it cannot guarantee the obligation will be enforced.

With all handlers resolved, execution proceeds through the enforcement locations in order. On-decision handlers run first (logging, audit). Then method-invocation handlers run, which can modify endpoint arguments if the policy requires it. Then your actual endpoint executes. After the endpoint returns, the PEP applies return-value handlers: resource replacement if the decision included one, filter predicates, mapping handlers, and consumer handlers. If any obligation handler fails at any stage, the PEP denies access.

#### PostEnforce Lifecycle

`@post_enforce` inverts the order. Your endpoint runs first, regardless of the authorization outcome. Only after it returns does the PEP build the authorization subscription (now including the return value) and consult the PDP.

This means the PDP can make decisions based on the actual data your endpoint produced. For example, a policy might permit access to a record only if its classification level is below a threshold, something that can only be checked after loading the record.

If the decision is not `PERMIT`, the PEP discards the return value and raises `HTTPException(403)`.

If the decision is `PERMIT`, constraint handlers proceed through the same stages as `@pre_enforce`, minus the method-invocation handlers (since the endpoint has already run). Return-value handlers can still transform the result before it reaches the caller.

Because the endpoint runs before the PDP is consulted, if the endpoint itself raises an exception, that exception propagates directly. The PDP is never called, because there is no return value to include in the subscription.

SAPL PEP libraries share a single unified enforcement model. It is a strict fail-closed state machine over the five decision verbs, where only `PERMIT` grants access and only an explicit `SUSPEND` pauses a stream without terminating it. See [Authorization Decisions](../2_3_AuthorizationDecisions/) for the decision-verb semantics.

### Constraint Handlers

When the PDP returns a decision with `obligations` or `advice`, the `EnforcementPlanner` resolves and schedules all matching handlers.

#### The ConstraintHandlerProvider Protocol

There is one extension point. A constraint handler is an object that implements the `ConstraintHandlerProvider` protocol, which has a single method.

```python
from collections.abc import Sequence
from typing import Any, Protocol

from sapl_base.pep import ScopedHandler


class ConstraintHandlerProvider(Protocol):
    def get_handlers(self, constraint: Any) -> Sequence[ScopedHandler]:
        ...
```

The planner calls `get_handlers` for each constraint in a decision. The provider inspects the constraint and decides whether it can handle it. If it can, it returns one or more `ScopedHandler` entries. If it cannot, it returns an empty sequence and the planner asks the other providers. If no provider claims a constraint that arrived as an obligation, or if more than one provider claims the same constraint, the planner schedules a synthetic failure runner so the decision fails closed.

A `ScopedHandler` bundles three things.

| Field      | Description                                                                                          |
| ---------- | ---------------------------------------------------------------------------------------------------- |
| `signal`   | The `SignalKind` the handler attaches to. The decision signal runs once when the decision arrives. The output signal runs on the return value or on each streamed item. |
| `priority` | Lower runs earlier among handlers on the same signal.                                                |
| `shape`    | `"runner"` is `() -> None`, `"consumer"` is `(value) -> None`, `"mapper"` is `(value) -> value`.     |
| `handler`  | The callable itself.                                                                                  |

The three shapes mirror the work a handler does. A `runner` is a side effect that needs no value, such as logging on a decision. A `consumer` is a side effect that has access to the value but does not change it, such as auditing the response. A `mapper` transforms the value flowing through a data-carrying signal, such as redacting fields. A mapper is admissible only for an obligation, never for advice. Advice is allowed to fail silently, and a value transformation that silently did not happen would leave the caller unable to tell whether the result was transformed.

#### Registering Custom Handlers

Register providers during application startup, inside the lifespan function:

```python
from collections.abc import Sequence
from typing import Any

from sapl_fastapi import configure_sapl, register_provider, SaplConfig
from sapl_base.pep import DECISION, ScopedHandler


class LogAccessProvider:
    def get_handlers(self, constraint: Any) -> Sequence[ScopedHandler]:
        if not (isinstance(constraint, dict) and constraint.get("type") == "logAccess"):
            return ()
        message = constraint.get("message", "Access logged")

        def run() -> None:
            print(f"[POLICY] {message}")

        return (ScopedHandler(signal=DECISION, priority=0, shape="runner", handler=run),)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    configure_sapl(SaplConfig(base_url="https://localhost:8443"))
    register_provider(LogAccessProvider())
    yield
    await cleanup_sapl()
```

Registration rebuilds the planner. A single obligation can drive several handlers at different signals. The provider returns one `ScopedHandler` per handler, and the planner schedules each one against its own signal. The bundle is all-or-nothing during admissibility checks. If any handler in the returned sequence is not well-formed for the constraint's tag, the entire claim is rejected and the decision fails closed.

### Built-in Constraint Handlers

#### ContentFilteringProvider

**Constraint type:** `filterJsonContent`

Registered automatically by `configure_sapl()`. Transforms response values by deleting, replacing, or blackening fields.

A policy can attach this obligation:

```
policy "permit-read-patient"
permit
  action == "readPatient";
  resource == "patient";
obligation
  {
    "type": "filterJsonContent",
    "actions": [
      { "type": "blacken", "path": "$.ssn", "discloseRight": 4 },
      { "type": "delete", "path": "$.internalNotes" },
      { "type": "replace", "path": "$.classification", "replacement": "REDACTED" }
    ]
  }
```

The `blacken` action supports these options:

| Option          | Type   | Default                       | Description                                 |
| --------------- | ------ | ----------------------------- | ------------------------------------------- |
| `path`          | string | (required)                    | Dot-notation path to a string field         |
| `replacement`   | string | `"\u2588"` (block character)  | Character used for masking                  |
| `discloseLeft`  | number | `0`                           | Characters to leave unmasked from the left  |
| `discloseRight` | number | `0`                           | Characters to leave unmasked from the right |
| `length`        | number | (masked section length)       | Override the length of the masked section   |

#### ContentFilterPredicateProvider

**Constraint type:** `jsonContentFilterPredicate`

Registered automatically by `configure_sapl()`. Filters array elements or nullifies single values that do not meet conditions.

```json
{
  "type": "jsonContentFilterPredicate",
  "conditions": [
    { "path": "$.classification", "type": "!=", "value": "top-secret" }
  ]
}
```

#### ContentFilter Limitations

The built-in content filter supports **simple dot-notation paths only** (`$.field.nested`). Recursive descent (`$..ssn`), bracket notation (`$['field']`), array indexing (`$.items[0]`), wildcards (`$.users[*].email`), and filter expressions (`$.books[?(@.price<10)]`) are not supported.

### Query Rewriting

FastAPI applications can filter results at the database through SAPL's SQLAlchemy integration, the `sapl-sqlalchemy` package: a policy attaches a `sql:queryRewriting` obligation and the integration rewrites the query before it reaches the database, so unauthorised rows never leave it. Install it separately and register it once at startup.

```bash
pip install sapl-sqlalchemy
```

```python
from sapl_sqlalchemy import SqlQueryRewritingProvider, register_orm_listener
from sapl_fastapi import register_provider

register_orm_listener()
register_provider(SqlQueryRewritingProvider())
```

See [Query Rewriting](../6_12_QueryRewriting/) for the obligation format, the shared semantics, and what the integration does and does not cover (including the off-session fail-open caveat).

### Streaming Authorization

For SSE endpoints returning async iterators, `@stream_enforce` provides continuous authorization where the PDP streams decisions over time. Access may flip between permitted, suspended, and denied based on time, location, or context changes.

The decorator returns a Starlette `StreamingResponse` with `media_type="text/event-stream"`. Each yielded item is rendered as an SSE `data:` event (dicts are JSON-serialized).

A time-based policy that cycles between `PERMIT` and `SUSPEND`, so the stream pauses and resumes without terminating:

```
policy "streaming-heartbeat-time-based"
permit
  action == "stream:heartbeat";
  resource == "heartbeat";
  var second = time.secondOf(<time.now>);
  second >= 0 && second < 20 || second >= 40;
suspend
  action == "stream:heartbeat";
  resource == "heartbeat";
```

Connect with curl to observe streaming behavior:

```bash
curl -N http://localhost:3000/stream/heartbeat
```

### Manual PDP Access

For cases where decorators are not suitable, access the PDP client directly:

```python
from fastapi import FastAPI, HTTPException, Request
from sapl_fastapi import get_pdp_client
from sapl_base.types import AuthorizationSubscription, Decision

app = FastAPI()


@app.get("/hello")
async def get_hello(request: Request):
    pdp_client = get_pdp_client()
    subscription = AuthorizationSubscription(
        subject="anonymous",
        action="read",
        resource="hello",
    )
    decision = await pdp_client.decide_once(subscription)

    if decision.decision == Decision.PERMIT and not decision.obligations:
        return {"message": "hello"}
    raise HTTPException(status_code=403, detail="Access denied")
```

When using the PDP client directly, you are responsible for checking the decision, enforcing obligations, and handling resource replacement.

### Service Layer Enforcement

The same `@pre_enforce` and `@post_enforce` decorators work at any layer, not just on FastAPI endpoints. When used on a service method without a `Request` parameter, the decorator automatically translates denial into `HTTPException(403)`:

```python
from sapl_fastapi import pre_enforce, post_enforce


@pre_enforce(action="listPatients", resource="patients")
async def list_patients() -> list[dict]:
    return [dict(p) for p in PATIENTS]


@post_enforce(
    action="getPatientDetail",
    resource=lambda ctx: {"type": "patientDetail", "data": ctx.return_value},
)
async def get_patient_detail(patient_id: str) -> dict | None:
    return next((dict(p) for p in PATIENTS if p["id"] == patient_id), None)
```

The calling endpoint does not need any special error handling. The `HTTPException` propagates through FastAPI's normal exception handling and returns HTTP 403:

```python
from fastapi import FastAPI, Request
from services import patient_service

app = FastAPI()


@app.get("/services/patients/{patient_id}")
async def get_patient_detail(request: Request, patient_id: str):
    result = await patient_service.get_patient_detail(patient_id)
    return result
```

Service-layer decorators accept the same subscription field options (`subject`, `action`, `resource`, `environment`, `secrets`) as when used on endpoints. When no `Request` is available, subject defaults to `"anonymous"` and environment is empty.

### Database Transactions

`@pre_enforce` and `@post_enforce` can own a transaction boundary, so a denial that lands after the endpoint has written to the database rolls the write back. Three triggers cause a rollback: a `@post_enforce` DENY, a `@post_enforce` output-obligation failure, and a `@pre_enforce` output-obligation failure (the pre-decision permits, but its output obligations run after the endpoint writes). A clean PERMIT commits.

This is opt-in. With no provider configured the PEP owns no transaction and enforcement behaves exactly as before. A provider is a zero-arg factory returning a context manager that commits on clean exit and rolls back on a propagated exception. It must match the endpoint kind it protects: a sync context manager for sync endpoints, an async one for async endpoints.

Async endpoints run on the async core, which uses the provider as an async context manager, so pass an async SQLAlchemy `AsyncSession.begin()` directly:

```python
from sapl_fastapi.dependencies import set_transaction_provider

set_transaction_provider(lambda: get_current_async_session().begin())
```

Sync endpoints run on the blocking core, which uses the provider as a sync context manager, so pass a sync context-manager factory directly. A sync SQLAlchemy session built from `create_engine` plus `session.begin` is exactly such a factory:

```python
set_transaction_provider(lambda: get_current_sync_session().begin())
```

The factory should resolve the current request's session (for example a request-scoped session held in a contextvar).

### Client Resilience

The PDP client treats every transport problem as an operational condition, never as a policy outcome, and never lets one surface as an exception. A connection drop, timeout, or decode error fails closed to `INDETERMINATE`, which the PEP enforces as a denial, so a transient PDP outage can never accidentally grant access.

One-shot requests (`decide_once`) fail closed to `INDETERMINATE` immediately, with no retry, and never throw. In steady state the connection is warm, so only a cold or dropped connection fails closed.

Subscriptions (streaming `decide`) never terminate on a transport problem or on a server-side stream completion. Either condition emits one `INDETERMINATE` and then reconnects with bounded exponential backoff, indefinitely. Consecutive identical decisions are de-duplicated, so an outage yields a single `INDETERMINATE`, not a flood. A subscription ends only when the consumer cancels it or the client shuts down. This contract holds identically across the HTTP and RSocket transports and across every SAPL PEP client.

### Demo Application

A complete working demo is available at [sapl-python-demos/fastapi_demo](https://github.com/heutelbeck/sapl-python-demos/tree/main/fastapi_demo). It includes:

- Manual PDP access (no decorators)
- `@pre_enforce` and `@post_enforce` with content filtering
- Service-layer enforcement using the same decorators on plain async functions
- Custom constraint handler providers returning runner, consumer, and mapper handlers
- SSE streaming with `@stream_enforce`, covering terminate-on-deny, drop-while-suspended, and signalled suspend/resume
- JWT-based ABAC with secrets

### Configuration Reference

All options are set via the `SaplConfig` dataclass passed to `configure_sapl()`:

| Parameter                    | Type    | Default                     | Description                                              |
| ---------------------------- | ------- | --------------------------- | -------------------------------------------------------- |
| `base_url`                            | `str`   | `"https://localhost:8443"`  | PDP server URL. Plain `http://` is accepted only for loopback hosts |
| `token`                               | `str`   | `None`                      | Bearer token / API key for authentication                |
| `username`                            | `str`   | `None`                      | Basic auth username (mutually exclusive with `token`)    |
| `secret`                              | `str`   | `None`                      | Basic auth secret                                        |
| `timeout_seconds`                     | `float` | `5.0`                       | PDP request timeout in seconds                           |
| `streaming_retry_base_delay_seconds`  | `float` | `1.0`                       | Base delay in seconds for exponential backoff on retry   |
| `streaming_retry_max_delay_seconds`   | `float` | `30.0`                      | Maximum delay in seconds for exponential backoff         |

### Troubleshooting

| Symptom                              | Likely Cause                            | Fix                                                              |
| ------------------------------------ | --------------------------------------- | ---------------------------------------------------------------- |
| All decisions are INDETERMINATE       | PDP unreachable                         | Check `base_url` and that PDP is running                         |
| 403 despite PERMIT decision           | Unhandled obligation                    | Check the provider's `get_handlers()` claims the obligation `type` |
| Handler not firing                    | Missing registration                    | Call `register_provider()` in lifespan                           |
| Subject is `"anonymous"`              | No auth middleware setting `state.user` | Set `request.state.user` in auth dependency or middleware        |
| Content filter throws                 | Unsupported path syntax                 | Only simple dot paths supported (`$.field.nested`)               |
| `RuntimeError: SAPL not configured`   | Missing `configure_sapl()`              | Call `configure_sapl()` in lifespan before yield                 |
| `RuntimeError: No Request object`     | Missing `request: Request` parameter    | Add `request: Request` to endpoint function signature            |
| Streaming response not SSE            | Missing `text/event-stream` content     | Use streaming decorators; they set the content type automatically |

### License

Apache-2.0
