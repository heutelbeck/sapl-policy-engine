---
layout: default
title: Policy Evaluation
#permalink: /reference/Policy-Evaluation/
parent: Publish/Subscribe Protocol
grand_parent: SAPL Reference
nav_order: 4
---

## Policy Evaluation

To come to the final decision included in the authorization decision object, the PDP evaluates all existing policy sets and top-level policies (i.e., policies which are not part of a policy set) against the authorization subscription and combines the results. Each policy set and policy evaluates to `PERMIT`, `DENY`, `NOT_APPLICABLE`, or `INDETERMINATE` (see [below](#evaluation)). The PDP can be configured with a **combining algorithm** which determines how to deal with multiple results. E.g., if access should only be granted if at least one policy evaluates to `PERMIT` and should be denied. Otherwise, the algorithm `deny-unless-permit` could be used.

Available combining algorithms for the PDP are:

- `deny-unless-permit`
- `permit-unless-deny`
- `only-one-applicable`
- `deny-overrides`
- `permit-overrides`

The algorithm `first-applicable` is not available for the PDP since the PDP’s collection of policy sets and policies is an unordered set.

The combining algorithms are described in more detail [later](#combining-algorithms).