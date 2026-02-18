---
layout: default
title: Authorization Subscription Evaluation
##permalink: /reference/Authorization-Subscription-Evaluation/
has_children: true
parent: SAPL Reference
nav_order: 400
has_toc: false
---

## Authorization Subscription Evaluation

For any authorization subscription, the PDP evaluates each top-level SAPL document against the subscription and combines the decisions. If a top-level document is a policy set, it contains multiple policies which have to be evaluated first. Their decisions are combined to form an evaluation decision for the policy set. Finally, a resource might be added to the final result, as well as obligations and advice.

The underlying concept assumes that during evaluation, a decision is assigned to each document. This process will be explained in the following sections.