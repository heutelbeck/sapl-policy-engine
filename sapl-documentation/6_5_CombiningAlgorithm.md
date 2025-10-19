---
layout: default
title: Combining Algorithm
#permalink: /reference/Combining-Algorithm/
parent: Authorization Subscription Evaluation
grand_parent: SAPL Reference
nav_order: 5
---

## Combining Algorithm

There are two layers with possibly multiple decisions that finally need to be consolidated into a single decision:

- A policy set might contain multiple policies evaluating to different decisions. There must be a final decision for the policy set (*Policy Combination*).
- The PDP might know multiple policy sets and policies which may evaluate to different decisions. In the end, the PDP must include a final decision in the SAPL authorization decision (*Document Combination*).

A combining algorithm describes how to come to the final decision. Both the PDP itself and each policy set must be configured with a combining algorithm.

Some complexity is added to the algorithms if transformation statements in policies are used: There is no possibility to combine multiple transformation statements. Hence, the combining algorithms have to deal with the situation that multiple policies evaluate to `PERMIT`, and at least one of them contains a transformation part. In case of such *transformation uncertainty*, the decision must not be `PERMIT`.

SAPL provides the following combining algorithms:

- `deny-unless-permit`
- `permit-unless-deny`
- `only-one-applicable`
- `deny-overrides`
- `permit-overrides`
- `first-applicable` (not allowed on PDP level for document combination)

The algorithms work similarly on the PDP and on the policy set level. Thus, the following section describes their function in general, using the term *policy document* for a policy and a policy set. If the algorithm is used on the PDP level, a *policy document* could be either a (top-level) policy or a policy set. On the policy set level, a *policy document* is always a policy.

### `deny-unless-permit`

This strict algorithm is used if the decision should be `DENY` except for there is a `PERMIT`. It ensures that any decision is either `DENY` or `PERMIT`.

It works as follows:

1. If any policy document evaluates to `PERMIT` and there is no *transformation uncertainty* (multiple policies evaluate to `PERMIT` and at least one of them has a transformation statement), the decision is `PERMIT`.
2. Otherwise, the decision is `DENY`.

### `permit-unless-deny`

This generous algorithm is used if the decision should be `PERMIT` except for there is a `DENY`. It ensures that any decision is either `DENY` or `PERMIT`.

It works as follows:

1. If any policy document evaluates to `DENY` or if there is a *transformation uncertainty* (multiple policies evaluate to `PERMIT` and at least one of them has a transformation statement), the decision is `DENY`.
2. Otherwise, the decision is `PERMIT`.

### `only-one-applicable`

This algorithm is used if policy sets, and policies are constructed in a way that multiple policy documents with a matching target are considered an error. A `PERMIT` or `DENY` decision will only be returned if there is exactly one policy set or policy with matching target expression and if this policy document evaluates to `PERMIT` or `DENY`.

It works as follows:

1. If any target evaluation results in an error (`INDETERMINATE`) or if more than one policy documents have a matching target, the decision is `INDETERMINATE`.
2. Otherwise (i.e., only one policy document with matching target, no errors):
   1. If there is no matching policy document, the decision is `NOT_APPLICABLE`.
   2. Otherwise (i.e., there is exactly one matching policy document), the decision is the result of evaluating this policy document.

> Transformation uncertainty cannot occur using the `only-one-applicable` combining algorithm.

### `deny-overrides`

This algorithm is used if a `DENY` decision should prevail a `PERMIT` without setting a default decision.

It works as follows:

1. If any policy document evaluates to `DENY`, the decision is `DENY`.
2. Otherwise (no policy document evaluates to `DENY`):
   1. If there is any `INDETERMINATE` or there is a *transformation uncertainty* (multiple policies evaluate to `PERMIT`, and at least one of them has a transformation statement), the decision is `INDETERMINATE`.
   2. Otherwise (no policy document evaluates to `DENY`, no policy document evaluates to `INDETERMINATE`, no transform uncertainty):
      1. If there is at least one `PERMIT`, the decision is `PERMIT`.
      2. Otherwise, the decision is `NOT_APPLICABLE`.

### `permit-overrides`

This algorithm is used if a `PERMIT` decision should prevail any `DENY` without setting a default decision.

It works as follows:

1. If any policy document evaluates to `PERMIT` and there is no *transformation uncertainty* (multiple policies evaluate to `PERMIT` and at least one of them has a transformation statement), the decision is `PERMIT`.
2. Otherwise (no policy document evaluates to `PERMIT`):
   1. If there is any `INDETERMINATE` or there is a *transformation uncertainty* (multiple policies evaluate to `PERMIT`, and at least one of them has a transformation statement), the decision is `INDETERMINATE`.
   2. Otherwise (no policy document evaluates to `PERMIT`, no policy document evaluates to `INDETERMINATE`, no transform uncertainty):
      1. If there is any `DENY`, the decision is `DENY`.
      2. Otherwise, the decision is `NOT_APPLICABLE`.

### `first-applicable`

This algorithm is used if the policy administrator manages the policy’s priority by their order in a policy set. As soon as the first policy returns `PERMIT`, `DENY`, or `INDETERMINATE`, its result is the final decision. Thus, a "default" can be specified by creating a last policy without any conditions. If a decision is found, errors that might occur in later policies are ignored.

Since there is no order in the policy documents known to the PDP, the PDP cannot be configured with this algorithm. `first-applicable` might only be used for policy combination inside a policy set.

It works as follows:

1. Each policy is evaluated in the order specified in the policy set.
   1. If it evaluates to `INDETERMINATE`, the decision is `INDETERMINATE`.
   2. If it evaluates to `PERMIT` or `DENY`, the decision is `PERMIT` or `DENY`
   3. If it evaluates to `NOT_APPLICABLE`, the next policy is evaluated.
2. If no policy with a decision different from `NOT_APPLICABLE` has been found, the decision of the policy set is `NOT_APPLICABLE`.
