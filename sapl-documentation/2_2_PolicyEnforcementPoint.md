---
layout: default
title: PEP
#permalink: /reference/pep/
parent: Reference Architecture
grand_parent: SAPL Reference
nav_order: 2
---

## Policy Enforcement Point (PEP)

The PEP is a software entity that intercepts actions taken by users within an application. Its task is to obtain a decision on whether the requested action should be allowed and accordingly either let the application process the action or deny access. For this purpose, the PEP includes data describing the subscription context (like the subject, the resource, the action, and other environment information) in an authorization subscription object which the PEP hands over to a PDP. The PEP subsequently receives an authorization decision object containing a decision and optionally a resource, obligations, and advice.

The PEP must let the application process the action if the decision is `PERMIT`. If the authorization decision object also contains `obligations`, the PEP must fulfill these obligations. Proper fulfillment is an additional requirement for granting access. If the decision is not `PERMIT` or the obligation cannot be fulfilled, the PEP must deny access. Policies may contain instructions to alter the resource (like blackening certain information, e.g., credit card numbers). If present, the PEP should ensure that the application only reveals the resource contained in the authorization decision object.

### Streaming and One-Shot PEPs

PEPs can operate in either mode described in [Publish/Subscribe Protocol](../3_1_PublishSubscribeProtocol/):

- A **streaming PEP** subscribes to decision updates and continuously enforces the latest decision. When the PDP pushes a new decision (e.g., revoking access because a time window closed), the PEP reacts immediately. This is used for IoT data streams, long-lived sessions, WebSocket connections, or UI components that need to reflect access changes in real time.
- A **one-shot PEP** requests a single decision and acts on it. This is the typical pattern for REST API endpoints, batch operations, or any short-lived request where a single authorization check is sufficient.

### Framework Integration

A PEP strongly depends on the application domain. SAPL ships with modules for deep integration with Spring Security and Spring Boot, providing annotation-based enforcement (`@PreEnforce`, `@PostEnforce`, `@EnforceTillDenied`), a constraint handler service for processing obligations and advice, and filter chain integration for HTTP request authorization. Developers working with other frameworks or languages can implement PEPs using the [HTTP API](../4_2_HTTPServer-SentEventsAPI/) or the [Java API](../4_3_JavaAPI/).
