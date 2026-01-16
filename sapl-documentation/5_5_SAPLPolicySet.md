---
layout: default
title: SAPL Policy Set
parent: The SAPL Policy Language
grand_parent: SAPL Reference
nav_order: 6
---

## SAPL Policy Set

While a policy can either be a top-level SAPL document or be contained in a policy set, policy sets are always top-level documents. For evaluating an authorization subscription, the PDP evaluates existing policy sets. Policy sets are evaluated against an authorization subscription by checking their target expression, if applicable evaluating their policies, and combining multiple votes according to a combining algorithm specified in the policy set. Finally, similarly to policies, policy sets vote either `PERMIT`, `DENY`, `NOT_APPLICABLE` or `INDETERMINATE`.

Policy sets are used to structure multiple policies and provide an order for the policies they contain. Hence, their policies can be evaluated one after another.

A policy set definition starts with the keyword `set`.

### Name

The keyword `set` is followed by the policy set name. The name is a string *identifying* the policy set. It must be unique within all policy sets and policies.

### Combining Algorithm

The name is followed by a combining algorithm. This algorithm describes how to combine the votes from evaluating each policy to determine the policy set's vote.

Example:

```
set "example-policies" deny-wins or deny
```

The algorithm notation follows the pattern:

```
<voting> or <default> [errors <error-handling>]
```

See [Combining Algorithm](../6_5_CombiningAlgorithm/#combining-algorithm) for the full explanation of voting styles, defaults, error handling, and how constraints like obligations and resource transformations are merged.

### Target Expression

After the combining algorithm, an **optional** target expression can be specified. The target expression is a condition for applying the policy set. It starts with the keyword `for` followed by an expression that must evaluate to either `true` or `false`. If the condition evaluates to `true` for a certain authorization subscription, the policy set *matches* this subscription. In case the target expression is missing, the policy set matches any authorization subscription.

The policy sets' target expression is used to select matching policy sets from a large collection of policy documents before evaluating them. As this needs to be done efficiently, there are no [attribute finder steps](../8_1_AttributeFinders/#attribute-finders) allowed at this place.

### Variable Assignments

The target expression can be followed by any number of variable assignments. Variable assignments are used to make a value available in all subsequent policies under a certain name. An assignment starts with the keyword `var`, followed by an identifier under which the assigned value should be available, followed by `=` and an expression.

Since variable assignments are only evaluated if the policy set's target matches, attribute finders may be used.

In case a policy within the policy set assigns a variable already assigned in the policy set, the assignment in the policy overwrites the old. The overwritten value only exists within the particular policy. In other policies, the variable has the value defined in the policy set.

### Policies

Each policy set must contain one or more policies. [See above](../5_4_SAPLPolicy/#sapl-policy) how to describe a SAPL policy. If the combining algorithm uses the `first-vote` voting style, the policies are evaluated in the order in which they appear in the policy set.

In each policy, functions and attribute finders imported at the beginning of the SAPL document can be used under their shorter name. All variables assigned for the policy set (see [Variable Assignments](5_4_SAPLPolicy/#variable-assignments)) are available within the policies but can be overwritten for a particular policy. The same applies to imports: imports at the policy level overwrite imports defined for the policy set but are only valid for the particular policy.
