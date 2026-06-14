---
layout: default
title: NestJS
parent: SDKs and APIs
nav_order: 604
---

## NestJS SDK

Attribute-Based Access Control (ABAC) for NestJS using SAPL (Streaming Attribute Policy Language). Provides decorator-driven policy enforcement with a constraint handler architecture for obligations, advice, and response transformation.

Version 2.0 re-architected enforcement from the legacy constraint-bundle model to the SAPL 4.1 planner and `@StreamEnforce` model, added the RSocket transport, support for the new `SUSPEND` decision verb, and data-layer query rewriting. Projects upgrading from 1.x should consult the `@sapl/nestjs` CHANGELOG for the migration table.

## What is SAPL?

SAPL is a policy language and Policy Decision Point (PDP) for attribute-based access control. Policies are written in a dedicated language and evaluated by the PDP, which streams authorization decisions based on subject, action, resource, and environment attributes.

## How @sapl/nestjs Works

Three core concepts:

1. **Authorization subscription**: your app sends `{ subject, action, resource, environment }` to the PDP.
2. **PDP decision**: the PDP evaluates policies and returns a decision verb (`PERMIT`, `DENY`, `SUSPEND`, `INDETERMINATE`, or `NOT_APPLICABLE`), optionally with obligations, advice, or a replacement resource.
3. **Constraint handlers**: registered handlers execute the policy's instructions (log, filter, transform, cap values, etc.).

A PDP decision looks like this:

```json
{
  "decision": "PERMIT",
  "obligations": [{ "type": "logAccess", "message": "Patient record accessed" }],
  "advice": [{ "type": "notifyAdmin" }]
}
```

`decision` is always present (`PERMIT`, `DENY`, `SUSPEND`, `INDETERMINATE`, or `NOT_APPLICABLE`). The other fields are optional. `obligations` and `advice` are arrays of arbitrary JSON objects (by convention with a `type` field for handler dispatch), and `resource` (when present) replaces the controller's return value entirely.

For a deeper introduction to SAPL's subscription model and policy language, see the [SAPL documentation](https://sapl.io/docs/latest/).

## Installation

[![npm](https://img.shields.io/npm/v/@sapl/nestjs)](https://www.npmjs.com/package/@sapl/nestjs)

Install the library and its required peer dependencies:

```bash
npm install @sapl/nestjs @toss/nestjs-aop nestjs-cls
```

If you use transactions and want obligation failures to trigger rollbacks, also install the transactional integration:

```bash
npm install @nestjs-cls/transactional
```

The library requires Node.js 22 or later, NestJS 11, and RxJS 7.

A complete working demo with JWT authentication, constraint handlers, and streaming enforcement is available at [sapl-nestjs-demo](https://github.com/heutelbeck/sapl-nestjs-demo).

## Setup

### Direct Configuration (API Key)

```typescript
import { Module } from '@nestjs/common';
import { SaplModule } from '@sapl/nestjs';

@Module({
  imports: [
    SaplModule.forRoot({
      baseUrl: 'https://localhost:8443',
      token: 'sapl_your_api_key_here',
      timeout: 5000, // PDP request timeout in ms (default: 5000)
    }),
  ],
})
export class AppModule {}
```

### Direct Configuration (Basic Auth)

```typescript
@Module({
  imports: [
    SaplModule.forRoot({
      baseUrl: 'https://localhost:8443',
      username: 'myPdpClient',
      secret: 'myPassword',
    }),
  ],
})
export class AppModule {}
```

`token` (API key or JWT) and `username`/`secret` (Basic Auth) are mutually exclusive. Configure one or the other. Providing both throws an error at startup.

### Direct Configuration (OAuth2 client_credentials)

For a service account registered at an OIDC issuer, set `oauth2`. The client obtains a bearer token via the `client_credentials` grant and refreshes it automatically before expiry. It works on both transports and is mutually exclusive with `token` and `username`/`secret`.

```typescript
@Module({
  imports: [
    SaplModule.forRoot({
      baseUrl: 'https://localhost:8443',
      oauth2: {
        issuerUrl: 'https://issuer.example.org/realms/sapl',
        clientId: 'sapl-client',
        clientSecret: 'your-client-secret',
        scope: 'sapl', // optional
      },
    }),
  ],
})
export class AppModule {}
```

### Async Configuration

```typescript
import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { SaplModule } from '@sapl/nestjs';

@Module({
  imports: [
    ConfigModule.forRoot(),
    SaplModule.forRootAsync({
      imports: [ConfigModule],
      useFactory: (config: ConfigService) => ({
        baseUrl: config.get('SAPL_PDP_URL', 'https://localhost:8443'),
        token: config.get('SAPL_PDP_TOKEN'),
      }),
      inject: [ConfigService],
    }),
  ],
})
export class AppModule {}
```

`SaplModule` registers everything automatically:
- `PdpService` for PDP communication
- `EnforcementPlanner` for constraint handler discovery and enforcement plan construction
- `ProviderRegistry` for constraint handler provider discovery
- `PreEnforceAspect`, `PostEnforceAspect`, and `StreamEnforceAspect` via `@toss/nestjs-aop`
- `ClsModule` from `nestjs-cls` for request context propagation
- Built-in `ContentFilteringProvider` and `ContentFilterPredicateProvider`

The decorators work on any injectable class method (controllers, services, repositories, etc.). Methods without enforcement decorators are unaffected.

### Transport

The PDP client speaks HTTP by default. To opt into the high-throughput binary protocol against a SAPL Node listening on its RSocket port, set `transport: 'rsocket'`.

```typescript
SaplModule.forRoot({
  baseUrl: 'https://localhost:8443',
  transport: 'rsocket',
  rsocketPort: 7000,
  token: 'sapl_your_api_key_here',
}),
```

The RSocket transport is covered in more detail under [RSocket Transport](#rsocket-transport) below.

## Security

### Transport Security

`@sapl/nestjs` encrypts PDP communication by default. Authorization decisions and potentially sensitive information are transmitted over this connection. Using unencrypted transport would expose this data to network-level attackers.

The library enforces a loopback-only plaintext rule. A plain `http://` base URL (HTTP transport) or a missing `tls` block (RSocket transport) is accepted only when the target host is a loopback address (`localhost`, `127.0.0.1`, `::1`). Any plaintext connection to a non-loopback host is refused at client construction with an error. On loopback the HTTP client logs a warning to flag that production must use TLS.

For custom CA certificates, self-signed certificates, or mutual TLS, pass a `tls` block. The library never reads files. Load PEM contents yourself, for example with `fs.readFileSync`, and pass the contents.

```typescript
import { readFileSync } from 'node:fs';

SaplModule.forRoot({
  baseUrl: 'https://pdp.example.org:8443',
  token: 'sapl_your_api_key_here',
  tls: {
    ca: readFileSync('ca.pem'),
    // cert and key for mutual TLS, both optional
    // cert: readFileSync('client-cert.pem'),
    // key: readFileSync('client-key.pem'),
    // rejectUnauthorized defaults to true. Leave true in production.
  },
}),
```

The same `tls` block applies to both transports. On the RSocket transport `servername` selects the SNI host name and defaults to the connection host. On the HTTP transport SNI is derived from the URL and `servername` is ignored. `rejectUnauthorized` defaults to `true` and should be set to `false` only in tests against self-signed certificates without a provided CA.

### Response Validation

PDP responses are validated before use. Malformed responses (non-object, missing or invalid `decision` field) are treated as `INDETERMINATE` (deny). Unknown fields in the response are silently dropped to stay robust against future PDP extensions.

### Streaming Limits

The streaming SSE parser enforces a 64 KB buffer limit per connection. If the PDP sends data without newline delimiters exceeding this limit, the connection is aborted and an `INDETERMINATE` decision is emitted. This protects against memory exhaustion from misbehaving upstream connections.

## Decorators

### @PreEnforce

Authorizes **before** the method executes. The method only runs on PERMIT. Works on any injectable class method.

```typescript
import { Controller, Get } from '@nestjs/common';
import { PreEnforce } from '@sapl/nestjs';

@Controller('api')
export class PatientController {
  @PreEnforce({ action: 'read', resource: 'patient' })
  @Get('patient')
  getPatient() {
    return { name: 'Jane Doe', ssn: '123-45-6789' };
  }
}
```

Use `@PreEnforce` for methods with side effects (database writes, emails) that should not execute when access is denied.

### @PostEnforce

Authorizes **after** the method executes. The method always runs. Its return value is available via `ctx.returnValue` in subscription field callbacks.

```typescript
import { Controller, Get, Param } from '@nestjs/common';
import { PostEnforce } from '@sapl/nestjs';

@Controller('api')
export class RecordController {
  @PostEnforce({
    action: 'read',
    resource: (ctx) => ({ type: 'record', data: ctx.returnValue }),
  })
  @Get('record/:id')
  getRecord(@Param('id') id: string) {
    return { id, value: 'sensitive-data' };
  }
}
```

Use `@PostEnforce` when the policy needs to see the actual return value to make its authorization decision (e.g., deny based on the data's classification).

### Subscription Fields

Both decorators accept `SubscriptionOptions` to customize the authorization subscription:

```typescript
type SubscriptionField<T = any> = T | ((ctx: SubscriptionContext) => T);
```

The `SubscriptionContext` provides:

| Field         | Type                                   | Description                                            |
| ------------- | -------------------------------------- | ------------------------------------------------------ |
| `request`     | `any`                                  | Full Express request (`req.user`, `req.headers`, etc.) |
| `params`      | `Record<string, string>`               | Route parameters (`@Get(':id')` -> `ctx.params.id`)    |
| `query`       | `Record<string, string \| string[]>`   | Query string parameters                                |
| `body`        | `any`                                  | Request body (POST/PUT)                                |
| `handler`     | `string`                               | Handler method name                                    |
| `controller`  | `string`                               | Controller class name                                  |
| `returnValue` | `any`                                  | Handler return value (`@PostEnforce` only)              |
| `args`        | `any[] \| undefined`                   | Method arguments (optional)                            |

#### Default Values

| Field         | Default                                                                           |
| ------------- | --------------------------------------------------------------------------------- |
| `subject`     | `req.user ?? 'anonymous'` (decoded JWT claims, or `'anonymous'` if no auth guard) |
| `action`      | `{ method, controller, handler }`                                                 |
| `resource`    | `{ path, params }`                                                                |
| `environment` | `{ ip, hostname }`                                                                |
| `secrets`     | Not sent unless explicitly specified                                              |

The `secrets` field carries sensitive data (tokens, API keys) that the PDP needs for policy evaluation but that must not appear in logs. It is excluded from debug logging automatically. Use it when a policy needs to inspect credentials, for example passing a raw JWT so the PDP can read its claims:

```typescript
@PreEnforce({
  action: 'exportData',
  resource: (ctx) => ({ pilotId: ctx.params.pilotId }),
  secrets: (ctx) => ({ jwt: ctx.request.headers.authorization?.split(' ')[1] }),
})
```

### Shaping the Deny Response

On denial the PEP throws a NestJS `ForbiddenException` (the streaming PEP throws `AccessDeniedError`, a subclass of `ForbiddenException`). There is no per-decorator deny callback. To shape the deny response, catch the exception with a standard NestJS exception filter.

```typescript
import { ArgumentsHost, Catch, ExceptionFilter, ForbiddenException } from '@nestjs/common';

@Catch(ForbiddenException)
export class AccessDeniedFilter implements ExceptionFilter {
  catch(exception: ForbiddenException, host: ArgumentsHost) {
    const response = host.switchToHttp().getResponse();
    response.status(403).json({ error: 'access_denied' });
  }
}
```

Exception filters integrate correctly with `@Transactional`. A per-decorator deny-return would silently commit the transaction when a post-method obligation fails, which is why deny shaping lives in the exception filter rather than the decorator.

## How Enforcement Works

The decorators above are convenient, but to use them well it helps to understand what actually happens behind the scenes. This section walks through the enforcement lifecycle so you can reason about behavior.

### The Deny Invariant

Only `PERMIT` grants access. The PDP can return five possible decisions (`PERMIT`, `DENY`, `SUSPEND`, `INDETERMINATE`, `NOT_APPLICABLE`), and only `PERMIT` ever results in your method running or your stream forwarding data. Everything else means denial. Streaming PEPs that honour `SUSPEND` pause the stream while keeping the subscription alive. One-shot PEPs treat `SUSPEND` as `DENY`. See [Authorization Decisions](../2_3_AuthorizationDecisions/) for details.

A `PERMIT` with obligations is not a free pass. The PEP checks that every obligation in the decision has a registered handler. If even one obligation cannot be fulfilled, the PEP treats the decision as a denial. If a handler accepts responsibility but fails during execution, that also results in denial. Advice is softer: if an advice handler fails, the PEP logs the failure and moves on. Advice never causes denial.

| Aspect          | Obligation                                                       | Advice                                          |
|-----------------|------------------------------------------------------------------|--------------------------------------------------|
| All handled?    | Required. Unhandled obligations deny access (ForbiddenException) | Optional. Unhandled advice is silently ignored.  |
| Handler failure | Denies access (ForbiddenException)                               | Logs a warning and continues.                    |

This means you can always trust that if your method runs, every obligation attached to the decision has been successfully enforced.

### Enforcement Locations

Depending on the decorator, constraint handlers can intervene at different points in the lifecycle of a request or stream.

For request-response methods (`@PreEnforce` and `@PostEnforce`), constraints can run at four points:

| Location              | When it happens                      | What constraints do here                        |
|-----------------------|--------------------------------------|-------------------------------------------------|
| On decision           | Authorization decision arrives       | Side effects like logging, audit, or notification|
| Pre-method invocation | Before the protected method executes | Modify method arguments (`@PreEnforce` only)    |
| On return value       | After the method returns             | Transform, filter, or replace the result        |
| On error              | If the method throws                 | Transform or observe the error                  |

For the streaming method decorator (`@StreamEnforce`), constraints attach to a wider set of lifecycle signals:

| Signal        | When it fires                                | What constraints do here                |
|---------------|----------------------------------------------|-----------------------------------------|
| `decision`    | Each new decision from the PDP stream        | Side effects like logging, audit        |
| `output`      | Each element emitted by the source stream    | Transform, filter, or observe items     |
| `error`       | Source stream produces an error               | Transform or observe the error          |
| `subscribe`   | The source stream is subscribed              | Setup side effects                      |
| `complete`    | Source stream completes normally              | Cleanup and finalization                |
| `cancel`      | Subscriber cancels                           | Release resources                       |
| `termination` | The pipeline finalizes for any reason        | Final cleanup                           |

A handler decides which signals it attaches to. A side-effect-only handler attaches to a void signal such as `decision`, `complete`, or `cancel`. A handler that observes or transforms a value attaches to a data-carrying signal (`input`, `output`, or `error`). The `input` signal exists only on `@PreEnforce`, where a handler can rewrite the method arguments before the method runs. After the method has executed there is nothing to rewrite, so `@PostEnforce` does not advertise it.

### PreEnforce Lifecycle

When you decorate a method with `@PreEnforce`, here is what happens step by step.

First, the PEP builds an authorization subscription from the decorator options (or from defaults if you left them out) and sends it to the PDP as a one-shot request. The PDP evaluates the subscription against all matching policies and returns a single decision.

If the decision is anything other than `PERMIT`, the PEP throws a `ForbiddenException` immediately. Your method never runs.

If the decision is `PERMIT`, the PEP resolves all constraint handlers. It walks through the obligations and advice attached to the decision and checks which registered handlers claim responsibility for each one. If any obligation has no matching handler, the PEP denies access right there, because it cannot guarantee the obligation will be enforced.

With all handlers resolved, execution proceeds through the enforcement locations in order. On-decision handlers run first (logging, audit). Then method-invocation handlers run, which can modify method arguments if the policy requires it. Then your actual method executes. After the method returns, the PEP applies return-value handlers: resource replacement if the decision included one, filter predicates, mapping handlers, and consumer handlers. If any obligation handler fails at any stage, the PEP denies access.

If you have transaction integration enabled (`transactional: true`), a constraint handler failure after the method returns will trigger a rollback, so the database write does not persist.

### PostEnforce Lifecycle

`@PostEnforce` inverts the order. Your method runs first, regardless of the authorization outcome. Only after it returns does the PEP build the authorization subscription (now including `ctx.returnValue`) and consult the PDP.

This means the PDP can make decisions based on the actual data your method produced. For example, a policy might permit access to a record only if its classification level is below a threshold, something that can only be checked after loading the record.

If the decision is not `PERMIT`, the PEP discards the return value and throws `ForbiddenException`. If you have transaction integration enabled, this triggers a rollback.

If the decision is `PERMIT`, constraint handlers proceed through the same stages as `@PreEnforce`, minus the method-invocation handlers (since the method has already run). Return-value handlers can still transform the result before it reaches the caller.

Because the method runs before the PDP is consulted, if the method itself throws an exception, that exception propagates directly. The PDP is never called, because there is no return value to include in the subscription.

SAPL PEP libraries share a single unified enforcement model. It is a strict fail-closed state machine over the five decision verbs, where only `PERMIT` grants access and only an explicit `SUSPEND` pauses a stream without terminating it. See [Authorization Decisions](../2_3_AuthorizationDecisions/) for the decision-verb semantics.

## Constraint Handlers

When the PDP returns a decision with `obligations` or `advice`, the `EnforcementPlanner` queries the registered constraint handler providers, builds an enforcement plan, and the active aspect executes that plan against each lifecycle signal.

### Obligation vs. Advice Semantics

The core contract between obligations and advice is covered in [The Deny Invariant](#the-deny-invariant) above. In short, unhandled or failing obligations deny access, advice failures are logged and ignored.

### The Provider Interface

There is a single constraint handler provider interface. A provider inspects one constraint and returns the scoped handlers that enforce it, or an empty array when it does not recognise the constraint.

```typescript
interface ConstraintHandlerProvider {
  getHandlers(constraint: unknown): ReadonlyArray<ScopedHandler>;
}
```

A `ScopedHandler` bundles four things. The `signal` it attaches to, a `priority` (lower runs earlier among handlers on the same signal), a `shape`, and the handler function itself.

```typescript
interface ScopedHandler {
  readonly signal: SignalKind;
  readonly priority: number;
  readonly shape: 'runner' | 'consumer' | 'mapper';
  readonly handler: (value: unknown) => unknown | void;
}
```

The three shapes determine what the handler does with the value passed to it.

| Shape      | Signature          | Use when                                                                 |
|------------|--------------------|--------------------------------------------------------------------------|
| `runner`   | `() => void`       | A side effect that needs no value. Logging the decision, sending a notification. |
| `consumer` | `(value) => void`  | A side effect that observes the value but does not change it. Structured audit logging of the response. |
| `mapper`   | `(value) => value` | A transformation of the value flowing through a data-carrying signal. Redacting fields in `output`, rewriting an error. |

A single provider can return several handlers across different signals for one constraint. For example one constraint can drive both a `decision` runner that records the outcome and an `output` consumer that audits the response.

Two admissibility rules apply. A `mapper` may only be returned for an obligation, never for advice. Advice is allowed to fail silently, and a value transformation that silently does not happen would leave the caller unable to tell whether the result was transformed. If a provider returns a mapper for an advice constraint, the planner replaces the whole claim with a synthetic failure runner. The second rule is that `consumer` and `mapper` handlers attach only to the data-carrying signals (`input`, `output`, `error`), while `runner` handlers attach to any signal. A handler scoped to a signal the active PEP does not advertise is inadmissible, and an inadmissible handler for an obligation denies access.

### Lifecycle Signals

A signal is the discriminated union of lifecycle events at which handlers may attach. There are eight kinds, four value-carrying and four void.

| Kind          | Carries           | Fires                                                       |
|---------------|-------------------|------------------------------------------------------------|
| `decision`    | the decision      | When a decision arrives (runners only).                    |
| `input`       | the method args   | Before the method runs, `@PreEnforce` only. Args are mutable. |
| `output`      | the return value  | After the method returns, or per item on a stream.         |
| `error`       | the thrown error  | When the method or stream produces an error.               |
| `subscribe`   | nothing           | When a streaming source is subscribed.                     |
| `cancel`      | nothing           | When the subscriber cancels.                               |
| `complete`    | nothing           | When the source completes normally.                        |
| `termination` | nothing           | When the streaming pipeline finalizes for any reason.      |

Which signals a PEP advertises depends on the decorator. `@PreEnforce` advertises `decision`, `input`, `output`, and `error`. `@PostEnforce` advertises `decision`, `output`, and `error` (no `input`, the method has already run). `@StreamEnforce` advertises `decision`, `output`, `error`, `subscribe`, `cancel`, `complete`, and `termination`.

### Registering Custom Handlers

A constraint handler is an injectable class annotated with `@SaplConstraintHandler('provider')`. The literal `'provider'` is the only accepted argument. It tags the class for discovery.

```typescript
import { Injectable } from '@nestjs/common';
import { SaplConstraintHandler } from '@sapl/nestjs';
import type { ConstraintHandlerProvider, ScopedHandler } from '@sapl/nestjs';

@Injectable()
@SaplConstraintHandler('provider')
export class AuditLogHandler implements ConstraintHandlerProvider {
  getHandlers(constraint: unknown): ReadonlyArray<ScopedHandler> {
    if ((constraint as { type?: unknown })?.type !== 'logAccess') {
      return [];
    }
    const message = (constraint as { message?: string }).message ?? 'Access logged';
    return [
      {
        signal: 'decision',
        priority: 0,
        shape: 'runner',
        handler: () => console.log(`Audit: ${message}`),
      },
    ];
  }
}
```

Register the handler in any module's `providers` array. The `ProviderRegistry` discovers all `@SaplConstraintHandler('provider')`-decorated classes automatically.

A handler that rewrites the method arguments returns a `mapper` on the `input` signal. The `input` value is the argument array, and the mapper returns the replacement array. This signal is only available under `@PreEnforce`.

```typescript
@Injectable()
@SaplConstraintHandler('provider')
export class CapTransferHandler implements ConstraintHandlerProvider {
  getHandlers(constraint: unknown): ReadonlyArray<ScopedHandler> {
    if ((constraint as { type?: unknown })?.type !== 'capTransferAmount') {
      return [];
    }
    const max = (constraint as { maxAmount: number }).maxAmount;
    const argIndex = (constraint as { argIndex?: number }).argIndex ?? 0;
    return [
      {
        signal: 'input',
        priority: 0,
        shape: 'mapper',
        handler: (value) => {
          const args = [...(value as unknown[])];
          if (Number(args[argIndex]) > max) {
            args[argIndex] = max;
          }
          return args;
        },
      },
    ];
  }
}
```

## Built-in Constraint Handlers

### ContentFilteringProvider

**Constraint type:** `filterJsonContent`

Transforms response values by deleting, replacing, or blackening fields.

```json
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

| Option          | Type   | Default                       | Description                                |
| --------------- | ------ | ----------------------------- | ------------------------------------------ |
| `path`          | string | (required)                    | Dot-notation path to a string field        |
| `replacement`   | string | `"\u2588"` (block character)  | Character used for masking                 |
| `discloseLeft`  | number | `0`                           | Characters to leave unmasked from the left |
| `discloseRight` | number | `0`                           | Characters to leave unmasked from the right |
| `length`        | number | (masked section length)       | Override the length of the masked section  |

### ContentFilterPredicateProvider

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

### ContentFilter Limitations

The built-in content filter supports **simple dot-notation paths only** (`$.field.nested`). Recursive descent (`$..ssn`), bracket notation (`$['field']`), array indexing (`$.items[0]`), wildcards (`$.users[*].email`), and filter expressions (`$.books[?(@.price<10)]`) are not supported and will throw an error.

## Query Rewriting

Constraint handlers also cover data-layer enforcement: a policy can attach a query-rewriting obligation that narrows the rows an enforced method reads at the database, rather than filtering them in memory. The query an enforced method issues is rewritten transparently, fail-closed and narrowing-only.

Two integrations ship as optional subpath exports, so you install only the driver you use:

- **`@sapl/nestjs/mongoose`** for MongoDB on Mongoose. Register the shim and apply `createSaplMongoosePlugin(cls)` to your schemas, then add `MongoDbQueryRewritingProvider` to your module. It honours the `mongo:queryRewriting` obligation.
- **`@sapl/nestjs/prisma`** for SQL on Prisma. Register the shim and extend your client with `createSaplPrismaExtension(cls)`, then add `SqlQueryRewritingProvider`. It honours the `sql:queryRewriting` obligation (typed `criteria` and `columns`, because Prisma's structured `where` cannot lower the raw-SQL `conditions` escape hatch).

The obligation format is identical across every SAPL PEP for a backend, so the same `mongo:queryRewriting` policy works unchanged on the Spring, Python, and NestJS MongoDB integrations. See [Query Rewriting](6_12_QueryRewriting.md) for the obligation schema, semantics, and setup.

## Streaming Enforcement with @StreamEnforce

`@PreEnforce` and `@PostEnforce` make a single authorization decision and either let the method run or deny it. They suit request-response endpoints. For SSE endpoints that return an `Observable<T>`, the decision is rarely a single point in time. The same subscription stays open while the policy evaluates against attribute streams that may change. The single `@StreamEnforce` decorator covers this case.

```typescript
import { Injectable } from '@nestjs/common';
import { Observable, interval, map } from 'rxjs';
import { StreamEnforce } from '@sapl/nestjs';

@Injectable()
export class HeartbeatService {
  @StreamEnforce({ action: 'stream:heartbeat', resource: 'heartbeat' })
  heartbeat(): Observable<any> {
    return interval(2000).pipe(map((i) => ({ seq: i })));
  }
}
```

`@StreamEnforce` consumes a continuous stream of authorization decisions from the PDP. As decisions change, the aspect lets items flow, drops them silently, or terminates the subscription accordingly. The protected method must return an `Observable`. For `Observable`-of-one or request-response semantics, use `@PreEnforce`/`@PostEnforce`.

### How Decisions Affect the Subscription

Every decision the PDP emits during the lifetime of the subscription has one of five verbs, and each maps to a single observable effect.

| PDP decision | Effect on the subscription |
|---|---|
| `PERMIT` | Items from the protected method flow through to the subscriber. |
| `SUSPEND` | Items are silently dropped. The subscription stays open. A later `PERMIT` resumes the flow. |
| `INDETERMINATE` | The subscription terminates with an `AccessDeniedError`. |
| `NOT_APPLICABLE` | The subscription terminates with an `AccessDeniedError`. |
| `DENY` | The subscription terminates with an `AccessDeniedError`. |

Under the strict fail-closed discipline, `INDETERMINATE`, `NOT_APPLICABLE`, and a `PERMIT` whose decision-scoped enforcement fails all terminate the subscription with an `AccessDeniedError`. Only an explicit `SUSPEND` from the PDP silences (rather than terminates) the subscription. Operators who want `NOT_APPLICABLE` to silence rather than terminate set the combining algorithm's `defaultDecision` to `SUSPEND` at the PDP level, producing a real `SUSPEND` decision the streaming PEP then routes through suspension.

A subscription that has been silenced by a `SUSPEND` resumes the moment the PDP emits a `PERMIT` again. This is the use case the `suspend` verb in policies was designed for. See [Authorization Decisions](../2_3_AuthorizationDecisions/) for the policy-side semantics.

Per-item obligation failure also terminates the subscription, with an `AccessDeniedError` carrying a message indicating the per-item discharge failure. Per-item failure is unconditionally terminal, matching strict `@PreEnforce` semantics on a per-item timeline.

`AccessDeniedError` is a subclass of NestJS `ForbiddenException`, so a terminal denial routes through the HTTP layer as a 403 natively and is caught by the same exception filters that catch a `@PreEnforce` denial.

### The Streaming State Machine

The streaming pipeline is a four-state machine over the decision verbs. It starts in `Pending` before any decision arrives. A `PERMIT` (with successful decision-scoped enforcement) moves it to `Permitting`, where items flow. A `SUSPEND` moves it to `Suspended`, where items are dropped and the subscription stays open. Any terminal verb (`DENY`, `INDETERMINATE`, `NOT_APPLICABLE`, a `PERMIT` whose enforcement fails, a per-item failure, a source error, or subscriber cancel) moves it to the absorbing `Terminated` state. From `Suspended` a later `PERMIT` returns to `Permitting`. The machine is the local realization of the unified enforcement model described under [Authorization Decisions](../2_3_AuthorizationDecisions/).

### Two Flags

`@StreamEnforce` carries two boolean flags, both defaulting to `false`. Each addresses one orthogonal concern.

```typescript
@StreamEnforce({
  signalTransitions: false,     // default false
  pauseRapDuringSuspend: false, // default false
})
```

**`signalTransitions`**. Surfaces every suspend/resume boundary to the subscriber as a non-terminal value on the `next` channel. When `false` (the default), boundary transitions are silent. The subscriber sees items while permitted and silence while suspended, with no programmatic notification of the transition itself. When `true`, the subscriber receives an `AccessSuspendedSignal` value every time the subscription is silenced and an `AccessGrantedSignal` value (carrying the granting decision) every time it resumes. These arrive on the `next` channel, not the error channel. Subscribers detect them with `instanceof` or with the `TransitionSignals` helper operators below. Terminal denials bypass the gate entirely and surface on the `error` channel as `AccessDeniedError` regardless of this flag.

**`pauseRapDuringSuspend`**. Controls the underlying source Observable while the subscription is silenced. With the default `false`, the protected method's Observable stays subscribed throughout the silenced period. Items keep arriving from upstream and are silently dropped on the way to the subscriber. Lower latency on resume, and upstream state is preserved. With `true`, the upstream subscription is disposed when the subscription enters `Suspended` and re-established when it resumes into `Permitting`. This stops upstream side effects during suspension at the cost of paying re-subscription latency on resume. Opt in for upstream sources with expensive side effects that must not run while the subscriber is denied access.

### The Source Observable and Authorization Ordering

The protected method's Observable is subscribed only after the first `PERMIT` decision arrives from the PDP. For hot observables (WebSocket streams, event emitters), events emitted before the initial `PERMIT` are not buffered and will not be delivered. This is intentional. Data should not be buffered before authorization is confirmed.

RxJS is push-only, so the streaming pipeline carries no demand-forwarding logic and no hidden buffer. A slow subscriber backs up in its own buffers, not in the PEP.

### Subscriber-Side Transition Handling

When `signalTransitions = true`, the `TransitionSignals` helper operators translate the in-band `AccessSuspendedSignal` / `AccessGrantedSignal` values into ordinary callbacks and re-emit a clean stream of source values to the downstream consumer.

```typescript
import { TransitionSignals } from '@sapl/nestjs';

const clean = TransitionSignals.onTransitions(
  heartbeatService.heartbeat(),
  (suspended) => log.info('Stream suspended'),
  (granted) => log.info('Stream resumed', granted.decision),
);
```

`TransitionSignals` exposes three operators. `onSuspend` observes the suspend boundary, `onGranted` observes the resume boundary, and `onTransitions` composes both. Each operator either drops the boundary value from the stream or, with an extra substitute callback, replaces it with a value of the source type.

### Three Common Patterns

The flag combinations encode the three behavioural patterns most streaming endpoints want.

**Terminate on deny.** The subscription should end the moment access is revoked, and the subscriber should know. The defaults are sufficient. A `DENY` from the PDP terminates the subscription with `AccessDeniedError`. A `SUSPEND` keeps the subscription alive but silently drops items.

```typescript
@StreamEnforce({ action: 'stream:trades' })
liveTrades(): Observable<Trade> { ... }
```

**Drop while suspended, silent transitions.** The subscription should survive deny windows transparently, with no boundary events. The defaults are again sufficient. The difference is in the policy, which uses the `suspend` verb instead of `deny` for the deny windows. The PDP returns `SUSPEND`, items are silently dropped, the subscription stays open, and a later `PERMIT` resumes the flow.

```typescript
@StreamEnforce({ action: 'stream:telemetry' })
telemetry(): Observable<TelemetryEvent> { ... }
```

**Survive deny with explicit transition signals.** The subscription should survive, and the subscriber wants to know about every boundary. The policy returns `SUSPEND` for windows where access should pause, and the subscriber observes the boundary signals.

```typescript
@StreamEnforce({ action: 'stream:market', signalTransitions: true })
marketData(): Observable<MarketData> { ... }
```

### Subscription, Action, and Resource

`@StreamEnforce` carries the same `SubscriptionOptions` slots as `@PreEnforce` for shaping the authorization subscription. When omitted, defaults are derived from the request and method invocation as for the request-response annotations. See [Subscription Fields](#subscription-fields) above.

### Streaming Constraint Handlers

The same `ConstraintHandlerProvider` mechanism that powers `@PreEnforce` and `@PostEnforce` applies. Decision-scoped handlers attach to the `decision` signal and run once per decision arrival. Per-item handlers attach to the `output` signal and run on every emitted item. The full set of signals `@StreamEnforce` advertises is listed under [Lifecycle Signals](#lifecycle-signals) above.

## Manual PDP Access

```typescript
import { Controller, ForbiddenException, Get, Request } from '@nestjs/common';
import { PdpService } from '@sapl/nestjs';

@Controller('api')
export class AppController {
  constructor(private readonly pdpService: PdpService) {}

  @Get('hello')
  async getHello(@Request() req) {
    const decision = await this.pdpService.decideOnce({
      subject: req.user,
      action: 'read',
      resource: 'hello',
    });

    if (decision.decision === 'PERMIT' && !decision.obligations?.length) {
      return { message: 'Hello World' };
    }
    throw new ForbiddenException('Access denied');
  }
}
```

### Multi-Subscription API

When you need authorization decisions for multiple resources in a single request, use the multi-subscription methods instead of calling `decideOnce` in a loop.

#### One-Shot (multiDecideAllOnce)

Returns a snapshot mapping each subscription ID to its decision:

```typescript
const result = await this.pdpService.multiDecideAllOnce({
  subscriptions: {
    readPatient: { subject: req.user, action: 'read', resource: 'patient' },
    readLab: { subject: req.user, action: 'read', resource: 'labResults' },
    readNotes: { subject: req.user, action: 'read', resource: 'clinicalNotes' },
  },
});

// result.decisions['readPatient'].decision === 'PERMIT'
// result.decisions['readLab'].decision === 'DENY'
// result.decisions['readNotes'].decision === 'PERMIT'
```

#### Streaming Individual Decisions (multiDecide)

Emits an `IdentifiableAuthorizationDecision` each time an individual subscription's decision changes:

```typescript
this.pdpService.multiDecide({
  subscriptions: {
    readPatient: { subject: req.user, action: 'read', resource: 'patient' },
    readLab: { subject: req.user, action: 'read', resource: 'labResults' },
  },
}).subscribe((event) => {
  // event.subscriptionId === 'readPatient'
  // event.decision.decision === 'PERMIT'
});
```

#### Streaming Complete Snapshots (multiDecideAll)

Emits a `MultiAuthorizationDecision` containing all current decisions whenever any individual decision changes:

```typescript
this.pdpService.multiDecideAll({
  subscriptions: {
    readPatient: { subject: req.user, action: 'read', resource: 'patient' },
    readLab: { subject: req.user, action: 'read', resource: 'labResults' },
  },
}).subscribe((snapshot) => {
  // snapshot.decisions['readPatient'].decision === 'PERMIT'
  // snapshot.decisions['readLab'].decision === 'DENY'
});
```

Both streaming methods reconnect with exponential backoff on connection loss and suppress consecutive duplicate events.

## Advanced Configuration

### Using nestjs-cls (Continuation-Local Storage) in Your Application

CLS (Continuation-Local Storage) provides per-request context that follows the async call chain, similar to thread-local storage in Java. `@sapl/nestjs` uses it internally to pass the HTTP request object from the middleware layer into the AOP aspects without requiring explicit parameter passing.

`SaplModule` manages `ClsModule` from `nestjs-cls` automatically. CLS middleware is mounted globally and the HTTP request is stored at the `CLS_REQ` key.

**If you already use `nestjs-cls`:** Remove your own `ClsModule.forRoot()` call. Since `ClsService` is globally available, inject it anywhere to set/get custom CLS values as before. Your interceptors and guards that use `ClsService` continue to work unchanged.

**If you need custom CLS options** (custom `idGenerator`, `setup` callback, guard/interceptor mode instead of middleware): Pass them via the `cls` option in `SaplModule.forRoot()`:

```typescript
SaplModule.forRoot({
  baseUrl: 'https://localhost:8443',
  cls: {
    middleware: {
      mount: true,
      setup: (cls, req) => {
        cls.set('TENANT_ID', req.headers['x-tenant-id']);
      },
    },
  },
})
```

The `cls` options are merged into the default configuration (`{ global: true, middleware: { mount: true } }`), so you only need to specify the parts you want to customize.

The `cls` option is honoured only by `SaplModule.forRoot()`. `SaplModule.forRootAsync()` ignores it, because module imports are resolved before the async factory runs and the factory result is not available at import time. `ClsModule` always gets the defaults under `forRootAsync`. Applications that need custom CLS setup with async configuration inject `ClsService` in a guard or interceptor instead.

### Transaction Integration

#### The Problem

When `@PreEnforce` and a database transaction coexist on a method, the transaction typically commits inside the method body. SAPL's post-method constraint handlers run after the method returns. If a constraint handler fails at that point, the transaction has already committed and cannot be rolled back.

The same problem applies to `@PostEnforce`. The method executes (and commits its transaction) before the PDP even makes its authorization decision. A subsequent DENY cannot undo committed database writes.

#### The Solution

Set `transactional: true` in `SaplModule.forRoot()`. When enabled, `@PreEnforce` and `@PostEnforce` wrap method execution and constraint handling in a single database transaction via `@nestjs-cls/transactional`. Any constraint failure, method error, or DENY decision triggers a rollback.

```bash
npm install @nestjs-cls/transactional @nestjs-cls/transactional-adapter-typeorm
```

```typescript
import { Module } from '@nestjs/common';
import { SaplModule } from '@sapl/nestjs';
import { ClsPluginTransactional } from '@nestjs-cls/transactional';
import { TransactionalAdapterTypeOrm } from '@nestjs-cls/transactional-adapter-typeorm';

@Module({
  imports: [
    TypeOrmModule.forRoot({ /* ... */ }),
    SaplModule.forRoot({
      baseUrl: 'https://localhost:8443',
      token: 'sapl_api_key',
      transactional: true,
      cls: {
        plugins: [
          new ClsPluginTransactional({
            imports: [TypeOrmModule],
            adapter: new TransactionalAdapterTypeOrm({ dataSourceName: 'default' }),
          }),
        ],
      },
    }),
  ],
})
export class AppModule {}
```

For Prisma, replace the adapter:

```bash
npm install @nestjs-cls/transactional @nestjs-cls/transactional-adapter-prisma
```

```typescript
cls: {
  plugins: [
    new ClsPluginTransactional({
      imports: [PrismaModule],
      adapter: new TransactionalAdapterPrisma({ prismaInjectionToken: PrismaService }),
    }),
  ],
},
```

#### What Gets Wrapped

| Decorator      | Without `transactional`                                                                                                             | With `transactional: true`                                                                                          |
| -------------- | ----------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `@PreEnforce`  | Phase 1 (pre-method handlers) runs outside any transaction. Phase 2 (method) and Phase 3 (post-method handlers) run independently. | Phase 2 + Phase 3 are wrapped in `withTransaction()`. Constraint failure after method execution triggers rollback.  |
| `@PostEnforce` | Method runs first, then PDP check, then constraint handlers. Each step is independent.                                             | The entire sequence (method + PDP check + constraint handling) runs in a single transaction. A DENY after method execution triggers rollback. |

#### Manual Alternative: Decorator Ordering

If you cannot use `@nestjs-cls/transactional`, ensure `@Transactional()` is applied above the enforcement decorator so the transaction boundary wraps the entire enforcement lifecycle:

```typescript
@Transactional()    // outer: starts transaction
@PreEnforce()       // inner: method + constraints run inside the transaction
@Post('transfer')
transfer(@Body() dto: TransferDto) {
  return this.accountService.transfer(dto);
}
```

NestJS decorators execute bottom-up, so `@PreEnforce` runs first (inside the transaction started by `@Transactional`).

#### Limitation: Callback-Based Transactions

Methods that manage their own transaction via callback APIs (`prisma.$transaction(async (tx) => ...)`, `queryRunner.startTransaction()`) are not affected by `transactional: true`. The SAPL transaction wrapper and the method's internal transaction are independent. Use the decorator-based approach or restructure to use `@nestjs-cls/transactional`'s `TransactionHost` instead.

#### Runtime Warning

If `transactional: true` is set but `@nestjs-cls/transactional` is not installed or `ClsPluginTransactional` is not registered, `SaplTransactionAdapter` logs a warning at first request time and falls back to non-transactional execution.

## RSocket Transport

The PDP client speaks one of two transports, selected at module configuration time by `transport`. The default `'http'` is the broadest fit. Its streaming path decodes server-sent events, with the buffer limit described under [Streaming Limits](#streaming-limits). The `'rsocket'` transport uses protobuf framing over a long-lived TCP connection against a SAPL Node listening on its RSocket port, with streaming carried over RSocket request-stream rather than SSE, trading per-request flexibility for substantially higher per-call throughput.

```typescript
SaplModule.forRoot({
  baseUrl: 'https://pdp.example.org:8443',
  transport: 'rsocket',
  rsocketHost: 'pdp.example.org', // defaults to the hostname from baseUrl
  rsocketPort: 7000,              // defaults to 7000
  token: 'sapl_your_api_key_here',
  tls: { ca: caPem },
}),
```

`rsocketHost` defaults to the hostname extracted from `baseUrl`, and `rsocketPort` defaults to `7000`. The same loopback-only plaintext rule applies. Without a `tls` block the RSocket client refuses to connect to a non-loopback host.

### Authentication

The RSocket transport authenticates once at connection setup. The credential is carried in the setup-frame metadata and binds the whole connection to a single identity for its lifetime. The auth modes are wired through `SaplModule.forRoot`.

| Mode | Configuration |
|---|---|
| No auth | omit `token`, `username`, `secret`, and `oauth2` |
| Basic | `username` + `secret` |
| API key (bearer) | `token` |
| OAuth2 client_credentials | `oauth2` |

These are the same `token`, `username`/`secret`, and `oauth2` fields used by the HTTP transport, and they are mutually exclusive on both transports.

The `oauth2` option wraps `openid-client` for the `client_credentials` grant with automatic refresh. On RSocket the bearer token is acquired before connection setup and carried in the setup-frame metadata.

All options for `SaplModule.forRoot()` / `SaplModule.forRootAsync()`:

| Option                    | Type                        | Default                                         | Description                                                                                  |
| ------------------------- | --------------------------- | ----------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `baseUrl`                 | `string`                    | (required)                                      | Base URL of the SAPL PDP server for the HTTP transport. Also the RSocket host fallback.       |
| `transport`              | `'http' \| 'rsocket'`       | `'http'`                                        | Which transport the PDP client uses.                                                          |
| `rsocketHost`            | `string`                    | hostname from `baseUrl`                          | RSocket host. Only used when `transport: 'rsocket'`.                                          |
| `rsocketPort`            | `number`                    | `7000`                                          | RSocket TCP port. Only used when `transport: 'rsocket'`.                                      |
| `token`                   | `string`                    | -                                               | Bearer token (API key or JWT). Mutually exclusive with `username`/`secret`.                  |
| `username`                | `string`                    | -                                               | Basic Auth username. Must be used together with `secret`. Mutually exclusive with `token`.   |
| `secret`                  | `string`                    | -                                               | Basic Auth password. Must be used together with `username`. Mutually exclusive with `token`. |
| `oauth2`                  | `OAuth2TokenProviderOptions`| -                                               | OAuth2 client_credentials config (`issuerUrl`, `clientId`, `clientSecret`, `scope?`). Mutually exclusive with `token` and `username`/`secret`. |
| `timeout`                 | `number`                    | `5000`                                          | Timeout in ms for PDP HTTP requests.                                                          |
| `streamingRetryBaseDelay` | `number`                    | `1000`                                          | Initial delay in ms before the first streaming reconnection.                                  |
| `streamingRetryMaxDelay`  | `number`                    | `30000`                                         | Maximum backoff delay in ms for streaming reconnection.                                       |
| `tls`                     | `TlsConfig`                 | -                                               | TLS configuration for the connection. See [Transport Security](#transport-security).         |
| `cls`                     | `Partial<ClsModuleOptions>` | `{ global: true, middleware: { mount: true } }` | Options merged into `ClsModule.forRoot()`. Ignored by `forRootAsync`.                         |
| `transactional`           | `boolean`                   | `false`                                         | Wrap enforcement in a database transaction via `@nestjs-cls/transactional`.                  |

The `tls` block (`TlsConfig`) carries `ca`, `cert`, `key`, `servername`, and `rejectUnauthorized`. All fields take PEM contents, not file paths. See [Transport Security](#transport-security).

## Client Resilience

The PDP client treats every transport problem as an operational condition, never as a policy outcome, and never lets one surface as an exception. A connection drop, timeout, or decode error fails closed to `INDETERMINATE`, which the PEP enforces as a denial, so a transient PDP outage can never accidentally grant access.

One-shot requests (`decideOnce`) fail closed to `INDETERMINATE` immediately, with no retry, and never reject the returned promise. In steady state the connection is warm, so only a cold or dropped connection fails closed.

Subscriptions (the streaming `decide`) never terminate on a transport problem or on a server-side stream completion. The returned RxJS `Observable` never errors or completes for a transport condition. Either condition emits one `INDETERMINATE` and then reconnects with bounded exponential backoff, indefinitely. Consecutive identical decisions are de-duplicated, so an outage yields a single `INDETERMINATE`, not a flood. A subscription ends only when the consumer unsubscribes or the client shuts down. This contract holds identically across the HTTP and RSocket transports and across every SAPL PEP client.

## Troubleshooting

| Symptom                         | Likely Cause                        | Fix                                                                         |
| ------------------------------- | ----------------------------------- | --------------------------------------------------------------------------- |
| All decisions are INDETERMINATE | PDP unreachable                     | Check `baseUrl` and that the PDP is running.                                 |
| 403 despite PERMIT decision     | Unhandled obligation                | Check that a provider's `getHandlers` matches the obligation `type`.        |
| Handler not firing              | Missing registration                | Add `@SaplConstraintHandler('provider')` and add the class to a module's `providers`. |
| Subject is `'anonymous'`        | No auth guard populating `req.user` | Add `@UseGuards()` or set `subject` explicitly in the decorator options.     |
| Content filter throws           | Unsupported JSONPath                | Only simple dot paths are supported (`$.field.nested`).                      |
| CLS context missing             | Module order                        | Ensure `SaplModule` is imported before modules that use it.                  |
| Plaintext connection refused    | Non-loopback host without TLS       | Use `https://` (HTTP) or a `tls` block (RSocket), or run the PDP on localhost. |
| Streaming buffer overflow       | PDP proxy injecting data            | Check the network path to the PDP. The buffer limit is 64 KB per SSE line.   |

## License

Apache-2.0
