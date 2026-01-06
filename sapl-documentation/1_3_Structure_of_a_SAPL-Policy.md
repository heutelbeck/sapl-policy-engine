---
layout: default
title: Structure of a SAPL Policy
#permalink: /reference/Structure-of-a-SAPL-Policy/
parent: Introduction
grand_parent: SAPL Reference
nav_order: 3
---

> **Introduction Series:** [1. Overview](../1_1_Introduction/) • [2. Subscriptions](../1_2_AuthorizationSubscriptions/) • **3. Policy Structure** • [4. Decisions](../1_4_AuthorizationDecisions/) • [5. Attributes](../1_5_AccessingAttributes/) • [6. Getting Started](../1_6_GettingStarted/)

## Structure of a SAPL Policy

A SAPL policy document generally consists of:

- the keyword `policy`, declaring that the document contains a policy (opposed to a policy set; see [Policy Sets](../5_5_SAPLPolicySet/) for more details)
- a unique (for the PDP) policy name
- the entitlement, which is the decision result to be returned upon successful evaluation of the policy, i.e., `permit` or `deny`
- an optional target expression for indexing and policy selection
- an optional `where` clause containing the conditions under which the entitlement (`permit` or `deny` as defined above) applies
- optional `advice` and `obligation` clauses to inform the PEP about optional and mandatory requirements for granting access to the resource
- an optional `transformation` clause for defining a transformed resource to be used instead of the original resource

A SAPL policy that permits reading patient records based on department membership would look as follows:

*Introduction - Sample Policy 1*

```sapl
policy "compartmentalize read access by department"  // (1)
permit
    resource.type == "patient_record" & action == "read" // (2)
where // (3)
    subject.role == "doctor"; // (4)
    resource.department == subject.department; // (5)
```

**(1)**
This statement declares the policy with the name `"compartmentalize read access by department"`. Policy names should describe what the policy does, not who it applies to. This is supposed to clearly communicate scope and intent of the policy from a business perspective to all involved stakeholders. Further it must be unique to make decisions attributable to individual policies. The JSON values of the authorization subscription object are bound to the variables `subject`, `action`, `resource`, and `environment` that are directly accessible in the policy.

**(2)**
This is the target expression. It filters by resource type and action. The target expression enables fast policy selection: the PDP uses it to quickly identify which policies might apply to a given authorization subscription. Only if the target expression evaluates to `true` does the PDP evaluate the `where` clause.

> **Note:** Target expressions are optimized for fast policy lookup. To maintain performance, they cannot access external attributes via the `<>` operator (see [Accessing Attributes](../1_5_AccessingAttributes/)). This enables the PDP to quickly identify relevant policies from large policy stores. Both `&`/`|` and `&&`/`||` operators work identically in target expressions.

**(3)**
This statement starts the `where` clause (policy body) consisting of a list of statements. Each statement must evaluate to a Boolean value (`true` or `false`). If a statement evaluates to any other type or produces an error, the entire policy evaluation fails. The policy body evaluates to `true` if and only if **all** statements evaluate to `true`. When the policy body evaluates to `true`, the policy applies and emits its entitlement, in this case `permit`.

**(4)**
This statement checks that the subject has the role "doctor". While we could put this in the target expression, placing it in the body keeps the target focused on resource/action filtering.

**(5)**
This attribute comparison demonstrates attribute-based access control in action: the policy works for any department without hardcoding specific values. The same rule applies to cardiology, radiology, neurology, and all other departments automatically, eliminating role explosion and policy duplication.

### Deny Policies

While the example above uses `permit` to grant access, policies can also use `deny` to explicitly prohibit access:

```sapl
policy "deny access outside business hours"
deny
    resource.type == "patient_record" & action == "read"
where
    !<time.localTimeIsBetween("08:00:00", "18:00:00")>;
```

This `deny` policy explicitly prohibits access outside business hours. The `<time.localTimeIsBetween(...)>` syntax accesses a streaming time attribute that continuously checks whether the current time falls within the specified window (covered in detail in [Accessing Attributes](../1_5_AccessingAttributes/)). The `!` operator negates the result, making the condition true when the current time is **outside** business hours.

Deny policies are useful for:
- Implementing explicit prohibitions (blacklists, time restrictions)
- Overriding broader permit policies in specific scenarios
- Documenting security boundaries clearly

When multiple policies apply (some permit, some deny), a **combining algorithm** determines the final decision. See [Multiple Policies and Policy Sets](#multiple-policies-and-policy-sets) below.

### Key Concepts

**Target Expression vs. Body:**
- The **target expression** (after `permit`/`deny`) is used for fast policy indexing and pre-filtering. It should contain conditions that can be evaluated quickly - typically checks on resource type and action. Attribute finders (`<>` operator) cannot be used in target expressions.
- The **body** (after `where`) contains detailed conditions. This is where you include complex logic, cross-entity attribute comparisons, and external attribute lookups (PIPs). The body is only evaluated if the target expression matches.

**Entitlement:**
- `permit` policies grant access when their conditions are met
- `deny` policies explicitly deny access when their conditions are met

The separation of target expression and body allows the PDP to efficiently select relevant policies from a large policy store before evaluating detailed conditions.

**Domain-Driven Policy Language:**
Notice how this policy uses business domain concepts (`action == "read"`, `resource.type == "patient_record"`) rather than technical infrastructure details (HTTP verbs, URLs). This makes the policy readable by non-technical stakeholders and independent of technology choices.

### Multiple Policies and Policy Sets

In production systems, many policies work together to determine access. When the PDP receives an authorization subscription, it follows this process:

1. **Policy Selection**: Finds all policies whose target expressions match the subscription
2. **Policy Evaluation**: Evaluates the `where` clause of matching policies
3. **Combining Results**: When multiple policies apply, uses a **combining algorithm** to determine the final decision

For example, one policy might `permit` based on role, while another `deny` based on time restrictions. The combining algorithm resolves such conflicts into a single authorization decision.

Policies are organized into **policy sets** that define combining algorithms and can share common target expressions and variable definitions. Common combining algorithms include:

- **deny-unless-permit**: Deny access unless at least one policy permits (safe default)
- **deny-overrides**: If any policy denies, the final decision is DENY
- **permit-overrides**: If any policy permits, the final decision is PERMIT

The details of policy evaluation, policy sets, and combining algorithms are covered in [Authorization Subscription Evaluation](../6_0_AuthorizationSubscriptionEvaluation/) and [Policy Sets](../5_5_SAPLPolicySet/).
