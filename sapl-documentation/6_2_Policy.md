---
layout: default
title: Policy
#permalink: /reference/Policy/
parent: Authorization Subscription Evaluation
grand_parent: SAPL Reference
nav_order: 2
---

## Policy

Evaluating a policy against an authorization subscription means assigning a value of `NOT_APPLICABLE`, `INDETERMINATE`, `PERMIT`, or `DENY` to it. The body conditions are evaluated as a conjunction (all must be true). The assigned value depends on the result:

| **Body Conditions**        | **Policy Value**                              |
|:---------------------------|:----------------------------------------------|
| All evaluate to `true`     | Policy's **Entitlement** (`PERMIT` or `DENY`) |
| Any evaluates to `false`   | `NOT_APPLICABLE`                              |
| Any produces an error      | `INDETERMINATE`                               |
| No body present            | Policy's **Entitlement** (`PERMIT` or `DENY`) |

Conditions are evaluated lazily: if an earlier condition evaluates to `false`, later conditions are not evaluated and cannot produce errors.
