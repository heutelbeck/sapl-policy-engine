---
layout: default
title: Policies
parent: The SAPL Policy Language
grand_parent: SAPL Reference
nav_order: 104
---

## SAPL Policy

> For worked examples and a tutorial-style walkthrough, see [Structure of a SAPL Policy](../2_3_PolicyStructure/).

This section describes the elements of a SAPL policy in more detail. A policy contains an entitlement (`permit` or `deny`) and can be evaluated against an authorization subscription. If all conditions in the body are fulfilled, the policy evaluates to its entitlement. Otherwise, it evaluates to `NOT_APPLICABLE` (if one of the conditions is not satisfied) or `INDETERMINATE` (if an error occurred).

A SAPL policy starts with the keyword `policy`.

### Name

The keyword `policy` is followed by the policy name. The name is a string *identifying* the policy. Therefore, it must be unique. Accordingly, in systems with many policy sets and policies, it is recommended to use a schema to create names (e.g., `"policy:patientdata:permit-doctors-read"`).

### Entitlement

SAPL expects an entitlement specification. This can either be `permit` or `deny`. The entitlement is the value to which the policy evaluates if the policy is applicable to the authorization subscription, i.e., if all conditions in the body are satisfied.

{: .note }
> Since multiple policies can be applicable and the combining algorithm can be chosen, it might make a difference whether there is an explicit `deny`-policy or whether there is just no permitting policy for a certain situation.

### Body

The policy body is **optional** and follows the entitlement directly. It contains one or more statements, each of which must evaluate to `true` for the policy to apply to a certain authorization subscription.

A statement within the body can either be a variable assignment which makes a variable available under a certain name (and always evaluates to `true`)

```sapl
var a_name = expression;
```

or a condition, i.e., an expression that evaluates to `true` or `false`.

```sapl
a_name == "a_string";
```

Each statement is concluded with a semicolon `;`.

The body supports all SAPL expression elements, including attribute finder steps that access external PIPs. Lazy evaluation is used for the conjunction of the statements: if one statement evaluates to `false`, the policy returns the decision `NOT_APPLICABLE`, even if future statements would cause an error.

If the body is missing (or does not contain any condition statement), the policy is applicable to any authorization subscription.

{: .info }
> Policy sets use a separate `for` target expression to control applicability. Individual policies express all conditions in the body. See [SAPL Policy Set](../2_5_PolicySets/) for details.

#### Variable Assignment

A variable assignment starts with the keyword `var`, followed by an identifier under which the assigned value should be available, followed by `=` and an expression. The assignment can be followed by the optional keyword `schema` and one or more schema expressions separated by `,`. The schema expression(s) must evaluate to a valid JSON schema. The schema will only be used by the code completion while editing policies with a dedicated editor.

After a variable assignment, the result of evaluating the expression can be used in later conditions within the same policy under the specified name. This is useful because it allows to execute time-consuming calculations or requests to external attribute stores only once, and the result can be used in multiple expressions. Additionally, it can make policies shorter and improve readability.

The expression can use any element of the SAPL expression language, including attribute finder steps.

The value assignment statement always evaluates to `true`.

#### Condition

A condition statement simply consists of an expression that must evaluate to `true` or `false`.

The expression can use any element of the SAPL expression language, including attribute finder steps. Conditions in the policy body limit the applicability of a policy.

### Obligation

An **optional** obligation expression contains a task which the PEP must fulfill before granting or denying access. It consists of the keyword `obligation` followed by an expression.

A common situation in which obligations are useful is *Break the Glass Scenarios*. Assuming in case of an emergency, a doctor should also have access to medical records that she normally cannot read. However, this emergency access must be logged to prevent abuse. In this situation, logging is a requirement for granting access and therefore must be commanded in an obligation.

Obligations are only returned in the authorization decision if the decision is `PERMIT` or `DENY`. The PDP simply collects all obligations from policies evaluating to one of these entitlements. Depending on the final decision, the obligations and advice which belong to this decision are included in the authorization decision object. It does not matter if the obligation is described with a string (like `"create_emergency_access_log"`) or an object (like `{ "task" : "create_log", "content" : "emergency_access" }`) or another JSON value - only the PEP must be implemented in a way that it knows how to process these obligations.

In any policy an arbitrary number of obligation expressions, all introduced with the **obligation** keyword may be present. All obligation expressions must be written down before any **advice**.

### Advice

An **optional** advice expression is treated similarly to an obligation expression. Unlike obligations, fulfilling the described tasks in the advice is not a requirement for granting or denying access. The advice expression consists of the keyword `advice` followed by any expression.

If the final decision is `PERMIT` or `DENY`, advice from all policies evaluating to this decision is included in the authorization decision object by the PDP.

In any policy an arbitrary number of advice expressions, all introduced with the **advice** keyword may be present. All advice expressions must be written down after any **obligation**.

### Transformation

An **optional** transformation statement starts with the keyword `transform` and followed by an expression. If a transformation statement is supplied and the policy evaluates to `permit`, the result of evaluating the expression will be returned as the `resource` in the authorization decision object.

Accordingly, a transformation statement might be used to hide certain information (e.g., *a doctor can access patient data but should not see bank account details*). This can be reached by applying a filter to the original resource, which removes or blackens certain attributes. Thus, SAPL allows for **fine-grained** or **field-level** access control without the need to treat each attribute as a resource and write a specific policy for it.

The original resource is accessible via the identifier `resource` and can be filtered as follows:

Transformation Example

transform resource |- { @.someValue : remove, @.anotherValue : filter.blacken }

The example would remove the attribute `someValue` and blacken the value of the attribute `anotherValue`. The filtering functions are described in more detail [below](#filtering).

It is not possible to combine multiple transformation statements through multiple policies. Each combining algorithm in SAPL will not return the decision `PERMIT` if there is more than one policy evaluating to `PERMIT`, and at least one of them contains a transformation statement (this is called *transformation uncertainty*). For more details, [see below](#combining-algorithms).

Transformation statements can be interpreted as a special case of obligation, requiring the PEP to replace the resource accordingly.
