---
layout: default
title: Policy Set
#permalink: /reference/Policy-Set/
parent: Authorization Subscription Evaluation
grand_parent: SAPL Reference
nav_order: 3
---

## Policy Set

A decision value (`NOT_APPLICABLE`, `INDETERMINATE`, `PERMIT` or `DENY`) can also be assigned to a policy set. This value depends on the result of evaluating the policy set’s target expression and the policies contained in the policy set:

| **Target Expression** | **Policy Values** | **Policy Set Value** | 
|:------------------|:--------------|:-----------------|
| `false` (not matching) | don’t care | `NOT_APPLICABLE` |  
| `true` (matching) | don’t care | Result of the **Combining Algorithm** applied to the Policies |  
| *Error* | don’t care | `INDETERMINATE` | 
