---
layout: default
title: FastMCP
has_children: false
parent: SDKs and APIs
nav_order: 609
---

## FastMCP SDK

Policy-based authorization for [FastMCP](https://gofastmcp.com/) servers using SAPL (Streaming Attribute Policy Language). The `sapl-fastmcp` library provides two authorization paths. A global `SAPLMiddleware` intercepts all MCP operations with full constraint handler support, and a per-component `auth=sapl()` check covers simpler binary permit and deny decisions. Both paths query the SAPL PDP for every tool call, resource read, and prompt access.

### What is SAPL?

SAPL is a policy language and Policy Decision Point (PDP) for attribute-based access control. Policies are written in a dedicated language and evaluated by the PDP, which streams authorization decisions based on subject, action, resource, and environment attributes.

Three core concepts shape the integration.

1. Authorization subscription. Your app sends `{ subject, action, resource, environment }` to the PDP.
2. PDP decision. The PDP evaluates policies and returns `PERMIT` or `DENY`, optionally with obligations, advice, or a replacement resource.
3. Constraint handlers. Registered handlers execute the policy's instructions such as log, filter, transform, or cap values.

A PDP decision looks like this.

```json
{
  "decision": "PERMIT",
  "obligations": [{ "type": "logAccess", "message": "Patient record accessed" }],
  "advice": [{ "type": "notifyAdmin" }]
}
```

The `decision` field is always present (`PERMIT`, `DENY`, `SUSPEND`, `INDETERMINATE`, or `NOT_APPLICABLE`). The other fields are optional. The `obligations` and `advice` arrays carry JSON objects, by convention with a `type` field for handler dispatch. When `resource` is present, it replaces the component's return value entirely.

For a deeper introduction to SAPL's subscription model and policy language, see the [SAPL documentation](https://sapl.io/docs/latest/).

### What is MCP?

The [Model Context Protocol](https://modelcontextprotocol.io/) (MCP) is a standardized interface for AI agents and LLMs to access external tools, resources, and prompts. [FastMCP](https://gofastmcp.com/) is a Python framework for building MCP servers.

Authorization matters because MCP servers expose capabilities to AI agents that may act on behalf of different users with different privilege levels. A single MCP server might serve tools for querying public data alongside tools that access PII or perform destructive operations. Without authorization, every agent has full access to every tool regardless of who it represents.

### Installation

```bash
pip install sapl-fastmcp
```

This also installs `sapl-base`, which provides the PDP client, the enforcement planner, and the built-in content filters. The library requires Python 3.12+ and FastMCP 3.1.0+.

A complete working demo with JWT authentication, constraint handlers, stealth mode, and both authorization paths is available at [sapl-python-demos/fastmcp_demo](https://github.com/heutelbeck/sapl-python-demos/tree/main/fastmcp_demo).

### Choosing an Authorization Path

The library offers two ways to enforce authorization. They can be used independently or together on the same server.

| Aspect | `SAPLMiddleware` | `auth=sapl()` |
|--------|-----------------|---------------|
| Enforcement point | Single middleware intercepts all operations | Each component has its own auth check |
| Constraint handlers | Full lifecycle, including input transformation and output mapping | Decision-scoped handlers only |
| Stealth mode | Supported, hides from listings and masks denial as not-found | Not supported, warning logged |
| Finalize callbacks | Supported | Not supported |
| Listing filter | Multi-decide hides unauthorized stealth components | FastMCP's built-in per-component visibility |
| Decorators | `@pre_enforce` and `@post_enforce` customize each component | Fields set in the `sapl()` call |
| Pre-enforce and post-enforce | Both supported | Pre-enforce only |
| Setup complexity | Slightly more, pass the PDP client and planner explicitly | Simpler, `configure_sapl()` plus `sapl()` |

Use the middleware when you need constraint handlers that modify arguments or transform results, stealth mode, finalize callbacks, or post-enforce. Use `auth=sapl()` for simpler setups where a binary permit or deny per component is sufficient.

### Setup: Per-Component Auth (`auth=sapl()`)

Call `configure_sapl()` once before the server starts. This initializes the PDP client and the enforcement planner.

```python
from sapl_base.transport import HttpPdpClientOptions
from sapl_fastmcp import configure_sapl, register_provider, sapl

configure_sapl(HttpPdpClientOptions(base_url="https://localhost:8443"))
```

To register a constraint handler provider, call `register_provider()`. A provider claims the constraints it understands and returns the scoped handlers that enforce them.

```python
register_provider(AccessLoggingProvider())
```

Then protect individual components with `auth=sapl()`.

```python
from fastmcp import FastMCP

mcp = FastMCP("server", auth=jwt_verifier)

# Defaults: subject=token claims, action="hello", resource="mcp"
@mcp.tool(auth=sapl())
def hello(name: str) -> str:
    return f"Hello, {name}!"

# Static override: action="read_status" instead of "get_time"
@mcp.tool(auth=sapl(action="read_status"))
def get_time() -> str:
    from datetime import datetime, timezone
    return datetime.now(timezone.utc).isoformat()

# Callable override: extract username from token claims
@mcp.tool(auth=sapl(
    subject=lambda ctx: ctx.token.claims.get("preferred_username") if ctx.token else "anonymous",
    action="write_config",
    resource="server_config",
    secrets=lambda ctx: {"raw_token": ctx.token.token if ctx.token else None},
))
def write_config(key: str, value: str) -> dict:
    return {"key": key, "value": value, "status": "updated"}
```

#### Subscription Field Defaults (Auth Path)

| Field | Default |
|-------|---------|
| `subject` | Token claims dict, or `client_id`, or `"anonymous"` |
| `action` | Component name, for example `"hello"` or `"server_status"` |
| `resource` | `"mcp"` |
| `environment` | Not sent |
| `secrets` | Not sent |

Each field accepts a static value, a `Callable[[AuthContext], Any]`, or `None` to use the default. Falsy values like `0`, `""`, or `False` are valid overrides and will not trigger the default.

### Setup: Middleware (`SAPLMiddleware`)

Configure the runtime once, then build the middleware from the configured PDP client and planner.

```python
from fastmcp import FastMCP
from sapl_base.transport import HttpPdpClientOptions
from sapl_fastmcp import (
    SAPLMiddleware,
    configure_sapl,
    get_pdp_client,
    get_planner,
    register_provider,
)

configure_sapl(HttpPdpClientOptions(base_url="https://localhost:8443"))

register_provider(AccessLoggingProvider())
register_provider(LimitResultsProvider())
register_provider(FilterByClassificationProvider())

mcp = FastMCP(name="analytics", auth=jwt_verifier)
mcp.add_middleware(SAPLMiddleware(get_pdp_client(), get_planner()))
```

The `SAPLMiddleware` constructor takes the PDP client first and the enforcement planner second. The planner is optional. When omitted, the middleware builds a fresh `EnforcementPlanner` with no registered providers.

```python
SAPLMiddleware(pdp, planner=None, enforce_listing=True)
```

The PDP client and planner are injected at construction time, so the middleware does not depend on module-level globals. You can configure different PDP connections for different servers by building separate `HttpPdpClient` and `EnforcementPlanner` instances and passing them directly.

Components without a `@pre_enforce` or `@post_enforce` decorator pass through with no PDP call, allowing gradual adoption.

### Enforcement Decorators (`@pre_enforce` / `@post_enforce`)

These decorators are only used with the middleware path. They attach metadata to the function as `fn.__sapl__`, and the middleware reads this metadata at request time. The decorators do not wrap the function, so its identity is preserved for FastMCP's introspection.

#### @pre_enforce

The PDP is queried before the tool executes. The tool only runs on `PERMIT`.

```python
from sapl_fastmcp import pre_enforce

@mcp.tool(tags={"public"})
@pre_enforce()
def query_public_data(dataset: str, date_range: str = "last_30d") -> dict:
    return {"dataset": dataset, "rows": 14823}
```

With overrides.

```python
@mcp.tool(tags={"pii"})
@pre_enforce(
    resource=lambda ctx: {
        "name": ctx.component.name,
        "tags": list(ctx.component.tags),
        "segment": ctx.arguments.get("segment"),
    },
    stealth=True,
)
def query_customer_data(segment: str, limit: int = 10) -> dict:
    return {"segment": segment, "total_matches": 2847, "limit": limit}
```

#### @post_enforce

The tool executes first, then the PDP is queried with the return value available in the subscription context. If the decision is not `PERMIT`, the result is suppressed.

```python
from sapl_fastmcp import post_enforce

@mcp.tool(tags={"engineering"})
@post_enforce(resource=lambda ctx: {
    "name": ctx.component.name,
    "tags": list(ctx.component.tags),
    "model": ctx.arguments.get("model_id"),
    "result_summary": ctx.return_value,
})
def run_model(model_id: str, dataset: str) -> dict:
    return {"model_id": model_id, "status": "completed", "accuracy": 0.924}
```

Use `@post_enforce` when the policy needs to see the actual return value to decide. For example, a policy might permit access only if the result's classification level is below a threshold.

You cannot apply both `@pre_enforce` and `@post_enforce` to the same function. Attempting to do so raises `TypeError`.

#### Subscription Field Defaults (Middleware Path)

| Field | Default |
|-------|---------|
| `subject` | Token claims dict, or `client_id`, or `"anonymous"` |
| `action` | Operation verb, one of `"call"`, `"read"`, or `"get"` |
| `resource` | Dict with `name`, `arguments`, `tags`, and optionally `uri` |
| `environment` | Not sent |
| `secrets` | Not sent |

Each field accepts a static value, a `Callable[[SubscriptionContext], Any]`, or `None` to use the default.

### SubscriptionContext Reference

The `SubscriptionContext` is available to callable field overrides in the middleware path.

| Field | Type | Description |
|-------|------|-------------|
| `token` | `AccessToken` or `None` | OAuth token from the request |
| `component` | `Any` | The FastMCP Tool, Resource, ResourceTemplate, or Prompt object |
| `operation` | `"call"`, `"read"`, `"get"`, `"list"`, or `None` | MCP operation verb |
| `arguments` | `dict[str, Any]` | Tool or prompt arguments, empty for resources |
| `uri` | `str` or `None` | Resource URI, only for read operations |
| `return_value` | `Any` | Tool return value, set for `@post_enforce` only and `None` otherwise |

For the `auth=sapl()` path, callable fields receive an `AuthContext` from FastMCP instead of a `SubscriptionContext`. The `AuthContext` provides `token`, the `AccessToken`, and `component`, the FastMCP component.

### Stealth Mode

When `stealth=True` is set on `@pre_enforce` or `@post_enforce`, two things happen.

1. The component is hidden from listings when the subject is not authorized. The listing filter uses multi-decide to batch-query the PDP for all stealth components at once.
2. Denial raises `NotFoundError` instead of `AccessDeniedError`, making hidden components indistinguishable from non-existent ones.

```python
@mcp.tool(tags={"pii", "export"})
@pre_enforce(action="export_data", stealth=True)
def export_csv(query_ref: str, columns: str = "all") -> dict:
    return {"query_ref": query_ref, "rows_exported": 2847}
```

An unauthorized user calling this tool receives the same `NotFoundError` they would get for a tool that does not exist. The tool also does not appear in `tools/list` responses for that user.

Stealth only works with `SAPLMiddleware`. Using `stealth=True` with `auth=sapl()` logs a warning and has no effect.

### Finalize Callbacks

The `finalize` parameter on decorators provides an async callback that runs after enforcement regardless of outcome. It receives the `AuthorizationDecision` and the `SubscriptionContext`.

```python
async def _purge_finalize(decision, ctx: SubscriptionContext) -> None:
    """In production this would commit or roll back a database transaction."""
    logger.info(
        "purge_finalize: decision=%s, dataset=%s",
        decision.decision.value,
        ctx.arguments.get("dataset_id"),
    )

@mcp.tool(tags={"destructive", "compliance"})
@pre_enforce(finalize=_purge_finalize, stealth=True)
def purge_dataset(dataset_id: str, reason: str) -> dict:
    return {"dataset_id": dataset_id, "status": "purged", "records_deleted": 15234}
```

The callback signature is `async def finalize(decision: AuthorizationDecision, ctx: SubscriptionContext) -> None`.

The finalize callback always runs, even when the tool throws an exception. Exceptions in the finalize callback itself are logged and swallowed, and they never affect the enforcement outcome. Use finalize for transaction commit or rollback, resource cleanup, or audit logging.

Finalize only works with `SAPLMiddleware`. It has no effect with `auth=sapl()`.

### How Enforcement Works

#### The Deny Invariant

Only `PERMIT` grants access. The PDP can return five possible decisions (`PERMIT`, `DENY`, `SUSPEND`, `INDETERMINATE`, `NOT_APPLICABLE`), and only `PERMIT` ever results in your tool running. Everything else means denial. FastMCP operations are one-shot, so the PEP treats `SUSPEND` as `DENY`. See [Authorization Decisions](../2_3_AuthorizationDecisions/) for the per-decision PEP semantics.

A `PERMIT` with obligations is not a free pass. The enforcement point checks that every obligation in the decision has a registered handler. If even one obligation cannot be fulfilled, the decision is treated as a denial. If a handler accepts responsibility but fails during execution, that also results in denial. Advice is softer. If an advice handler fails, the failure is logged and the request proceeds.

| Aspect | Obligation | Advice |
|--------|-----------|--------|
| All handled? | Required. Unhandled obligations deny access. | Optional. Unhandled advice is silently ignored. |
| Handler failure | Denies access. | Logs a warning and continues. |

#### Enforcement Signals (Middleware Path)

The middleware delegates the pre and post enforcement logic to `sapl_base.pep`. Constraint handlers attach to one of four signals.

| Signal | When | What handlers do |
|--------|------|------------------|
| `DECISION` | Decision arrives | Side effects such as logging or audit |
| `INPUT` | Before the tool executes, `@pre_enforce` only | Modify tool arguments |
| `OUTPUT` | After the tool returns | Transform, filter, or replace the result |
| `ERROR` | The tool throws | Transform or observe the error |

#### Pre-Enforce Lifecycle

The middleware builds an authorization subscription from the decorator options or defaults and sends it to the PDP. If the decision is not `PERMIT`, `AccessDeniedError` is raised, or `NotFoundError` if stealth is set. If the decision is `PERMIT`, the planner resolves all constraint handlers. `DECISION` handlers run first, then `INPUT` handlers, which can modify tool arguments, then the tool executes, then `OUTPUT` handlers apply.

#### Post-Enforce Lifecycle

The tool executes first. Then the middleware builds the authorization subscription including the return value and queries the PDP. If the decision is not `PERMIT`, the return value is suppressed. If it is `PERMIT`, `OUTPUT` handlers can transform the result. `INPUT` handlers do not run because the tool has already executed.

#### Auth Path Lifecycle

The `auth=sapl()` path uses gate-level enforcement only. It builds the subscription, queries the PDP, runs `DECISION` handlers with obligations strict and advice best-effort, and returns a boolean. Resource replacement in the auth path is not supported and causes denial. There is no argument modification, result transformation, or error mapping.

### Constraint Handlers

When the PDP returns a decision with `obligations` or `advice`, the enforcement planner resolves and runs the matching handlers.

#### The Provider Model

A constraint handler provider implements a single method.

```python
from collections.abc import Sequence
from typing import Any

from sapl_base.pep import ScopedHandler

class AccessLoggingProvider:
    def get_handlers(self, constraint: Any) -> Sequence[ScopedHandler]:
        ...
```

`get_handlers(constraint)` inspects one constraint. If the provider claims it, the method returns the scoped handlers that enforce it. If the provider does not claim the constraint, it returns an empty sequence. The planner enforces exactly one claim per constraint. If no provider claims an obligation, or if more than one does, the planner installs a synthetic failure runner and the decision becomes a denial.

Each `ScopedHandler` declares the signal it attaches to, a priority that orders handlers within a signal, a shape, and the handler callable.

| Shape | Signature | Admissible at |
|-------|-----------|---------------|
| `runner` | `() -> None` | `DECISION` and other signals |
| `consumer` | `(value) -> None` | `OUTPUT` and `ERROR`, data-carrying signals |
| `mapper` | `(value) -> value` | `INPUT`, `OUTPUT`, and `ERROR`, data-carrying signals |

#### Registering Providers

Both paths register providers the same way. The `register_provider()` function adds a provider to the configured runtime and rebuilds the planner.

```python
from sapl_fastmcp import register_provider

register_provider(AccessLoggingProvider())
register_provider(LimitResultsProvider())
register_provider(FilterByClassificationProvider())
```

For an explicitly constructed planner passed to the middleware, supply the providers at construction time.

```python
from sapl_base.pep import EnforcementPlanner

planner = EnforcementPlanner(providers=(
    AccessLoggingProvider(),
    LimitResultsProvider(),
    FilterByClassificationProvider(),
))
```

#### Example: Decision Handler (Logging)

A `DECISION`-signal runner runs once per decision arrival. It produces a side effect and returns nothing.

```python
from collections.abc import Sequence
from typing import Any

from sapl_base.pep import DECISION, ScopedHandler

class AccessLoggingProvider:
    def get_handlers(self, constraint: Any) -> Sequence[ScopedHandler]:
        if not isinstance(constraint, dict) or constraint.get("type") != "logAccess":
            return ()
        message = constraint.get("message", "Tool access")
        subject = constraint.get("subject", "unknown")
        action = constraint.get("action", "unknown")

        def handler() -> None:
            logger.info("ACCESS LOG: %s, subject=%s, action=%s", message, subject, action)

        return (ScopedHandler(signal=DECISION, priority=0, shape="runner", handler=handler),)
```

#### Example: Input Handler (Argument Capping)

An `INPUT`-signal mapper runs before the tool, receives the call arguments as `(args, kwargs)`, and returns the modified arguments. It runs only on the `@pre_enforce` path.

```python
from collections.abc import Sequence
from typing import Any

from sapl_base.pep import INPUT, ScopedHandler

class LimitResultsProvider:
    def get_handlers(self, constraint: Any) -> Sequence[ScopedHandler]:
        if not isinstance(constraint, dict) or constraint.get("type") != "limitResults":
            return ()
        max_limit = int(constraint.get("maxLimit", 10))

        def handler(value: Any) -> Any:
            args, kwargs = value
            current = kwargs.get("limit")
            if current is not None:
                try:
                    if int(current) > max_limit:
                        kwargs = {**kwargs, "limit": max_limit}
                except (TypeError, ValueError):
                    kwargs = {**kwargs, "limit": max_limit}
            return (args, kwargs)

        return (ScopedHandler(signal=INPUT, priority=0, shape="mapper", handler=handler),)
```

#### Example: Output Handler (Classification Filter)

An `OUTPUT`-signal mapper runs after the tool returns, receives the return value, and returns a transformed value. This one filters list elements by their classification.

```python
from collections.abc import Sequence
from typing import Any

from sapl_base.pep import OUTPUT, ScopedHandler

class FilterByClassificationProvider:
    def get_handlers(self, constraint: Any) -> Sequence[ScopedHandler]:
        if not isinstance(constraint, dict) or constraint.get("type") != "filterByClassification":
            return ()
        allowed = set(constraint.get("allowedLevels", []))

        def handler(value: Any) -> Any:
            if not isinstance(value, list):
                return value
            return [
                element
                for element in value
                if not isinstance(element, dict)
                or element.get("classification") in allowed
            ]

        return (ScopedHandler(signal=OUTPUT, priority=20, shape="mapper", handler=handler),)
```

### Built-in Constraint Handlers

#### ContentFilteringProvider

Constraint type: `filterJsonContent`

Transforms response values by deleting, replacing, or blackening fields. A policy can attach this obligation.

```
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

The `blacken` action supports these options.

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `path` | string | required | Dot-notation path to a string field |
| `replacement` | string | block character | Character used for masking |
| `discloseLeft` | number | `0` | Characters to leave unmasked from the left |
| `discloseRight` | number | `0` | Characters to leave unmasked from the right |
| `length` | number | masked section length | Override the length of the masked section |

#### ContentFilterPredicateProvider

Constraint type: `jsonContentFilterPredicate`

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

The built-in content filter supports simple dot-notation paths only (`$.field.nested`). Recursive descent (`$..ssn`), bracket notation (`$['field']`), array indexing (`$.items[0]`), wildcards (`$.users[*].email`), and filter expressions (`$.books[?(@.price<10)]`) are not supported.

#### Registration

Both built-in providers are registered automatically when `configure_sapl()` initializes the runtime. They are always present in the planner alongside any providers you register. When you build an `EnforcementPlanner` explicitly to pass to the middleware, add `ContentFilteringProvider` and `ContentFilterPredicateProvider` yourself if you want them.

### STDIO Transport

SAPL authorization is bypassed for the STDIO transport. STDIO is a local subprocess transport with no network boundary and no authentication context, meaning no tokens and no headers. This matches FastMCP's built-in `AuthorizationMiddleware` behavior.

All middleware hooks pass through without PDP calls when the transport is STDIO. From an authorization perspective, constraining agent actions over STDIO requires a different trust and identity model that is outside the scope of the current integration.

### Manual PDP Access

For cases where neither middleware nor `auth=sapl()` fits, access the PDP client directly.

```python
from sapl_base import AuthorizationSubscription, Decision
from sapl_fastmcp import get_pdp_client

pdp = get_pdp_client()
subscription = AuthorizationSubscription(
    subject="anonymous",
    action="read",
    resource="hello",
)
decision = await pdp.decide_once(subscription)

if decision.decision == Decision.PERMIT and not decision.obligations:
    # proceed
    ...
```

When using the PDP client directly, you are responsible for checking the decision, enforcing obligations, and handling resource replacement.

### Writing SAPL Policies for MCP

SAPL policies evaluate against the subscription fields your MCP server sends. This section uses the demo's `analytics.sapl` policy file as a running example.

#### Subscription Shape

With the middleware path using default subscription fields.

- `subject` is the JWT claims dict, including `preferred_username`, `realm_access.roles`, and similar fields.
- `action` is the operation verb, one of `"call"`, `"read"`, or `"get"`.
- `resource` is a dict with `name`, `arguments`, `tags`, and optionally `uri`.

#### Tag-Based Policies

Components tagged in FastMCP have their tags included in the resource. A policy granting access to all public components.

```
policy "public-access"
permit
    "public" in resource.tags;
```

#### Role-Based Policies

With JWT claims as the subject, check roles from the identity provider.

```
policy "engineering-access"
permit
    "engineering" in resource.tags;
    "ENGINEER" in subject.realm_access.roles;
```

#### Obligation Examples

Attach constraints that handlers enforce at runtime.

```
policy "analyst-customer-queries"
permit
    resource.name == "query_customer_data";
    "ANALYST" in subject.realm_access.roles;
obligation
{
    "type": "limitResults",
    "maxLimit": 5
}
obligation
{
    "type": "logAccess",
    "message": "Customer PII query (result limit enforced)",
    "subject": subject.preferred_username,
    "action": action
}
```

The `limitResults` obligation is handled by an `INPUT`-signal mapper that caps the `limit` argument before the tool executes. The `logAccess` obligation is handled by a `DECISION`-signal runner that logs the access event.

#### Output Filter Obligations

Filter list results based on element properties.

```
policy "analyst-export-listing"
permit
    resource.name == "list_data_exports";
    "ANALYST" in subject.realm_access.roles;
obligation
{
    "type": "filterByClassification",
    "allowedLevels": ["public", "internal"]
}
```

The `FilterByClassificationProvider` removes list elements whose `classification` field is not in the allowed set.

#### Advice (Best-Effort)

Use `advice` instead of `obligation` when failure should not block access.

```
policy "pii-access"
permit
    "pii" in resource.tags;
    "ANALYST" in subject.realm_access.roles | "COMPLIANCE" in subject.realm_access.roles;
advice
{
    "type": "logAccess",
    "message": "PII data accessed",
    "subject": subject.preferred_username,
    "action": action
}
```

#### Policy Set Ordering

Use `first or abstain` to apply the first matching policy. More specific policies should come before general ones.

```
set "analytics"
first or abstain

policy "analyst-customer-queries"  // specific: analyst + customer data + obligations
permit ...

policy "public-access"             // general: all authenticated users + public tag
permit ...

policy "default-deny"              // catch-all: log and deny
deny ...
```

#### Default Deny

A catch-all deny policy at the end ensures unauthorized access is logged.

```
policy "default-deny"
deny
obligation
{
    "type": "logAccess",
    "message": "Unauthorized access attempt denied",
    "subject": subject.preferred_username,
    "action": action
}
```

### Demo Application

A complete working demo is available at [sapl-python-demos/fastmcp_demo](https://github.com/heutelbeck/sapl-python-demos/tree/main/fastmcp_demo). It includes the following.

- Middleware server (`middleware_server.py`) with `@pre_enforce`, `@post_enforce`, stealth mode, finalize callbacks, and several constraint handler providers
- Per-component auth server (`auth_server.py`) with `auth=sapl()` on every tool, resource, and prompt
- MCP client (`client.py`) that exercises both servers
- Automated end-to-end test (`demo.py`) with a decision matrix across four users with different roles (ANALYST, ENGINEER, COMPLIANCE, INTERN)
- SAPL policy file (`analytics.sapl`) with tag-based, role-based, and obligation-driven policies

### Configuration Reference

`HttpPdpClientOptions` parameters passed to `configure_sapl()` or `HttpPdpClient()`.

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `base_url` | `str` | required | PDP server URL |
| `token` | `str` | `None` | Bearer token or API key |
| `username` | `str` | `None` | Basic auth username |
| `secret` | `str` | `None` | Basic auth secret |
| `tls` | `TlsConfig` | `None` | TLS configuration for client certificates and trust |
| `timeout_seconds` | `float` | transport default | PDP request timeout in seconds |

The auth options are mutually exclusive. Pass exactly one of `token`, the `username` and `secret` pair, or `token_provider`. Pass none when targeting a SAPL Node configured with `allow-no-auth`.

`SAPLMiddleware` constructor parameters.

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `pdp` | `HttpPdpClient` | required | PDP client instance |
| `planner` | `EnforcementPlanner` | new instance | Enforcement planner with registered providers |
| `enforce_listing` | `bool` | `True` | Enable the multi-decide listing filter for stealth components |

### Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| All decisions INDETERMINATE | PDP unreachable | Check `base_url`, verify the PDP is running |
| AccessDeniedError despite PERMIT | Unhandled obligation | Confirm a provider claims the obligation `type` in `get_handlers` |
| Handler not firing | Missing registration | Call `register_provider` before the server starts |
| Subject is `"anonymous"` | No JWT configured or STDIO transport | Configure an auth provider on FastMCP |
| Stealth warning in logs | `stealth=True` with `auth=sapl()` | Use `SAPLMiddleware` for stealth mode |
| `RuntimeError: SAPL not configured` | Missing `configure_sapl()` | Call `configure_sapl()` before the server starts |
| STDIO requests bypass auth | Expected behavior | SAPL skips STDIO, a local transport with no auth context |
| Content filter throws | Unsupported path syntax | Only simple dot paths are supported (`$.field.nested`) |

### License

Apache-2.0
</content>
</invoke>
