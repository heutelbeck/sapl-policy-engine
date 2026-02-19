---
layout: default
title: Policy Structure
parent: The SAPL Policy Language
grand_parent: SAPL Reference
nav_order: 103
---


## What is a Policy?

A policy expresses a collection of authorization rules that implement an arbitrary access control model required by an
application.

The basic idea is that a policy states "if these conditions are met, then either vote `permit` or `deny` in regard to the
current authorization process." If the conditions are not met, the policy abstains from voting.

This is the core. Everything else is either a refinement of the basic idea or tools for dealing with the coordination
of votes cast from multiple policies and how to resolve them deterministically to the correct overall decision.

For example, one policy might say "you are a financial advisor, and therefore you are permitted to read financial reports of clients."
But at the same time another policy might say "you worked with another client in the same sector, and therefore you
may have a conflict of interest and may not read the report of this other client."
By defining either the right combining algorithm on PDP level or by using a policy set, an organization can ensure that
these conflicting policies are resolved deterministically to a single overall decision. This way an organization
can implement an access control matching the actual application domain.

Further, policies can define constraints on access, i.e., obligations and advice, that define additional requirements for
the application through which users access resources. For example, redacting partial information, adjusting queries on the
fly, or triggering side effects and processes like audits.

## A Simple Policy Example

Here is a simple example of a SAPL policy:

```sapl
policy "I am a minimal example"
permit
    action == "read";
```

Each policy starts with the keyword `policy` followed by a unique policy name (string).
Then comes the **entitlement**, which is either `permit` or `deny`.
This expresses that "if the policy conditions are met, cast a vote with this entitlement."
The entitlement is then followed by a number of statements that define the policy conditions, each terminated by a semicolon.
Each condition must be an expression that evaluates to `true` or `false`. The policy is considered *applicable*
if all conditions evaluate to `true`. Then, and only then, the policy casts a vote with its entitlement.

> **Note:** If an error occurs during policy evaluation, the policy will cast an `indeterminate` vote. The combining algorithm of the PDP will decide how to map this to a final decision.

> **Note:** If the policy has no conditions, it is always considered applicable and will always cast a vote with the indicated entitlement.

## SAPL Documents

Policies and policy sets are organized into documents. A document is managed as a text file with the `.sapl` extension.

Each SAPL document contains exactly **one** policy or [policy set](../2_5_PolicySets/). A document cannot contain both, and it cannot contain more than one of either. Optionally, the policy or policy set can be preceded by:

- **[Import statements](../2_7_Imports/)** for referencing functions and attribute finders from libraries
- **[Schema statements](../2_8_Schemas/)** for validating authorization subscription elements

Imports and schemas must appear before the policy or policy set. The PDP loads all `.sapl` files from its configured policy source and evaluates the documents together using the configured [combining algorithm](../2_4_CombiningAlgorithms/).

## Policy Syntax

Every policy begins with the keyword `policy` followed by a unique name (a string literal). After the name, a policy consists of:

- An **entitlement**: `permit` or `deny`
- An optional **body**: semicolon-separated conditions and value definitions
- Optional **obligation** blocks (requirements the PEP **must** fulfill)
- Optional **advice** blocks (recommendations the PEP **should** consider)
- An optional **transform** expression (resource transformation)

```sapl
policy "permit reading patient records for doctors"
permit                                       // entitlement
    resource.type == "patient_record";       // condition 1
    action == "read";                        // condition 2
    var dept = subject.department;           // value definition
    resource.department == dept;             // condition 3
obligation
    { "type": "logAccess", "level": "info" }
advice
    { "type": "notifyDataOwner" }
transform
    resource |- filter.blacken
```

### Name

The policy name is a string that uniquely identifies the policy. In systems with many policies and policy sets, a naming schema is recommended, for example `"policy:patientdata:permit-doctors-read"`.

### Entitlement

The entitlement is either `permit` or `deny`. It determines the vote the policy casts when it is applicable, i.e., when all conditions in the body are satisfied.

{: .note }
> Since multiple policies can be applicable and the combining algorithm can be chosen, it might make a difference whether there is an explicit `deny` policy or whether there is just no permitting policy for a certain situation.

```sapl
policy "allow doctors to read patient records"
permit
    subject.role == "doctor";
    action == "read";
    resource.type == "patient_record";
```

```sapl
policy "deny access outside business hours"
deny
    resource.type == "patient_record";
    action == "read";
    !<time.localTimeIsBetween("08:00:00", "18:00:00")>;
```

The `<time.localTimeIsBetween(...)>` syntax accesses a streaming time attribute (covered in [Attribute Finders](../4_0_AttributeFinders/)).

### Body

The body is optional and contains **statements** separated by semicolons. A statement is either:

- A **condition**: an expression that must evaluate to `true` or `false`
- A **value definition**: `var name = expression`

All conditions must evaluate to `true` for the policy to apply. If the body is missing or contains no conditions, the policy is applicable to any authorization subscription.

The values from the authorization subscription are bound to `subject`, `action`, `resource`, and `environment`.

```sapl
policy "compartmentalize read access by department"
permit
    resource.type == "patient_record";
    action == "read";
    var userDept = subject.department;
    var resourceDept = resource.department;
    subject.role == "doctor";
    userDept == resourceDept;
```

A variable assignment starts with the keyword `var`, followed by an identifier, `=`, and an expression. The result can then be used in later statements within the same policy. This avoids redundant calculations or repeated calls to external attribute sources, and improves readability. Variable assignments always evaluate to `true`.

A variable assignment can optionally include the keyword `schema` followed by one or more JSON schema expressions separated by `,`. These schemas are used by policy editors for code completion and do not affect evaluation.

{: .info }
> Policy sets use a separate `for` target expression to control applicability. Individual policies express all conditions in the body. See [Policy Sets](../2_5_PolicySets/) for details.

For details on how conditions are evaluated, including short-circuit behavior and error handling, see [Evaluation Semantics](../2_9_EvaluationSemantics/).

**Automatic Optimization:** The SAPL compiler analyzes the body and identifies statements that do not use attribute finders (`<>` operator). These statements are automatically used for fast policy indexing, allowing the PDP to efficiently select relevant policies from large policy stores without evaluating external attributes.

### Obligations

An obligation expression consists of the keyword `obligation` followed by an expression. It describes a task the PEP **must** fulfill before granting or denying access. If the PEP cannot fulfill an obligation, it must not grant access even on a `PERMIT` decision.

A common use case is *break-the-glass* scenarios: in an emergency, a doctor may access records they normally cannot read, but this access must be logged to prevent abuse. Logging is a requirement for granting access and therefore must be expressed as an obligation.

The PDP collects all obligations from applicable policies. Depending on the final decision, the obligations belonging to that decision are included in the authorization decision object. An obligation can be any JSON value - a string (like `"create_emergency_access_log"`), an object (like `{ "task": "create_log", "content": "emergency_access" }`), or any other type. The PEP must be implemented to process these obligations.

A policy can contain multiple obligation expressions. All obligations must appear before any advice.

### Advice

An advice expression is similar to an obligation but fulfilling the described task is not mandatory. The advice expression consists of the keyword `advice` followed by an expression.

If the final decision is `PERMIT` or `DENY`, advice from all applicable policies evaluating to that decision is included in the authorization decision object.

A policy can contain multiple advice expressions. All advice must appear after any obligations.

### Transformation

A transformation statement starts with the keyword `transform` followed by an expression. If a policy with a transformation evaluates to `PERMIT`, the result of the expression is returned as the `resource` in the authorization decision.

Transformations enable **field-level access control**: instead of writing a separate policy for each attribute of a resource, a single policy can redact or filter sensitive fields. The original resource is accessible via the identifier `resource`:

```sapl
transform resource |- { @.someValue : remove, @.anotherValue : filter.blacken }
```

This removes the attribute `someValue` and blackens the value of `anotherValue`.

It is not possible to combine transformation results from multiple policies. If more than one applicable policy evaluates to `PE                                              
RMIT` and at least one contains a transformation, the combining algorithm will not return `PERMIT` (*transformation uncertainty*). See [Combining Algorithms](../2_4_CombiningAlgorithms/) for details.

For organizing multiple policies with shared combining algorithms and target expressions, see [Policy Sets](../2_5_PolicySets/).
