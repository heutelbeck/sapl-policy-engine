---
layout: default
title: Flask
#permalink: /python-flask/
has_children: false
parent: SDKs and APIs
nav_order: 606
---

## Flask SDK

Attribute-Based Access Control (ABAC) for Flask using SAPL (Streaming Attribute Policy Language). Provides decorator-driven policy enforcement with a constraint handler architecture for obligations, advice, and response transformation.

The `sapl-flask` library integrates SAPL policy enforcement into Flask applications as a Flask extension. Flask is WSGI and always synchronous, so the one-shot enforcement decorators run on the blocking enforcement core, which executes the view and its PDP communication off the event loop. The library also supports streaming responses with Server-Sent Events for continuous authorization.

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
pip install sapl-flask
```

This also installs `sapl-base`, which provides the PDP client, constraint engine, and content filtering. The library requires Python 3.12 or later and Flask 3.0+.

A complete working demo with constraint handlers and streaming enforcement is available at [sapl-python-demos/flask_demo](https://github.com/heutelbeck/sapl-python-demos/tree/main/flask_demo).

### Setup

#### Flask Extension

Initialize the `SaplFlask` extension with your Flask application. Configuration is read from `app.config`:

```python
from flask import Flask
from sapl_flask import SaplFlask

app = Flask(__name__)
app.config["SAPL_BASE_URL"] = "https://localhost:8443"
app.config["SAPL_TOKEN"] = "sapl_your_api_key_here"

sapl = SaplFlask(app)
```

For basic authentication instead of an API key:

```python
app.config["SAPL_BASE_URL"] = "https://localhost:8443"
app.config["SAPL_USERNAME"] = "myPdpClient"
app.config["SAPL_SECRET"] = "myPassword"

sapl = SaplFlask(app)
```

`SAPL_TOKEN` (API key) and `SAPL_USERNAME`/`SAPL_SECRET` (Basic Auth) are mutually exclusive. Configure one or the other.

#### Application Factory Pattern

For applications using the factory pattern, use `init_app`:

```python
from sapl_flask import SaplFlask

sapl = SaplFlask()


def create_app():
    app = Flask(__name__)
    app.config["SAPL_BASE_URL"] = "https://localhost:8443"
    app.config["SAPL_TOKEN"] = "sapl_your_api_key_here"

    sapl.init_app(app)
    return app
```

#### Local Development (HTTP)

For local development without TLS, point `SAPL_BASE_URL` at a loopback host. A plain `http://` URL is accepted only when the host is `localhost`, `127.0.0.1`, or `::1`. Any plain-HTTP URL targeting a remote host is refused at construction time, so plaintext authorization decisions never leave the machine.

```python
app.config["SAPL_BASE_URL"] = "http://localhost:8443"

sapl = SaplFlask(app)
```

#### Cleanup

Register the extension's `close()` method with `atexit` to release PDP connections on shutdown:

```python
import atexit

sapl = SaplFlask(app)
atexit.register(sapl.close)
```

The extension registers itself as `app.extensions["sapl"]` and is automatically discoverable by the enforcement decorators within any Flask application context.

### Enforcement Decorators

All decorators work on synchronous Flask view functions. Because Flask is always synchronous, the one-shot decorators (`@pre_enforce`, `@post_enforce`) always run on the blocking enforcement core, which executes the view off the event loop, so synchronous database and IO access works normally. When you configure a transaction provider (see [Database Transactions](#database-transactions)) it must be a sync context-manager factory, since the blocking core uses it as a sync context manager. The Flask request context is accessed via `flask.request` and `flask.g`, so no explicit `request` parameter is needed in decorator arguments.

#### @pre_enforce

Authorizes **before** the view executes. The view only runs on PERMIT.

```python
from flask import Flask, jsonify
from sapl_flask import SaplFlask, pre_enforce

app = Flask(__name__)
sapl = SaplFlask(app)


@app.route("/patient/<patient_id>")
@pre_enforce(action="readPatient", resource="patient")
def get_patient(patient_id: str):
    return jsonify({"id": patient_id, "name": "Jane Doe", "ssn": "123-45-6789"})
```

Use `@pre_enforce` for views with side effects (database writes, emails) that should not execute when access is denied. On denial, Flask's `abort(403)` is called.

#### @post_enforce

Authorizes **after** the view executes. The view always runs; its return value is available to the subscription builder via the `return_value` parameter.

```python
from sapl_flask import post_enforce


@app.route("/record/<record_id>")
@post_enforce(
    action="read",
    resource=lambda: {"type": "record", "path": request.path},
)
def get_record(record_id: str):
    return jsonify({"id": record_id, "value": "sensitive-data"})
```

Use `@post_enforce` when the policy needs to see the actual return value to make its authorization decision (e.g., deny based on the data's classification). On denial, the return value is discarded and `abort(403)` is called.

#### Building the Authorization Subscription

Each decorator accepts keyword arguments to customize the authorization subscription fields: `subject`, `action`, `resource`, `environment`, and `secrets`.

**Default Values**

When not explicitly provided, the subscription fields are derived from the Flask request context:

| Field         | Default                                                                         |
| ------------- | ------------------------------------------------------------------------------- |
| `subject`     | `g.user`, or `flask_login.current_user`, or `"anonymous"`                       |
| `action`      | `{"method": request.method, "endpoint": function_name}`                         |
| `resource`    | `{"path": request.path, "view_args": request.view_args}`                        |
| `environment` | `{"ip": request.remote_addr}` (when available)                                  |
| `secrets`     | Not sent unless explicitly specified                                            |

Flask-Login integration is automatic: if `flask-login` is installed and a user is authenticated, `current_user.username` (or `str(current_user)`) is used as the subject.

**Static Values**

Pass a string or dict directly:

```python
@pre_enforce(action="read", resource="patient")
```

**Dynamic Values (Callables)**

Pass a callable that receives a `SubscriptionContext` and returns the field value. The context provides `request`, `return_value` (`None` for `@pre_enforce`), `params` (view args), `query` (query string), and `args` (resolved function arguments):

```python
@pre_enforce(
    subject=lambda ctx: getattr(ctx.request, "user", "anonymous") if ctx.request else "anonymous",
    resource=lambda ctx: {"path": ctx.params, "method": ctx.request.method} if ctx.request else {},
)
```

The `SubscriptionContext` is the same across all Python SAPL integrations, making subscription field callables portable between frameworks.

**Secrets**

The `secrets` field carries sensitive data (tokens, API keys) that the PDP needs for policy evaluation but that must not appear in logs. It is excluded from debug logging automatically. Use it when a policy needs to inspect credentials, for example passing a raw JWT so the PDP can read its claims:

```python
@pre_enforce(
    action="exportData",
    resource=lambda ctx: {"pilotId": ctx.params.get("pilot_id")},
    secrets=lambda ctx: {"jwt": g.token} if hasattr(g, "token") else None,
)
```

#### @stream_enforce

Streaming enforcement for SSE endpoints. The decorated view returns an async iterator of data items. The wrapper opens a streaming PDP subscription, drives the streaming state machine, and returns a Flask `Response` whose body is each item rendered as an SSE `data:` frame on `text/event-stream`.

```python
import asyncio
from datetime import datetime, timezone
from sapl_flask import stream_enforce


@app.route("/stream/heartbeat")
@stream_enforce(action="stream:heartbeat", resource="heartbeat")
async def heartbeat():
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

| Aspect          | Obligation                                                | Advice                                         |
|-----------------|-----------------------------------------------------------|-------------------------------------------------|
| All handled?    | Required. Unhandled obligations deny access (403).        | Optional. Unhandled advice is silently ignored. |
| Handler failure | Denies access (403).                                      | Logs a warning and continues.                   |

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

If the decision is anything other than `PERMIT`, the PEP calls `abort(403)` immediately. Your view never runs.

If the decision is `PERMIT`, the PEP resolves all constraint handlers. It walks through the obligations and advice attached to the decision and checks which registered handlers claim responsibility for each one. If any obligation has no matching handler, the PEP denies access right there, because it cannot guarantee the obligation will be enforced.

With all handlers resolved, execution proceeds through the enforcement locations in order. On-decision handlers run first (logging, audit). Then method-invocation handlers run, which can modify view arguments if the policy requires it. Then your actual view executes. After the view returns, the PEP applies return-value handlers: resource replacement if the decision included one, filter predicates, mapping handlers, and consumer handlers. If any obligation handler fails at any stage, the PEP denies access.

#### PostEnforce Lifecycle

`@post_enforce` inverts the order. Your view runs first, regardless of the authorization outcome. Only after it returns does the PEP build the authorization subscription (now including the return value) and consult the PDP.

This means the PDP can make decisions based on the actual data your view produced. For example, a policy might permit access to a record only if its classification level is below a threshold, something that can only be checked after loading the record.

If the decision is not `PERMIT`, the PEP discards the return value and calls `abort(403)`.

If the decision is `PERMIT`, constraint handlers proceed through the same stages as `@pre_enforce`, minus the method-invocation handlers (since the view has already run). Return-value handlers can still transform the result before it reaches the caller.

Because the view runs before the PDP is consulted, if the view itself raises an exception, that exception propagates directly. The PDP is never called, because there is no return value to include in the subscription.

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

Register providers on the `SaplFlask` extension instance after calling `SaplFlask(app)` or `sapl.init_app(app)`:

```python
from collections.abc import Sequence
from typing import Any

from sapl_flask import SaplFlask
from sapl_base.pep import DECISION, ScopedHandler


class LogAccessProvider:
    def get_handlers(self, constraint: Any) -> Sequence[ScopedHandler]:
        if not (isinstance(constraint, dict) and constraint.get("type") == "logAccess"):
            return ()
        message = constraint.get("message", "Access logged")

        def run() -> None:
            print(f"[POLICY] {message}")

        return (ScopedHandler(signal=DECISION, priority=0, shape="runner", handler=run),)


sapl = SaplFlask(app)
sapl.register_provider(LogAccessProvider())
```

Registration rebuilds the planner. A single obligation can drive several handlers at different signals. The provider returns one `ScopedHandler` per handler, and the planner schedules each one against its own signal. The bundle is all-or-nothing during admissibility checks. If any handler in the returned sequence is not well-formed for the constraint's tag, the entire claim is rejected and the decision fails closed.

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

### Query Manipulation

The `sapl-sqlalchemy` package lets a policy rewrite SQLAlchemy ORM queries so the database returns only the rows and columns it authorises. This enforces at the data layer rather than after the fact. Instead of loading every row and filtering the result in Python, the policy injects a `WHERE` clause and a column projection into the query before it runs, so unauthorised rows never leave the database. It is an optional add-on, installed separately.

```bash
pip install sapl-sqlalchemy
```

This is driven by the `sql:queryManipulation` obligation and the `SqlQueryManipulationProvider`. A policy attaches the obligation, and the provider lowers it into SQLAlchemy `Select`, `Update`, and `Delete` expressions.

```
policy "tenant-scoped-read"
permit
  action == "read";
  resource == "patient";
obligation
  {
    "type": "sql:queryManipulation",
    "criteria": [
      { "column": "tenant_id", "op": "=", "value": subject.tenantId }
    ],
    "columns": ["id", "name", "tenant_id"]
  }
```

The obligation carries three optional parts. `criteria` is a tree of `and`, `or`, and `not` nodes over leaf comparisons of the form `{ "column", "op", "value" }`, with operators `=`, `!=`, `>`, `>=`, `<`, `<=`, `in`, `like`, `notLike`, `isNull`, and `isNotNull`. `conditions` is a list of raw SQL `WHERE` fragments for expressions the criteria tree cannot express. `columns` is a projection list. The provider lowers `criteria` and `conditions` into a `WHERE` predicate on the statement, and `columns` into the statement's projection.

#### Setup

Register the listener and the provider once at startup.

```python
from sapl_sqlalchemy import SqlQueryManipulationProvider, register_orm_listener

register_orm_listener()
sapl.register_provider(SqlQueryManipulationProvider())
```

`register_orm_listener()` attaches to the SQLAlchemy `Session` class, so it covers every session including `AsyncSession` through its sync-session proxy. It also advertises that the integration can satisfy a `sql:queryManipulation` obligation. Until it is called, that obligation is inadmissible and any decision carrying it fails closed.

#### Where It Hooks In and What It Covers

The integration hooks into SQLAlchemy through the `do_orm_execute` ORM event on the `Session`, which fires for every query a session runs. Covered access patterns are ORM executes through a session. A `Select`, an ORM `Update`, and an ORM `Delete` get the authorised `WHERE` predicate injected, and a column-typed select gets its projection narrowed.

Some statements are rejected rather than rewritten, and a rejected obligation denies access. Raw `text()` executed through the session, a set operation such as `UNION` combined with predicates, and a column projection against an entity-typed select all raise, which fails the obligation and denies the decision. Malformed criteria deny the same way.

Not covered is execution that bypasses the ORM session entirely, such as SQLAlchemy Core `engine.execute()` or a raw DBAPI cursor obtained outside the session. Those never trigger the `do_orm_execute` event, so no filter is applied. This is a fail-open consequence you must account for. Once the listener is registered the `sql:queryManipulation` obligation is admissible, so it does not fail closed. An enforced method that reaches the database off the ORM session leaves that access unfiltered. The accepted position is that off-session database access means the developer owns row-level security manually for that path, because the integration cannot anticipate arbitrary access and does not parse SQL strings. The contrast is not registering the shim at all, in which case the obligation is inadmissible and the decision fails closed by denying.

### Streaming Authorization

For SSE endpoints, `@stream_enforce` provides continuous authorization where the PDP streams decisions over time. Access may flip between permitted, suspended, and denied based on time, location, or context changes.

Flask streaming responses use `Response` with `mimetype="text/event-stream"`. The decorator bridges between Flask's synchronous response model and the async PDP streaming protocol using a dedicated event loop, and renders each yielded item as an SSE frame.

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

For cases where decorators are not suitable, access the PDP client directly via the extension:

```python
import asyncio
from flask import jsonify, abort
from sapl_flask import get_sapl_extension
from sapl_base.types import AuthorizationSubscription, Decision


@app.route("/hello")
def get_hello():
    sapl = get_sapl_extension()
    subscription = AuthorizationSubscription(
        subject="anonymous",
        action="read",
        resource="hello",
    )
    decision = asyncio.run(sapl.pdp_client.decide_once(subscription))

    if decision.decision == Decision.PERMIT and not decision.obligations:
        return jsonify({"message": "hello"})
    abort(403, description="Access denied by policy")
```

When using the PDP client directly, you are responsible for checking the decision, enforcing obligations, and handling resource replacement. Flask views are synchronous, so wrap async PDP calls with `asyncio.run()`.

### Service Layer Enforcement

The same `@pre_enforce` and `@post_enforce` decorators work at any layer, not just on Flask views. When used on a service method outside of a Flask request context, the decorator automatically translates denial into `abort(403)` when a request context is available, or propagates the error normally otherwise:

```python
from sapl_flask import pre_enforce, post_enforce


@pre_enforce(action="listPatients", resource="patients")
def list_patients() -> list[dict]:
    return [dict(p) for p in PATIENTS]


@post_enforce(
    action="getPatientDetail",
    resource=lambda ctx: {"type": "patientDetail", "data": ctx.return_value},
)
def get_patient_detail(patient_id: str) -> dict | None:
    return next((dict(p) for p in PATIENTS if p["id"] == patient_id), None)
```

The calling view does not need any special error handling. The decorator handles denial automatically:

```python
from flask import jsonify
from services import patient_service


@app.route("/services/patients/<patient_id>")
def get_patient_detail(patient_id: str):
    result = patient_service.get_patient_detail(patient_id)
    return jsonify(result)
```

Service-layer decorators accept the same subscription field options (`subject`, `action`, `resource`, `environment`, `secrets`) as when used on views. When no Flask request context is available, subject defaults to `"anonymous"` and environment is empty.

### Database Transactions

`@pre_enforce` and `@post_enforce` can own a transaction boundary, so a denial that lands after the view has written to the database rolls the write back. Three triggers cause a rollback: a `@post_enforce` DENY, a `@post_enforce` output-obligation failure, and a `@pre_enforce` output-obligation failure (the pre-decision permits, but its output obligations run after the view writes). A clean PERMIT commits.

This is opt-in. With no provider configured the PEP owns no transaction and enforcement behaves exactly as before. A provider is a zero-arg factory returning a context manager that commits on clean exit and rolls back on a propagated exception. `set_transaction_provider` is a method on the `SaplFlask` extension.

Flask views are synchronous and run on the blocking core, which uses the provider as a sync context manager, so pass the sync context-manager factory directly. A sync SQLAlchemy `session.begin` is exactly such a factory:

```python
sapl = SaplFlask(app)
sapl.set_transaction_provider(lambda: get_current_session().begin())
```

The factory should resolve the current request's session (for example a request-scoped session held in a contextvar). Django's `transaction.atomic` is passed the same way:

```python
from django.db import transaction

sapl.set_transaction_provider(transaction.atomic)
```

### Client Resilience

The PDP client treats every transport problem as an operational condition, never as a policy outcome, and never lets one surface as an exception. A connection drop, timeout, or decode error fails closed to `INDETERMINATE`, which the PEP enforces as a denial, so a transient PDP outage can never accidentally grant access.

One-shot requests (`decide_once`) fail closed to `INDETERMINATE` immediately, with no retry, and never throw. In steady state the connection is warm, so only a cold or dropped connection fails closed.

Subscriptions (streaming `decide`) never terminate on a transport problem or on a server-side stream completion. Either condition emits one `INDETERMINATE` and then reconnects with bounded exponential backoff, indefinitely. Consecutive identical decisions are de-duplicated, so an outage yields a single `INDETERMINATE`, not a flood. A subscription ends only when the consumer cancels it or the client shuts down. This contract holds identically across the HTTP and RSocket transports and across every SAPL PEP client.

### Demo Application

A complete working demo is available at [sapl-python-demos/flask_demo](https://github.com/heutelbeck/sapl-python-demos/tree/main/flask_demo). It includes:

- Manual PDP access (no decorators)
- `@pre_enforce` and `@post_enforce` with content filtering
- Service-layer enforcement using the same decorators on plain functions
- Custom constraint handler providers returning runner, consumer, and mapper handlers
- SSE streaming with `@stream_enforce`, covering terminate-on-deny, drop-while-suspended, and signalled suspend/resume
- JWT-based ABAC with secrets

### Configuration Reference

All options are set via `app.config`:

| Key                                | Type    | Default                     | Description                                              |
| ---------------------------------- | ------- | --------------------------- | -------------------------------------------------------- |
| `SAPL_BASE_URL`                    | `str`   | `"https://localhost:8443"`  | PDP server URL. Plain `http://` is accepted only for loopback hosts |
| `SAPL_TOKEN`                       | `str`   | `None`                      | Bearer token / API key for authentication                |
| `SAPL_USERNAME`                    | `str`   | `None`                      | Basic auth username (mutually exclusive with `TOKEN`)    |
| `SAPL_SECRET`                      | `str`   | `None`                      | Basic auth secret                                        |
| `SAPL_TIMEOUT`                     | `float` | `5.0`                       | PDP request timeout in seconds                           |

Streaming retry configuration is set at the `sapl_base` level via the PDP client options. The `SaplFlask` extension does not expose these through `app.config`.

### Troubleshooting

| Symptom                              | Likely Cause                            | Fix                                                              |
| ------------------------------------ | --------------------------------------- | ---------------------------------------------------------------- |
| All decisions are INDETERMINATE       | PDP unreachable                         | Check `SAPL_BASE_URL` and that PDP is running                    |
| 403 despite PERMIT decision           | Unhandled obligation                    | Check the provider's `get_handlers()` claims the obligation `type` |
| Handler not firing                    | Missing registration                    | Call `sapl.register_provider()` after init                       |
| Subject is `"anonymous"`              | No user in `g.user` or flask-login      | Set `g.user` in a before_request hook or use flask-login         |
| Content filter throws                 | Unsupported path syntax                 | Only simple dot paths supported (`$.field.nested`)               |
| `RuntimeError: SAPL not initialized`  | Extension not registered                | Call `SaplFlask(app)` or `sapl.init_app(app)`                    |
| Streaming response empty              | Generator not yielding dicts            | Ensure generator yields dicts (serialized as JSON SSE events)    |

### License

Apache-2.0
