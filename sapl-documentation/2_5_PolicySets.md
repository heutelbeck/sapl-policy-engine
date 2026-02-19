---
layout: default
title: Policy Sets
parent: The SAPL Policy Language
grand_parent: SAPL Reference
nav_order: 105
---

## SAPL Policy Set

While a policy can either be a top-level SAPL document or be contained in a policy set, policy sets are always top-level documents. For evaluating an authorization subscription, the PDP evaluates existing policy sets. Policy sets are evaluated against an authorization subscription by checking their target expression, if applicable, evaluating their policies, and combining multiple votes according to a combining algorithm specified in the policy set. Finally, similarly to policies, policy sets vote either `PERMIT`, `DENY`, `NOT_APPLICABLE` or `INDETERMINATE`.

Policy sets are used to structure multiple policies and provide an order for the policies they contain. Hence, their policies can be evaluated one after another.

A policy set definition starts with the keyword `set`.

### Name

The keyword `set` is followed by the policy set name. The name is a string *identifying* the policy set. It must be unique within all policy sets and policies.

### Combining Algorithm

The name is followed by a [combining algorithm](../2_4_CombiningAlgorithms/) that specifies how the policy set resolves its policies' votes. For example:

```
set "example-policies" priority deny or deny
```

### Target Expression

After the combining algorithm, an **optional** target expression can be specified. The target expression is a condition for applying the policy set. It starts with the keyword `for` followed by an expression that must evaluate to either `true` or `false`. If the condition evaluates to `true` for a certain authorization subscription, the policy set *matches* this subscription. In case the target expression is missing, the policy set matches any authorization subscription.

The policy sets' target expression is used to select matching policy sets from a large collection of policy documents before evaluating them. As this needs to be done efficiently, there are no [attribute finder steps](../4_0_AttributeFinders/#attribute-finders) allowed at this place.

### Variable Assignments

The target expression can be followed by any number of variable assignments. Variable assignments are used to make a value available in all later policies under a certain name. An assignment starts with the keyword `var`, followed by an identifier under which the assigned value should be available, followed by `=` and an expression.

Since variable assignments are only evaluated if the policy set's target matches, attribute finders may be used.

In case a policy within the policy set assigns a variable already assigned in the policy set, the assignment in the policy overwrites the old. The overwritten value only exists within the particular policy. In other policies, the variable has the value defined in the policy set.

### Policies

Each policy set must contain one or more policies. [See above](../2_3_PolicyStructure/#sapl-policy) how to describe a SAPL policy. If the combining algorithm uses the `first` voting style, the policies are evaluated in the order in which they appear in the policy set.

In each policy, functions and attribute finders imported at the beginning of the SAPL document can be used under their shorter name. All variables assigned for the policy set (see [Variable Assignments](../2_3_PolicyStructure/#variable-assignment)) are available within the policies but can be overwritten by a variable assignment within a particular policy.

### Example: First Applicable Policy

The `first or deny` algorithm evaluates policies in document order and uses the first applicable policy's vote. This is useful when policies have overlapping conditions and business rules dictate a priority.

> **Note:** This is a fictional scenario to illustrate how policy order can encode business priority.

```sapl
set "facility access control"
first or deny
for resource.type == "facility"

policy "VIP always allowed"
permit
    subject.id in resource.vipList;

policy "blacklisted users denied"
deny
    subject.id in resource.blacklist;

policy "standard access during business hours"
permit
    <time.localTimeIsBetween("08:00:00", "18:00:00")>;
```

The policy order encodes business priority: "VIP status trumps blacklist status."

| Scenario                               | Result   | Reason                               |
|----------------------------------------|----------|--------------------------------------|
| VIP who is also blacklisted            | `permit` | VIP policy checked first             |
| Blacklisted user during business hours | `deny`   | Blacklist before hours check         |
| Normal user during business hours      | `permit` | Hours policy applies                 |
| Normal user outside business hours     | `deny`   | No policy applies, default is `deny` |

If the blacklist policy came first, VIPs on the blacklist would be denied. The `first or deny` algorithm lets organizations express "check these exceptions first" patterns that cannot be achieved with priority-based algorithms where all permits or all denies are grouped together.
