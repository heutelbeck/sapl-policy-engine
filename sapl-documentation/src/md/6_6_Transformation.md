---
layout: default
title: Transformation
#permalink: /reference/Transformation/
parent: Authorization Subscription Evaluation
grand_parent: SAPL Reference
nav_order: 6
---

## Transformation

A policy with an entitlement `permit` can contain a transformation statement. If the decision is `PERMIT` and there is a policy evaluating to `PERMIT` with transformation, the result of evaluating the expression after the keyword `transform` is returned as the `resource` in the authorization decision.

The combining algorithms ensure that transformation is always unambiguous. Consequently, there either is exactly one transformation or none.