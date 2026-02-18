---
layout: default
title: Transformation
#permalink: /reference/Transformation/
parent: Authorization Subscription Evaluation
grand_parent: SAPL Reference
nav_order: 405
---

## Transformation

A policy with an entitlement `permit` can contain a transformation statement. If the decision is `PERMIT` and there is a policy evaluating to `PERMIT` with a transformation, the result of evaluating the expression after the keyword `transform` is returned as the `resource` in the authorization decision.

When multiple policies vote `PERMIT` and more than one includes a transformation, the combining algorithm encounters *transformation uncertainty*. Since there is no way to merge two different transformed resources, the algorithm cannot return `PERMIT`. Depending on the error handling setting, the result is either `DENY` (with `errors abstain`) or `INDETERMINATE` (with `errors propagate`). See [Combining Algorithm](../6_5_CombiningAlgorithm/) for details.

Consequently, the final authorization decision contains either exactly one transformation or none.