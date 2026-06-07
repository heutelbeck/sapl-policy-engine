---
layout: default
title: .NET
has_children: false
parent: SDKs and APIs
nav_order: 610
---

## .NET SDK

Attribute-Based Access Control (ABAC) for ASP.NET Core using SAPL (Streaming Attribute Policy Language). Provides attribute-driven policy enforcement with a constraint handler architecture for obligations, advice, and response transformation.

The `Sapl.AspNetCore` library integrates SAPL policy enforcement into ASP.NET Core applications. It hooks into the MVC filter pipeline and the DI container to enforce authorization on controller actions and service-layer methods. The library is fully async, supports Server-Sent Events streaming for continuous authorization, and works with standard ASP.NET Core middleware and routing.

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

`decision` is always present (`PERMIT`, `DENY`, `SUSPEND`, `INDETERMINATE`, or `NOT_APPLICABLE`). The other fields are optional. `obligations` and `advice` are arrays of arbitrary JSON objects (by convention with a `type` field for handler dispatch), and `resource` (when present) replaces the action's return value entirely.

For a deeper introduction to SAPL's subscription model and policy language, see the [SAPL documentation](https://sapl.io/docs/latest/).

### Installation

Install the NuGet packages:

```bash
dotnet add package Sapl.Core
dotnet add package Sapl.AspNetCore
```

`Sapl.Core` contains the framework-agnostic core: the PDP client, authorization models, constraint handling engine, and enforcement engine. `Sapl.AspNetCore` adds ASP.NET Core integration: MVC filters, enforcement attributes, middleware, the subscription builder, and service-layer proxy support. Installing `Sapl.AspNetCore` automatically brings in `Sapl.Core` as a transitive dependency.

The library requires .NET 9.0 or later.

An RSocket transport is available in the separate `Sapl.Rsocket` package as an alternative to HTTP for the PDP connection. `AddSapl` wires the HTTP client; the RSocket client is registered explicitly. See the demo and integration tests for the RSocket setup.

A complete working demo with constraint handlers, service-layer enforcement, and streaming authorization is available at [sapl-dotnet-demos](https://github.com/heutelbeck/sapl-dotnet-demos).

### Setup

#### Service Registration

Call `AddSapl` on your `IServiceCollection` in `Program.cs`. This registers the PDP client, the enforcement engine and planner, the subscription resolver, and the MVC filters.

```csharp
using Sapl.AspNetCore.Extensions;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();

builder.Services.AddSapl(options =>
{
    options.BaseUrl = "https://localhost:8443";
    options.Token = "sapl_your_api_key_here";
});

var app = builder.Build();

app.UseSaplAccessDenied();
app.MapControllers();

app.Run();
```

For basic authentication instead of a bearer token:

```csharp
builder.Services.AddSapl(options =>
{
    options.BaseUrl = "https://localhost:8443";
    options.Username = "myPdpClient";
    options.Secret = "myPassword";
});
```

`Token` (bearer) and `Username`/`Secret` (basic auth) are mutually exclusive. Configure one or the other.

#### Configuration from appsettings.json

Instead of configuring options inline, you can bind from a configuration section:

```csharp
builder.Services.AddSapl(builder.Configuration, sectionName: "Sapl");
```

With the corresponding `appsettings.json`:

```json
{
  "Sapl": {
    "BaseUrl": "https://localhost:8443",
    "Token": "sapl_your_api_key_here"
  }
}
```

#### Local Development (HTTP)

For local development without TLS, point `BaseUrl` at an `http://` URL:

```csharp
builder.Services.AddSapl(options =>
{
    options.BaseUrl = "http://localhost:8443";
});
```

#### Access Denied Middleware

Register the access denied middleware to catch `AccessDeniedException` from enforcement filters and return HTTP 403 automatically:

```csharp
app.UseSaplAccessDenied();
```

This middleware should be registered before `app.MapControllers()`. Without it, unhandled `AccessDeniedException` would propagate as HTTP 500.

### Enforcement Attributes

`[PreEnforce]` and `[PostEnforce]` can be placed on controller action methods or on the controller class itself. `[StreamEnforce]` applies to methods only and targets actions returning `IAsyncEnumerable<T>`. The attributes work through ASP.NET Core's MVC filter pipeline, so they require standard controller routing (`[ApiController]`, `MapControllers()`).

#### [PreEnforce]

Authorizes **before** the action executes. The action only runs on PERMIT.

```csharp
using Microsoft.AspNetCore.Mvc;
using Sapl.Core.Attributes;

[ApiController]
[Route("api")]
public sealed class PatientController : ControllerBase
{
    [HttpGet("patient/{id}")]
    [PreEnforce(Action = "readPatient", Resource = "patient")]
    public IActionResult GetPatient(string id)
    {
        return Ok(new { id, name = "Jane Doe", ssn = "123-45-6789" });
    }
}
```

Use `[PreEnforce]` for actions with side effects (database writes, emails) that should not execute when access is denied. On denial, the filter short-circuits and returns HTTP 403.

#### [PostEnforce]

Authorizes **after** the action executes. The action always runs; its return value is available to the subscription builder and to constraint handlers for transformation.

```csharp
[HttpGet("patients")]
[PostEnforce(Action = "readPatients", Resource = "patients")]
public IActionResult GetPatients()
{
    return Ok(patients.Select(p => new { p.Id, p.Name, p.Ssn }));
}
```

Use `[PostEnforce]` when the policy needs to see the actual return value to make its authorization decision (e.g., deny based on the data's classification) or when constraint handlers need to transform the result (e.g., blacken SSN fields). On denial, the return value is discarded and HTTP 403 is returned.

#### Building the Authorization Subscription

Each attribute accepts named properties to customize the authorization subscription fields: `Subject`, `Action`, `Resource`, `Environment`, and `Secrets`.

**Default Values**

When not explicitly provided, the subscription fields are derived from the HTTP context:

| Field         | Default                                                                        |
| ------------- | ------------------------------------------------------------------------------ |
| `Subject`     | Claims from `HttpContext.User` as a dictionary, or `"anonymous"`               |
| `Action`      | `{"method": actionName, "controller": controllerName, "httpMethod": "GET"}`    |
| `Resource`    | `{"path": requestPath, "params": routeValues, "query": queryString}`           |
| `Environment` | Not sent unless explicitly specified                                           |
| `Secrets`     | Not sent unless explicitly specified                                           |

JWT integration is automatic: if the request carries a Bearer token and the ASP.NET Core authentication middleware has validated it, the claims principal is used as the subject. All JWT claims are included.

**Static Values**

Pass a string directly via the attribute property:

```csharp
[PreEnforce(Action = "read", Resource = "patient")]
```

**Secrets**

The `Secrets` property carries sensitive data that the PDP needs for policy evaluation but that must not appear in logs. It is excluded from debug logging automatically:

```csharp
[PreEnforce(Action = "exportData", Secrets = "jwt")]
```

**Subscription Customizers**

For subscription fields that require computed or structured values (which C# attributes cannot express as constants), implement `ISubscriptionCustomizer` and reference it via the `Customizer` property:

```csharp
using Sapl.Core.Subscription;

public sealed class PatientDetailCustomizer : ISubscriptionCustomizer
{
    public void Customize(SubscriptionContext context, SubscriptionBuilder builder)
    {
        builder.WithStaticResource(new { type = "patientDetail" });
    }
}
```

```csharp
[PostEnforce(Action = "getPatientDetail", Customizer = typeof(PatientDetailCustomizer))]
public async Task<object?> GetPatientDetail(string id)
{
    return await LoadPatient(id);
}
```

The customizer receives a `SubscriptionContext` with access to the `ClaimsPrincipal`, method name, class name, method arguments, return value (`PostEnforce` only), bearer token, and additional HTTP properties (path, route params, query string). Customizers are resolved from the DI container or instantiated via `ActivatorUtilities`, so they can have constructor-injected dependencies.

#### Streaming Enforcement

For SSE endpoints, the single `[StreamEnforce]` attribute provides continuous authorization. The PDP streams decisions over time and the PEP drives them through a four-state machine (Pending, Permitting, Suspended, Terminated). The decision verb chooses the behaviour, not a client-side attribute choice:

- **DENY** terminates the stream.
- **SUSPEND** pauses it: items are dropped while suspended and forwarding resumes on the next PERMIT. The subscription stays alive.
- **PERMIT** forwards items, transformed by any output handlers.

`[StreamEnforce]` carries the same subscription properties as the other attributes (`Subject`, `Action`, `Resource`, `Environment`, `Secrets`, `Customizer`) plus one streaming flag:

| Property            | Effect                                                                                                                                                                                                                       |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `SignalTransitions` | When `true`, each suspend/resume boundary surfaces an out-of-band frame to the subscriber (`ACCESS_SUSPENDED` on entering suspended, `ACCESS_RESTORED` on resuming). When `false` (default) transitions are silent and items simply drop while suspended. |

The three streaming semantics the old library expressed with three separate attributes are now expressed with one attribute plus the policy's decision verb:

| Goal                                  | Policy decision in the closed window | `SignalTransitions` |
| ------------------------------------- | ------------------------------------ | ------------------- |
| Terminate on access loss              | `deny`                               | (any)               |
| Pause silently, resume on permit      | `suspend`                            | `false`             |
| Pause with client-visible status      | `suspend`                            | `true`              |

```csharp
[ApiController]
[Route("api/streaming")]
public sealed class StreamingController(IStreamingService streamingService) : ControllerBase
{
    [HttpGet("heartbeat/till-denied")]
    [StreamEnforce(Action = "stream:terminate", Resource = "heartbeat")]
    public IAsyncEnumerable<Heartbeat> HeartbeatTillDenied() =>
        streamingService.Heartbeats(HttpContext.RequestAborted);

    [HttpGet("heartbeat/silent-suspending")]
    [StreamEnforce(Action = "stream:suspend", Resource = "heartbeat")]
    public IAsyncEnumerable<Heartbeat> HeartbeatSilentSuspending() =>
        streamingService.Heartbeats(HttpContext.RequestAborted);

    [HttpGet("heartbeat/observed-suspending")]
    [StreamEnforce(Action = "stream:suspend", Resource = "heartbeat", SignalTransitions = true)]
    public IAsyncEnumerable<Heartbeat> HeartbeatObservedSuspending() =>
        streamingService.Heartbeats(HttpContext.RequestAborted);
}
```

`[StreamEnforce]` applies to methods returning `IAsyncEnumerable<T>`. The filter renders the enforced stream as `text/event-stream`; on a terminal denial it writes a final `ACCESS_DENIED` frame before closing.

### How Enforcement Works

The attributes above are convenient, but to use them well it helps to understand what actually happens behind the scenes. This section walks through the enforcement lifecycle so you can reason about behavior.

#### The Deny Invariant

Only `PERMIT` grants access. The PDP can return five possible decisions (`PERMIT`, `DENY`, `SUSPEND`, `INDETERMINATE`, `NOT_APPLICABLE`), and only `PERMIT` ever results in your action running or your stream forwarding data. Everything else means denial. Streaming PEPs that honour `SUSPEND` pause the stream while keeping the subscription alive; one-shot PEPs treat `SUSPEND` as `DENY`. See [Authorization Decisions](../2_3_AuthorizationDecisions/) for details.

A `PERMIT` with obligations is not a free pass. The enforcement engine checks that every obligation in the decision has a registered handler. If even one obligation cannot be fulfilled, the engine treats the decision as a denial. If a handler accepts responsibility but fails during execution, that also results in denial. Advice is softer: if an advice handler fails, the engine logs the failure and moves on. Advice never causes denial.

| Aspect          | Obligation                                                | Advice                                         |
|-----------------|-----------------------------------------------------------|-------------------------------------------------|
| All handled?    | Required. Unhandled obligations deny access (403).        | Optional. Unhandled advice is silently ignored. |
| Handler failure | Denies access (403).                                      | Logs a warning and continues.                   |

This means you can always trust that if your action runs, every obligation attached to the decision has been successfully enforced.

#### Enforcement Locations

Depending on the attribute, constraint handlers can intervene at different points in the lifecycle of a request or stream.

For request-response actions (`[PreEnforce]` and `[PostEnforce]`), constraints can run at four points:

| Location              | When it happens                      | What constraints do here                            |
|-----------------------|--------------------------------------|-----------------------------------------------------|
| On decision           | Authorization decision arrives       | Side effects like logging, audit, or notification   |
| Pre-method invocation | Before the protected action executes | Modify action arguments (`[PreEnforce]` only)       |
| On return value       | After the action returns             | Transform, filter, or replace the result            |
| On error              | If the action throws                 | Transform or observe the error                      |

For streaming actions (`[StreamEnforce]`), constraints can run at these points:

| Location           | When it happens                              | What constraints do here                |
|--------------------|----------------------------------------------|-----------------------------------------|
| On decision        | Each new decision from the PDP stream        | Side effects like logging, audit        |
| On each data item  | Each element yielded by the stream           | Transform, filter, or replace items     |
| On stream error    | Stream produces an error                     | Transform or observe the error          |
| On stream complete | Stream finishes normally                     | Cleanup and finalization                |
| On cancel          | Client disconnects or enforcement terminates | Release resources and close connections |

#### PreEnforce Lifecycle

When you decorate an action with `[PreEnforce]`, here is what happens step by step.

First, the enforcement filter builds an authorization subscription from the attribute properties (or from defaults if you left them out) and sends it to the PDP as a one-shot request. The PDP evaluates the subscription against all matching policies and returns a single decision.

If the decision is anything other than `PERMIT`, the filter runs best-effort handlers and short-circuits with HTTP 403. Your action never runs.

If the decision is `PERMIT`, the engine resolves all constraint handlers. It walks through the obligations and advice attached to the decision and checks which registered handlers claim responsibility for each one. If any obligation has no matching handler, the engine denies access right there, because it cannot guarantee the obligation will be enforced.

With all handlers resolved, execution proceeds through the enforcement locations in order. On-decision handlers run first (logging, audit). Then method-invocation handlers run, which can modify action arguments if the policy requires it. Then your actual action executes. After the action returns, the engine applies return-value handlers: resource replacement if the decision included one, filter predicates, mapping handlers, and consumer handlers. If any obligation handler fails at any stage, the engine denies access.

#### PostEnforce Lifecycle

`[PostEnforce]` inverts the order. Your action runs first, regardless of the authorization outcome. Only after it returns does the engine build the authorization subscription (now including the return value) and consult the PDP.

This means the PDP can make decisions based on the actual data your action produced. For example, a policy might permit access to a record only if its classification level is below a threshold, something that can only be checked after loading the record.

If the decision is not `PERMIT`, the engine discards the return value and returns HTTP 403.

If the decision is `PERMIT`, constraint handlers proceed through the same stages as `[PreEnforce]`, minus the method-invocation handlers (since the action has already run). Return-value handlers can still transform the result before it reaches the caller.

Because the action runs before the PDP is consulted, if the action itself throws an exception, that exception propagates directly. The PDP is never called, because there is no return value to include in the subscription.

SAPL PEP libraries share a single unified enforcement model. It is a strict fail-closed state machine over the five decision verbs, where only `PERMIT` grants access and only an explicit `SUSPEND` pauses a stream without terminating it. See [Authorization Decisions](../2_3_AuthorizationDecisions/) for the decision-verb semantics.

### Constraint Handlers

When the PDP returns a decision with `obligations` or `advice`, the `EnforcementPlanner` asks every registered `IConstraintHandlerProvider` to translate each constraint into the handlers that enforce it, then schedules those handlers against the points of the request or stream lifecycle.

#### The Provider Model

A constraint handler provider implements a single method:

```csharp
public interface IConstraintHandlerProvider
{
    IReadOnlyList<ScopedHandler> GetConstraintHandlers(
        JsonElement constraint, IReadOnlySet<SignalType> supportedSignals);
}
```

Given one constraint, the provider returns an empty list if it does not recognise it, or one or more `ScopedHandler`s. Each `ScopedHandler` binds a handler to the lifecycle point (`SignalType`) it runs at, with a priority:

```csharp
public sealed record ScopedHandler(ConstraintHandler Handler, SignalType SignalType, int Priority);
```

Because a provider returns a list, one obligation can drive several handlers across different lifecycle points.

#### Handler Shapes

`ConstraintHandler` is one of three shapes:

| Shape                        | Signature                  | Use                                                      |
| ---------------------------- | -------------------------- | -------------------------------------------------------- |
| `ConstraintHandler.Runner`   | `Action Run`               | A side effect with no value (log, notify, audit).        |
| `ConstraintHandler.Consumer` | `Action<object?> Accept`   | Inspect the signal value without changing it.            |
| `ConstraintHandler.Mapper`   | `Func<object?, object?> Apply` | Transform the signal value (filter, redact, cap, replace). |

#### Lifecycle Points (SignalType)

A handler is scheduled against one `SignalType`. The value it sees depends on the point:

| SignalType                 | Fires when                                        | Value the handler sees                          |
| -------------------------- | ------------------------------------------------- | ----------------------------------------------- |
| `SignalType.Decision`      | A decision arrives                                | the `AuthorizationDecision`                     |
| `SignalType.Input`         | Before the protected method runs (`[PreEnforce]`) | the argument dictionary (keyed by parameter name) |
| `SignalType.Output(type)`  | After the method returns, or per stream item      | the return value or item                        |
| `SignalType.Error`         | The method throws                                 | the exception                                   |
| `SignalType.Complete`      | A stream completes normally                       | (none)                                          |
| `SignalType.Cancel`        | The subscriber cancels                            | (none)                                          |
| `SignalType.Termination`   | A stream terminates by enforcement                | (none)                                          |

Each enforcement point advertises which signals it supports. `GetConstraintHandlers` receives that `supportedSignals` set, so a provider can bind to the right one (for example, only attach an output mapper when an `Output` signal is available). When several mappers bind to the same signal, the `Priority` on each `ScopedHandler` orders them (higher runs first).

#### Static Helpers

`IConstraintHandlerProvider` exposes two static helpers for the common dispatch pattern:

- `ConstraintIsOfType(constraint, "typeName")` -- true when the constraint object's `type` field matches.
- `StringField(constraint, "field")` -- the string value of a named field, or null.

#### Registering Custom Handlers

Register handlers in `Program.cs` using the `AddSaplConstraintHandler<T>()` extension method:

```csharp
using Sapl.AspNetCore.Extensions;

builder.Services.AddSaplConstraintHandler<LogAccessHandler>();
builder.Services.AddSaplConstraintHandler<CapTransferHandler>();
builder.Services.AddSaplConstraintHandler<RedactFieldsHandler>();
```

This method inspects the type and automatically registers it under all applicable constraint handler interfaces it implements. The default lifetime is `Singleton`. An optional `ServiceLifetime` parameter can be passed if a different lifetime is needed.

Handlers are resolved from the DI container, so they can have constructor-injected dependencies like `ILogger<T>`.

#### Writing a Runner on the Decision

A runner performs a side effect when a decision arrives. This `logAccess` handler is the simplest shape:

```csharp
using System.Text.Json;
using Sapl.Core.Pep.Constraints;

public sealed class LogAccessHandler(ILogger<LogAccessHandler> logger) : IConstraintHandlerProvider
{
    public IReadOnlyList<ScopedHandler> GetConstraintHandlers(
        JsonElement constraint, IReadOnlySet<SignalType> supportedSignals)
    {
        if (!IConstraintHandlerProvider.ConstraintIsOfType(constraint, "logAccess"))
            return [];

        var message = IConstraintHandlerProvider.StringField(constraint, "message") ?? "Access logged";
        return [new ScopedHandler(
            new ConstraintHandler.Runner(() => logger.LogInformation("[POLICY] {Message}", message)),
            SignalType.Decision, 0)];
    }
}
```

#### Writing a Mapper on the Input

An input mapper rewrites method arguments before execution. This runs only with `[PreEnforce]`. The handler receives the argument dictionary keyed by parameter name and returns it (possibly mutated):

```csharp
using System.Text.Json;
using Sapl.Core.Pep.Constraints;

public sealed class CapTransferHandler(ILogger<CapTransferHandler> logger) : IConstraintHandlerProvider
{
    public IReadOnlyList<ScopedHandler> GetConstraintHandlers(
        JsonElement constraint, IReadOnlySet<SignalType> supportedSignals)
    {
        if (!IConstraintHandlerProvider.ConstraintIsOfType(constraint, "capTransferAmount"))
            return [];

        var maxAmount = constraint.TryGetProperty("maxAmount", out var max)
            && max.TryGetDouble(out var value) ? value : 5000d;
        return [new ScopedHandler(
            new ConstraintHandler.Mapper(args => Cap(args, maxAmount)), SignalType.Input, 0)];
    }

    private object? Cap(object? args, double maxAmount)
    {
        if (args is IDictionary<string, object?> arguments
            && arguments.TryGetValue("amount", out var raw) && raw is double requested
            && requested > maxAmount)
        {
            logger.LogInformation("[CAP] transfer amount {Requested} -> {Max}", requested, maxAmount);
            arguments["amount"] = maxAmount;
        }

        return args;
    }
}
```

#### Writing a Mapper on the Output

An output mapper transforms the return value. This `redactFields` handler binds to whichever `Output` signal the enforcement point advertises, then redacts named fields from the JSON response:

```csharp
using System.Text.Json;
using System.Text.Json.Nodes;
using Sapl.Core.Pep.Constraints;

public sealed class RedactFieldsHandler : IConstraintHandlerProvider
{
    public IReadOnlyList<ScopedHandler> GetConstraintHandlers(
        JsonElement constraint, IReadOnlySet<SignalType> supportedSignals)
    {
        if (!IConstraintHandlerProvider.ConstraintIsOfType(constraint, "redactFields"))
            return [];

        var output = supportedSignals.FirstOrDefault(signal => signal.Kind == SignalKind.Output);
        if (output is null)
            return [];

        var fields = ReadFields(constraint);
        return [new ScopedHandler(new ConstraintHandler.Mapper(value => Redact(value, fields)), output, 0)];
    }

    private static object? Redact(object? value, IReadOnlyList<string> fields)
    {
        var json = value is JsonElement el ? el.GetRawText() : JsonSerializer.Serialize(value);
        if (JsonNode.Parse(json) is not JsonObject obj)
            return value;

        foreach (var field in fields.Where(obj.ContainsKey))
            obj[field] = "[REDACTED]";

        return JsonDocument.Parse(obj.ToJsonString()).RootElement.Clone();
    }

    private static List<string> ReadFields(JsonElement constraint)
    {
        var fields = new List<string>();
        if (constraint.TryGetProperty("fields", out var array) && array.ValueKind == JsonValueKind.Array)
            fields.AddRange(array.EnumerateArray().Select(f => f.GetString()).OfType<string>());
        return fields;
    }
}
```

### Built-in Constraint Handlers

#### ContentFilteringConstraintHandlerProvider

**Constraint type:** `filterJsonContent`

Transforms response values by deleting, replacing, or blackening fields. When the value is a list, each action is applied to every element.

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

| Option          | Type   | Default                | Description                                 |
| --------------- | ------ | ---------------------- | ------------------------------------------- |
| `path`          | string | (required)             | JSONPath to a string field                  |
| `replacement`   | string | `"*"`                  | Character used for masking                  |
| `discloseLeft`  | number | `0`                    | Characters to leave unmasked from the left  |
| `discloseRight` | number | `0`                    | Characters to leave unmasked from the right |
| `length`        | number | (masked section length)| Override the length of the masked section   |

#### Path Syntax

Paths are resolved with Newtonsoft `SelectToken`, so JSONPath is supported: simple dot paths (`$.field.nested`), recursive descent (`$..ssn`), array indexing (`$.items[0]`), wildcards (`$.users[*].email`), and filter expressions (`$.books[?(@.price<10)]`). `blacken` targets a single text node (the first match); a path that does not resolve is left unchanged.

### Service-Layer Enforcement

SAPL enforcement is not limited to controller actions. The same attributes work on service interface methods, enforced transparently through .NET's `DispatchProxy` mechanism. This lets you push authorization into the service layer, so that controllers remain free of SAPL concerns.

#### Registering Enforced Services

Register a service interface with its implementation using `AddSaplService<TInterface, TImpl>()`:

```csharp
builder.Services.AddSaplService<IPatientService, PatientService>();
```

This registers the real `PatientService` in the DI container and wraps it with a `SaplProxy<IPatientService>`. When any code resolves `IPatientService`, it receives the proxy. The proxy intercepts every call to a method decorated with a SAPL attribute and runs the enforcement engine before or after the real method, exactly as the MVC filters do for controller actions.

Methods without SAPL attributes pass through to the real implementation unmodified.

#### Decorating Interface Methods

Place SAPL attributes directly on the interface methods. All three enforcement attributes are supported: `[PreEnforce]`, `[PostEnforce]`, and `[StreamEnforce]`.

```csharp
using Sapl.Core.Attributes;

public interface IPatientService
{
    [PreEnforce(Action = "listPatients", Resource = "patients")]
    Task<object?> ListPatients(CancellationToken ct = default);

    [PostEnforce(Action = "getPatientDetail", Customizer = typeof(PatientDetailCustomizer))]
    Task<object?> GetPatientDetail(string id, CancellationToken ct = default);

    [PreEnforce(Action = "transfer", Resource = "account")]
    Task<object?> Transfer(double amount, CancellationToken ct = default);
}
```

The implementation class needs no SAPL annotations at all:

```csharp
public sealed class PatientService : IPatientService
{
    public Task<object?> ListPatients(CancellationToken ct)
    {
        return Task.FromResult<object?>(patients);
    }

    public Task<object?> GetPatientDetail(string id, CancellationToken ct)
    {
        return Task.FromResult<object?>(patients.FirstOrDefault(p => p.Id == id));
    }

    public Task<object?> Transfer(double amount, CancellationToken ct)
    {
        return Task.FromResult<object?>(new { transferred = amount });
    }
}
```

#### How the Proxy Works

`SaplProxy<T>` extends `DispatchProxy`, a built-in .NET mechanism for creating interface-based runtime proxies. When a proxied method is called, the proxy reads the SAPL attribute from the interface method via reflection and delegates to the `SaplMethodInterceptor`, which builds the authorization subscription, calls the PDP, resolves constraint handlers, and executes the real method at the appropriate point in the enforcement lifecycle.

The proxy supports both synchronous and async return types. For `Task<T>` methods, the proxy correctly awaits the underlying method. For `IAsyncEnumerable<T>` methods carrying `[StreamEnforce]`, the proxy drives the source stream through the streaming enforcement engine.

On denial, the proxy throws `AccessDeniedException`. When called from a controller with `UseSaplAccessDenied()` middleware registered, this is caught and translated to HTTP 403 automatically.

#### Streaming Services

Streaming enforcement on services works the same as on controllers. The interface method returns `IAsyncEnumerable<T>` and the proxy wraps it:

```csharp
using Sapl.Core.Attributes;

public interface IStreamingService
{
    [StreamEnforce(Action = "stream:suspend", Resource = "heartbeat")]
    IAsyncEnumerable<Heartbeat> Heartbeats(CancellationToken ct = default);
}
```

At the service (proxy) layer the enforced stream keeps yielding the concrete element type; boundary frames (`ACCESS_SUSPENDED` / `ACCESS_RESTORED` / `ACCESS_DENIED`) are a transport concern rendered by the SSE controller filter, so `SignalTransitions` applies to controller-level streaming.

### Manual PDP Access

For cases where attributes are not suitable, inject `IPolicyDecisionPoint` directly and call the PDP programmatically:

```csharp
using Sapl.Core.Authorization;
using Sapl.Core.Client;

[HttpGet("hello")]
public async Task<IActionResult> GetHello(
    [FromServices] IPolicyDecisionPoint pdp)
{
    var subscription = AuthorizationSubscription.Create(
        subject: "anonymous",
        action: "read",
        resource: "hello");

    var decision = await pdp.DecideOnceAsync(subscription, HttpContext.RequestAborted);

    if (decision.Decision == Decision.Permit
        && decision.Obligations is null)
    {
        return Ok(new { message = "hello" });
    }

    return StatusCode(403, new { error = "Access denied" });
}
```

When using the PDP client directly, you are responsible for checking the decision, enforcing obligations, and handling resource replacement.

The `IPolicyDecisionPoint` interface exposes both one-shot and streaming endpoints:

| Method                 | Return Type                                  | Description                                  |
| ---------------------- | -------------------------------------------- | -------------------------------------------- |
| `DecideOnceAsync`      | `Task<AuthorizationDecision>`                | One-shot single subscription                 |
| `Decide`               | `IAsyncEnumerable<AuthorizationDecision>`    | Streaming single subscription                |
| `MultiDecideAllOnceAsync` | `Task<MultiAuthorizationDecision>`        | One-shot multi subscription (all decisions)  |
| `MultiDecide`          | `IAsyncEnumerable<IdentifiableAuthorizationDecision>` | Streaming multi subscription    |
| `MultiDecideAll`       | `IAsyncEnumerable<MultiAuthorizationDecision>` | Streaming multi subscription (all decisions) |

### Client Resilience

The PDP client treats every transport problem as an operational condition, never as a policy outcome, and never lets one surface as an exception. A connection drop, timeout, or decode error fails closed to `INDETERMINATE`, which the PEP enforces as a denial, so a transient PDP outage can never accidentally grant access.

One-shot requests (`DecideOnceAsync`, `MultiDecideAllOnceAsync`) fail closed to `INDETERMINATE` immediately, with no retry, and never throw. The returned `Task` always completes with a decision. In steady state the connection is warm, so only a cold or dropped connection fails closed.

Subscriptions (the streaming `Decide`) never terminate on a transport problem or on a server-side stream completion. The returned `IAsyncEnumerable<AuthorizationDecision>` never throws or ends for a transport condition. Either condition yields one `INDETERMINATE` and then reconnects with bounded exponential backoff, indefinitely. Consecutive identical decisions are de-duplicated, so an outage yields a single `INDETERMINATE`, not a flood. A subscription ends only when the consumer cancels it or the client shuts down. This contract holds identically across the HTTP and RSocket transports and across every SAPL PEP client.

### Demo Application

A complete working demo is available at [sapl-dotnet-demos](https://github.com/heutelbeck/sapl-dotnet-demos). It includes:

- Manual PDP access (no attributes)
- `[PreEnforce]` and `[PostEnforce]` with content filtering and field redaction
- Service-layer enforcement using `DispatchProxy` and interface attributes
- Constraint handlers across every signal and shape (decision/input/output/error; runner/consumer/mapper)
- SSE streaming with the three semantics (till-denied, silent-suspending, observed-suspending)
- JWT-based ABAC

### Configuration Reference

All options are set via `PdpClientOptions`, either inline or from configuration:

| Property                      | Type   | Default                     | Description                                               |
| ----------------------------- | ------ | --------------------------- | --------------------------------------------------------- |
| `BaseUrl`                     | `string` | `"https://localhost:8443"` | PDP server URL                                            |
| `Token`                       | `string?` | `null`                    | Bearer token for authentication                           |
| `Username`                    | `string?` | `null`                    | Basic auth username (mutually exclusive with `Token`)     |
| `Secret`                      | `string?` | `null`                    | Basic auth password                                       |
| `TimeoutMs`                   | `int`   | `5000`                     | PDP request timeout in milliseconds                       |
| `StreamingRetryBaseDelayMs`   | `int`   | `1000`                     | Base delay for exponential backoff on reconnection        |
| `StreamingRetryMaxDelayMs`    | `int`   | `30000`                    | Maximum delay between reconnection attempts               |

Streaming retries use exponential backoff with jitter. The delay doubles on each attempt up to the maximum, with random jitter between 50% and 100% of the calculated delay. After 5 consecutive failures, log severity escalates from Warning to Error.

### Troubleshooting

| Symptom                              | Likely Cause                          | Fix                                                               |
| ------------------------------------ | ------------------------------------- | ----------------------------------------------------------------- |
| All decisions are INDETERMINATE       | PDP unreachable                       | Check `BaseUrl` and that the PDP is running                       |
| 403 despite PERMIT decision           | Unhandled obligation                  | Check a provider's `GetConstraintHandlers` returns a handler for the obligation `type` |
| Handler not firing                    | Missing registration                  | Call `AddSaplConstraintHandler<T>()` in `Program.cs`              |
| Subject is `"anonymous"`              | No authenticated user                 | Configure ASP.NET Core authentication middleware and JWT validation |
| Content filter throws                 | Invalid JSONPath                      | Paths resolve through Newtonsoft `SelectToken`; check the JSONPath syntax (recursive descent, array indexing, wildcards, and filter expressions are all supported) |
| Service method throws `AccessDeniedException` | Normal denial behavior       | Register `UseSaplAccessDenied()` middleware for automatic 403      |
| Streaming SSE empty                   | Action does not return `IAsyncEnumerable` | Ensure streaming methods return `IAsyncEnumerable<T>`          |
| HTTP 500 on service denial            | Missing middleware                    | Add `app.UseSaplAccessDenied()` before `app.MapControllers()`      |

### License

Apache-2.0
