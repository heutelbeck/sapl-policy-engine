---
layout: default
title: Authorization Subscription
#permalink: /reference/Authorization-Subscription/
parent: Authorization Subscription Evaluation
grand_parent: SAPL Reference
nav_order: 4
---

## Authorization Subscription

The value, which is assigned to the authorization subscription, i.e., the final authorization decision to be returned by the PDP, is the result of applying a combining algorithm to the values assigned to all top-level SAPL documents.

Finally, in case the decision is `PERMIT`, and there is a `transform` statement, the transformed resource is added to the authorization decision. Additionally, there might be an obligation and advice contained in the policies which have to be added to the authorization decision.