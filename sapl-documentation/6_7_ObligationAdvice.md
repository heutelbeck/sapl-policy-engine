---
layout: default
title: Obligation / Advice
#permalink: /reference/Obligation-Advice/
parent: Authorization Subscription Evaluation
grand_parent: SAPL Reference
nav_order: 406
---

## Obligation / Advice

Finally, obligation and advice might be added to the authorization decision. Both can be defined for each policy individually. If a final decision is `PERMIT`, there can be multiple policies and policy sets evaluating to `PERMIT`, each of them containing an obligation and/or advice statement - same goes for `DENY`. The final authorization decision with a certain decision must contain all obligations and advice of policy documents evaluating to this decision, but not the obligation and advice of those policy documents evaluating to a different decision.

On the two levels (PDP and policy set), collection of obligation and advice works as follows:

- **Policy Set**: If the policy set evaluates to a certain decision (`PERMIT` or `DENY`), the obligation and advice from all contained policies evaluating to this decision are bundled as the obligation and advice of the policy set.

  (For combining algorithms using the `first` voting mode, not all policies might be evaluated. A value `PERMIT` or `DENY` is only assigned to evaluated policies. Thus, the policy set’s obligation and advice do only contain obligations and advice from evaluated policies.)
- **PDP**: If the final decision is `PERMIT` or `DENY`, the obligation and advice from all top-level policy documents evaluating to this final decision are collected as the final decision’s obligation and advice.