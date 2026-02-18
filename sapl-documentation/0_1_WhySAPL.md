---
layout: default
title: Why SAPL?
parent: SAPL Reference
nav_order: 1
---

## Why SAPL?

SAPL is a policy language and authorization engine for Attribute Stream-Based Access Control (ASBAC). It provides a concise, purpose-built syntax for writing authorization policies, a managed infrastructure for integrating external data sources, and a reactive architecture that supports both continuous streaming and one-shot request-response authorization - with zero overhead when streaming is not used.

### From ABAC to ASBAC

Attribute-Based Access Control (ABAC) is the established model for fine-grained authorization: decisions are based on attributes of the subject, action, resource, and environment. Every authorization engine in the current landscape implements some form of ABAC.

In traditional ABAC, the Policy Decision Point (PDP) evaluates a request against policies, consults external data sources if needed, and returns a decision. This works well when authorization is a gate - a single check at the start of an operation.

But many real-world scenarios require authorization that persists beyond a single check. A doctor's shift ends while they are viewing a patient record. A device's safety certification expires during an active control session. A user's clearance is revoked while they are connected to a classified data stream. In these cases, the authorization decision must change when conditions change - without the application polling for updates or missing the transition entirely.

**Attribute Stream-Based Access Control (ASBAC)** extends ABAC by treating attributes as streams rather than snapshots. When a policy accesses an external attribute, the engine subscribes to a live data stream. If the underlying data changes - a clock crosses a shift boundary, a certificate expires, a permission is revoked - the engine re-evaluates the policy and pushes an updated decision to the application automatically.

ASBAC is a strict superset of ABAC. Every request-response authorization decision is also valid in a streaming model. When policies do not access streaming attributes, SAPL's stratified policy compilation produces a fully synchronous evaluation path with no reactive overhead. Applications that use SAPL purely for request-response authorization do not pay for streaming capabilities they are not using.

### Why a New Language and Engine

Streaming attributes are not a feature that can be added to an existing request-response authorization engine. They change the system architecture from the ground up.

**The language operates on streams, not just data.** In SAPL, adding angle brackets to an expression turns it into a stream subscription. Writing `<time.now>` in a policy does not fetch the current time once - it subscribes to a time stream that emits new values as time passes. The policy author writes what looks like a simple expression; the engine manages the ongoing subscription, re-evaluates the policy when new values arrive, and pushes updated decisions to the application. Continuous monitoring comes from the language semantics, not from infrastructure the application has to build.

**The evaluation model is fundamentally different.** A request-response-only engine evaluates a policy once and returns. A streaming engine maintains live evaluation contexts. When any subscribed attribute stream emits a new value, the engine re-evaluates the affected expressions and determines whether the decision has changed. This requires reactive expression evaluation, dependency tracking between expressions and their attribute sources, and an efficient mechanism for propagating changes through the policy without re-evaluating everything from scratch.

**The engine must manage stream lifecycles.** When two policies access the same attribute, the engine shares a single connection to the data source rather than creating two. When a data source disconnects, the engine retries with exponential backoff. When no policy is currently using an attribute, the engine keeps the connection alive briefly in case a new subscription arrives. When a PIP plugin is loaded or unloaded at runtime, active streams reconnect automatically. When policies are added, removed, or modified, the engine re-evaluates all active subscriptions against the new policy set without interrupting them.

**Policies must be able to parameterize and compose attribute access.** A streaming attribute finder is more than a named data source - it is a parameterized, composable expression. Policies need to pass runtime values to data sources, control stream behavior (timeouts, retry policies, caching), and chain one attribute lookup into the parameters of another:

```sapl
subject.employeeId.<schedules.shifts(time.dayOfWeek(<time.now>))>
```

This expression passes the subject's employee ID to a scheduling data source, with a parameter derived from a time stream. Both the scheduling data and the time are live streams - when the day changes, the shift lookup updates, and the policy re-evaluates. The policy author writes a single line; the engine manages the stream composition, lifecycle, and resilience.

**The PDP-PEP contract is different.** In request-response authorization, the application asks a question and gets an answer. In ASBAC, the application subscribes and receives a stream of decisions. The subscription is the stable contract. Policies change, attribute sources reconnect, configuration updates, static data reloads - the PDP absorbs every kind of change internally and expresses the net effect as decision updates on the existing subscription. The PEP never has to reconnect, re-query, or even know what changed.

### What SAPL Provides

- **A purpose-built policy language** with subject, action, resource, and environment as first-class concepts. Angle brackets (`<...>`) denote attribute stream access, making the distinction between local data and external streams visible in the policy syntax.
- **Parameterized attribute finders** that accept runtime arguments, entity context, and composed expressions - not just static attribute identifiers. PIPs can be overloaded by parameter count, and policies control per-access stream behavior (timeouts, retries, caching) via options.
- **Obligations and advice** as first-class policy constructs. Obligations are structured instructions that the application must enforce before granting access - even on PERMIT. Advice is optional. Both travel with the authorization decision and are declarable in the policy language itself.
- **Streaming and request-response** in the same engine, the same policies, and the same deployment. The evaluation strategy is determined automatically by the policies loaded into the PDP.
- **A testing DSL** (SAPLTest) with declarative PIP and function mocking, streaming assertions, domain-aware matchers for decisions, obligations, advice, and resource transformations.
- **Hot-reloadable policies** that take effect without restarting the PDP. Active subscriptions re-evaluate against the new policy set automatically.
- **Open source** with no proprietary dependencies or licensing barriers.

SAPL's authorization architecture follows the component model defined in RFC 2904: Policy Enforcement Points (PEP), Policy Decision Points (PDP), Policy Information Points (PIP), and Policy Administration Points (PAP). Concepts like obligations and advice also have deep roots in authorization standards. SAPL inherits these proven architectural ideas and redesigns the policy language, evaluation engine, and data integration model for a streaming world.

SAPL is used in production across European research and industry projects, including industrial energy market systems and eHealth applications managing access to highly sensitive participant data in multi-centre clinical studies.

### Choosing an Authorization Engine

Different authorization engines reflect different design priorities. A brief, non-exhaustive overview:

**OPA** (Open Policy Agent) provides Rego, a query language for policy evaluation, with broad integration across the cloud-native ecosystem including Kubernetes, Envoy, and Terraform. It evaluates policies in a request-response model. External data is loaded via bundles or fetched during evaluation.

**Cedar** (AWS) provides a purpose-built authorization language with formal verification tooling that can mathematically prove properties about policy sets (e.g., "no policy can ever grant access to resource X"). Requiring all data upfront in the request or an entity store is what makes this verification possible - the trade-off is that policies cannot access external data sources during evaluation.

**XACML** (OASIS) established the foundational architecture for attribute-based access control: PEP, PDP, PIP, PAP, obligations, and advice. SAPL inherits these architectural concepts. XACML uses XML-based policy syntax and a request-response evaluation model.

**SAPL** provides a purpose-built authorization language with parameterized external data integration, streaming and request-response evaluation, first-class obligations and advice, and a dedicated policy testing language.

All of these engines implement attribute-based access control. The meaningful differences are in how they integrate external data, whether they support streaming evaluation, what they include in the authorization decision beyond permit/deny, and how they approach policy testing and verification.

To see the language in action, continue to the [Introduction](../1_1_Introduction/).
