---
layout: default
title: Publish/Subscribe Protocol
#permalink: /reference/publish-subscribe-protocol/
has_children: true
parent: SAPL Reference
nav_order: 3
has_toc: false
---

## Publish / Subscribe Protocol

SAPL's authorization protocol operates in two modes: **streaming** and **one-shot**.

In **streaming mode**, the PEP subscribes to a decision stream by sending an authorization subscription to the PDP. The PDP evaluates the subscription against all applicable policies, returns an initial authorization decision, and then keeps the subscription open. Whenever policies change, external attributes update, or environment conditions shift, the PDP automatically pushes a new decision to the PEP. The PEP does not need to re-request authorization; updates arrive as they happen.

In **one-shot mode**, the PEP sends the same authorization subscription but receives a single decision and the connection closes. This is the traditional request-response pattern, suitable for use cases where continuous updates are not needed.

Both subscription and decision are JSON objects with predefined field names. A PEP must be able to create an authorization subscription and process an authorization decision. The following sections define the format of each.

### Choosing Between Modes

Streaming mode is the natural choice when authorization decisions depend on conditions that change over time: shift schedules, time-based access windows, live attribute feeds from external systems, or policies that are updated while sessions are active. The PDP handles all change detection and only sends updates when the decision actually changes.

One-shot mode is appropriate for simple request-response authorization where a single decision is sufficient: API gateway checks, batch processing, or any scenario where the operation completes before conditions are likely to change. The PDP evaluates the subscription once, returns the decision, and releases all resources.

The same policies serve both modes. The PDP does not require separate policies for streaming and one-shot evaluation.
