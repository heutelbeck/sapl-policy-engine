---
layout: default
title: PDP
#permalink: /reference/pdp/
parent: Reference Architecture
grand_parent: SAPL Reference
nav_order: 3
---

## Policy Decision Point (PDP)

The PDP must make an authorization decision based on an authorization subscription object and the access policies it receives from a **Policy Retrieval Point (PRP)** connected to a policy store. Beginning with the authorization subscription object, the PDP fetches policy sets and policies matching the authorization subscription, evaluates them, and combines the results to create and return an authorization decision object. There may be multiple matching policies that might evaluate to different results. To resolve these conflicts, the administrator or developer using a PDP must select a **combining algorithm** (e.g., `priority permit or deny` stating that permit votes take priority, with deny as the default decision).

A policy may refer to attributes not included in the authorization subscription object. It will have to obtain them from an external **Policy Information Point (PIP)**. The PDP fetches those attributes while evaluating the policy. To be able to access external PIPs, developers can extend the PDP by adding custom attribute finders. Policies might also contain functions not included in the default SAPL implementation. Developers may add custom functions by implementing **Function Libraries**.

### Evaluation Strategy

The PDP exposes both streaming and one-shot evaluation:

- `decide()` returns a stream of decisions that updates whenever policies, attributes, or conditions change
- `decideOnce()` and `decideOnceBlocking()` return a single decision and complete

For one-shot evaluation, the PDP automatically selects the most efficient code path based on the policies currently loaded. When no policy uses external attribute accesses (PIP expressions), the entire evaluation runs synchronously with no reactive or asynchronous overhead. The engine only falls back to asynchronous processing when at least one applicable policy accesses external attributes. This optimization is transparent to callers.

### Deployment Options

SAPL provides three ways to deploy a PDP:

- **Embedded PDP**: Runs inside a Java (or any other JVM language) application with policies loaded from the classpath, a filesystem directory, or signed bundles. Suitable for single-instance applications or microservices where policies are deployed alongside the application.
- **SAPL Node**: A standalone, headless PDP server that exposes the PDP via an HTTP API. Supports filesystem directories, signed bundles, and remote bundle fetching. Designed for centralized policy management across multiple applications.
- **Remote PDP client**: A lightweight client library that connects to a SAPL Node (or any SAPL-compatible server) via HTTP. Applications use this when policies are managed centrally rather than embedded.
