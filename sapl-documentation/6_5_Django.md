---
layout: default
title: Django
#permalink: /python-django/
has_children: false
parent: SDKs and APIs
nav_order: 605
---

## Django SDK

Attribute-Based Access Control (ABAC) for Django using SAPL (Streaming Attribute Policy Language). Provides decorator-driven policy enforcement with a constraint handler architecture for obligations, advice, and response transformation.

The `sapl-django` library integrates SAPL policy enforcement into Django applications, supporting both synchronous and asynchronous views, with Server-Sent Events streaming for continuous authorization.

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

`decision` is always present (`PERMIT`, `DENY`, `SUSPEND`, `INDETERMINATE`, or `NOT_APPLICABLE`). The other fields are optional. `obligations` and `advice` are arrays of arbitrary JSON objects (by convention with a `type` field for handler dispatch), and `resource` (when present) replaces the view's return value entirely.

For a deeper introduction to SAPL's subscription model and policy language, see the [SAPL documentation](https://sapl.io/docs/latest/).

### Installation

Install the library and the base dependency:

```bash
pip install sapl-django
```

This also installs `sapl-base`, which provides the PDP client, the `EnforcementPlanner`, and content filtering. The library requires Python 3.12 or later and Django 4.2+.

A complete working demo with constraint handlers, content filtering, and streaming enforcement is available at [sapl-python-demos/django_demo](https://github.com/heutelbeck/sapl-python-demos/tree/main/django_demo).

### Setup

#### Configuration via Django Settings

Add `SAPL_CONFIG` to your Django settings module:

```python
# settings.py

SAPL_CONFIG = {
    "base_url": "https://localhost:8443",
    "token": "sapl_your_api_key_here",
}
```

For basic authentication instead of an API key:

```python
SAPL_CONFIG = {
    "base_url": "https://localhost:8443",
    "username": "myPdpClient",
    "secret": "myPassword",
}
```

`token` (API key) and `username`/`secret` (Basic Auth) are mutually exclusive. Configure one or the other.

For local development without TLS, point `base_url` at a loopback host. A plain `http://` URL is accepted only when the host is `localhost`, `127.0.0.1`, or `::1`. Any plain-HTTP URL targeting a remote host is refused at construction time, so plaintext authorization decisions never leave the machine.

```python
SAPL_CONFIG = {
    "base_url": "http://localhost:8443",
}
```

#### Middleware

Add `SaplRequestMiddleware` to the `MIDDLEWARE` list. It propagates the current `HttpRequest` via `contextvars` so the subscription builder can access it during enforcement:

```python
# settings.py

MIDDLEWARE = [
    "sapl_django.middleware.SaplRequestMiddleware",
    "django.middleware.common.CommonMiddleware",
    # ...
]
```

The middleware supports both synchronous (`__call__`) and asynchronous (`__acall__`) request handling.

#### Installed Apps

Add `sapl_django` to `INSTALLED_APPS`:

```python
INSTALLED_APPS = [
    "django.contrib.contenttypes",
    "django.contrib.auth",
    "sapl_django",
    # your apps ...
]
```

The PDP client and the `EnforcementPlanner` are created lazily on first use from `SAPL_CONFIG`. The built-in `ContentFilteringProvider` and `ContentFilterPredicateProvider` are registered automatically. No explicit initialization call is required.

### Enforcement Decorators

The `@pre_enforce` and `@post_enforce` decorators work on both synchronous (`def`) and asynchronous (`async def`) Django view functions; `@stream_enforce` requires an async view served under ASGI. The decorated view must accept `request: HttpRequest` as a parameter (typically the first argument).

The decorators auto-detect the view kind, so you write the view in whichever style suits it. An async view runs on the async enforcement core. A sync view runs on the blocking core, which executes the view off the event loop, so synchronous Django ORM access works normally with no `SynchronousOnlyOperation`. When you configure a transaction provider (see [Database Transactions](#database-transactions)) it must match the view kind: a sync context-manager factory such as `transaction.atomic` for sync views, an async one for async views.

#### @pre_enforce

Authorizes **before** the view executes. The view only runs on PERMIT.

```python
from django.http import HttpRequest, JsonResponse
from sapl_django import pre_enforce


@pre_enforce(action="read", resource="patient")
async def get_patient(request: HttpRequest, patient_id: str) -> JsonResponse:
    return JsonResponse({"id": patient_id, "name": "Jane Doe", "ssn": "123-45-6789"})
```

Use `@pre_enforce` for views with side effects (database writes, emails) that should not execute when access is denied. On denial, Django's `PermissionDenied` exception is raised, which returns HTTP 403.

#### @post_enforce

Authorizes **after** the view executes. The view always runs; its return value is available to the subscription builder via the `return_value` argument.

```python
from django.http import HttpRequest, JsonResponse
from sapl_django import post_enforce


@post_enforce(
    action="read",
    resource=lambda ctx: {"type": "record", "data": ctx.return_value},
)
async def get_record(request: HttpRequest, record_id: str) -> JsonResponse:
    return JsonResponse({"id": record_id, "value": "sensitive-data"})
```

Use `@post_enforce` when the policy needs to see the actual return value to make its authorization decision (e.g., deny based on the data's classification). On denial, the return value is discarded and `PermissionDenied` is raised.

#### Building the Authorization Subscription

Each decorator accepts keyword arguments to customize the authorization subscription fields: `subject`, `action`, `resource`, `environment`, and `secrets`.

**Default Values**

When not explicitly provided, the subscription fields are derived from the Django `HttpRequest`:

| Field         | Default                                                                     |
| ------------- | --------------------------------------------------------------------------- |
| `subject`     | `request.user.username` or `"anonymous"` if no authenticated user           |
| `action`      | `{"method": request.method, "view": function_name}`                         |
| `resource`    | `{"path": request.path, "kwargs": resolver_match.kwargs}`                   |
| `environment` | `{"ip": request.META["REMOTE_ADDR"]}` (when available)                      |
| `secrets`     | Not sent unless explicitly specified                                        |

**Static Values**

Pass a string or dict directly:

```python
@pre_enforce(action="read", resource="patient")
```

**Dynamic Values (Callables)**

Pass a callable that receives a `SubscriptionContext` and returns the field value. The context provides `request`, `return_value` (`None` for `@pre_enforce`), `params` (URL kwargs), `query` (query string), and `args` (resolved function arguments):

```python
@pre_enforce(
    subject=lambda ctx: ctx.request.user.username,
    resource=lambda ctx: {"path": ctx.request.path, "method": ctx.request.method},
)
```

**Secrets**

The `secrets` field carries sensitive data (tokens, API keys) that the PDP needs for policy evaluation but that must not appear in logs. It is excluded from debug logging automatically. Use it when a policy needs to inspect credentials, for example passing a raw JWT so the PDP can read its claims:

```python
@pre_enforce(
    action="exportData",
    resource=lambda ctx: {"pilotId": ctx.params.get("pilot_id")},
    secrets=lambda ctx: {"jwt": getattr(ctx.request, "sapl_token", None)} if ctx.request else None,
)
```

#### @stream_enforce

Streaming enforcement for SSE endpoints. The decorated view returns an async iterator of data items. The wrapper opens a streaming PDP subscription, drives the streaming state machine, and returns a Django `StreamingHttpResponse` whose body is each item rendered as an SSE `data:` frame on `text/event-stream`.

```python
import asyncio
from datetime import datetime, timezone
from django.http import HttpRequest
from sapl_django import stream_enforce


@stream_enforce(action="stream:heartbeat", resource="heartbeat")
async def heartbeat(request: HttpRequest):
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
| `PERMIT`         | Items flow through to the client as SSE frames.                                                       |
| `SUSPEND`        | Items are silently dropped. The subscription stays open. A later `PERMIT` resumes the flow.            |
| `DENY`           | The subscription terminates. A final `ACCESS_DENIED` SSE frame is emitted and the stream closes.       |
| `INDETERMINATE`  | The subscription terminates, the same way `DENY` does.                                                 |
| `NOT_APPLICABLE` | The subscription terminates, the same way `DENY` does.                                                 |

Under the strict fail-closed discipline only an explicit `SUSPEND` keeps the subscription alive while pausing it. `DENY`, `INDETERMINATE`, and `NOT_APPLICABLE` all terminate. For keep-alive semantics where access pauses and later resumes, the policy must emit `SUSPEND` rather than `DENY`. Operators who want `NOT_APPLICABLE` to pause rather than terminate set the combining algorithm's `defaultDecision` to `SUSPEND` at the PDP level.

**signal_transitions.** With the default `False`, suspend and resume boundaries are silent. The client sees items while permitted and a gap while suspended, with no boundary frame. With `True`, the wrapper emits an `ACCESS_SUSPENDED` SSE frame each time the stream is suspended and an `ACCESS_RESTORED` SSE frame each time it resumes. Use this when the client should render a paused/resumed status.

**pause_rap_during_suspend.** With the default `False`, the protected async iterator stays subscribed during suspension. Items keep arriving from upstream and are dropped on the way to the client, giving lower latency on resume. With `True`, the upstream iterator is cancelled on entry to the suspended state and re-subscribed on resume. Use this for upstream sources with expensive side effects that must not run while access is paused.

| Scenario                                       | Configuration                                                |
| ---------------------------------------------- | ------------------------------------------------------------ |
| Access loss is permanent (revoked credentials)  | policy emits `deny`; defaults                                |
| Client does not need to know about gaps         | policy emits `suspend`; defaults                             |
| Client should show suspended/restored status    | policy emits `suspend`; `signal_transitions=True`            |

### How Enforcement Works

The decorators above are convenient, but to use them well it helps to understand what actually happens behind the scenes. This section walks through the enforcement lifecycle so you can reason about behavior.

#### The Deny Invariant

Only `PERMIT` grants access. The PDP can return five possible decisions (`PERMIT`, `DENY`, `SUSPEND`, `INDETERMINATE`, `NOT_APPLICABLE`), and only `PERMIT` ever results in your view running or your stream forwarding data. Everything else means denial. The streaming PEP honours `SUSPEND` by pausing the stream while keeping the subscription alive, so a later `PERMIT` resumes it. One-shot enforcement (`@pre_enforce`, `@post_enforce`) treats `SUSPEND` as a denial. See [Authorization Decisions](../2_3_AuthorizationDecisions/) for details.

A `PERMIT` with obligations is not a free pass. The PEP checks that every obligation in the decision has a registered handler. If even one obligation cannot be fulfilled, the PEP treats the decision as a denial. If a handler accepts responsibility but fails during execution, that also results in denial. Advice is softer: if an advice handler fails, the PEP logs the failure and moves on. Advice never causes denial.

| Aspect          | Obligation                                                         | Advice                                         |
|-----------------|--------------------------------------------------------------------|-------------------------------------------------|
| All handled?    | Required. Unhandled obligations deny access (PermissionDenied).    | Optional. Unhandled advice is silently ignored. |
| Handler failure | Denies access (PermissionDenied).                                  | Logs a warning and continues.                   |

This means you can always trust that if your view runs, every obligation attached to the decision has been successfully enforced.

#### Enforcement Locations

Depending on the decorator, constraint handlers can intervene at different points in the lifecycle of a request or stream.

For request-response views (`@pre_enforce` and `@post_enforce`), constraints can run at four points:

| Location              | When it happens                      | What constraints do here                         |
|-----------------------|--------------------------------------|--------------------------------------------------|
| On decision           | Authorization decision arrives       | Side effects like logging, audit, or notification |
| Pre-method invocation | Before the protected view executes   | Modify view arguments (`@pre_enforce` only)      |
| On return value       | After the view returns               | Transform, filter, or replace the result         |
| On error              | If the view throws                   | Transform or observe the error                   |

For streaming views (`@stream_enforce`), constraints can run at five points:

| Location           | When it happens                              | What constraints do here                |
|--------------------|----------------------------------------------|-----------------------------------------|
| On decision        | Each new decision from the PDP stream        | Side effects like logging, audit        |
| On each data item  | Each element yielded by the async iterator   | Transform, filter, or replace items     |
| On stream error    | The iterator produces an error               | Transform or observe the error          |
| On stream complete | The iterator finishes normally               | Cleanup and finalization                |
| On cancel          | Client disconnects or enforcement terminates | Release resources and close connections |

SAPL models each of these points as a named signal, and a handler attaches to whichever signal fits the work it does. A handler that fires once when the decision arrives attaches to the decision signal. A handler that processes each emitted item attaches to the output signal. The signal a handler attaches to determines when it runs. The same `ConstraintHandlerProvider` mechanism is used for one-shot and streaming enforcement alike.

#### PreEnforce Lifecycle

When you decorate a view with `@pre_enforce`, here is what happens step by step.

First, the PEP builds an authorization subscription from the decorator options (or from defaults if you left them out) and sends it to the PDP as a one-shot request. The PDP evaluates the subscription against all matching policies and returns a single decision.

If the decision is anything other than `PERMIT`, the PEP raises `PermissionDenied` immediately. Your view never runs.

If the decision is `PERMIT`, the PEP resolves all constraint handlers. It walks through the obligations and advice attached to the decision and checks which registered handlers claim responsibility for each one. If any obligation has no matching handler, the PEP denies access right there, because it cannot guarantee the obligation will be enforced.

With all handlers resolved, execution proceeds through the enforcement locations in order. On-decision handlers run first (logging, audit). Then method-invocation handlers run, which can modify view arguments if the policy requires it. Then your actual view executes. After the view returns, the PEP applies return-value handlers: resource replacement if the decision included one, filter predicates, mapping handlers, and consumer handlers. If any obligation handler fails at any stage, the PEP denies access.

#### PostEnforce Lifecycle

`@post_enforce` inverts the order. Your view runs first, regardless of the authorization outcome. Only after it returns does the PEP build the authorization subscription (now including the return value) and consult the PDP.

This means the PDP can make decisions based on the actual data your view produced. For example, a policy might permit access to a record only if its classification level is below a threshold, something that can only be checked after loading the record.

If the decision is not `PERMIT`, the PEP discards the return value and raises `PermissionDenied`.

If the decision is `PERMIT`, constraint handlers proceed through the same stages as `@pre_enforce`, minus the method-invocation handlers (since the view has already run). Return-value handlers can still transform the result before it reaches the caller.

Because the view runs before the PDP is consulted, if the view itself raises an exception, that exception propagates directly. The PDP is never called, because there is no return value to include in the subscription.

SAPL PEP libraries share a single unified enforcement model. It is a strict fail-closed state machine over the five decision verbs, where only `PERMIT` grants access and only an explicit `SUSPEND` pauses a stream without terminating it. See [Authorization Decisions](../2_3_AuthorizationDecisions/) for the decision-verb semantics.

### Constraint Handlers

When the PDP returns a decision with `obligations` or `advice`, the `EnforcementPlanner` resolves and schedules all matching handlers.

#### The ConstraintHandlerProvider Protocol

There is one extension point. A constraint handler is an object that implements the `ConstraintHandlerProvider` protocol, which has a single method.

```python
from collections.abc import Sequence
from typing import Any

from sapl_base.pep import ConstraintHandlerProvider, ScopedHandler


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

```python
from collections.abc import Sequence
from typing import Any

from sapl_django import register_provider
from sapl_base.pep import DECISION, ScopedHandler


class LogAccessProvider:
    def get_handlers(self, constraint: Any) -> Sequence[ScopedHandler]:
        if not (isinstance(constraint, dict) and constraint.get("type") == "logAccess"):
            return ()
        message = constraint.get("message", "Access logged")

        def run() -> None:
            print(f"[POLICY] {message}")

        return (ScopedHandler(signal=DECISION, priority=0, shape="runner", handler=run),)


# Register during Django app startup (e.g., in AppConfig.ready())
register_provider(LogAccessProvider())
```

Register providers in your Django `AppConfig.ready()` method so they are available when the first request arrives. Registration rebuilds the planner.

A single obligation can drive several handlers at different signals. The provider returns one `ScopedHandler` per handler, and the planner schedules each one against its own signal. The bundle is all-or-nothing during admissibility checks. If any handler in the returned sequence is not well-formed for the constraint's tag, the entire claim is rejected and the decision fails closed.

### Built-in Constraint Handlers

#### ContentFilteringProvider

**Constraint type:** `filterJsonContent`

Transforms response values by deleting, replacing, or blackening fields.

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

Filters array elements or nullifies single values that do not meet conditions.

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

### Streaming Authorization

For SSE endpoints returning async iterators, `@stream_enforce` provides continuous authorization where the PDP streams decisions over time. Access may flip between permitted, suspended, and denied based on time, location, or context changes.

Django streaming responses use `StreamingHttpResponse` with `content_type="text/event-stream"`. The decorator wraps each yielded item in SSE format automatically.

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

Deploy with ASGI (e.g., Daphne or Uvicorn) for async view and streaming support:

```bash
uvicorn demo_project.asgi:application --host 0.0.0.0 --port 3000
```

### Manual PDP Access

For cases where decorators are not suitable, access the PDP client directly:

```python
from django.http import HttpRequest, JsonResponse
from sapl_django import get_pdp_client
from sapl_base.types import AuthorizationSubscription, Decision


async def get_hello(request: HttpRequest) -> JsonResponse:
    pdp_client = get_pdp_client()
    subscription = AuthorizationSubscription(
        subject="anonymous",
        action="read",
        resource="hello",
    )
    decision = await pdp_client.decide_once(subscription)

    if decision.decision == Decision.PERMIT and not decision.obligations:
        return JsonResponse({"message": "hello"})
    return JsonResponse({"error": "Access denied"}, status=403)
```

When using the PDP client directly, you are responsible for checking the decision, enforcing obligations, and handling resource replacement.

### Service Layer Enforcement

The same `@pre_enforce` and `@post_enforce` decorators work at any layer, not just on Django views. When used on a service method without an `HttpRequest` parameter, the decorator automatically translates denial into Django's `PermissionDenied` exception, which the calling view can handle normally:

```python
from sapl_django import pre_enforce, post_enforce


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

The calling view does not need any special error handling. `PermissionDenied` propagates through Django's normal exception handling and returns HTTP 403:

```python
from django.http import HttpRequest, JsonResponse
from . import patient_service


async def get_patient_detail(request: HttpRequest, patient_id: str) -> JsonResponse:
    result = await patient_service.get_patient_detail(patient_id)
    return JsonResponse(result)
```

Service-layer decorators accept the same subscription field options (`subject`, `action`, `resource`, `environment`, `secrets`) as when used on views. When no `HttpRequest` is available, subject defaults to `"anonymous"` and environment is empty.

### Database Transactions

`@pre_enforce` and `@post_enforce` can own a transaction boundary, so a denial that lands after the view has written to the database rolls the write back. Three triggers cause a rollback: a `@post_enforce` DENY, a `@post_enforce` output-obligation failure, and a `@pre_enforce` output-obligation failure (the pre-decision permits, but its output obligations run after the view writes). A clean PERMIT commits.

This is opt-in. With no provider configured the PEP owns no transaction and enforcement behaves exactly as before. A provider is a zero-arg factory returning a context manager that commits on clean exit and rolls back on a propagated exception. It must match the view kind it protects: a sync context manager for sync views, an async one for async views.

Sync views run on the blocking core, which uses the provider as a sync context manager. `transaction.atomic` is exactly such a factory, so pass it directly:

```python
from django.db import transaction
from sapl_django.config import set_transaction_provider

set_transaction_provider(transaction.atomic)
```

A sync SQLAlchemy `session.begin` is passed the same way: `set_transaction_provider(lambda: get_current_session().begin())`. Async views run on the async core, which uses the provider as an async context manager, so pass an async SQLAlchemy `AsyncSession.begin()` directly: `set_transaction_provider(lambda: get_current_async_session().begin())`.

Transactional enforcement with the Django ORM is a sync-view feature. Django's `transaction.atomic` is async-unsafe, so it cannot run on an async view. The async enforcement core opens the transaction boundary on the event loop thread, where entering `transaction.atomic` raises `SynchronousOnlyOperation`. To wrap a Django ORM write in an enforced transaction, write the view as a sync `def` and pass `transaction.atomic` directly, as above. Async views can still own a transaction over an async-native resource such as async SQLAlchemy, but not over the Django ORM.

### Client Resilience

The PDP client treats every transport problem as an operational condition, never as a policy outcome, and never lets one surface as an exception. A connection drop, timeout, or decode error fails closed to `INDETERMINATE`, which the PEP enforces as a denial, so a transient PDP outage can never accidentally grant access.

One-shot requests (`decide_once`) fail closed to `INDETERMINATE` immediately, with no retry, and never throw. In steady state the connection is warm, so only a cold or dropped connection fails closed.

Subscriptions (streaming `decide`) never terminate on a transport problem or on a server-side stream completion. Either condition emits one `INDETERMINATE` and then reconnects with bounded exponential backoff, indefinitely. Consecutive identical decisions are de-duplicated, so an outage yields a single `INDETERMINATE`, not a flood. A subscription ends only when the consumer cancels it or the client shuts down. This contract holds identically across the HTTP and RSocket transports and across every SAPL PEP client.

### Demo Application

A complete working demo is available at [sapl-python-demos/django_demo](https://github.com/heutelbeck/sapl-python-demos/tree/main/django_demo). It includes:

- Manual PDP access (no decorators)
- `@pre_enforce` and `@post_enforce` with content filtering
- Service-layer enforcement using the same decorators on plain async functions
- Custom constraint handler providers returning runner, consumer, and mapper handlers
- SSE streaming with `@stream_enforce`, covering terminate-on-deny, drop-while-suspended, and signalled suspend/resume
- JWT-based ABAC with secrets

### Configuration Reference

All options are set via the `SAPL_CONFIG` dictionary in Django settings:

| Key                            | Type    | Default                     | Description                                              |
| ------------------------------ | ------- | --------------------------- | -------------------------------------------------------- |
| `base_url`                            | `str`   | `"https://localhost:8443"`  | PDP server URL. Plain `http://` is accepted only for loopback hosts |
| `token`                               | `str`   | `None`                      | Bearer token / API key for authentication                |
| `username`                            | `str`   | `None`                      | Basic auth username (mutually exclusive with `token`)    |
| `secret`                              | `str`   | `None`                      | Basic auth secret                                        |
| `timeout_seconds`                     | `float` | `5.0`                       | PDP request timeout in seconds                           |
| `streaming_retry_base_delay_seconds`  | `float` | `1.0`                       | Base delay in seconds for exponential backoff on reconnect |
| `streaming_retry_max_delay_seconds`   | `float` | `30.0`                      | Maximum delay in seconds for exponential backoff         |

### Troubleshooting

| Symptom                              | Likely Cause                            | Fix                                                              |
| ------------------------------------ | --------------------------------------- | ---------------------------------------------------------------- |
| All decisions are INDETERMINATE       | PDP unreachable                         | Check `base_url` and that PDP is running                         |
| 403 despite PERMIT decision           | Unhandled obligation                    | Check the provider's `get_handlers()` claims the obligation `type` |
| Handler not firing                    | Missing registration                    | Call `register_provider()` in `AppConfig.ready()`               |
| Subject is `"anonymous"`              | No authenticated user on request        | Set up Django authentication or set subject explicitly           |
| Content filter throws                 | Unsupported path syntax                 | Only simple dot paths supported (`$.field.nested`)               |
| `ImproperlyConfigured`                | Missing `SAPL_CONFIG`                   | Add `SAPL_CONFIG` dict to Django settings                        |
| Streaming not working                 | Running under WSGI                      | Use ASGI server (Uvicorn/Daphne) for async views                 |

### License

Apache-2.0
