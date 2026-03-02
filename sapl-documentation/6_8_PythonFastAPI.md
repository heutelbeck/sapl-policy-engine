---
layout: default
title: Python FastAPI
#permalink: /python-fastapi/
has_children: false
parent: Integration
nav_order: 608
---

## Python FastAPI Integration

Attribute-Based Access Control (ABAC) for FastAPI using SAPL (Streaming Attribute Policy Language). Provides decorator-driven policy enforcement with a constraint handler architecture for obligations, advice, and response transformation.

The `sapl-fastapi` library integrates SAPL policy enforcement into FastAPI and Starlette applications. It is fully async-native, supports Server-Sent Events streaming for continuous authorization, and works with FastAPI's dependency injection system.

### What is SAPL?

SAPL is a policy language and Policy Decision Point (PDP) for attribute-based access control. Policies are written in a dedicated language and evaluated by the PDP, which streams authorization decisions based on subject, action, resource, and environment attributes.

Three core concepts:

1. **Authorization subscription** -- your app sends `{ subject, action, resource, environment }` to the PDP
2. **PDP decision** -- the PDP evaluates policies and returns `PERMIT` or `DENY`, optionally with obligations, advice, or a replacement resource
3. **Constraint handlers** -- registered handlers execute the policy's instructions (log, filter, transform, cap values, etc.)

A PDP decision looks like this:

```json
{
  "decision": "PERMIT",
  "obligations": [{ "type": "logAccess", "message": "Patient record accessed" }],
  "advice": [{ "type": "notifyAdmin" }]
}
```

`decision` is always present (`PERMIT`, `DENY`, `INDETERMINATE`, or `NOT_APPLICABLE`). The other fields are optional -- `obligations` and `advice` are arrays of arbitrary JSON objects (by convention with a `type` field for handler dispatch), and `resource` (when present) replaces the endpoint's return value entirely.

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
    password="myPassword",
)
```

`token` (API key) and `username`/`password` (Basic Auth) are mutually exclusive -- configure one or the other.

#### Local Development (HTTP)

For local development without TLS:

```python
config = SaplConfig(
    base_url="http://localhost:8443",
    allow_insecure_connections=True,
)
```

#### What configure_sapl Registers

`configure_sapl()` creates the module-level singleton PDP client and constraint enforcement service. It automatically registers the built-in `ContentFilteringProvider` and `ContentFilterPredicateProvider` for content filtering support. Custom constraint handlers are registered separately via `register_constraint_handler()`.

`cleanup_sapl()` closes the PDP client and releases HTTP connections. Always call it during shutdown (in the lifespan `yield` teardown block).

### Enforcement Decorators

All decorators work on async FastAPI endpoint functions. The decorated endpoint **must** include `request: Request` as a parameter (either positional or keyword) so the decorator can extract request context.

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

Authorizes **after** the endpoint executes. The endpoint always runs; its return value is available to the subscription builder via the `return_value` argument of callable fields.

```python
from fastapi import Request
from sapl_fastapi import post_enforce


@app.get("/record/{record_id}")
@post_enforce(
    action="read",
    resource=lambda request, return_value: {"type": "record", "data": return_value},
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

Pass a callable that receives `(request, return_value)` and returns the field value. The `return_value` is `None` for `@pre_enforce` and the actual return value for `@post_enforce`:

```python
@pre_enforce(
    subject=lambda request, rv: getattr(request.state, "user", "anonymous"),
    resource=lambda request, rv: {"pilotId": request.path_params.get("pilot_id")},
)
```

**Secrets**

The `secrets` field carries sensitive data (tokens, API keys) that the PDP needs for policy evaluation but that must not appear in logs. It is excluded from debug logging automatically. Use it when a policy needs to inspect credentials -- for example, passing a raw JWT so the PDP can read its claims:

```python
@pre_enforce(
    action="exportData",
    resource=lambda request, rv: {"pilotId": request.path_params.get("pilot_id")},
    secrets=lambda request, rv: {"jwt": request.headers.get("authorization", "").split(" ")[-1]},
)
```

**Custom Deny Handling**

Add `on_deny` to any `@pre_enforce` or `@post_enforce` to return a custom response instead of raising `HTTPException(403)`:

```python
@pre_enforce(
    action="exportData",
    on_deny=lambda decision: {
        "error": "access_denied",
        "decision": decision.decision.value,
    },
)
```

#### @enforce_till_denied

Streaming enforcement that **terminates permanently** on the first non-PERMIT decision. The decorated endpoint must return an async generator. Returns a Starlette `StreamingResponse` with SSE format.

```python
import asyncio
from datetime import datetime, timezone
from fastapi import Request
from sapl_fastapi import enforce_till_denied


@app.get("/stream/heartbeat")
@enforce_till_denied(
    action="stream:heartbeat",
    resource="heartbeat",
    on_stream_deny=lambda decision: {"type": "ACCESS_DENIED"},
)
async def heartbeat(request: Request):
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
from sapl_fastapi import enforce_drop_while_denied


@app.get("/stream/data")
@enforce_drop_while_denied(action="stream:heartbeat", resource="heartbeat")
async def data_stream(request: Request):
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
from sapl_fastapi import enforce_recoverable_if_denied


@app.get("/stream/recoverable")
@enforce_recoverable_if_denied(
    action="stream:heartbeat",
    resource="heartbeat",
    on_stream_deny=lambda decision: {"type": "ACCESS_SUSPENDED"},
    on_stream_recover=lambda decision: {"type": "ACCESS_RESTORED"},
)
async def recoverable_stream(request: Request):
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

Only `PERMIT` grants access. The PDP can return four possible decisions (`PERMIT`, `DENY`, `INDETERMINATE`, `NOT_APPLICABLE`), and only `PERMIT` ever results in your endpoint running or your stream forwarding data. Everything else means denial.

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

For streaming endpoints (`@enforce_till_denied`, `@enforce_drop_while_denied`, `@enforce_recoverable_if_denied`), constraints can run at five points:

| Location           | When it happens                              | What constraints do here                |
|--------------------|----------------------------------------------|-----------------------------------------|
| On decision        | Each new decision from the PDP stream        | Side effects like logging, audit        |
| On each data item  | Each element yielded by the async generator  | Transform, filter, or replace items     |
| On stream error    | Generator produces an error                  | Transform or observe the error          |
| On stream complete | Generator finishes normally                  | Cleanup and finalization                |
| On cancel          | Client disconnects or enforcement terminates | Release resources and close connections |

This is why the handler interfaces have different shapes. A `RunnableConstraintHandlerProvider` fires at a lifecycle point like "on decision". A `ConsumerConstraintHandlerProvider` processes each data item. A `MethodInvocationConstraintHandlerProvider` only exists in `@pre_enforce` because it modifies arguments before the endpoint runs, which makes no sense after the endpoint has already executed.

#### PreEnforce Lifecycle

When you decorate an endpoint with `@pre_enforce`, here is what happens step by step.

First, the PEP builds an authorization subscription from the decorator options (or from defaults if you left them out) and sends it to the PDP as a one-shot request. The PDP evaluates the subscription against all matching policies and returns a single decision.

If the decision is anything other than `PERMIT`, the PEP raises `HTTPException(403)` immediately. Your endpoint never runs.

If the decision is `PERMIT`, the PEP resolves all constraint handlers. It walks through the obligations and advice attached to the decision and checks which registered handlers claim responsibility for each one. If any obligation has no matching handler, the PEP denies access right there, because it cannot guarantee the obligation will be enforced.

With all handlers resolved, execution proceeds through the enforcement locations in order. On-decision handlers run first (logging, audit). Then method-invocation handlers run, which can modify endpoint arguments if the policy requires it. Then your actual endpoint executes. After the endpoint returns, the PEP applies return-value handlers: resource replacement if the decision included one, filter predicates, mapping handlers, and consumer handlers. If any obligation handler fails at any stage, the PEP denies access.

#### PostEnforce Lifecycle

`@post_enforce` inverts the order. Your endpoint runs first, regardless of the authorization outcome. Only after it returns does the PEP build the authorization subscription (now including the return value) and consult the PDP.

This means the PDP can make decisions based on the actual data your endpoint produced. For example, a policy might permit access to a record only if its classification level is below a threshold -- something that can only be checked after loading the record.

If the decision is not `PERMIT`, the PEP discards the return value and raises `HTTPException(403)`.

If the decision is `PERMIT`, constraint handlers proceed through the same stages as `@pre_enforce`, minus the method-invocation handlers (since the endpoint has already run). Return-value handlers can still transform the result before it reaches the caller.

Because the endpoint runs before the PDP is consulted, if the endpoint itself raises an exception, that exception propagates directly. The PDP is never called, because there is no return value to include in the subscription.

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
| Modify request or endpoint arguments      | `MethodInvocationConstraintHandlerProvider` |
| Log/notify on errors (side-effect)        | `ErrorHandlerProvider`                      |
| Transform errors                          | `ErrorMappingConstraintHandlerProvider`     |

#### Handler Types Reference

| Type                | Protocol                                      | Handler Signature                                       | When It Runs                            |
| ------------------- | --------------------------------------------- | ------------------------------------------------------- | --------------------------------------- |
| `runnable`          | `RunnableConstraintHandlerProvider`            | `() -> None`                                            | On decision (side effects)              |
| `method_invocation` | `MethodInvocationConstraintHandlerProvider`    | `(context: MethodInvocationContext) -> None`             | Before endpoint (`@pre_enforce` only)   |
| `consumer`          | `ConsumerConstraintHandlerProvider`            | `(value: Any) -> None`                                  | After endpoint, inspects response       |
| `mapping`           | `MappingConstraintHandlerProvider`             | `(value: Any) -> Any`                                   | After endpoint, transforms response     |
| `filter_predicate`  | `FilterPredicateConstraintHandlerProvider`     | `(element: Any) -> bool`                                | After endpoint, filters list elements   |
| `error_handler`     | `ErrorHandlerProvider`                         | `(error: Exception) -> None`                            | On error, inspects                      |
| `error_mapping`     | `ErrorMappingConstraintHandlerProvider`        | `(error: Exception) -> Exception`                       | On error, transforms                    |

`MappingConstraintHandlerProvider` and `ErrorMappingConstraintHandlerProvider` also require `get_priority() -> int`. When multiple mapping handlers match the same constraint, they execute in descending priority order (higher number runs first).

#### Registering Custom Handlers

Register handlers during application startup (inside the lifespan function):

```python
from sapl_fastapi import configure_sapl, register_constraint_handler, SaplConfig
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


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    configure_sapl(SaplConfig(base_url="https://localhost:8443"))
    register_constraint_handler(LogAccessHandler(), "runnable")
    yield
    await cleanup_sapl()
```

The seven handler type strings for `register_constraint_handler` are: `"runnable"`, `"consumer"`, `"mapping"`, `"filter_predicate"`, `"method_invocation"`, `"error_handler"`, `"error_mapping"`.

#### MethodInvocationContext

The `MethodInvocationContext` provides:

| Field           | Type              | Description                                                           |
| --------------- | ----------------- | --------------------------------------------------------------------- |
| `args`          | `list[Any]`       | Positional arguments -- handlers can mutate or replace entries         |
| `kwargs`        | `dict[str, Any]`  | Keyword arguments -- handlers can add, modify, or remove keys         |
| `function_name` | `str`             | The intercepted endpoint function name                                |
| `class_name`    | `str`             | Qualified class name (empty for plain functions)                      |
| `request`       | `Any`             | The Starlette `Request`, or `None` for service-layer calls            |

Handlers can modify `context.kwargs` to change what arguments the endpoint receives. This enables patterns like policy-driven transfer limits:

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

### Streaming Authorization

For SSE endpoints returning async generators, the three streaming decorators provide continuous authorization where the PDP streams decisions over time. Access may flip between PERMIT and DENY based on time, location, or context changes.

The decorators return a Starlette `StreamingResponse` with `media_type="text/event-stream"`. Each yielded item from the async generator is automatically formatted as an SSE `data:` event (dicts are JSON-serialized).

A time-based policy that cycles between PERMIT and DENY:

```
policy "streaming-heartbeat-time-based"
permit
  action == "stream:heartbeat";
  resource == "heartbeat";
  var second = time.secondOf(<time.now>);
  second >= 0 && second < 20 || second >= 40;
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

For service methods that should not return HTTP responses directly, use `@service_pre_enforce` and `@service_post_enforce`. These decorators raise `AccessDeniedError` on denial instead of `HTTPException(403)`, letting the calling endpoint handle the error:

```python
from sapl_fastapi import service_pre_enforce, service_post_enforce
from sapl_base.enforcement import AccessDeniedError


@service_pre_enforce(action="service:listPatients", resource="patients")
async def list_patients() -> list[dict]:
    return [dict(p) for p in PATIENTS]


@service_post_enforce(
    action="service:getPatientDetail",
    resource=lambda request, return_value: {"type": "patientDetail", "data": return_value},
)
async def get_patient_detail(patient_id: str) -> dict | None:
    return next((dict(p) for p in PATIENTS if p["id"] == patient_id), None)
```

The calling endpoint catches `AccessDeniedError` and converts it to an HTTP response:

```python
from fastapi import FastAPI, HTTPException, Request
from sapl_base.enforcement import AccessDeniedError
from services import patient_service

app = FastAPI()


@app.get("/services/patients/{patient_id}")
async def get_patient_detail(request: Request, patient_id: str):
    try:
        result = await patient_service.get_patient_detail(patient_id)
        return result
    except AccessDeniedError:
        raise HTTPException(status_code=403, detail="Access denied")
```

Service decorators accept the same subscription field options (`subject`, `action`, `resource`, `environment`, `secrets`) as the HTTP decorators. The `request` parameter is optional -- when omitted, subject defaults to `"anonymous"` and environment is empty.

### Demo Application

A complete working demo is available at [sapl-python-demos/fastapi_demo](https://github.com/heutelbeck/sapl-python-demos/tree/main/fastapi_demo). It includes:

- Manual PDP access (no decorators)
- `@pre_enforce` and `@post_enforce` with content filtering
- `@service_pre_enforce` and `@service_post_enforce` for service-layer enforcement
- All 7 constraint handler types (runnable, consumer, mapping, filter predicate, method invocation, error handler, error mapping)
- SSE streaming with all five enforcement patterns (till-denied, drop-while-denied, recoverable, terminated-by-callback, drop-with-callbacks)
- JWT-based ABAC with secrets

### Configuration Reference

All options are set via the `SaplConfig` dataclass passed to `configure_sapl()`:

| Parameter                    | Type    | Default                     | Description                                              |
| ---------------------------- | ------- | --------------------------- | -------------------------------------------------------- |
| `base_url`                   | `str`   | `"https://localhost:8443"`  | PDP server URL                                           |
| `token`                      | `str`   | `None`                      | Bearer token / API key for authentication                |
| `username`                   | `str`   | `None`                      | Basic auth username (mutually exclusive with `token`)    |
| `password`                   | `str`   | `None`                      | Basic auth password                                      |
| `timeout`                    | `float` | `5.0`                       | PDP request timeout in seconds                           |
| `allow_insecure_connections` | `bool`  | `False`                     | Allow HTTP connections (never use in production)         |
| `streaming_max_retries`      | `int`   | `0`                         | Maximum reconnection attempts for streaming connections  |
| `streaming_retry_base_delay` | `float` | `1.0`                       | Base delay in seconds for exponential backoff on retry   |
| `streaming_retry_max_delay`  | `float` | `30.0`                      | Maximum delay in seconds for exponential backoff         |

### Troubleshooting

| Symptom                              | Likely Cause                            | Fix                                                              |
| ------------------------------------ | --------------------------------------- | ---------------------------------------------------------------- |
| All decisions are INDETERMINATE       | PDP unreachable                         | Check `base_url` and that PDP is running                         |
| 403 despite PERMIT decision           | Unhandled obligation                    | Check handler `is_responsible()` matches the obligation `type`   |
| Handler not firing                    | Missing registration                    | Call `register_constraint_handler()` in lifespan                 |
| Subject is `"anonymous"`              | No auth middleware setting `state.user` | Set `request.state.user` in auth dependency or middleware        |
| Content filter throws                 | Unsupported path syntax                 | Only simple dot paths supported (`$.field.nested`)               |
| `RuntimeError: SAPL not configured`   | Missing `configure_sapl()`              | Call `configure_sapl()` in lifespan before yield                 |
| `RuntimeError: No Request object`     | Missing `request: Request` parameter    | Add `request: Request` to endpoint function signature            |
| Streaming response not SSE            | Missing `text/event-stream` content     | Use streaming decorators; they set the content type automatically |

### License

Apache-2.0
