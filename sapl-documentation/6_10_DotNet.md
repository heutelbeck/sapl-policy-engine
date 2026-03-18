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

`decision` is always present (`PERMIT`, `DENY`, `INDETERMINATE`, or `NOT_APPLICABLE`). The other fields are optional. `obligations` and `advice` are arrays of arbitrary JSON objects (by convention with a `type` field for handler dispatch), and `resource` (when present) replaces the action's return value entirely.

For a deeper introduction to SAPL's subscription model and policy language, see the [SAPL documentation](https://sapl.io/docs/latest/).

### Installation

Install the NuGet packages:

```bash
dotnet add package Sapl.Core
dotnet add package Sapl.AspNetCore
```

`Sapl.Core` contains the framework-agnostic core: the PDP client, authorization models, constraint handling engine, and enforcement engine. `Sapl.AspNetCore` adds ASP.NET Core integration: MVC filters, enforcement attributes, middleware, the subscription builder, and service-layer proxy support. Installing `Sapl.AspNetCore` automatically brings in `Sapl.Core` as a transitive dependency.

The library requires .NET 9.0 or later.

A complete working demo with constraint handlers, service-layer enforcement, and streaming authorization is available at [sapl-dotnet-demos](https://github.com/heutelbeck/sapl-dotnet-demos).

### Setup

#### Service Registration

Call `AddSapl` on your `IServiceCollection` in `Program.cs`. This registers the PDP client, constraint enforcement service, enforcement engine, and MVC filters.

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

For local development without TLS:

```csharp
builder.Services.AddSapl(options =>
{
    options.BaseUrl = "http://localhost:8443";
    options.AllowInsecureConnections = true;
});
```

#### Access Denied Middleware

Register the access denied middleware to catch `AccessDeniedException` from enforcement filters and return HTTP 403 automatically:

```csharp
app.UseSaplAccessDenied();
```

This middleware should be registered before `app.MapControllers()`. Without it, unhandled `AccessDeniedException` would propagate as HTTP 500.

### Enforcement Attributes

All enforcement attributes can be placed on controller action methods or on the controller class itself. The attributes work through ASP.NET Core's MVC filter pipeline, so they require standard controller routing (`[ApiController]`, `MapControllers()`).

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

For SSE endpoints, three streaming attributes provide continuous authorization where the PDP streams decisions over time. Access may flip between PERMIT and DENY based on time, location, or context changes.

**[EnforceTillDenied]**

Streaming enforcement that terminates permanently on the first non-PERMIT decision.

**[EnforceDropWhileDenied]**

Silently drops data during DENY periods. The stream stays alive and resumes forwarding when a new PERMIT decision arrives.

**[EnforceRecoverableIfDenied]**

Sends in-band suspend/resume signals on policy transitions. Edge-triggered: the `onDeny` callback fires on PERMIT-to-DENY transitions, the `onRecover` callback fires on DENY-to-PERMIT transitions.

| Scenario                                      | Strategy                     |
| --------------------------------------------- | ---------------------------- |
| Access loss is permanent (revoked credentials) | `[EnforceTillDenied]`        |
| Client does not need to know about gaps        | `[EnforceDropWhileDenied]`   |
| Client should show suspended/restored status   | `[EnforceRecoverableIfDenied]` |

Streaming attributes apply to methods returning `IAsyncEnumerable<T>` or `Task<IAsyncEnumerable<T>>`. In ASP.NET Core, SSE output is written manually since there is no built-in SSE framework. The demo application shows how to write `text/event-stream` responses from `IAsyncEnumerable<T>` streams.

### How Enforcement Works

The attributes above are convenient, but to use them well it helps to understand what actually happens behind the scenes. This section walks through the enforcement lifecycle so you can reason about behavior.

#### The Deny Invariant

Only `PERMIT` grants access. The PDP can return four possible decisions (`PERMIT`, `DENY`, `INDETERMINATE`, `NOT_APPLICABLE`), and only `PERMIT` ever results in your action running or your stream forwarding data. Everything else means denial.

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

For streaming actions (`[EnforceTillDenied]`, `[EnforceDropWhileDenied]`, `[EnforceRecoverableIfDenied]`), constraints can run at five points:

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

For a complete formal specification of all enforcement modes, including state machines, teardown invariants, and handler resolution timing, see the [PEP Implementation Specification](../8_1_PEPImplementationSpecification/).

### Constraint Handlers

When the PDP returns a decision with `obligations` or `advice`, the constraint enforcement service resolves and executes all matching handlers.

#### When to Use Which Handler

| You want to...                            | Use this interface                              |
| ----------------------------------------- | ----------------------------------------------- |
| Log or notify on a decision               | `IRunnableConstraintHandlerProvider`            |
| Record/inspect the response (side-effect) | `IConsumerConstraintHandlerProvider`            |
| Transform the response                    | `IMappingConstraintHandlerProvider`             |
| Filter array elements from the response   | `IFilterPredicateConstraintHandlerProvider`     |
| Modify request or action arguments        | `IMethodInvocationConstraintHandlerProvider`    |
| Log/notify on errors (side-effect)        | `IErrorHandlerProvider`                         |
| Transform errors                          | `IErrorMappingConstraintHandlerProvider`        |

#### Handler Interfaces Reference

All constraint handler providers implement `IConstraintHandlerProvider`, which defines `IsResponsible(JsonElement constraint)` for dispatch and an optional `Signal` property (defaults to `Signal.OnDecision`).

| Interface                                    | Handler Signature                          | When It Runs                          |
| -------------------------------------------- | ------------------------------------------ | ------------------------------------- |
| `IRunnableConstraintHandlerProvider`         | `Action GetHandler(JsonElement)`           | On decision (side effects)            |
| `IMethodInvocationConstraintHandlerProvider` | `Action<MethodInvocationContext> GetHandler(JsonElement)` | Before action (`[PreEnforce]` only) |
| `IConsumerConstraintHandlerProvider`         | `Action<object> GetHandler(JsonElement)`   | After action, inspects response       |
| `IMappingConstraintHandlerProvider`          | `Func<object, object> GetHandler(JsonElement)` | After action, transforms response |
| `IFilterPredicateConstraintHandlerProvider`  | `Func<object, bool> GetHandler(JsonElement)` | After action, filters list elements |
| `IErrorHandlerProvider`                      | `Action<Exception> GetHandler(JsonElement)` | On error, inspects                   |
| `IErrorMappingConstraintHandlerProvider`     | `Func<Exception, Exception> GetHandler(JsonElement)` | On error, transforms          |

`IMappingConstraintHandlerProvider` and `IErrorMappingConstraintHandlerProvider` also expose `int Priority` (default 0). When multiple mapping handlers match the same constraint, they execute in descending priority order (higher number runs first).

#### Signal Timing

The `Signal` property on `IConstraintHandlerProvider` controls when a runnable handler fires during streaming enforcement:

| Signal             | Fires when                                       |
| ------------------ | ------------------------------------------------ |
| `Signal.OnDecision`  | A new authorization decision arrives             |
| `Signal.OnComplete`  | The data stream completes normally               |
| `Signal.OnCancel`    | The client disconnects or enforcement terminates |

For request-response enforcement (`[PreEnforce]` and `[PostEnforce]`), only `Signal.OnDecision` applies.

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

#### Writing a Runnable Handler

A runnable handler performs side effects when a decision arrives. This is the simplest handler type:

```csharp
using System.Text.Json;
using Sapl.Core.Constraints.Api;

public sealed class LogAccessHandler : IRunnableConstraintHandlerProvider
{
    private readonly ILogger<LogAccessHandler> _logger;

    public LogAccessHandler(ILogger<LogAccessHandler> logger)
    {
        _logger = logger;
    }

    public bool IsResponsible(JsonElement constraint)
    {
        return constraint.TryGetProperty("type", out var t)
            && t.GetString() == "logAccess";
    }

    public Signal Signal => Signal.OnDecision;

    public Action GetHandler(JsonElement constraint)
    {
        var message = constraint.TryGetProperty("message", out var m)
            ? m.GetString() ?? "Access logged"
            : "Access logged";

        return () => _logger.LogInformation("[POLICY] {Message}", message);
    }
}
```

#### Writing a Method Invocation Handler

A method invocation handler modifies action arguments before execution. This runs only with `[PreEnforce]`:

```csharp
using System.Text.Json;
using Sapl.Core.Constraints.Api;

public sealed class CapTransferHandler : IMethodInvocationConstraintHandlerProvider
{
    private readonly ILogger<CapTransferHandler> _logger;

    public CapTransferHandler(ILogger<CapTransferHandler> logger)
    {
        _logger = logger;
    }

    public bool IsResponsible(JsonElement constraint)
    {
        return constraint.TryGetProperty("type", out var t)
            && t.GetString() == "capTransferAmount";
    }

    public Action<MethodInvocationContext> GetHandler(JsonElement constraint)
    {
        var maxAmount = constraint.TryGetProperty("maxAmount", out var m)
            ? m.GetDouble() : 5000;

        return context =>
        {
            if (context.Args.Length > 0
                && context.Args[0] is double requested
                && requested > maxAmount)
            {
                context.Args[0] = maxAmount;
            }
        };
    }
}
```

#### Writing a Mapping Handler

A mapping handler transforms the return value. This example redacts named fields from the JSON response:

```csharp
using System.Text.Json;
using System.Text.Json.Nodes;
using Sapl.Core.Constraints.Api;

public sealed class RedactFieldsHandler : IMappingConstraintHandlerProvider
{
    public bool IsResponsible(JsonElement constraint)
    {
        return constraint.TryGetProperty("type", out var t)
            && t.GetString() == "redactFields";
    }

    public int Priority => 0;

    public Func<object, object> GetHandler(JsonElement constraint)
    {
        var fields = new List<string>();
        if (constraint.TryGetProperty("fields", out var f)
            && f.ValueKind == JsonValueKind.Array)
        {
            foreach (var field in f.EnumerateArray())
            {
                var name = field.GetString();
                if (name is not null)
                    fields.Add(name);
            }
        }

        return value =>
        {
            var json = value is JsonElement el
                ? el.GetRawText()
                : JsonSerializer.Serialize(value);
            var node = JsonNode.Parse(json);
            if (node is JsonObject obj)
            {
                foreach (var field in fields)
                {
                    if (obj.ContainsKey(field))
                        obj[field] = "[REDACTED]";
                }
                return JsonDocument.Parse(obj.ToJsonString()).RootElement.Clone();
            }
            return value;
        };
    }
}
```

#### MethodInvocationContext

The `MethodInvocationContext` provides:

| Field        | Type         | Description                                                          |
| ------------ | ------------ | -------------------------------------------------------------------- |
| `Args`       | `object?[]`  | Positional arguments. Handlers can mutate or replace entries.         |
| `MethodName` | `string`     | The intercepted method name                                          |
| `ClassName`  | `string?`    | The class name (null for free-standing methods)                      |
| `Request`    | `object?`    | The HTTP request object, or null for service-layer calls             |

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

| Option          | Type   | Default                | Description                                 |
| --------------- | ------ | ---------------------- | ------------------------------------------- |
| `path`          | string | (required)             | Dot-notation path to a string field         |
| `replacement`   | string | `"*"`                  | Character used for masking                  |
| `discloseLeft`  | number | `0`                    | Characters to leave unmasked from the left  |
| `discloseRight` | number | `0`                    | Characters to leave unmasked from the right |
| `length`        | number | (masked section length)| Override the length of the masked section   |

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

The built-in content filter supports simple dot-notation paths only (`$.field.nested`). Recursive descent (`$..ssn`), bracket notation (`$['field']`), array indexing (`$.items[0]`), wildcards (`$.users[*].email`), and filter expressions (`$.books[?(@.price<10)]`) are not supported.

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

Place SAPL attributes directly on the interface methods. All five enforcement attributes are supported: `[PreEnforce]`, `[PostEnforce]`, `[EnforceTillDenied]`, `[EnforceDropWhileDenied]`, and `[EnforceRecoverableIfDenied]`.

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

The proxy supports both synchronous and async return types. For `Task<T>` methods, the proxy correctly awaits the underlying method. For `IAsyncEnumerable<T>` methods (streaming), the proxy wraps the source stream with the appropriate streaming enforcement strategy.

On denial, the proxy throws `AccessDeniedException`. When called from a controller with `UseSaplAccessDenied()` middleware registered, this is caught and translated to HTTP 403 automatically.

#### Streaming Services

Streaming enforcement on services works the same as on controllers. The interface method returns `IAsyncEnumerable<T>` and the proxy wraps it:

```csharp
using Sapl.Core.Attributes;

public interface IStreamingService
{
    [EnforceTillDenied(Action = "stream:heartbeat", Resource = "heartbeat")]
    IAsyncEnumerable<Heartbeat> HeartbeatTillDenied(CancellationToken ct = default);

    [EnforceDropWhileDenied(Action = "stream:heartbeat", Resource = "heartbeat")]
    IAsyncEnumerable<Heartbeat> HeartbeatDropWhileDenied(CancellationToken ct = default);

    [EnforceRecoverableIfDenied(Action = "stream:heartbeat", Resource = "heartbeat")]
    IAsyncEnumerable<object> HeartbeatRecoverable(CancellationToken ct = default);
}
```

The recoverable stream returns `IAsyncEnumerable<object>` instead of a concrete type because the enforcement interceptor injects `AccessSignal` items into the stream to notify consumers of deny/recover transitions. Using a concrete type would make signal injection impossible.

#### Recoverable Streams and Access Signals

For `[EnforceRecoverableIfDenied]`, the stream may contain `AccessSignal` items mixed with regular data items. The `RecoverableStreams` extension methods help process these:

```csharp
using Sapl.Core.Interception;

// Filter out signals, invoke a callback on each transition:
var filtered = stream.Recover(onSignal: signal =>
{
    Console.WriteLine(signal.Kind == AccessSignalKind.Denied
        ? "Access suspended"
        : "Access restored");
});

// Replace signals with custom items:
var withItems = stream.RecoverWith(
    onDenyItem: () => (object)new { type = "ACCESS_SUSPENDED" },
    onRecoverItem: () => (object)new { type = "ACCESS_RESTORED" });
```

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
| `MultiDecideOnceAsync` | `Task<MultiAuthorizationDecision>`           | One-shot multi subscription                  |
| `MultiDecideAllOnceAsync` | `Task<MultiAuthorizationDecision>`        | One-shot multi subscription (all decisions)  |
| `MultiDecide`          | `IAsyncEnumerable<IdentifiableAuthorizationDecision>` | Streaming multi subscription    |
| `MultiDecideAll`       | `IAsyncEnumerable<MultiAuthorizationDecision>` | Streaming multi subscription (all decisions) |

### Demo Application

A complete working demo is available at [sapl-dotnet-demos](https://github.com/heutelbeck/sapl-dotnet-demos). It includes:

- Manual PDP access (no attributes)
- `[PreEnforce]` and `[PostEnforce]` with content filtering and field redaction
- Service-layer enforcement using `DispatchProxy` and interface attributes
- All 7 constraint handler types (runnable, consumer, mapping, filter predicate, method invocation, error handler, error mapping)
- SSE streaming with all three enforcement strategies (till-denied, drop-while-denied, recoverable-if-denied)
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
| `AllowInsecureConnections`    | `bool`  | `false`                    | Allow HTTP connections (never use in production)          |
| `StreamingMaxRetries`         | `int`   | `0` (unlimited)            | Maximum reconnection attempts for streaming subscriptions |
| `StreamingRetryBaseDelayMs`   | `int`   | `1000`                     | Base delay for exponential backoff on reconnection        |
| `StreamingRetryMaxDelayMs`    | `int`   | `30000`                    | Maximum delay between reconnection attempts               |

Streaming retries use exponential backoff with jitter. The delay doubles on each attempt up to the maximum, with random jitter between 50% and 100% of the calculated delay. After 5 consecutive failures, log severity escalates from Warning to Error.

### Troubleshooting

| Symptom                              | Likely Cause                          | Fix                                                               |
| ------------------------------------ | ------------------------------------- | ----------------------------------------------------------------- |
| All decisions are INDETERMINATE       | PDP unreachable                       | Check `BaseUrl` and that the PDP is running                       |
| 403 despite PERMIT decision           | Unhandled obligation                  | Check handler `IsResponsible()` matches the obligation `type`     |
| Handler not firing                    | Missing registration                  | Call `AddSaplConstraintHandler<T>()` in `Program.cs`              |
| Subject is `"anonymous"`              | No authenticated user                 | Configure ASP.NET Core authentication middleware and JWT validation |
| Content filter throws                 | Unsupported path syntax               | Only simple dot paths supported (`$.field.nested`)                 |
| Service method throws `AccessDeniedException` | Normal denial behavior       | Register `UseSaplAccessDenied()` middleware for automatic 403      |
| Streaming SSE empty                   | Action does not return `IAsyncEnumerable` | Ensure streaming methods return `IAsyncEnumerable<T>`          |
| HTTP 500 on service denial            | Missing middleware                    | Add `app.UseSaplAccessDenied()` before `app.MapControllers()`      |

### License

Apache-2.0
