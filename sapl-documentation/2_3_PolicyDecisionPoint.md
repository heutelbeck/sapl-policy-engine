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

SAPL provides two simple PDP implementations: An **embedded PDP** with an embedded PRP which can be integrated easily into a Java application, and a **remote PDP client** that obtains decisions through a RESTful interface.