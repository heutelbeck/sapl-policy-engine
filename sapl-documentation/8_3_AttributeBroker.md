---
layout: default
title: Attribute Broker
parent: Attribute Finders
grand_parent: SAPL Reference
nav_order: 240
---

## Attribute Broker

This section will document the internal attribute broker that mediates between policies and PIPs.

### For Policy Authors

- **Stream sharing:** Identical attribute accesses share one PIP connection, reducing load on external systems. The `fresh` option bypasses this cache when a policy needs an independent stream.
- **The resilience pipeline:** Every attribute lookup is automatically wrapped in a pipeline: timeout, retry with exponential backoff, poll on stream completion, and error-to-undefined conversion. Each option (`initialTimeOutMs`, `retries`, `backoffMs`, `pollIntervalMs`) controls one stage.

### For PDP Operators

- **Global option defaults:** Configure default attribute finder options in `pdp.json` under `variables.attributeFinderOptions` to tune stream behavior across all policies without modifying them.
- **Grace period:** After the last subscriber disconnects, the broker keeps PIP connections alive for 3 seconds. This prevents unnecessary reconnections when policies are rapidly re-evaluated.
- **Hot-swapping:** PIPs can be loaded and unloaded at runtime. Active streams automatically reconnect to newly loaded PIPs.

### For PIP Developers

- **Resolution priority:** When the broker receives an attribute request, it resolves the PIP using: exact parameter match, then varargs match, then repository fallback, then error.
- **Automatic resilience wrapping:** PIP authors implement only the data-fetching logic. The broker automatically wraps PIP streams with the timeout/retry/polling pipeline.
- **Collision detection:** If multiple PIPs register the same fully qualified attribute name, the broker detects the collision at load time and logs a warning.

> **Planned content.** This page will be expanded with implementation details, configuration examples, and diagrams.
