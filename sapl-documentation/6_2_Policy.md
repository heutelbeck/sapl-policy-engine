---
layout: default
title: Policy
#permalink: /reference/Policy/
parent: Authorization Subscription Evaluation
grand_parent: SAPL Reference
nav_order: 2
---

## Policy

Evaluating a policy against an authorization subscription means assigning a value of `NOT_APPLICABLE`, `INDETERMINATE`, `PERMIT`, or `DENY` to it. The assigned value depends on the result of evaluating the policy’s target and condition (which are conditions that can either be `true` or `false`):

| **Target Expression** | **Condition** | **Policy Value** |
|:------------------|:----------|:-------------|
| `false` (not matching) | don’t care | `NOT_APPLICABLE` | 
| `true` (matching) | `false` | `NOT_APPLICABLE` | 
| *Error* | don’t care | `INDETERMINATE` |  
| `true` (matching) | *Error* | `INDETERMINATE` |  
| `true` (matching) | `true` | Policy’s **Entitlement** (`PERMIT` or `DENY`) | 
