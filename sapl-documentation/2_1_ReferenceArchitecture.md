---
layout: default
title: Reference Architecture
#permalink: Reference-Architecture
has_children: true
parent: SAPL Reference
nav_order: 50
has_toc: false
---

## Reference Architecture

> For a conceptual introduction to these components and their interactions, see [Introduction](../1_1_Introduction/).

The architecture of the SAPL policy engine follows the terminology defined by [RFC2904 "AAA Authorization Framework"](https://tools.ietf.org/html/rfc2904).

![SAPL_Architecture.svg](/docs/XXXSAPLVERSIONXXX/assets/sapl_reference_images/SAPL_Architecture.svg)

The core components are:

- **Policy Enforcement Point (PEP)**: Intercepts application actions and enforces authorization decisions. The PEP constructs an authorization subscription from the application context and sends it to the PDP.
- **Policy Decision Point (PDP)**: Evaluates authorization subscriptions against policies and returns decisions. The PDP fetches policies from a Policy Retrieval Point (PRP) and may consult external Policy Information Points (PIPs) for additional attributes.
- **Policy Information Point (PIP)**: Provides external attributes that policies need but are not included in the authorization subscription (e.g., user profiles, schedules, real-time conditions).
- **Policy Administration Point (PAP)**: Manages the policy store. Policies can be stored on the filesystem, in signed bundles, or fetched from a remote server.

### Evaluation Modes

SAPL supports two evaluation modes that share the same policies, the same PDP, and the same infrastructure:

**Streaming evaluation** maintains a live subscription between PEP and PDP. When policies change, external attributes update, or environment conditions shift, the PDP pushes updated decisions to the PEP automatically. This enables continuous authorization that adapts to changing conditions without the PEP re-requesting decisions.

**One-shot evaluation** follows a traditional request-response pattern. The PEP sends a subscription, receives a single decision, and the interaction completes. For policies that do not access external attributes (PIPs), the PDP evaluates the subscription on a fully synchronous code path with no reactive or asynchronous overhead. The engine only introduces asynchronous processing when the policies loaded into the PDP actually require it.

This means applications that use SAPL purely for request-response authorization do not pay for streaming capabilities they are not using. The same engine handles both modes, and the evaluation strategy is determined automatically by the policies loaded into the PDP.
