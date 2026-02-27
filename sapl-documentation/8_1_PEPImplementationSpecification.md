---
layout: default
title: PEP Implementation Specification
parent: Extending SAPL
grand_parent: SAPL Reference
nav_order: 801
---

# PEP Implementation Specification

This document specifies what a SAPL Policy Enforcement Point (PEP) must do, why each requirement exists, and how to implement one from scratch. It targets library authors building framework-integrated PEP SDKs and application developers who need to understand (or manually implement) PEP enforcement.

The specification is derived from two production-grade reference implementations (Java/Spring Security and TypeScript/NestJS) and grounded in XACML, NIST, and academic literature on authorization architectures.

## 1. Introduction and Background

### 1.1 The Policy Enforcement Point in Authorization Architecture

The separation of policy enforcement from policy decision has its roots in the IETF policy framework: RFC 2753 [[5]](#ref-5) defined the PEP-PDP split for policy-based admission control, and RFC 2748 [[3]](#ref-3) operationalized it with the COPS protocol, including a stateful PEP-PDP connection and the concept of a Local PDP (LPDP) for fallback decisions. RFC 2904 [[4]](#ref-4) placed the PEP collocated with the protected resource in a broader AAA authorization framework.

XACML formalized the full four-component decomposition [[6]](#ref-6), [[9]](#ref-9):

- **Policy Administration Point (PAP):** Manages and publishes policies.
- **Policy Information Point (PIP):** Supplies attribute values for policy evaluation.
- **Policy Decision Point (PDP):** Evaluates policies and produces authorization decisions.
- **Policy Enforcement Point (PEP):** Intercepts access requests, queries the PDP, and enforces the decision.

This separation of concerns is endorsed by NIST SP 800-162 [[10]](#ref-10), NIST SP 800-207 [[14]](#ref-14), and remains the foundational architecture for externalized authorization.

### 1.2 From Request-Response to Streaming Authorization

Traditional PEP implementations follow request-response: one access attempt, one PDP query, one decision. Heutelbeck [[11]](#ref-11), [[13]](#ref-13) identified the fundamental limitation:

> "Current architectures and data flow models for access control are based on request-response communication. In stateful or session-based applications monitoring access rights over time, this results in polling of authorization services and for ABAC in the polling of policy information points. This introduces latency or increased load due to polling."

Attribute-Stream-Based Access Control (ASBAC) replaces request-response with publish-subscribe: a single authorization subscription produces a continuous stream of decisions that update as policies, attributes, or environment change. SAPL implements this model natively.

This streaming model aligns with emerging standards: OpenID CAEP [[15]](#ref-15), OpenID Shared Signals Framework [[16]](#ref-16), and OpenID AuthZEN [[17]](#ref-17).

### 1.3 The Role of Obligations and Advice

XACML 3.0 [[9]](#ref-9) (Section 7.17) distinguishes obligations from advice:

- **Obligations** are directives the PEP MUST fulfill. A conforming PEP denies access unless it can discharge ALL obligations attached to a decision.
- **Advice** is supplemental information the PEP SHOULD act on but MAY safely ignore.

This is the single most important semantic contract between PDP and PEP. Mishandling obligations (ignoring unknown ones, swallowing handler errors) is a security vulnerability.

Academic literature identifies several open challenges in obligation enforcement. XACML treats obligations as opaque attribute assignments without specifying processing semantics [[8]](#ref-8). Obligation recurrence in streaming contexts requires distinguishing one-shot from repeating obligations [[18]](#ref-18). Obligation conflicts may depend on runtime parameter values, requiring detection at enforcement time rather than at policy authoring time.

### 1.4 Key References

The following works form the conceptual foundation of this specification. Full citations are in the References section at the end of this document.

| Reference                                                                 | Contribution                                                              |
|---------------------------------------------------------------------------|---------------------------------------------------------------------------|
| Saltzer and Schroeder [[1]](#ref-1)                                       | Complete mediation, fail-safe defaults                                    |
| RFC 2748 - COPS [[3]](#ref-3)                                             | Stateful PEP-PDP protocol, persistent connection, LPDP                    |
| RFC 2753 [[5]](#ref-5)                                                    | PEP-PDP separation for policy-based admission control                     |
| RFC 2904 [[4]](#ref-4)                                                    | PEP collocated with resource in AAA framework                             |
| XACML 1.0 [[6]](#ref-6), 3.0 [[9]](#ref-9)                                | Canonical PEP/PDP/PIP/PAP, obligations/advice                             |
| NIST SP 800-162 [[10]](#ref-10)                                           | Federal ABAC reference architecture                                       |
| Heutelbeck [[11]](#ref-11), [[13]](#ref-13)                               | Streaming authorization, publish-subscribe PEP                            |
| NIST SP 800-207 [[14]](#ref-14)                                           | PEP as mandatory pervasive gatekeeper                                     |
| OpenID CAEP [[15]](#ref-15), SSF [[16]](#ref-16), AuthZEN [[17]](#ref-17) | Continuous access evaluation, real-time signals, PEP-PDP interoperability |

**Related work.** Google's Zanzibar [[12]](#ref-12) addresses a different concern: relationship-based access control (ReBAC) with global consistency at scale. While influential in the authorization space, its architecture (centralized tuple store, consistency tokens) does not inform PEP design as specified here. SAPL's streaming attribute-based model predates the Zanzibar publication and solves a fundamentally different problem -- continuous policy enforcement over attribute streams rather than graph-based relationship checks.

---

## 2. Implementation Philosophy

A PEP library is only useful if developers actually adopt it. The best security library is the one people use correctly without thinking about it. This section captures the design philosophy that both reference implementations follow and that new implementations should internalize.

### 2.1 Developer Experience First

The library must feel native to its framework. A NestJS developer should see NestJS patterns. A Spring developer should see Spring Security patterns. Authorization is already an unwelcome interruption to feature work, so the PEP should minimize cognitive overhead by speaking the framework's language.

Concrete examples from the reference implementations:

**Java/Spring PEP:**
- Throws `AccessDeniedException` from Spring Security, not a custom exception type.
- `@PreEnforce` / `@PostEnforce` mirrors Spring Security's `@PreAuthorize` / `@PostAuthorize`.
- SpEL expressions for subscription fields, the same expression language Spring Security uses.
- Standard `@AutoConfiguration` + `@ConfigurationProperties` + `META-INF/spring/*.imports` for boot integration.
- Constraint handlers: implement an interface, expose as `@Bean`, using standard Spring DI collection injection.
- `Flux` / `Mono` from Project Reactor, Spring's native reactive library.
- `MethodInterceptor` + `PointcutAdvisor` from Spring AOP, ordering relative to `AuthorizationInterceptorsOrder.PRE_AUTHORIZE`.

**TypeScript/NestJS PEP:**
- Throws `ForbiddenException` from `@nestjs/common`, not a custom exception type.
- `forRoot()` / `forRootAsync()` dynamic module pattern, following standard NestJS module registration.
- `createDecorator()` from `@toss/nestjs-aop` for aspect-oriented enforcement.
- `DiscoveryService.createDecorator()` for handler auto-discovery.
- RxJS `Observable`, NestJS's native reactive primitive.
- `nestjs-cls` for request context propagation across async boundaries.
- `@Injectable()` + `@SaplConstraintHandler('mapping')` for handler registration, following standard DI patterns.

**Guideline for new implementations:** Before writing a single line of PEP code, list the framework's native patterns for exceptions, decorators/annotations, dependency injection, configuration, reactive streams, and AOP. Then design the PEP to use exactly those patterns. If a Framework used different patterns, examine how a SAPL PEP would fit in naturally.

### 2.2 Principle of Least Surprise

Everything the PEP does should match what a framework developer expects. This applies to error types, configuration patterns, lifecycle hooks, and API surface.

| Concern              | Framework-native choice                                     | Why it matters                                                       |
|----------------------|-------------------------------------------------------------|----------------------------------------------------------------------|
| Access denied error  | Framework's "forbidden" exception                           | Error handlers, filters, and middleware already know how to catch it |
| Configuration        | Framework's config system (properties, env, module options) | Developers know where to look and how to override                    |
| Handler registration | Framework's DI / decorator system                           | No new registration API to learn                                     |
| Reactive types       | Framework's stream type (Flux, Observable, AsyncIterator)   | Composes with existing pipelines without wrapping                    |
| Lifecycle            | Framework's shutdown hooks                                  | Cleanup happens when the framework says it does                      |

When the PEP surprises the developer (custom exception types they have to catch separately, a bespoke configuration format, a proprietary handler registry), adoption friction goes up and correct usage goes down.

### 2.3 Quick Adoption Path

A developer should go from "no authorization" to "basic enforcement" in under five minutes:

1. Install the package.
2. Configure the PDP URL (and optionally a token).
3. Add a decorator to a controller method.

This means sensible defaults for subscription building: derive subject from the authenticated user, action from the method or route, resource from the path. The 80% case should require zero subscription configuration. Override fields individually when you need to, but never force the developer to build the entire subscription from scratch.

### 2.4 Secure Defaults, Explicit Escape Hatches

Saltzer and Schroeder [[1]](#ref-1) introduced the principle of "fail-safe defaults" (based on concepts from at least as early as 1965): base access decisions on permission rather than exclusion, so the default configuration is the secure one. Making things less secure should require deliberate, visible action.

These concepts now manifest as specific guidance for software development. The joint publication "Shifting the Balance of Cybersecurity Risk" [[19]](#ref-19) by CISA, NSA, FBI, and international partners calls on software manufacturers to ship products that are secure by design and by default. The guidance explicitly requires that security controls are enabled out of the box, that insecure legacy features are deprecated, and that customers do not bear the burden of hardening the product themselves. A PEP library that defaults to HTTPS, denies on unknown obligations, and excludes secrets from logs without configuration directly implements these principles.

In practice:

- **HTTPS by default.** Using HTTP requires setting an explicit flag (`allowInsecureConnections`) and produces a startup warning.
- **Fail-closed by default.** PDP unreachable = deny. Unknown obligation = deny. No "permissive mode" that silently grants access.
- **No secrets in logs by default.** The subscription's `secrets` field is structurally excluded from log output, not filtered after the fact.
- **Unknown obligations deny by default.** A PDP can add new obligation types at any time. The PEP denies until a handler is registered.

Insecure options exist for development and testing. They require deliberate opt-in. A single flag is acceptable, but it must not be the default, and it must produce visible warnings.

### 2.5 Graceful Degradation

When things go wrong (and they will), the PEP should deny access, log what happened, and make the degraded state observable. It should never crash, never silently permit, and never require a restart to recover.

- PDP goes down: the PDP client emits INDETERMINATE (deny), retry with backoff, auto-recover when PDP returns.
- Obligation handler throws: deny the current request, log the error, continue accepting new requests. The PEP catches application-level exceptions (e.g., `IOException`, `HttpException`), not fatal platform errors (e.g., `OutOfMemoryError`, `StackOverflowError`). Fatal errors must propagate normally so the runtime can shut down. The invariant is that no data leaks during shutdown: the PEP denies the current request before the error propagates.
- Configuration error: fail at startup with a clear message, not at the first request with a cryptic stack trace.

---

## 3. Terminology

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document are to be interpreted as described in RFC 2119 [[2]](#ref-2).

**A note on tone:** This document uses RFC 2119 keywords sparingly and only for hard invariants, meaning security-critical properties where deviation creates vulnerabilities. Everything else is stated as engineering guidance. If a requirement says "MUST", violating it breaks security. If it says "should" (lowercase), it is a strong recommendation informed by operational experience.

| Term                            | Definition                                                                                                                                                          |
|---------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Authorization Decision**      | The PDP's verdict: one of `PERMIT`, `DENY`, `INDETERMINATE`, or `NOT_APPLICABLE`, optionally accompanied by obligations, advice, and a resource replacement value.  |
| **Authorization Subscription**  | The request sent to the PDP containing `subject`, `action`, `resource`, `environment`, and optionally `secrets`.                                                    |
| **Constraint**                  | A JSON object attached to a decision as either an obligation or advice. Contains at minimum a `type` field for routing to handlers.                                 |
| **Obligation**                  | A constraint that MUST be enforced. Failure to enforce ANY obligation MUST result in access denial.                                                                 |
| **Advice**                      | A constraint that SHOULD be enforced but MAY be safely ignored on failure.                                                                                          |
| **Constraint Handler**          | A registered component that can enforce a specific type of constraint.                                                                                              |
| **Constraint Handler Set**      | The resolved and composed set of all constraint handlers applicable to a single authorization decision. Implementations SHOULD cache this for repeated application. |
| **Resource Access Point (RAP)** | The protected resource or data source (e.g., database query, service call, data stream).                                                                            |
| **Enforcement Aspect**          | The component that intercepts a method call or request and applies authorization enforcement.                                                                       |
| **Decide-once**                 | Single request-response PDP communication.                                                                                                                          |
| **Streaming decide**            | Long-lived subscription returning a stream of decisions.                                                                                                            |

---

## 4. How a PEP Works

This section is for developers building a PEP from scratch, whether implementing a framework SDK or adding manual enforcement to an application that cannot use a pre-built library. It walks through the mechanics from simplest to full-featured, building incrementally.

### 4.1 The Minimum Viable PEP

Before diving into code, here are the six invariants every PEP must satisfy. If your implementation violates any of these, it has a security hole.

1. **Only PERMIT grants access.** `DENY`, `INDETERMINATE`, and `NOT_APPLICABLE` all result in denial. There is no "default permit."
2. **Every obligation must have a handler.** Before granting access on a PERMIT, verify that every obligation in the decision can be enforced. If even one obligation has no registered handler, deny.
3. **Obligation handler failures deny.** If a handler accepts responsibility for an obligation but throws during execution, deny access. Do not grant access with partially-enforced obligations.
4. **Use HTTPS for PDP communication.** The authorization subscription may contain secrets, and the decision contains policy internals. Unencrypted transport exposes both to network attackers.
5. **When the PDP is unreachable: deny.** Never default-permit on communication failure. Never crash. Deny and retry.
6. **Never expose policy internals to clients.** The client gets "access denied," not which policy matched, which obligation failed, or what the PDP returned.

### 4.2 Basic Request-Response Enforcement

The simplest PEP intercepts a request, asks the PDP, and grants or denies:

```
function enforcePreRequest(request):
    subscription = buildSubscription(request)
    // subscription = { subject, action, resource, environment }

    decision = httpPost(pdpUrl + "/api/pdp/decide-once", subscription, timeout=5s)

    if decision is error or timeout:
        return deny("Access denied")  // fail-closed

    if not isValidDecision(decision):
        return deny("Access denied")  // malformed response = deny

    if decision.decision != "PERMIT":
        return deny("Access denied")

    if decision.obligations is not empty:
        return deny("Access denied")  // no handlers = unhandled obligations = deny

    if decision.resource is present:
        return deny("Access denied")  // no resource replacement support = deny

    // PERMIT with no obligations and no resource - proceed
    return callProtectedMethod(request)
```

Key points:
- The subscription is built from request context: authenticated user, HTTP method, route path.
- Any communication failure maps to denial. The specific failure is logged server-side but never returned to the client.
- Response validation is mandatory: a malformed PDP response is treated as INDETERMINATE.
- A PERMIT with obligations MUST be denied if no obligation handlers are registered. Granting access with unhandled obligations violates the obligation contract.
- A PERMIT with a resource replacement MUST be denied if the implementation does not support resource replacement. Ignoring it would skip policy-mandated data transformation.

All of these checks collapse into a single condition. The true minimal PEP is:

```
function enforcePreRequest(request):
    subscription = buildSubscription(request)
    decision = httpPost(pdpUrl + "/api/pdp/decide-once", subscription, timeout=5s)

    if decision.decision is PERMIT
            and decision.obligations is empty
            and decision.resource is absent:
        return callProtectedMethod(request)
    else:
        return deny("Access denied")
```

### 4.3 Adding Obligation Handling

The examples in this section and Section 4.2 cover request-response (decide-once) enforcement. Streaming enforcement is covered in Section 4.4.

Real-world PERMIT decisions often carry obligations ("log this access", "redact these fields", "add this HTTP header"). The `resource` field in the decision, when present, is an implicit obligation: the PEP MUST replace the protected resource's return value with the given content. If the enforcement context does not support resource replacement, this is equivalent to an unhandled obligation and the PEP must deny.

Extending the basic flow:

```
function enforcePreRequest(request):
    subscription = buildSubscription(request)
    decision = httpPost(pdpUrl + "/api/pdp/decide-once", subscription, timeout=5s)

    if decision is error or timeout:
        return deny("Access denied")

    if not isValidDecision(decision):
        return deny("Access denied")

    if decision.decision != "PERMIT":
        return deny("Access denied")

    // Check that all obligations can be enforced
    for each obligation in decision.obligations:
        if not hasHandlerFor(obligation):
            log.error("Unhandled obligation: " + obligation)
            return deny("Access denied")

    if decision.resource is present and not supportsResourceReplacement():
        return deny("Access denied")  // implicit obligation cannot be fulfilled

    // Execute ALL handlers - do not short-circuit on first failure
    failures = []
    for each obligation in decision.obligations:
        try: executeHandler(obligation)
        catch error: failures.append(error)
    for each advice in decision.advice:
        try: executeHandler(advice)
        catch error: log.warn("Advice handler failed: " + error)  // non-fatal

    if failures is not empty:
        log.error("Obligation handler failures: " + failures)
        return deny("Access denied")

    if decision.resource is present:
        return decision.resource

    result = callProtectedMethod(request)
    return result
```

Key points:
- Every obligation must have a registered handler. If any obligation is unhandled, deny before executing anything.
- All obligation and advice handlers are executed. The PEP does not short-circuit on the first obligation failure. Remaining handlers (including advice handlers for audit logging) are still attempted.
- After all handlers have run, if any obligation handler failed, deny. Advice handler failures are logged but do not cause denial.
- The `resource` field in the decision is an implicit obligation to replace the return value. If the PEP cannot perform this replacement in the current context, it must deny.

Note that without obligation handling or resource replacement support, this entire flow collapses to the basic enforcement in Section 4.2, which correctly denies any decision carrying obligations or a resource.

### 4.4 Streaming Enforcement

For long-lived connections (WebSockets, SSE, streaming HTTP), the PEP subscribes to a decision stream instead of making a one-shot request:

```
function enforceStream(request, sourceFactory):
    subscription = buildSubscription(request)
    decisionStream = httpPostStream(pdpUrl + "/api/pdp/decide", subscription)
    sourceStarted = false
    permitted = false

    decisionStream.onDecision(decision):
        if decision.decision is PERMIT
                and all obligations can be handled
                and resource replacement is supported (if present):
            execute obligation and advice handlers for this decision
            permitted = true

            if not sourceStarted:
                // Only start the protected data source on first PERMIT
                sourceStarted = true
                sourceFactory.subscribe(onNext: forwardIfPermitted)

        else:
            permitted = false

    decisionStream.onError(err):
        permitted = false
        // retry with backoff...

    function forwardIfPermitted(item):
        if not permitted:
            // drop, error, or signal denial depending on enforcement mode
            return
        apply obligation and advice handlers to item
        emit(item)
```

Key points:
- **Deferred invocation:** The protected data source is not started until the first PERMIT arrives. This prevents executing side-effectful code before authorization is confirmed.
- **The source is started once.** Subsequent decisions change the enforcement state (permitted or denied) and swap which handlers are applied, but do not restart the source.
- **Decisions can change at any time.** When a new decision arrives (PERMIT with different obligations, or a DENY), the PEP updates its enforcement state. Data items in flight are processed under the old or new state, never a mix.

### 4.5 Common Mistakes That Create Security Holes

These are patterns we have seen (or carefully avoided) in real implementations:

**Default-permit on PDP outage.** The PDP being unreachable is not a reason to let everyone in. It is a reason to let no one in. If your application cannot tolerate PDP downtime, deploy the PDP for high availability. Do not weaken the PEP.

**Ignoring unknown obligations.** A PDP administrator can add new obligation types at any time. If the PEP encounters an obligation it has no handler for and grants access anyway, the policy's intent is violated. The safe default: deny until a handler is registered.

**Swallowing obligation handler errors.** An obligation handler that throws has not fulfilled the obligation. Catching the error and granting access means the obligation was not enforced. Log the error, deny access.

**Leaking decisions in error responses.** The PDP decision may contain obligation details that reveal policy structure ("you need role X", "this resource requires clearance Y"). Returning the raw decision to the client is an information leak. Return a generic "access denied" and log the details server-side.

**Not validating PDP responses.** A malformed PDP response (missing `decision` field, non-JSON body, unexpected structure) should be treated as INDETERMINATE, not as a reason to crash or, worse, to extract a partial decision that happens to look like PERMIT.

**Non-atomic decision swap in streaming enforcement.** When a new decision arrives while data items are in flight, the enforcement state and the handlers applied to each item must change atomically. If a data item is partially processed under the old decision's handlers and partially under the new one, the result may violate the intent of both decisions. Each data item must be processed entirely under one decision's enforcement state.

**Mutating the original data during content filtering.** Content filtering handlers (redaction, field deletion) MUST operate on a copy of the data. Mutating the original can leak unfiltered data through concurrent readers or framework caching.

---

## 5. Authorization Decision Model

### 5.1 Decision Values

An authorization decision MUST contain a `decision` field with one of exactly four values:

| Decision         | Semantics                                            | PEP Action                                                  |
|------------------|------------------------------------------------------|-------------------------------------------------------------|
| `PERMIT`         | Access is granted, subject to constraint enforcement | Grant access if and only if all obligations can be enforced |
| `DENY`           | Access is explicitly denied                          | Deny access. Run best-effort constraint handlers.           |
| `INDETERMINATE`  | Error during evaluation or no definitive answer      | Deny access (fail-closed)                                   |
| `NOT_APPLICABLE` | No matching policies found                           | Deny access (fail-closed)                                   |

**CRITICAL INVARIANT:** Only `PERMIT` MAY result in access being granted. All other decision values MUST result in access denial.

### 5.2 Decision Structure

```
{
  "decision": "PERMIT" | "DENY" | "INDETERMINATE" | "NOT_APPLICABLE",
  "obligations": [ <constraint>, ... ],   // optional
  "advice": [ <constraint>, ... ],         // optional
  "resource": <any>                        // optional
}
```

- `obligations`: Array of constraint objects. MUST be enforced for the decision to take effect.
- `advice`: Array of constraint objects. SHOULD be enforced but failures are non-fatal.
- `resource`: A replacement value for the protected resource. When present, the PEP MUST substitute this value for the actual resource access result.

### 5.3 Response Validation

The PEP MUST validate every PDP response before acting on it:

1. If the response is not an object, or is null/undefined, or is an array: treat as `INDETERMINATE`.
2. If the `decision` field is missing, not a string, or not one of the four valid values: treat as `INDETERMINATE`.
3. If `obligations` is present but not an array: ignore it (treat as empty).
4. If `advice` is present but not an array: ignore it (treat as empty).
5. Unknown fields in the response should be stripped (defense-in-depth against PDP response injection).

A malformed PDP response could lead to incorrect authorization if not validated. The fail-closed default ensures that validation failures result in denial rather than accidental grants.

---

## 6. PDP Communication

### 6.1 Communication Modes

A conforming PEP MUST support both communication modes:

#### 6.1.1 Decide-Once (Request-Response)

- **Endpoint:** `POST /api/pdp/decide-once`
- **Request content type:** `application/json`
- **Request body:** JSON-encoded `AuthorizationSubscription`.
- **Response content type:** `application/json`
- **Response body:** A single JSON-encoded `AuthorizationDecision`.
- **Use case:** One-shot enforcement for individual HTTP requests (PreEnforce, PostEnforce).

#### 6.1.2 Streaming Decide (Subscription)

- **Endpoint:** `POST /api/pdp/decide`
- **Request content type:** `application/json`
- **Request body:** JSON-encoded `AuthorizationSubscription`.
- **Response content type:** `text/event-stream`
- **Response body:** The PDP streams decisions as Server-Sent Events (SSE). Each SSE event contains a complete JSON-encoded `AuthorizationDecision` in its `data` field. The PDP MAY send SSE comment events (lines starting with `:`) as keep-alive signals to prevent connection timeouts from firewalls or proxies.
- **Lifetime:** The connection remains open indefinitely. The PDP pushes a new decision whenever the authorization state changes for the given subscription.
- **Use case:** Continuous enforcement for long-lived connections (SSE, WebSocket, streaming endpoints).

#### 6.1.3 Multi-Subscription Endpoints

The PDP provides multi-subscription endpoints for batch authorization. These allow a client to bundle multiple authorization subscriptions into a single request, avoiding per-subscription HTTP overhead.

Multi-subscription support is OPTIONAL for a conforming PEP. A PEP that only uses single-subscription enforcement (PreEnforce, PostEnforce, streaming decorators) does not need to implement multi-subscription methods.

**Request type - `MultiAuthorizationSubscription`:**

A map of subscription IDs to `AuthorizationSubscription` objects. Each subscription ID is a client-chosen string used to correlate decisions with their subscriptions. JSON wire format:

```json
{
  "subscriptions": {
    "read-file":  { "subject": "alice", "action": "read",  "resource": "file-1" },
    "write-file": { "subject": "alice", "action": "write", "resource": "file-1" }
  }
}
```

**Response types:**

- **`IdentifiableAuthorizationDecision`** - A single `AuthorizationDecision` tagged with its `subscriptionId`. Used when decisions arrive individually as they become available.

  ```json
  { "subscriptionId": "read-file", "decision": { "decision": "PERMIT" } }
  ```

- **`MultiAuthorizationDecision`** - A map of all subscription IDs to their current decisions. Emitted as a complete snapshot whenever any individual decision changes. Useful when the client needs a consistent view of all decisions at once.

  ```json
  {
    "decisions": {
      "read-file":  { "decision": "PERMIT" },
      "write-file": { "decision": "DENY" }
    }
  }
  ```

**Endpoints:**

##### 6.1.3.1 Multi-Decide (Streaming, Individual)

- **Endpoint:** `POST /api/pdp/multi-decide`
- **Request content type:** `application/json`
- **Request body:** JSON-encoded `MultiAuthorizationSubscription`.
- **Response content type:** `text/event-stream`
- **Response body:** The PDP streams `IdentifiableAuthorizationDecision` events as they become available. Each SSE event contains one decision tagged with its subscription ID. Decisions arrive independently - a fast-resolving subscription emits before a slow one.
- **Use case:** Streaming enforcement for multiple concurrent subscriptions where the client handles each decision independently (e.g., updating individual UI elements).

##### 6.1.3.2 Multi-Decide-All (Streaming, Bundled)

- **Endpoint:** `POST /api/pdp/multi-decide-all`
- **Request content type:** `application/json`
- **Request body:** JSON-encoded `MultiAuthorizationSubscription`.
- **Response content type:** `text/event-stream`
- **Response body:** The PDP streams `MultiAuthorizationDecision` snapshots. The first event is emitted only after all subscriptions have at least one decision. Subsequent events are emitted whenever any individual decision changes, always containing the complete current state of all decisions.
- **Use case:** Streaming enforcement where the client needs a consistent, complete view of all authorization states (e.g., rendering a page where multiple elements depend on authorization).

##### 6.1.3.3 Multi-Decide-All-Once (Request-Response, Bundled)

- **Endpoint:** `POST /api/pdp/multi-decide-all-once`
- **Request content type:** `application/json`
- **Request body:** JSON-encoded `MultiAuthorizationSubscription`.
- **Response content type:** `application/json`
- **Response body:** A single `MultiAuthorizationDecision` containing one decision per subscription.
- **Use case:** One-shot batch authorization for pages or API responses that require multiple access control decisions upfront (e.g., rendering navigation with permission-dependent items).

### 6.2 Transport, Authentication, and Secret Handling

PDP communication carries authorization decisions and potentially sensitive subscription data (including secrets). Unencrypted or unauthenticated channels expose this to network attackers. RFC 4261 [[7]](#ref-7) established the requirement for TLS-protected policy protocol communication in 2005.

**REQ-TRANSPORT-1:** The PEP MUST use HTTPS (TLS) for PDP communication by default.

**REQ-TRANSPORT-2:** The PEP MAY provide an explicit opt-out for development environments (`allowInsecureConnections` or equivalent). This opt-out:
- MUST require a deliberate configuration action (not just omitting TLS config).
- MUST log a warning at startup when active.
- MUST NOT be the default.

**REQ-TRANSPORT-3:** The PEP MUST validate the PDP URL at construction/startup time, rejecting malformed URLs immediately rather than at first request.

**REQ-AUTH-1:** The PEP MUST support the following authentication methods for PDP communication:
- **API Key:** Sent as `Authorization: Bearer sapl_<key>`. The `sapl_` prefix distinguishes API keys from OAuth2 JWT tokens. This is the simplest setup for dedicated PDP-to-PEP communication.
- **Basic Auth:** Sent as `Authorization: Basic <base64(username:secret)>`. Required because the SAPL PDP server supports Basic Auth as a first-class authentication method. A PEP that only supports Bearer tokens cannot connect to a Basic Auth-configured server.

**REQ-AUTH-2:** The PEP SHOULD support Bearer token authentication with externally obtained OAuth2 JWT tokens (i.e., the PEP sends `Authorization: Bearer <jwt>` where the token was acquired out-of-band). Full OAuth2 Client Credentials flow (automated token acquisition and refresh) is RECOMMENDED but not required.

**REQ-AUTH-3:** The PEP MUST NOT log authentication credentials (tokens, passwords, API keys) at any log level.

**REQ-AUTH-4:** The PEP MUST reject configuration where both Basic Auth credentials and a Bearer token are provided simultaneously. Exactly one authentication method must be active (or none, for unauthenticated development setups).

**REQ-SCHEMA-1:** The authorization subscription schema treats `subject`, `action`, and `resource` as mandatory fields. The `environment` and `secrets` fields are optional and SHOULD be omitted from the wire format when undefined/empty (not sent as `null` or `{}`).

**REQ-SECRETS-1:** When the `secrets` field is present (non-empty) in an authorization subscription, the PEP MUST transmit it to the PDP -- it is needed for policy evaluation.

**REQ-SECRETS-2:** The `secrets` field MUST be excluded from all log output, including debug-level logs. The PEP MUST destructure or filter the subscription before logging.

**REQ-LOG-1:** The `secrets` field MUST NEVER appear in logs at any level.

**REQ-LOG-2:** Authentication tokens MUST NEVER appear in logs at any level.

### 6.3 Request-Response Semantics

The request-response endpoints (`decide-once`, `multi-decide-all-once`) follow standard HTTP POST semantics: the PEP sends a subscription, waits for a single JSON response, and returns the result. No persistent connection is maintained.

**REQ-RR-1:** Request-response calls MUST have a configurable timeout (RECOMMENDED default: 5000ms). The timeout covers the entire round trip from request to response.

**REQ-RR-2:** On timeout, the PEP MUST return `INDETERMINATE` (fail-closed).

**REQ-RR-3:** Request-response calls MUST NOT retry automatically. The caller is responsible for retry logic if needed. This avoids surprising latency spikes from hidden retries in what the caller expects to be a single round trip.

### 6.4 Streaming Connection Lifecycle

The streaming endpoints (`decide`, `multi-decide`, `multi-decide-all`) open a persistent SSE connection. The PDP pushes new decisions whenever the authorization state changes. The connection is long-lived by design and requires its own lifecycle management.

**REQ-STREAM-1:** Streaming connections MUST have a configurable connect timeout covering the initial HTTP handshake (RECOMMENDED: same default as request-response, 5000ms). After the connection is established, no per-read timeout is applied - the stream stays open indefinitely.

**REQ-STREAM-2:** The PEP MUST automatically reconnect after connection loss using exponential backoff with jitter. The reconnection strategy MUST satisfy these constraints:
- **Exponential growth:** The delay between attempts MUST increase exponentially (e.g., doubling), not linearly or at a fixed interval.
- **Upper bound:** The delay MUST be capped at a configurable maximum to avoid indefinite waits.
- **Jitter:** Each delay MUST include a random component to prevent thundering herd when multiple PEP instances reconnect simultaneously after a PDP restart. Any jitter strategy (full, half, decorrelated) is acceptable as long as the delay is not deterministic.
- **Configurable:** The initial delay, maximum delay, and maximum retry count MUST be configurable by the operator.

All delay parameters are deployment-dependent. The spec does not prescribe specific defaults.

**REQ-STREAM-3:** Between connection attempts and during reconnection, the PEP MUST emit `INDETERMINATE` to subscribers (fail-closed during outage).

**REQ-STREAM-4:** Log severity SHOULD escalate after repeated failures (e.g., WARN for first N attempts, ERROR thereafter) to prevent log flooding while ensuring persistent failures are visible to operators.

**REQ-STREAM-5:** On authentication errors (HTTP 401/403), the PEP MUST log at ERROR level on every occurrence (not subject to log escalation dampening) to ensure the operator notices a likely configuration problem. The PEP MUST still retry -- authentication failures can be transient (gateway rolling deployment, token rotation timing, PDP redeployment) and permanent retry abandonment would require manual restart to recover.

### 6.5 SSE Streaming Parser

The PDP streaming endpoints use Server-Sent Events (SSE, `text/event-stream`) as the wire format. If the platform provides a native SSE parser (e.g., `EventSource` in browsers, Spring's `ServerSentEventHttpMessageReader`), use it. If not, the PEP must implement the following parsing requirements.

**REQ-SSE-1:** The streaming parser MUST handle incremental UTF-8 decoding (partial multi-byte characters across chunk boundaries).

**REQ-SSE-2:** The parser MUST process the SSE wire format: lines are separated by `\n`. Lines starting with `data:` contain decision payloads. Lines starting with `:` are comments (used for keep-alive) and MUST be silently discarded. Blank lines delimit SSE events.

**REQ-SSE-3:** The parser MUST extract the JSON payload from `data:` lines by stripping the `data:` prefix and any leading whitespace, then parsing the remainder as JSON. Each complete event produces one `AuthorizationDecision`.

**REQ-SSE-4:** JSON parse failures for individual events MUST be logged but MUST NOT terminate the stream. Other valid decisions on the same stream MUST continue to flow.

**REQ-SSE-5:** The SSE line buffer MUST have a maximum size limit (RECOMMENDED: 1 MB). If the buffer exceeds this limit (indicating a PDP sending data without newline delimiters), the PEP MUST:
1. Emit an `INDETERMINATE` decision to the subscriber.
2. Abort the connection.
3. Allow the retry mechanism (6.4) to reconnect.

Buffer overflow protection prevents memory exhaustion from a misbehaving or compromised PDP.

### 6.6 Decision Deduplication

**REQ-DEDUP-1:** The streaming PDP client MUST apply `distinctUntilChanged` (or equivalent) on the decision stream using deep structural equality. Two decisions are equal if and only if their `decision`, `obligations`, `advice`, and `resource` fields are structurally identical.

**REQ-DEDUP-2:** The deep equality comparison MUST have a depth limit (RECOMMENDED: 20) to prevent stack overflow from pathological input. Comparisons exceeding the depth limit SHOULD return `false` (treating deeply nested objects as different, which is the safe default: it may cause redundant constraint handler re-resolution but never suppresses a genuine change).

Deduplication prevents downstream enforcement aspects from redundantly processing identical consecutive decisions. This matters in streaming scenarios where the PDP may re-evaluate frequently (e.g., time-based attributes ticking every second) without the decision actually changing.

### 6.7 Fail-Closed Communication Invariant

**REQ-FAILCLOSE-1:** Every PDP communication failure MUST result in an `INDETERMINATE` decision being returned or emitted. This applies to both request-response and streaming communication and includes:
- HTTP error status codes (4xx, 5xx)
- Network errors (connection refused, DNS failure)
- Timeout (request-response round trip or streaming connect)
- Malformed response (non-JSON, missing fields)
- Buffer overflow (streaming)
- Stream end (PDP closes connection)
- TLS handshake failure

No communication failure path may result in `PERMIT`.

---

## 7. Enforcement Modes

A conforming PEP MUST support the following five enforcement modes. Each mode defines when the PDP is consulted, when the protected method executes, and how the decision affects the ongoing operation.

This section also specifies error handling, deny behavior, and teardown for each mode, because those concerns are inseparable from the mode's semantics. A deny handler that lives in a separate "error handling" chapter, disconnected from the mode it applies to, is harder to implement correctly.

### Enforcement Locations

A PEP does not enforce authorization at a single checkpoint. Enforcement happens at multiple locations in the lifecycle of a request or stream, and each location serves a different purpose. The handler type taxonomy in Section 8.1 exists because of these distinct enforcement locations - each handler type corresponds to a point in the lifecycle where a constraint can meaningfully intervene.

**Request-response enforcement** (PreEnforce, PostEnforce) has these enforcement locations:

| Location              | When                                 | What constraints do here                          |
|-----------------------|--------------------------------------|---------------------------------------------------|
| On decision           | Authorization decision arrives       | Side effects: logging, audit, notification        |
| Pre-method invocation | Before the protected method executes | Modify method arguments (PreEnforce only)         |
| On return value       | After the method returns             | Transform, filter, observe, or replace the result |
| On error              | If the method throws                 | Transform or observe the error                    |

**Streaming enforcement** (EnforceTillDenied, EnforceDropWhileDenied, EnforceRecoverableIfDenied) has these enforcement locations:

| Location           | When                                         | What constraints do here                      |
|--------------------|----------------------------------------------|-----------------------------------------------|
| On decision        | Each new decision from the PDP stream        | Side effects: logging, audit, notification    |
| On each data item  | Each element emitted by the source stream    | Transform, filter, observe, or replace items  |
| On stream error    | Source stream produces an error              | Transform or observe the error                |
| On stream complete | Source stream completes normally             | Cleanup: release resources, finalize audit    |
| On cancel/teardown | Subscriber cancels or enforcement terminates | Cleanup: release resources, close connections |

Each handler type (Side-effect, Consumer, Mapping, FilterPredicate, MethodInvocation, ErrorHandler, ErrorMapping) maps to one or more of these locations. A side-effect handler with signal ON_DECISION fires at the "on decision" location. A mapping handler fires at "on return value" or "on each data item," depending on the enforcement mode. A method invocation handler only exists in PreEnforce because it operates at the "pre-method invocation" location, which does not exist in PostEnforce (the method has already run) or in streaming modes (the method is invoked once on first PERMIT, not on each decision).

**Platform-specific enforcement locations:** Frameworks with richer reactive abstractions may expose additional lifecycle hooks. For example, Java's Project Reactor provides `doOnSubscribe`, `doOnRequest` (backpressure signal), `doOnTerminate`, and `doAfterTerminate` -- each representing an enforcement location where constraints can intervene. The Java reference implementation exposes handler provider interfaces for all of these. The NestJS reference implementation, built on RxJS, does not have equivalents for these signals and therefore defines fewer handler types. When implementing a PEP for a new framework, the set of handler types should match the lifecycle hooks the framework's reactive or async model naturally provides. Do not invent artificial enforcement locations; only expose locations that the framework supports natively. An example of a framework-specific enforcement locations would be for example in specific modeling frameworks, e.g., Axon Framework for CQRS-ES applications, here one should implement the hooks that naturally fit in the modeling style (e.g., Pre or Post Command handling).

### Handler Resolution Timing

In streaming enforcement modes, the PEP resolves which registered handlers are responsible for each constraint when a new decision arrives, not when each data item passes through. This serves two purposes: the PEP can deny immediately when an obligation has no registered handler (instead of discovering this only when the first data item arrives), and the resolved handlers are reused across all data items in a potentially high-throughput stream without redundant resolution on every element.

### 7.1 PreEnforce (Pre-Method Authorization)

**Semantics:** Authorize BEFORE method execution. The method only runs on PERMIT with all obligations satisfied.

**Flow:**
1. Build authorization subscription from request context and decorator options.
2. Call `decideOnce()` on the PDP.
3. If decision is not `PERMIT`: deny access (see Section 7.6).
4. Resolve constraint handlers for all obligations and advice in the decision. If any obligation has no registered handler: deny access (log at ERROR). Unhandled advice is silently ignored.
5. Execute on-decision constraint handlers (obligations and advice).
6. Execute method-invocation constraint handlers (may modify method arguments).
7. Execute the protected method.
8. Apply all constraint handlers to the return value: resource replacement (if present in the decision), filter predicates, consumer handlers, and mapping handlers (see Section 8.10 for execution order). If any obligation fails at this stage, deny access (log ERROR). Note, that in this case special attention has to be given to transaction semantics. E.g., if the method has succeeded a modifying DB operation, a failure here should roll back the DB transaction.
9. Return the (possibly transformed) result.

**Error handling:**
- If any obligation is unhandled: deny access.
- If an obligation handler throws at any point (on-decision, method-invocation, or return value processing): deny access.
- If the method throws: pass the error through error constraint handlers, then re-throw.
- Any deny MUST roll back DB transactions.

Advice handler failures are logged and absorbed at any point, never causing denial (see Section 8.3).

### 7.2 PostEnforce (Post-Method Authorization)

**Semantics:** Execute the method FIRST, then authorize. The PDP can inspect the method's return value for its decision.

**Flow:**
1. Execute the protected method unconditionally.
2. Build authorization subscription from request context, decorator options, AND the method's return value.
3. Call `decideOnce()` on the PDP.
4. If decision is not `PERMIT`: deny access (discard the method's result).
5. Resolve constraint handlers for all obligations and advice. If any obligation has no registered handler: deny access. Unhandled advice is silently ignored.
6. Execute on-decision constraint handlers (obligations and advice).
7. Apply all constraint handlers to the return value: resource replacement, filter predicates, consumer handlers, and mapping handlers (see Section 8.10). 
8. Return the (possibly transformed) result.

Method-invocation constraint handlers are NOT applicable in PostEnforce because the method has already executed.

**Error handling:**
- If the method throws: propagate directly (PDP not yet consulted, so this is an application error).
- If any obligation is unhandled: deny access.
- If an obligation handler throws at any point (on-decision or return value processing): deny access.
- Any deny MUST roll back DB transactions.
 
Advice handler failures are logged and absorbed, never causing denial (see Section 8.3).

### 7.3 EnforceTillDenied (Streaming, Terminal on Deny)

**Semantics:** Stream data while PERMIT. On the first non-PERMIT decision, terminate the stream with an error. The stream never recovers.

**Flow:**
1. Subscribe to `decide()` on the PDP (streaming).
2. Wait for the first decision.
3. On PERMIT:
   - Resolve constraint handlers for all obligations and advice. If any obligation has no registered handler: terminate the stream with an authorization error. Unhandled advice is silently ignored.
   - Execute on-decision constraint handlers (obligations and advice).
   - Subscribe to the source data stream (deferred, see Section 7.7).
   - Forward data through constraint handlers (resource replacement, filter predicates, consumers, mappers).
4. On subsequent PERMIT (with changed constraints):
   - Re-resolve constraint handlers for the new decision (obligations and advice).
   - Continue forwarding data with the new handlers.
5. On non-PERMIT:
   - Execute constraint handlers in best-effort mode for audit/logging (obligations and advice, see Section 7.6).
   - Emit access-denied signal to subscriber (if configured).
   - Terminate the stream with an authorization error.

**Terminal invariant:** Once a non-PERMIT decision is received, the stream MUST terminate. There is no recovery path.

**REQ-ERROR-5 (EnforceTillDenied):** If an on-next obligation handler fails while processing a data item, terminate the stream with an error. Advice handler failures are logged and absorbed (see Section 8.3).

**Teardown:** On termination (deny, source completion, or subscriber cancellation), execute ON_CANCEL constraint handlers, unsubscribe from the PDP decision stream, unsubscribe from the source data stream, and release all constraint handler references.

### 7.4 EnforceDropWhileDenied (Streaming, Silent Drop)

**Semantics:** Silently drop data during non-PERMIT periods. The stream never terminates due to authorization changes. The subscriber is unaware of deny periods.

**Flow:**
1. Subscribe to `decide()` on the PDP (streaming).
2. Wait for the first PERMIT before subscribing to the source (deferred).
3. On PERMIT: resolve constraint handlers for all obligations and advice. If any obligation is unhandled, treat as deny. Unhandled advice is silently ignored. Otherwise, set state to permitted, subscribe to source if first time.
4. On non-PERMIT: set state to denied, release constraint handler references. Data silently dropped.
5. Source data: if state is denied, silently discard. If permitted, forward through constraint handlers (obligations and advice).
6. On subsequent PERMIT: re-resolve constraint handlers (obligations and advice), resume forwarding.

**Key properties:**
- No error emission on deny.
- No callback notification to the subscriber.
- The subscriber observes a gap in data (no items arrive during deny periods).
- Stream only terminates on source completion, source error, or subscriber cancellation.

**REQ-ERROR-5 (EnforceDropWhileDenied):** If an on-next obligation handler fails while processing a data item, silently drop the single element and continue processing subsequent items. Advice handler failures are logged and absorbed (see Section 8.3).

**Teardown:** On termination (source completion, source error, or subscriber cancellation), execute ON_CANCEL/ON_COMPLETE handlers as appropriate, unsubscribe from the PDP decision stream, and release all constraint handler references.

### 7.5 EnforceRecoverableIfDenied (Streaming, Suspend/Resume with Notification)

**Semantics:** Suspend data forwarding on deny, resume on re-permit. The subscriber MUST be able to distinguish "access denied" from "access permitted, no data available." The PEP MUST emit an access-state signal on every PERMITTED-to-DENIED and DENIED-to-PERMITTED transition.

**REQ-ACCESS-VISIBILITY-1:** On a PERMITTED-to-DENIED transition, the PEP MUST deliver a deny signal to the subscriber before suppressing further data. On a DENIED-to-PERMITTED transition, the PEP MUST deliver a recovery signal to the subscriber before forwarding source data. These signals MUST be delivered immediately when the decision arrives, not deferred to the next source emission. Without recovery signals, the subscriber cannot distinguish "access revoked" from "access restored, source idle" -- a UI would show "no access" indefinitely even after access is restored, until the source happens to emit.

**Flow:**
1. Subscribe to `decide()` on the PDP (streaming).
2. Track a three-state machine: `INITIAL`, `PERMITTED`, `DENIED`.
3. On PERMIT (from INITIAL or DENIED):
   - Resolve constraint handlers for all obligations and advice. If any obligation is unhandled, treat as deny (go to step 4). Unhandled advice is silently ignored.
   - Execute on-decision constraint handlers (obligations and advice).
   - If from DENIED: emit access-recovered signal to subscriber.
   - Subscribe to source if first time.
4. On non-PERMIT (from INITIAL or PERMITTED):
   - Set state to DENIED, release constraint handler references.
   - Execute constraint handlers in best-effort mode for audit/logging (obligations and advice).
   - Emit access-denied signal to subscriber.
5. On non-PERMIT (from DENIED): no signal (already denied, avoid duplicate notifications).
6. On PERMIT (from PERMITTED with changed constraints): re-resolve constraint handlers (obligations and advice), no signal.

**REQ-ERROR-5 (EnforceRecoverableIfDenied):** If an on-next obligation handler fails while processing a data item, drop the single element and continue processing subsequent items. Do NOT transition to denied state. Advice handler failures are logged and absorbed (see Section 8.3).

**Teardown:** On termination, execute ON_CANCEL/ON_COMPLETE handlers, unsubscribe from the PDP decision stream and source data stream, and release all constraint handler references.

#### 7.5.1 Implementation Strategies for Access-State Signals

The access-state signal requirement (REQ-ACCESS-VISIBILITY-1) can be satisfied through platform-appropriate mechanisms:

- **Callbacks with restricted emitter** (e.g., NestJS): The application developer provides `onStreamDeny` and `onStreamRecover` handlers that receive a restricted emitter exposing only `next(value)`. The handler injects a synthetic event into the stream (e.g., `sink.next({ type: 'ACCESS_DENIED' })`).
- **Signal wrapper types** (e.g., Java): The PEP wraps each emission in an access-state envelope (e.g., `AccessStateEvent<T>` with `PERMITTED`/`DENIED` variants). The subscriber pattern-matches on the envelope type.
- **Dedicated protocol events**: For SSE or WebSocket streams, the PEP emits a framework-level event (e.g., SSE event type `access-state`) that the client can handle separately from data events.

The choice of mechanism is platform-dependent. All mechanisms MUST satisfy REQ-ACCESS-VISIBILITY-1.

### 7.6 Deny Handling

When access is denied, whether from a non-PERMIT decision, an unhandled obligation, or an obligation handler failure, the PEP applies a consistent deny procedure.

1. Resolve constraint handlers for the decision in best-effort mode: attempt to match handlers for obligations and advice, but do not deny on unhandled obligations.
2. Execute on-decision handlers (for audit/logging).
3. If an `onDeny` callback is configured: invoke it with the decision and request context. The return value becomes the response.
4. If no `onDeny` callback: throw/return a framework-appropriate "forbidden" error (HTTP 403).

**REQ-DENY-BESTEFF-1:** Best-effort handlers MUST be executed on deny paths. This ensures that audit and logging obligations fire even when access is denied. Audit obligations attached to a DENY decision are a legitimate use case.

**REQ-ERROR-1:** When access is denied (any cause), the default client-facing error MUST be a generic "access denied" with the framework-appropriate status code (HTTP 403 Forbidden).

**REQ-ERROR-2:** The error response MUST NOT reveal:
- Which policy denied access.
- Which obligation was unhandled.
- Which handler failed.
- The raw PDP decision (obligations, advice may contain policy internals).
- Internal error details.

**REQ-ERROR-3:** The `onDeny` callback, if provided by the application developer, MUST be accompanied by documentation warning against returning the raw decision object to the client.

**REQ-LOG-4:** Obligation handler failure messages MUST NOT reveal the specific handler implementation to the client. Log detailed errors server-side. Return generic "access denied" to the client.

### 7.7 Deferred Method Invocation

**REQ-STREAM-DEFER-1:** In all streaming enforcement modes, the protected method (which produces the source data stream) MUST NOT be invoked until the first PERMIT decision is received from the PDP.

Invoking the method before authorization is confirmed would execute potentially side-effectful code without authorization, produce data that might need to be buffered or discarded, and create a window where the source is producing data but the PEP has no resolved constraint handlers to apply.

The source subscription is created exactly once, on the first PERMIT. Subsequent PERMIT decisions with different constraints re-resolve the constraint handlers but do NOT recreate the source subscription.

### 7.8 Streaming Teardown

These invariants apply to all streaming enforcement modes (7.3, 7.4, 7.5).

**REQ-TEARDOWN-1:** When a streaming enforcement subscription is cancelled or completes, the PEP MUST:
1. Execute ON_CANCEL constraint handlers (from the currently resolved handlers).
2. Unsubscribe from the PDP decision stream (closing the SSE connection).
3. Unsubscribe from the source data stream.

**REQ-TEARDOWN-2:** The teardown sequence MUST be idempotent. Multiple calls to the teardown function MUST NOT cause errors or duplicate handler execution.

**REQ-TEARDOWN-3:** When transitioning from PERMIT to DENY in streaming modes, the PEP MUST release all constraint handler references to allow garbage collection. The `permitted` flag or `accessState` alone is not sufficient. The handler references must also be cleared.

---

## 8. Constraint Handling

Constraints are the mechanism through which the PDP communicates side effects to the PEP: "log this access", "redact these fields", "add this HTTP header". This section covers how constraints are routed to handlers, how handlers are composed, and how they execute.

### 8.1 Handler Types

A conforming PEP MUST support the following handler types:

| Type                 | Signature              | Lifecycle                                          | Purpose                                         |
|----------------------|------------------------|----------------------------------------------------|-------------------------------------------------|
| **Side-effect**      | `() -> void`           | Signal-based (ON_DECISION, ON_COMPLETE, ON_CANCEL) | Side effects at lifecycle points                |
| **Consumer**         | `(value) -> void`      | On each data element                               | Observe/log values                              |
| **Mapping**          | `(value) -> value`     | On each data element                               | Transform values (content filtering, redaction) |
| **ErrorHandler**     | `(error) -> void`      | On error                                           | Observe/log errors                              |
| **ErrorMapping**     | `(error) -> error`     | On error                                           | Transform errors (wrapping, redaction)          |
| **FilterPredicate**  | `(element) -> boolean` | On each data element                               | Filter array elements or reject scalar values   |
| **MethodInvocation** | `(context) -> void`    | Before method execution                            | Modify method arguments                         |

### 8.2 Registration and Discovery

**REQ-HANDLER-DISC-1:** The PEP MUST provide a mechanism for registering constraint handler providers. The recommended approach is framework-native dependency injection with decorator/annotation-based registration.

**REQ-HANDLER-DISC-2:** Each handler provider MUST implement an `isResponsible(constraint)` method that determines whether the provider can handle a given constraint. The recommended convention is that constraints are JSON objects with a `type` field, and providers match on this field.

**REQ-HANDLER-DISC-3:** Multiple providers MAY match the same constraint. All matching handlers are composed (see Section 8.4).

### 8.3 Obligation vs Advice Error Semantics

**REQ-OBLIGATION-1:** If an obligation handler throws an application-level exception, the PEP MUST deny access. The error MUST be logged, and a generic "access denied" error returned (no information leakage about the specific handler failure). Fatal platform errors (e.g., `OutOfMemoryError`) are not caught here. They are governed by REQ-ERROR-4.

**REQ-OBLIGATION-2:** If ANY obligation in the decision has no matching handler (unhandled obligation), the PEP MUST deny access. The unhandled obligations MUST be logged at ERROR level.

**REQ-OBLIGATION-3:** The PEP MUST NOT short-circuit obligation execution on the first failure. All obligation and advice handlers MUST be attempted. After all handlers have been executed, the PEP MUST deny if any obligation handler failed. This ensures that handlers for cross-cutting concerns (audit logging, cleanup) are still executed even when an earlier obligation fails.

**REQ-ADVICE-1:** If an advice handler throws/fails, the PEP MUST log a warning and continue processing. For mapping-type advice, the identity value (input unchanged) MUST be returned. For consumer/side-effect-type advice, the failure is silently absorbed.

**REQ-ADVICE-2:** Unhandled advice (no matching handler) MUST be silently ignored. No tracking or error is required.

### 8.4 Handler Composition

When multiple handlers match the same constraint, they MUST be composed as follows:

| Handler Type     | Composition Strategy                                                                                             |
|------------------|------------------------------------------------------------------------------------------------------------------|
| Side-effect      | Sequential execution: both handlers run                                                                          |
| Consumer         | Sequential execution: both called with same value                                                                |
| Mapping          | Pipeline composition: `result = handler2(handler1(value))`. Handlers MUST be sorted by priority (highest first). |
| FilterPredicate  | Logical AND: `result = handler1(value) && handler2(value)`                                                       |
| ErrorHandler     | Sequential execution: both called with same error                                                                |
| ErrorMapping     | Pipeline composition: `result = handler2(handler1(error))`. Sorted by priority.                                  |
| MethodInvocation | Sequential execution: both called with same invocation context                                                   |

### 8.5 Handler Lifecycle Signals

Side-effect handlers are parameterized by signal, indicating when they execute:

| Signal        | When                             | Available In          |
|---------------|----------------------------------|-----------------------|
| `ON_DECISION` | New decision received from PDP   | All enforcement modes |
| `ON_COMPLETE` | Source stream completes normally | Streaming modes only  |
| `ON_CANCEL`   | Subscriber cancels/unsubscribes  | Streaming modes only  |

**REQ-SIGNAL-1:** Non-streaming enforcement modes (PreEnforce, PostEnforce) MUST only process `ON_DECISION` side-effect handlers.

**REQ-SIGNAL-2:** Streaming enforcement modes MUST process `ON_DECISION`, `ON_COMPLETE`, and `ON_CANCEL` side-effect handlers.

**REQ-SIGNAL-3:** An obligation registered as a side-effect handler with signal `ON_COMPLETE` or `ON_CANCEL` in a streaming context MUST be counted as handled (removed from the unhandled set), even though its execution is deferred to the relevant lifecycle event.

### 8.6 Resource Replacement

**REQ-RESOURCE-1:** The `resource` field in the decision is an implicit obligation. When present (not undefined/absent), the PEP MUST substitute it for the actual method return value. The replacement happens BEFORE any mapping or consumer constraint handlers process the value. If the enforcement context does not support resource replacement (e.g., a void method or a context where the return value cannot be substituted), the PEP MUST deny access, just as it would for any unhandled obligation.

**Platform note (null resource in Reactive Streams):** In Java Reactive Streams, `null` cannot be emitted as a signal. When the resource field is `null` (JSON null) in a reactive context, the PEP MUST deny access because the implicit obligation cannot be fulfilled. In blocking contexts and in JavaScript/TypeScript environments (where `null` flows freely through return values and observables), `null` replacement works as specified.

**Platform note (void methods):** Whether void methods support resource replacement is platform-dependent. In frameworks where the return value always propagates to the caller (e.g., NestJS HTTP handlers where the return value becomes the response body), resource replacement on void-returning handlers is acceptable. In frameworks where void means "no return value" and the replacement cannot propagate, the PEP MUST deny access. Implementations SHOULD document their behavior.

**REQ-RESOURCE-2:** The PEP MUST distinguish "resource field absent/undefined" from "resource field is null" using a sentinel value or presence check. A `null` resource replacement is a valid PDP instruction meaning "replace the result with null."

**REQ-RESOURCE-3:** If the resource value cannot be deserialized to the expected return type, the PEP MUST deny access.

**Platform note:** Type validation of resource replacement is platform-dependent. In statically typed platforms (Java), the PEP deserializes the resource to the expected return type and denies on mismatch. In dynamically typed platforms (TypeScript/JavaScript), the replacement is applied as-is; type safety is the application developer's responsibility.

**REQ-RESOURCE-STREAM-1:** In streaming enforcement modes, resource replacement is part of the per-element constraint handler pipeline, not a stream-terminating event. The resource value is stored with the resolved constraint handlers. For each data element, resource replacement is applied (substituting the source element with the stored resource) before other per-element handlers (filter, consumer, mapping). When a new decision arrives, the constraint handlers are re-resolved with the new resource value (or without one). The stream does NOT terminate when a resource is present.

### 8.7 Content Filtering (Built-in Handler)

The PEP should provide a built-in content filtering constraint handler that supports field-level data transformation. Content filtering operates on a deep clone of the data. The original MUST NOT be mutated.

#### Action Types

| Action    | Parameters                                                                                  | Behavior                                |
|-----------|---------------------------------------------------------------------------------------------|-----------------------------------------|
| `blacken` | `path`, `replacement` (default: block character), `discloseLeft`, `discloseRight`, `length` | Mask characters in a string field       |
| `replace` | `path`, `replacement`                                                                       | Replace a field with an arbitrary value |
| `delete`  | `path`                                                                                      | Remove a field from the object          |

#### Path Syntax

The PEP MUST support simple dot-path syntax (e.g., `$.field.nested`). The PEP should reject unsupported JSONPath features (recursive descent, bracket notation, wildcards) with descriptive errors rather than silently producing incorrect results.

#### Security

**REQ-FILTER-SEC-1:** Path traversal MUST reject prototype-polluting segments (`__proto__`, `constructor`, `prototype`). This MUST be checked both at parse time and at traversal time (defense-in-depth).

**REQ-FILTER-SEC-2:** Regex patterns in filter conditions MUST be validated for catastrophic backtracking (ReDoS) before compilation. The PEP should use a safe-regex validation library.

**REQ-FILTER-SEC-3:** Content filtering MUST operate on a deep clone of the data. The original data MUST NOT be mutated.

### 8.8 Constraint Handler Resolution and Application

**REQ-HANDLER-RESOLVE-1:** For each constraint in the decision, the PEP MUST discover all registered handler providers that accept the constraint (e.g., via a responsibility check). If any obligation has no matching handler, the PEP MUST deny access. Unmatched advice MAY be silently ignored.

**REQ-HANDLER-RESOLVE-2:** On deny paths (access denied, indeterminate, not applicable), obligation matching MUST use best-effort semantics: unmatched obligations are ignored rather than causing a secondary denial.

**REQ-HANDLER-RESOLVE-3:** Handlers SHOULD be resolved, composed, and cached once per decision for repeated application to data items. Re-resolving handlers per data item is unnecessary overhead.

**REQ-HANDLER-ORDER-1:** When applying constraint handlers to a value, the PEP MUST follow this order:

1. Resource replacement (if present in decision): substitute the value with the decision's resource field.
2. Content filtering: apply filter predicates to collections or scalar values.
3. Consumer handlers: observe the (possibly filtered) value.
4. Mapping handlers: transform the value (in priority order).
5. Return the final value.

---

## 9. Concurrency and Thread Safety

### 9.1 Single-Threaded Environments (JavaScript, Python with GIL)

In single-threaded event-loop environments:
- No explicit synchronization is needed for shared mutable state (decision, constraint handler set, subscription references).
- The enforcement aspect's closure variables are safely accessed because event-loop callbacks execute atomically within a tick.
- However, the PEP MUST still ensure correct ordering: when a new decision arrives, the re-resolved constraint handlers MUST take effect before the next data item is processed.

### 9.2 Multi-Threaded Environments (Java, C#, Go, Rust)

**REQ-THREAD-1:** All mutable shared state in streaming enforcement aspects (current decision, current constraint handler set, subscription references, stopped flag) MUST be protected by atomic operations or equivalent concurrency primitives.

**REQ-THREAD-2:** Constraint handler transitions MUST be atomic. A data item MUST be processed entirely by either the previous or the new set of constraint handlers, never a mix.

**REQ-THREAD-3:** Streaming enforcement operators MUST enforce a single-subscription invariant: only one subscriber may be active at a time. Attempting to subscribe a second time MUST throw an error.

**REQ-THREAD-4:** Disposal/teardown MUST use a guard flag (atomic boolean) to prevent processing after the stream has been terminated. All handler entry points (next, error, complete) MUST check this flag first.

### 9.3 Reactive/Async Environments

**REQ-ASYNC-1:** The PEP MUST prevent downstream consumers from swallowing authorization errors. In reactive frameworks, this means applying `onErrorStop()` (Project Reactor) or equivalent to prevent `onErrorContinue` from bypassing the PEP.

**REQ-ASYNC-2:** The PEP MUST properly manage backpressure. If the downstream consumer cannot keep up, the PEP MUST NOT buffer unbounded data.

### 9.4 Fatal Error Propagation

**REQ-ERROR-4:** Fatal platform errors (out of memory, stack overflow) MUST be propagated immediately and MUST NOT be caught by obligation/advice error wrappers. The PEP MUST deny the current request before allowing the fatal error to propagate. No data may leak because the process is shutting down.

---

## 10. Framework Integration

Building a PEP that works is necessary. Building one that developers want to use requires meeting them where they are, with the patterns, conventions, and configuration mechanisms they already know.

### 10.1 Decorator/Annotation-Based Enforcement

**REQ-FRAMEWORK-1:** The PEP MUST provide declarative annotations/decorators for all five enforcement modes:
- `@PreEnforce(options?)`
- `@PostEnforce(options?)`
- `@EnforceTillDenied(options?)`
- `@EnforceDropWhileDenied(options?)`
- `@EnforceRecoverableIfDenied(options?)`

### 10.2 Subscription Building

The authorization subscription is the PEP's way of asking the PDP a question: "Can this subject perform this action on this resource in this environment?" The quality of this question determines the quality of the policies that answer it.

#### 10.2.1 Technical vs. Domain-Driven Subscriptions

A PEP that automatically derives subscription fields from code context produces **technical subscriptions**:

```json
{
  "subject": { "name": "alice", "authorities": ["ROLE_USER"] },
  "action": { "http": { "method": "GET", "path": "/api/patients/42" },
              "java": { "name": "findById", "declaringTypeName": "PatientRepository" } },
  "resource": { "http": { "path": "/api/patients/:id", "params": { "id": "42" } } }
}
```

These work out of the box and require zero configuration from the developer. But policies written against technical subscriptions are coupled to implementation details -- renaming a method, changing a URL path, or moving a controller breaks policies. They also leak implementation structure into the policy layer: a policy that matches on `action.java.name == "findById"` is meaningful to a developer but opaque to a policy administrator.

When the developer **manually overrides** subscription fields, the subscription becomes **domain-driven**:

```json
{
  "subject": "alice",
  "action": "view",
  "resource": "patient-record:42"
}
```

Policies written against domain-driven subscriptions are readable, stable across refactoring, and express business intent. A policy that says `permit subject == "alice" & action == "view" & resource =~ "patient-record:.*"` is meaningful to anyone who understands the domain.

This is not a binary choice. The PEP should support the full spectrum:

- **No configuration (80% case):** Sensible automatic defaults get developers started immediately. Technical subscriptions work for prototyping and simple applications where policies are maintained by the same team that writes the code.
- **Partial override:** Override one or two fields (typically `action` and `resource` with domain terms) while keeping automatic defaults for others (typically `subject` from the authentication context). This is the most common production pattern.
- **Full override:** All fields explicitly specified with domain-driven values. Used when policy readability and long-term stability across refactoring are priorities, or when policies are maintained by a separate team or policy administrator.

The implementation mechanism for overrides is framework-specific:

**Expression language (Java/Spring):** SpEL expressions in annotations, evaluated against the method invocation context. The expression context includes the Spring Security `Authentication` object, method arguments by name, the return value (PostEnforce only), and Spring Security functions like `hasRole()`:

```java
@PreEnforce(subject = "#authentication.name",
            action  = "'view'",
            resource = "'patient-record:' + #id")
Mono<Patient> findById(long id) { ... }
```

**Callbacks (TypeScript/NestJS):** Functions receiving a `SubscriptionContext` with the HTTP request, route parameters, method arguments, and the authenticated user:

```typescript
@PreEnforce({
  subject:  (ctx) => ctx.request.user?.username ?? 'anonymous',
  action:   'view',
  resource: (ctx) => `patient-record:${ctx.params.id}`,
})
async findById(@Param('id') id: string): Promise<Patient> { ... }
```

Other frameworks should use their native expression or callback mechanisms. The key requirement is that the developer can inject domain semantics at the subscription level without modifying PEP internals.

#### 10.2.2 Runtime Context for Subscription Building

The PEP gathers runtime context from multiple sources to populate subscription fields, whether automatically or via developer-specified expressions/callbacks. The available context depends on the framework and the enforcement mode:

| Context source         | Available in                    | Examples                                              |
|------------------------|---------------------------------|-------------------------------------------------------|
| Authenticated user     | All modes                       | User identity, roles, claims, JWT payload             |
| HTTP request           | All modes (if HTTP-triggered)   | Method, path, headers, query params, body, client IP  |
| Route parameters       | All modes (if HTTP-triggered)   | Path variables (`:id`, `:slug`)                       |
| Method metadata        | All modes                       | Method name, class name, parameter names, annotations |
| Method arguments       | All modes                       | Actual argument values at call time                   |
| Return value           | PostEnforce only                | The method's return value                             |
| Session / auth context | All modes (framework-dependent) | Session attributes, security context                  |

**REQ-CONTEXT-POSTEXEC-1:** In PostEnforce mode, the subscription MUST be built after the method executes, and the method's return value MUST be available as context for dynamic subscription fields. This is PostEnforce's distinguishing feature: the PDP can make decisions based on what the method actually returned.

#### 10.2.3 Secrets in Subscriptions

The `secrets` field enables the PDP to access credential material needed for policy evaluation (e.g., a JWT token that a PIP must forward to a downstream service). Because secrets are security-sensitive:

- **Never set by default.** Secrets require explicit opt-in by the developer.
- **Injection pattern:** The PEP may provide a framework-integrated secrets injector that automatically extracts credential material from the authentication context (e.g., the raw JWT from Spring Security's `JwtAuthenticationToken`). This injector is an optional extension point, not a default behavior.
- **Overridden by explicit value.** If the developer specifies `secrets` in the decorator/annotation, the injector is bypassed.

#### 10.2.4 Subscription Field Requirements

**REQ-SUB-1:** Each decorator MUST support overriding all five subscription fields (`subject`, `action`, `resource`, `environment`, `secrets`).

**REQ-SUB-2:** Subscription fields MUST support both static values (literal) and dynamic values. Dynamic values are framework-specific: expression languages (e.g., SpEL), callbacks receiving request context, or equivalent mechanisms that allow runtime resolution.

**REQ-SUB-3:** The PEP MUST provide sensible defaults for all subscription fields:

| Field         | Default                                                             |
|---------------|---------------------------------------------------------------------|
| `subject`     | Authenticated user identity, or `"anonymous"`                       |
| `action`      | Method name, HTTP method, controller name, or combination           |
| `resource`    | Route path, URL, route parameters                                   |
| `environment` | Client IP, hostname. MUST NOT include forgeable headers by default. |
| `secrets`     | Not set (omitted from subscription)                                 |

The automatic defaults are intentionally technical. This is the correct trade-off: zero-configuration adoption matters more than policy aesthetics for getting started. Developers who need domain-driven subscriptions override individual fields using the mechanisms described in Section 10.2.1.

### 10.3 Request Context Propagation

**REQ-CONTEXT-1:** The PEP MUST have access to the HTTP request context (or equivalent) at enforcement time. The mechanism is framework-specific:
- Thread-local / scoped values (Java)
- Continuation-local storage / AsyncLocalStorage (Node.js)
- HttpContext.Items (ASP.NET)
- Request-scoped dependency injection

**REQ-CONTEXT-2:** For streaming enforcement, the request context MUST be captured at subscription time (when the endpoint handler is invoked), not at decision-evaluation time or data-emission time.

### 10.4 Module/Service Registration

**REQ-MODULE-1:** The PEP MUST be configurable as a framework module/service with the following options:

| Option                     | Type        | Required | Default   | Description               |
|----------------------------|-------------|----------|-----------|---------------------------|
| `baseUrl`                  | string      | Yes      | -         | PDP server URL            |
| `token`                    | string      | No       | -         | Bearer token for PDP auth |
| `timeout`                  | number (ms) | No       | 5000      | HTTP request timeout      |
| `streamingMaxRetries`      | number      | No       | unlimited | Max reconnection attempts |
| `streamingRetryBaseDelay`  | number (ms) | No       | 1000      | Initial retry delay       |
| `streamingRetryMaxDelay`   | number (ms) | No       | 30000     | Maximum retry delay       |
| `allowInsecureConnections` | boolean     | No       | false     | Allow HTTP (not HTTPS)    |

---

## 11. Observability

A PEP that denies access without explanation is operationally useless. Logging and health exposure give operators the information they need to diagnose authorization failures without exposing sensitive data.

### 11.1 Required Log Events

| Event                       | Level                                    | Content                                           |
|-----------------------------|------------------------------------------|---------------------------------------------------|
| PDP configured              | INFO                                     | Base URL (NOT token)                              |
| HTTP connection to PDP      | INFO                                     | URL configured, no sensitive data                 |
| Insecure connection warning | WARN                                     | Clear message about HTTP risk                     |
| Decision received           | DEBUG                                    | Full decision (without secrets from subscription) |
| Subscription sent           | DEBUG                                    | Subscription WITHOUT secrets field                |
| Unhandled obligation        | ERROR                                    | The unhandled constraint objects                  |
| Obligation handler failure  | ERROR                                    | Constraint that failed, error message             |
| Advice handler failure      | WARN                                     | Constraint that failed, error message             |
| PDP communication error     | ERROR                                    | Error type, URL, status code if available         |
| Retry attempt               | WARN (escalate to ERROR after threshold) | Attempt number, delay                             |
| Buffer overflow             | ERROR                                    | Buffer size, limit                                |
| Response validation failure | WARN                                     | What was invalid (without full response)          |

### 11.2 Security Constraints on Logging

**REQ-LOG-3:** PDP error response bodies should be truncated in logs (RECOMMENDED: 500 characters) to prevent log flooding from verbose error responses.

Note: REQ-LOG-1 (secrets exclusion) and REQ-LOG-2 (token exclusion) are specified in Section 6.2 alongside the transport and authentication requirements they protect. REQ-LOG-4 (handler failure info leakage) is specified in Section 7.6 alongside deny handling.

---

## 12. Testing Requirements

Every REQ- requirement and every failure mode in the catalog (Section 13) MUST have at least one corresponding test. Test suites SHOULD be organized by operational concern (PDP communication, enforcement modes, constraint handling, concurrency, failure and recovery) rather than by implementation class.

---

## 13. Failure Mode Catalog

This section catalogs every failure mode a PEP implementation must handle, along with the required behavior.

| #   | Failure Mode                            | Cause                                   | Required Behavior                                                                              |
|-----|-----------------------------------------|-----------------------------------------|------------------------------------------------------------------------------------------------|
| F1  | PDP unreachable                         | Network failure, DNS failure, PDP down  | Return/emit INDETERMINATE. Retry with backoff (streaming).                                     |
| F2  | PDP timeout                             | Slow PDP, network congestion            | Abort request. Return INDETERMINATE.                                                           |
| F3  | PDP returns HTTP error                  | Server error, misconfiguration          | Log status + truncated body. Return INDETERMINATE.                                             |
| F4  | PDP returns malformed JSON              | PDP bug, proxy interference             | Log warning. Return INDETERMINATE.                                                             |
| F5  | PDP returns invalid decision field      | PDP bug, protocol mismatch              | Validate. Return INDETERMINATE.                                                                |
| F6  | PDP stream ends unexpectedly            | PDP restart, network drop               | Emit INDETERMINATE. Reconnect with backoff.                                                    |
| F7  | PDP stream buffer overflow              | Malicious/misconfigured PDP             | Emit INDETERMINATE. Abort. Reconnect.                                                          |
| F8  | Unhandled obligation                    | Missing handler registration            | Deny access. Log unhandled constraints at ERROR.                                               |
| F9  | Obligation handler throws (application) | Handler bug, transient failure          | Deny access. Log at ERROR. Continue accepting new requests.                                    |
| F10 | Advice handler throws                   | Handler bug, transient failure          | Log at WARN. Continue processing. Return identity value for mappings.                          |
| F11 | Resource replacement type mismatch      | Policy misconfiguration                 | Deny access. Log at ERROR.                                                                     |
| F12 | Content filter path traversal attack    | Malicious constraint                    | Reject path. Deny access.                                                                      |
| F13 | Content filter ReDoS pattern            | Malicious constraint                    | Reject pattern. Deny access (if obligation).                                                   |
| F14 | Authentication error to PDP             | Expired/invalid token                   | Return INDETERMINATE. Log at ERROR on every occurrence. Retry with backoff (see REQ-STREAM-5). |
| F15 | TLS handshake failure                   | Certificate mismatch, expiry            | Return INDETERMINATE. Log at ERROR.                                                            |
| F16 | Method throws during PreEnforce         | Application error                       | Pass through error constraint handlers. Re-throw.                                              |
| F17 | Method throws during PostEnforce        | Application error                       | Propagate directly (PDP not yet consulted).                                                    |
| F18 | Streaming on-next obligation failure    | Handler bug on specific data item       | TillDenied: terminate. Drop: drop item. Recoverable: drop item, continue.                      |
| F19 | onDeny callback throws                  | Application bug                         | Log at WARN. Fall through to default 403.                                                      |
| F20 | Access-state signal handler throws      | Application bug                         | Log at WARN. Continue with enforcement lifecycle.                                              |
| F21 | Handler resolution fails on PERMIT      | Unhandled obligation in PERMIT decision | Deny access. Best-effort handlers from deny path should still execute.                         |
| F22 | Fatal platform error during handler     | OutOfMemoryError, StackOverflowError    | Deny current request. Propagate error. Do not catch (REQ-ERROR-4).                             |

---

## 14. Implementation Requirements Index

This section provides a cross-reference of all implementation requirements by component. Security requirements are integrated into the component where they apply. Each requirement traces back to its normative section.

### 14.1 PDP Client

| ID     | Requirement                                               | Section |
|--------|-----------------------------------------------------------|---------|
| PDP-1  | HTTP client with configurable base URL, token, timeout    | 6.1     |
| PDP-2  | HTTPS enforcement with explicit opt-out                   | 6.2     |
| PDP-3  | Startup warning when insecure connections are enabled     | 6.2     |
| PDP-4  | URL validation at startup                                 | 6.2     |
| PDP-5  | Decide-once (request-response)                            | 6.3     |
| PDP-6  | Streaming decide (SSE parser)                             | 6.5     |
| PDP-7  | Response validation (validateDecision)                    | 5.3     |
| PDP-8  | Unknown field stripping on PDP responses                  | 5.3     |
| PDP-9  | Fail-closed on all error paths (return INDETERMINATE)     | 6.7     |
| PDP-10 | Secret exclusion from logs (structural, not filter-based) | 6.2     |
| PDP-11 | Token exclusion from logs                                 | 6.2     |
| PDP-12 | Retry with exponential backoff and jitter                 | 6.4     |
| PDP-13 | No retry on 401/403                                       | 6.4     |
| PDP-14 | Decision deduplication (deep equality)                    | 6.6     |
| PDP-15 | Depth limit on deep equality comparison                   | 6.6     |
| PDP-16 | Buffer overflow protection with size limit                | 6.4     |
| PDP-17 | Timeout handling with proper cleanup                      | 6.4     |

### 14.2 Constraint Engine

| ID     | Requirement                                                             | Section |
|--------|-------------------------------------------------------------------------|---------|
| CON-1  | Handler registration and discovery mechanism                            | 8.2     |
| CON-2  | Seven handler types                                                     | 8.1     |
| CON-3  | isResponsible routing                                                   | 8.2     |
| CON-4  | Obligation vs advice error semantics                                    | 8.3     |
| CON-5  | Unhandled obligation detection                                          | 8.3     |
| CON-6  | Handler composition (runBoth, mapBoth, filterBoth, etc.)                | 8.4     |
| CON-7  | Priority sorting for mapping handlers                                   | 8.4     |
| CON-8  | Signal-based side-effect handlers (ON_DECISION, ON_COMPLETE, ON_CANCEL) | 8.5     |
| CON-9  | Resource replacement with sentinel for "not present"                    | 8.6     |
| CON-10 | Best-effort handler resolution (for deny paths)                         | 7.6     |

### 14.3 Enforcement Aspects

| ID     | Requirement                                                 | Section |
|--------|-------------------------------------------------------------|---------|
| ENF-1  | PreEnforce (decide-once, pre-method)                        | 7.1     |
| ENF-2  | PostEnforce (decide-once, post-method)                      | 7.2     |
| ENF-3  | EnforceTillDenied (streaming, terminal)                     | 7.3     |
| ENF-4  | EnforceDropWhileDenied (streaming, silent drop)             | 7.4     |
| ENF-5  | EnforceRecoverableIfDenied (streaming, suspend/resume)      | 7.5     |
| ENF-6  | Deferred method invocation in all streaming modes           | 7.7     |
| ENF-7  | Restricted emitter for stream callbacks (next only)         | 7.5     |
| ENF-8  | Proper teardown (cancel handlers, unsubscribe both streams) | 7.8     |
| ENF-9  | Clear constraint handlers on deny transitions               | 7.8     |
| ENF-10 | Deny handling: best-effort resolution and generic 403       | 7.6     |
| ENF-11 | Error response sanitization (no policy internals)           | 7.6     |
| ENF-12 | Per-mode on-next obligation failure behavior                | 7.1-7.5 |
| ENF-13 | onErrorStop or equivalent (prevent error swallowing)        | 9.4     |

### 14.4 Framework Binding

| ID    | Requirement                                       | Section |
|-------|---------------------------------------------------|---------|
| FWK-1 | Decorator/annotation for each enforcement mode    | 10.1    |
| FWK-2 | Subscription field overrides (static and dynamic) | 10.2    |
| FWK-3 | Sensible subscription defaults                    | 10.2    |
| FWK-4 | Request context propagation                       | 10.3    |
| FWK-5 | Module/service configuration (sync and async)     | 10.4    |
| FWK-6 | Constraint handler auto-discovery                 | 8.2     |

### 14.5 Built-in Handlers

| ID    | Requirement                                               | Section |
|-------|-----------------------------------------------------------|---------|
| BLT-1 | Content filtering (blacken, replace, delete)              | 8.7     |
| BLT-2 | Simple dot-path JSONPath parser                           | 8.7     |
| BLT-3 | Prototype pollution protection (parse time and traversal) | 8.7     |
| BLT-4 | ReDoS-safe regex validation                               | 8.7     |
| BLT-5 | Deep clone before mutation                                | 8.7     |

### 14.6 Verification

| ID    | Requirement                             | Section |
|-------|-----------------------------------------|---------|
| VER-1 | All scenarios from testing requirements | 12      |
| VER-2 | All failure modes                       | 13      |
| VER-3 | Integration test with real PDP          | 12      |

---

## Appendix A: Reference Implementations

- **Java / Spring Security:** [heutelbeck/sapl-policy-engine/.../sapl-spring-boot-starter](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-spring-boot-starter)
- **TypeScript / NestJS:** [heutelbeck/sapl-nestjs](https://github.com/heutelbeck/sapl-nestjs)

### Key Differences Between Reference Implementations

| Aspect                      | Java/Spring                                                      | TypeScript/NestJS                                        |
|-----------------------------|------------------------------------------------------------------|----------------------------------------------------------|
| Reactive framework          | Project Reactor (Flux/Mono)                                      | RxJS (Observable)                                        |
| Thread safety               | AtomicReference, AtomicBoolean                                   | Single-threaded event loop (no synchronization needed)   |
| Handler types               | 10 (includes Subscription, Request, OnTerminate, AfterTerminate) | 7 (simplified: no reactive-specific signals)             |
| Deferred method invocation  | Yes (source subscribed on first PERMIT)                          | Yes (source only subscribed after first PERMIT)          |
| Access-state signals        | AccessRecoveredException + signalAccessRecovery callbacks        | Restricted StreamEventEmitter (next only) via callbacks  |
| Error swallowing prevention | `.onErrorStop()`                                                 | Not applicable (no `onErrorContinue` equivalent in RxJS) |
| Content filtering           | Java SAPL Value model                                            | Plain JSON with structuredClone                          |
| Response validation         | Validates via Jackson AuthorizationDecisionDeserializer          | Validates and strips unknown fields                      |
| HTTPS enforcement           | Depends on HTTP client config                                    | Built-in with explicit opt-out                           |

### Improvements in NestJS Implementation (Recommended for New Implementations)

1. **Deferred method invocation:** All new implementations MUST defer source subscription until first PERMIT. Both the Java and NestJS reference implementations now implement this pattern.
2. **Response validation:** All new implementations should validate PDP responses and strip unknown fields.
3. **HTTPS enforcement at startup:** All new implementations should validate transport security at construction time.
4. **Secret exclusion by construction:** All new implementations should use destructuring or equivalent to structurally prevent secrets from reaching log statements.

---

## References

Sorted by year of publication, then alphabetically by first author.

<a id="ref-1"></a>[1] Saltzer, J. H. and Schroeder, M. D. "[The Protection of Information in Computer Systems](https://ieeexplore.ieee.org/document/1451869)." *Proceedings of the IEEE*, 63(9):1278-1308, September 1975.

<a id="ref-2"></a>[2] Bradner, S. "[Key words for use in RFCs to Indicate Requirement Levels](https://www.rfc-editor.org/rfc/rfc2119)." RFC 2119, IETF, March 1997.

<a id="ref-3"></a>[3] Durham, D., Boyle, J., Cohen, R., Herzog, S., Rajan, R., and Sastry, A. "[The COPS (Common Open Policy Service) Protocol](https://www.rfc-editor.org/rfc/rfc2748)." RFC 2748, IETF, January 2000.

<a id="ref-4"></a>[4] Vollbrecht, J., Calhoun, P., Farrell, S., Gommans, L., Gross, G., de Bruijn, B., de Laat, C., Holdrege, M., and Spence, D. "[AAA Authorization Framework](https://www.rfc-editor.org/rfc/rfc2904)." RFC 2904, IETF, August 2000.

<a id="ref-5"></a>[5] Yavatkar, R., Pendarakis, D., and Guerin, R. "[A Framework for Policy-based Admission Control](https://www.rfc-editor.org/rfc/rfc2753)." RFC 2753, IETF, January 2000.

<a id="ref-6"></a>[6] OASIS. "[eXtensible Access Control Markup Language (XACML) Version 1.0](http://www.oasis-open.org/committees/download.php/2406/oasis-xacml-1.0.pdf)." OASIS Standard, February 2003.

<a id="ref-7"></a>[7] Walker, J. and Kulkarni, A. "[Common Open Policy Service (COPS) Over Transport Layer Security (TLS)](https://www.rfc-editor.org/rfc/rfc4261)." RFC 4261, IETF, December 2005.

<a id="ref-8"></a>[8] Chadwick, D. W. "[Obligation Standardization](https://www.w3.org/2009/policy-ws/papers/Chadwick.pdf)." Position paper, W3C Workshop on Access Control Application Scenarios, November 2009.

<a id="ref-9"></a>[9] OASIS. "[eXtensible Access Control Markup Language (XACML) Version 3.0](https://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html)." OASIS Standard, January 2013.

<a id="ref-10"></a>[10] Hu, V. C., Ferraiolo, D., Kuhn, R., Schnitzer, A., Sandlin, K., Miller, R., and Scarfone, K. "[Guide to Attribute Based Access Control (ABAC) Definition and Considerations](https://csrc.nist.gov/publications/detail/sp/800-162/final)." NIST Special Publication 800-162, January 2014.

<a id="ref-11"></a>[11] Heutelbeck, D. "[The Structure and Agency Policy Language (SAPL) for Attribute Stream-Based Access Control (ASBAC)](https://doi.org/10.1007/978-3-030-39749-4_4)." In *Emerging Technologies for Authorization and Authentication (ETAA 2019)*, Springer LNCS vol. 11967, 2019.

<a id="ref-12"></a>[12] Pang, R., Caceres, R., Burrows, M., Chen, Z., Dave, P., Gerber, N., Golynski, A., Graney, K., Kang, N., Kissner, L., Korn, J. L., Parmar, A., Richards, C. D., and Wang, M. "[Zanzibar: Google's Consistent, Global Authorization System](https://www.usenix.org/conference/atc19/presentation/pang)." In *Proceedings of the 2019 USENIX Annual Technical Conference (USENIX ATC '19)*, pages 33-46, 2019.

<a id="ref-13"></a>[13] Heutelbeck, D. "[Streamlining ABAC with SAPL - An Attribute-Stream-Based Policy Language](https://doi.org/10.1007/978-3-030-66504-3_1)." In Katsikas, S. et al. (eds.), *Computer Security*, Springer LNCS, 2020.

<a id="ref-14"></a>[14] Rose, S., Borchert, O., Mitchell, S., and Connelly, S. "[Zero Trust Architecture](https://csrc.nist.gov/publications/detail/sp/800-207/final)." NIST Special Publication 800-207, August 2020.

<a id="ref-15"></a>[15] OpenID Foundation. "[Continuous Access Evaluation Profile (CAEP)](https://openid.net/specs/openid-caep-specification-1_0.html)." OpenID Implementer's Draft, 2024.

<a id="ref-16"></a>[16] OpenID Foundation. "[Shared Signals Framework (SSF)](https://openid.net/specs/openid-sharedsignals-framework-1_0.html)." OpenID Implementer's Draft, 2024.

<a id="ref-17"></a>[17] OpenID Foundation. "[AuthZEN - Authorization API](https://openid.net/specs/authorization-api-1_0.html)." OpenID Implementer's Draft, 2024.

<a id="ref-18"></a>[18] El Kateb, D., ElRakaiby, Y., Mouelhi, T., Rubab, I., and Le Traon, Y. "[Towards a Full Support of Obligations in XACML](https://doi.org/10.1007/978-3-319-17127-2_14)." In *International Conference on Risks and Security of Internet and Systems (CRiSIS 2014)*, Springer LNCS vol. 8924, 2015.

<a id="ref-19"></a>[19] U.S. Cybersecurity and Infrastructure Security Agency (CISA), National Security Agency (NSA), Federal Bureau of Investigation (FBI), et al. "[Shifting the Balance of Cybersecurity Risk: Principles and Approaches for Secure by Design Software](https://www.cisa.gov/sites/default/files/2023-10/Shifting-the-Balance-of-Cybersecurity-Risk-Principles-and-Approaches-for-Secure-by-Design-Software.pdf)." Joint Guidance, April 2023 (revised October 2023).
