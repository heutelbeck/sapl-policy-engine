---
layout: default
title: Structure of a SAPL Policy
#permalink: /reference/Structure-of-a-SAPL-Policy/
parent: Introduction
grand_parent: SAPL Reference
nav_order: 3
---

> **Introduction Series:** [1. Overview](../1_1_Introduction/) | [2. Subscriptions](../1_2_AuthorizationSubscriptions/) | **3. Policy Structure** | [4. Decisions](../1_4_AuthorizationDecisions/) | [5. Attributes](../1_5_AccessingAttributes/) | [6. Getting Started](../1_6_GettingStarted/)

## What is a Policy?

A Policy expresses a collection of authorization rules that implement an arbitrary access control model required by an
application.

The basic idea is that a policy states "if these conditions are met, then either vote `permit` or `deny` in regard to the
current authorization process." If the conditions are not met, the policy abstains from voting.

This is the core. Everything else is either a refinement of the basic idea or tools for dealing with the coordination
of votes cast from multiple policies and how to resolve them deterministically to the correct overall decision.

E.g., one policy might say "you are a financial advisor, and therefore you are permitted to read financial reports of clients."
But at the same time another policy might say "you worked with another client in the same sector, and therefore you
may have a conflict of interest and may not read the report of this other client."
By defining either the right combining algorithm on PDP level or by using a policy set, an organization can ensure that
these conflicting policies are resolved deterministically to a single overall decision. This way an organization
can implement an access control matching the actual application domain.

Further, policies can define constraints on access, i.e., obligations and advice, that define additional requirements for
the application though which the users access resources. E.g., redacting partial information, adjust queries on the
fly, trigger side effects and processes like audits.

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
Each condition must be an expression that evaluates to `true` or `false`. And the policy is considered *applicable*
if all conditions evaluate to `true`. Then, and only then, the policy casts a vote with its entitlement.

> **Note:** If an error occurs during policy evaluation, the policy will cast an `indeterminate` vote. The combining algorithm of the PDP will decide how to map this to a final decision.

> **Note:** If the policy has no conditions, it is always considered applicable and will always cast a vote.

## Structure of a SAPL Document

Policies and policy sets are organized into documents. A document managed as a text file uses the `.sapl` extension by convention.

A SAPL document can contain:

- **Import statements** for referencing functions and policy information points from libraries
- **Schema statements** for validating authorization subscription elements
- A **policy** or **policy set**

### Imports

Import statements allow you to use functions from libraries with shorter names:

```sapl
import filter.blacken
import time.now as currentTime

policy "use imported functions"
permit
    time.secondOf<currentTime> < 40;
transform
    blacken(resource.payload, 0, 4)
```

Without imports, you would write `filter.blacken(...)` and `<time.now>`.

### Schema Statements

In SAPL, JSON Schema serves two distinct purposes:

1. **Validation** of authorization subscription elements against JSON schemas
2. **Authoring support** in the IDE (e.g., using the SAPL Language Server)

Only if a schema is explicitly marked as `enforced` in the header of a document is it actually used during policy evaluation. Otherwise, it only serves documentation purposes and supports IDE tooling for autocompletion.


```sapl
subject enforced schema { "type": "object", "required": ["userId", "role"] }
resource schema { "type": "object" }

policy "requires valid subject"
permit
    subject.role == "admin";
```

The `subject` and `resource` keywords are used to mark the JSON schema used to validate the corresponding 
authorization subscription elements.

The `enforced` keyword causes the policy to inject an implicit condition that tests 
"does the authorization subscription fulfil the enforced schema conditions?" 
If the subscription does not fulfil the schema, the condition evaluates to `false` and the policy is not applied.

> **Note:** Multiple `enforced` schemas for the same subscription element mean that *any* of the schemas are considered valid (OR semantics).

### Policy Structure

A policy consists of:

- The keyword `policy`
- A unique policy name (string)
- An **entitlement**: `permit` or `deny`
- An optional **body**: semicolon-separated statements
- Optional `obligation` expressions (mandatory requirements for the PEP)
- Optional `advice` expressions (optional recommendations for the PEP)
- An optional `transform` expression (resource transformation)

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

### Policy Body

The body contains **statements** separated by semicolons. A statement is either:

- A **condition**: an expression that must evaluate to `true` or `false`
- A **value definition**: `var name = expression`

All conditions must evaluate to `true` for the policy to apply. Value definitions make variables available to all statements that follow them in the source.

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

The values from the authorization subscription are bound to `subject`, `action`, `resource`, and `environment`.

**Automatic Optimization:** The SAPL compiler analyzes the body and identifies statements that do not use attribute finders (`<>` operator). These statements are automatically used for fast policy indexing, allowing the PDP to efficiently select relevant policies from large policy stores without evaluating external attributes.

### Permit and Deny Policies

Policies declare their **entitlement** which indicates the type of vote cast by the policy, if it is applicable (i.e., all conditions are `true`).:

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

The `<time.localTimeIsBetween(...)>` syntax accesses a streaming time attribute (covered in [Accessing Attributes](../1_5_AccessingAttributes/)).

## Policy Sets

Policy sets are collections of policies that share a common combining algorithm. They serve two key purposes:
* **Organizational convenience**: Organizations can define a set of policies that share a common combining algorithm and apply them to multiple applications.
* **Combining of policies based on evaluation order**: For some scenarios, it can be beneficial to prioritize policies based on their evaluation order. Due to non-deterministic evaluation order, on PDP-level due to optimizations, this can only be done in policy sets.

Following potential imports and chemas, a SAPL document as a policy set starts with the keyword `set` followed by a unique name (string).

Then it contains:
- An optional **target expression** (after `for`) for pre-filtering
- Shared **value definitions** available to all policies in the set

```sapl
set "hospital patient record policies"
priority deny or permit
for resource.type == "patient_record"

var businessHoursStart = "08:00:00";
var businessHoursEnd = "18:00:00";

policy "allow doctors to read"
permit
    subject.role == "doctor";
    action == "read";

policy "deny outside business hours"
deny
    !<time.localTimeIsBetween(businessHoursStart, businessHoursEnd)>;
```

The `for` target expression filters which subscriptions the entire policy set applies to. The shared value definitions are available to all policies within the set.

The combining algorithm syntax is:

```
votingMode or defaultDecision [, errors errorHandling]
```

Examples:
- `first or deny` - first applicable policy wins, default deny
- `priority deny or permit` - deny policies take priority, default permit
- `priority permit or deny` - permit policies take priority, default deny
- `unanimous or deny, errors propagate` - all must agree, propagate errors

For complete details on combining algorithms, see [Combining Algorithm](../6_5_CombiningAlgorithm/).

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

### Key Concepts

**Entitlement:**
- `permit` policies grant access when their conditions are met
- `deny` policies explicitly deny access when their conditions are met

**Domain-Driven Language:**
Policies use business domain concepts (`action == "read"`, `resource.type == "patient_record"`) rather than technical details (HTTP verbs, URLs). This makes policies readable by non-technical stakeholders.

**Attribute-Based Access Control:**
Comparing attributes (`resource.department == subject.department`) enables policies that work across all values without modification. Add new departments, and the policy handles them automatically.
