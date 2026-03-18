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

The `sapl-django` library integrates SAPL policy enforcement into Django applications with asynchronous (ASGI) views and Server-Sent Events streaming for continuous authorization.

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

`decision` is always present (`PERMIT`, `DENY`, `INDETERMINATE`, or `NOT_APPLICABLE`). The other fields are optional. `obligations` and `advice` are arrays of arbitrary JSON objects (by convention with a `type` field for handler dispatch), and `resource` (when present) replaces the view's return value entirely.

For a deeper introduction to SAPL's subscription model and policy language, see the [SAPL documentation](https://sapl.io/docs/latest/).

### Installation

Install the library and the base dependency:

```bash
pip install sapl-django
```

This also installs `sapl-base`, which provides the PDP client, constraint engine, and content filtering. The library requires Python 3.12 or later and Django 4.2+.

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
    "password": "myPassword",
}
```

`token` (API key) and `username`/`password` (Basic Auth) are mutually exclusive. Configure one or the other.

For local development without TLS:

```python
SAPL_CONFIG = {
    "base_url": "http://localhost:8443",
    "allow_insecure_connections": True,
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

The PDP client and constraint enforcement service are created lazily on first use from `SAPL_CONFIG`. No explicit initialization call is required.

### Enforcement Decorators

All decorators work on async Django view functions. The decorated view must accept `request: HttpRequest` as a parameter (typically the first argument).

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

**Custom Deny Handling**

Add `on_deny` to any `@pre_enforce` or `@post_enforce` to return a custom response instead of raising `PermissionDenied`:

```python
@pre_enforce(
    action="exportData",
    on_deny=lambda decision: JsonResponse(
        {"error": "access_denied", "decision": decision.decision.value},
        status=403,
    ),
)
```

#### @enforce_till_denied

Streaming enforcement that **terminates permanently** on the first non-PERMIT decision. The decorated view must return an async generator. Returns a Django `StreamingHttpResponse` with SSE format.

```python
import asyncio
from datetime import datetime, timezone
from django.http import HttpRequest, StreamingHttpResponse
from sapl_django import enforce_till_denied


@enforce_till_denied(
    action="stream:heartbeat",
    resource="heartbeat",
    on_stream_deny=lambda decision: {"type": "ACCESS_DENIED"},
)
async def heartbeat(request: HttpRequest):
    seq = 0
    while True:
        yield {"seq": seq, "ts": datetime.now(timezone.utc).isoformat()}
        seq += 1
        await asyncio.sleep(2)
```

The `on_stream_deny` callback receives the PDP decision and can return a final data item that is sent to the client before the stream terminates.

#### @enforce_drop_while_denied

Silently **drops data** during DENY periods. The stream stays alive and resumes forwarding when a new PERMIT decision arrives.

```python
from sapl_django import enforce_drop_while_denied


@enforce_drop_while_denied(action="stream:heartbeat", resource="heartbeat")
async def heartbeat(request: HttpRequest):
    seq = 0
    while True:
        yield {"seq": seq}
        seq += 1
        await asyncio.sleep(2)
```

The client sees gaps in sequence numbers but the connection remains open. No signals are sent during DENY periods.

#### @enforce_recoverable_if_denied

Sends **in-band suspend/resume signals** on policy transitions. Edge-triggered: `on_stream_deny` fires on PERMIT-to-DENY transitions, `on_stream_recover` fires on DENY-to-PERMIT transitions.

```python
from sapl_django import enforce_recoverable_if_denied


@enforce_recoverable_if_denied(
    action="stream:heartbeat",
    resource="heartbeat",
    on_stream_deny=lambda decision: {"type": "ACCESS_SUSPENDED"},
    on_stream_recover=lambda decision: {"type": "ACCESS_RESTORED"},
)
async def heartbeat(request: HttpRequest):
    seq = 0
    while True:
        yield {"seq": seq}
        seq += 1
        await asyncio.sleep(2)
```

| Scenario                                       | Strategy                          |
| ---------------------------------------------- | --------------------------------- |
| Access loss is permanent (revoked credentials)  | `@enforce_till_denied`            |
| Client does not need to know about gaps         | `@enforce_drop_while_denied`      |
| Client should show suspended/restored status    | `@enforce_recoverable_if_denied`  |

### How Enforcement Works

The decorators above are convenient, but to use them well it helps to understand what actually happens behind the scenes. This section walks through the enforcement lifecycle so you can reason about behavior.

#### The Deny Invariant

Only `PERMIT` grants access. The PDP can return four possible decisions (`PERMIT`, `DENY`, `INDETERMINATE`, `NOT_APPLICABLE`), and only `PERMIT` ever results in your view running or your stream forwarding data. Everything else means denial.

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

For streaming views (`@enforce_till_denied`, `@enforce_drop_while_denied`, `@enforce_recoverable_if_denied`), constraints can run at five points:

| Location           | When it happens                              | What constraints do here                |
|--------------------|----------------------------------------------|-----------------------------------------|
| On decision        | Each new decision from the PDP stream        | Side effects like logging, audit        |
| On each data item  | Each element yielded by the async generator  | Transform, filter, or replace items     |
| On stream error    | Generator produces an error                  | Transform or observe the error          |
| On stream complete | Generator finishes normally                  | Cleanup and finalization                |
| On cancel          | Client disconnects or enforcement terminates | Release resources and close connections |

This is why the handler interfaces have different shapes. A `RunnableConstraintHandlerProvider` fires at a lifecycle point like "on decision". A `ConsumerConstraintHandlerProvider` processes each data item. A `MethodInvocationConstraintHandlerProvider` only exists in `@pre_enforce` because it modifies arguments before the view runs, which makes no sense after the view has already executed.

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

For a complete formal specification of all enforcement modes, including state machines, teardown invariants, and handler resolution timing, see the [PEP Implementation Specification](../8_1_PEPImplementationSpecification/).

### Constraint Handlers

When the PDP returns a decision with `obligations` or `advice`, the constraint enforcement service resolves and executes all matching handlers.

#### When to Use Which Handler

| You want to...                            | Use this handler type                       |
| ----------------------------------------- | ------------------------------------------- |
| Log or notify on a decision               | `RunnableConstraintHandlerProvider`         |
| Record/inspect the response (side-effect) | `ConsumerConstraintHandlerProvider`         |
| Transform the response                    | `MappingConstraintHandlerProvider`          |
| Filter array elements from the response   | `FilterPredicateConstraintHandlerProvider`  |
| Modify request or view arguments          | `MethodInvocationConstraintHandlerProvider` |
| Log/notify on errors (side-effect)        | `ErrorHandlerProvider`                      |
| Transform errors                          | `ErrorMappingConstraintHandlerProvider`     |

#### Handler Types Reference

| Type                | Protocol                                      | Handler Signature                                       | When It Runs                         |
| ------------------- | --------------------------------------------- | ------------------------------------------------------- | ------------------------------------ |
| `runnable`          | `RunnableConstraintHandlerProvider`            | `() -> None`                                            | On decision (side effects)           |
| `method_invocation` | `MethodInvocationConstraintHandlerProvider`    | `(context: MethodInvocationContext) -> None`             | Before view (`@pre_enforce` only)    |
| `consumer`          | `ConsumerConstraintHandlerProvider`            | `(value: Any) -> None`                                  | After view, inspects response        |
| `mapping`           | `MappingConstraintHandlerProvider`             | `(value: Any) -> Any`                                   | After view, transforms response      |
| `filter_predicate`  | `FilterPredicateConstraintHandlerProvider`     | `(element: Any) -> bool`                                | After view, filters list elements    |
| `error_handler`     | `ErrorHandlerProvider`                         | `(error: Exception) -> None`                            | On error, inspects                   |
| `error_mapping`     | `ErrorMappingConstraintHandlerProvider`        | `(error: Exception) -> Exception`                       | On error, transforms                 |

`MappingConstraintHandlerProvider` and `ErrorMappingConstraintHandlerProvider` also require `get_priority() -> int`. When multiple mapping handlers match the same constraint, they execute in descending priority order (higher number runs first).

#### Registering Custom Handlers

```python
from sapl_django import register_constraint_handler
from sapl_base.constraint_types import Signal


class LogAccessHandler:
    def is_responsible(self, constraint) -> bool:
        return isinstance(constraint, dict) and constraint.get("type") == "logAccess"

    def get_signal(self) -> Signal:
        return Signal.ON_DECISION

    def get_handler(self, constraint):
        message = constraint.get("message", "Access logged")

        def handler() -> None:
            print(f"[POLICY] {message}")

        return handler


# Register during Django app startup (e.g., in AppConfig.ready())
register_constraint_handler(LogAccessHandler(), "runnable")
```

Register handlers in your Django `AppConfig.ready()` method so they are available when the first request arrives.

#### MethodInvocationContext

The `MethodInvocationContext` provides:

| Field           | Type              | Description                                                           |
| --------------- | ----------------- | --------------------------------------------------------------------- |
| `args`          | `list[Any]`       | Positional arguments. Handlers can mutate or replace entries.          |
| `kwargs`        | `dict[str, Any]`  | Keyword arguments. Handlers can add, modify, or remove keys.          |
| `function_name` | `str`             | The intercepted view function name                                    |
| `class_name`    | `str`             | Qualified class name (empty for plain functions)                      |
| `request`       | `Any`             | The Django `HttpRequest`, or `None` for service-layer calls           |

Handlers can modify `context.kwargs` to change what arguments the view receives. This enables patterns like policy-driven transfer limits:

```python
from sapl_base.constraint_types import MethodInvocationContext


class CapTransferHandler:
    def is_responsible(self, constraint) -> bool:
        return isinstance(constraint, dict) and constraint.get("type") == "capTransferAmount"

    def get_handler(self, constraint):
        max_amount = constraint.get("maxAmount", 0)
        arg_name = constraint.get("argName", "amount")

        def handler(context: MethodInvocationContext) -> None:
            if arg_name in context.kwargs:
                requested = float(context.kwargs[arg_name])
                if requested > max_amount:
                    context.kwargs[arg_name] = max_amount

        return handler
```

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

For SSE endpoints returning async generators, the three streaming decorators provide continuous authorization where the PDP streams decisions over time. Access may flip between PERMIT and DENY based on time, location, or context changes.

Django streaming responses use `StreamingHttpResponse` with `content_type="text/event-stream"`. The decorators automatically wrap the async generator output in SSE format.

A time-based policy that cycles between PERMIT and DENY:

```
policy "streaming-heartbeat-time-based"
permit
  action == "stream:heartbeat";
  resource == "heartbeat";
  var second = time.secondOf(<time.now>);
  second >= 0 && second < 20 || second >= 40;
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

### Demo Application

A complete working demo is available at [sapl-python-demos/django_demo](https://github.com/heutelbeck/sapl-python-demos/tree/main/django_demo). It includes:

- Manual PDP access (no decorators)
- `@pre_enforce` and `@post_enforce` with content filtering
- Service-layer enforcement using the same decorators on plain async functions
- All 7 constraint handler types (runnable, consumer, mapping, filter predicate, method invocation, error handler, error mapping)
- SSE streaming with all three enforcement strategies (till-denied, drop-while-denied, recoverable-if-denied)
- JWT-based ABAC with secrets

### Configuration Reference

All options are set via the `SAPL_CONFIG` dictionary in Django settings:

| Key                            | Type    | Default                     | Description                                              |
| ------------------------------ | ------- | --------------------------- | -------------------------------------------------------- |
| `base_url`                     | `str`   | `"https://localhost:8443"`  | PDP server URL                                           |
| `token`                        | `str`   | `None`                      | Bearer token / API key for authentication                |
| `username`                     | `str`   | `None`                      | Basic auth username (mutually exclusive with `token`)    |
| `password`                     | `str`   | `None`                      | Basic auth password                                      |
| `timeout`                      | `float` | `5.0`                       | PDP request timeout in seconds                           |
| `allow_insecure_connections`   | `bool`  | `False`                     | Allow HTTP connections (never use in production)         |
| `streaming_max_retries`        | `int`   | `0`                         | Maximum reconnection attempts for streaming connections  |
| `streaming_retry_base_delay`   | `float` | `1.0`                       | Base delay in seconds for exponential backoff on retry   |
| `streaming_retry_max_delay`    | `float` | `30.0`                      | Maximum delay in seconds for exponential backoff         |

### Troubleshooting

| Symptom                              | Likely Cause                            | Fix                                                              |
| ------------------------------------ | --------------------------------------- | ---------------------------------------------------------------- |
| All decisions are INDETERMINATE       | PDP unreachable                         | Check `base_url` and that PDP is running                         |
| 403 despite PERMIT decision           | Unhandled obligation                    | Check handler `is_responsible()` matches the obligation `type`   |
| Handler not firing                    | Missing registration                    | Call `register_constraint_handler()` in `AppConfig.ready()`      |
| Subject is `"anonymous"`              | No authenticated user on request        | Set up Django authentication or set subject explicitly           |
| Content filter throws                 | Unsupported path syntax                 | Only simple dot paths supported (`$.field.nested`)               |
| `ImproperlyConfigured`                | Missing `SAPL_CONFIG`                   | Add `SAPL_CONFIG` dict to Django settings                        |
| Streaming not working                 | Running under WSGI                      | Use ASGI server (Uvicorn/Daphne) for async views                 |

### License

Apache-2.0
