---
layout: default
title: Evaluation Semantics
parent: The SAPL Policy Language
grand_parent: SAPL Reference
nav_order: 109
---

## Evaluation Semantics

This section defines how the PDP evaluates policies and policy sets against an authorization subscription.

### Policy Evaluation

Evaluating a policy against an authorization subscription means assigning a value of `NOT_APPLICABLE`, `INDETERMINATE`, `PERMIT`, or `DENY` to it. The body conditions are evaluated as a conjunction (all must be true). The assigned value depends on the result:

| **Body Conditions**        | **Policy Value**                              |
|:---------------------------|:----------------------------------------------|
| All evaluate to `true`     | Policy's **Entitlement** (`PERMIT` or `DENY`) |
| Any evaluates to `false`   | `NOT_APPLICABLE`                              |
| Any produces an error      | `INDETERMINATE`                               |
| No body present            | Policy's **Entitlement** (`PERMIT` or `DENY`) |

Conditions are evaluated lazily: if an earlier condition evaluates to `false`, later conditions are not evaluated and cannot produce errors.

For policy syntax and structure, see [Policies](../2_3_PolicyStructure/).

### Policy Set Evaluation

A decision value (`NOT_APPLICABLE`, `INDETERMINATE`, `PERMIT` or `DENY`) can also be assigned to a policy set. This value depends on the result of evaluating the policy set's target expression and the policies contained in the policy set:

| **Target Expression**  | **Policy Values** | **Policy Set Value**                                          |
|:-----------------------|:------------------|:--------------------------------------------------------------|
| `false` (not matching) | don't care        | `NOT_APPLICABLE`                                              |
| `true` (matching)      | care              | Result of the **Combining Algorithm** applied to the Policies |
| *Error*                | don't care        | `INDETERMINATE`                                               |

For policy set syntax and structure, see [Policy Sets](../2_5_PolicySets/). For how combining algorithms resolve multiple votes into a single decision, see [Combining Algorithms](../2_4_CombiningAlgorithms/).
