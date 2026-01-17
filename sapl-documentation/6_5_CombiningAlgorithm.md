---
layout: default
title: Combining Algorithm
parent: Authorization Subscription Evaluation
grand_parent: SAPL Reference
nav_order: 5
---

## Combining Algorithm

When evaluating an authorization subscription, multiple policies may vote differently. A combining algorithm defines how individual votes are combined.

Combining algorithms apply at two levels:

- **Policy Combination**: A policy set contains multiple policies. The combining algorithm determines the policy set's vote.
- **Document Combination**: The PDP knows multiple top-level policy documents (policies and policy sets). The combining algorithm determines the authorization decision returned to the PEP.

### Algorithm Notation

A combining algorithm in SAPL looks like this:

```
deny-wins or permit
```

This reads naturally: "deny wins, or permit by default." It tells you:

1. **How conflicts resolve**: If any policy votes deny, the result is deny.
2. **What happens otherwise**: If no policy votes deny, the result is permit.

SAPL's notation makes behavior explicit by separating three orthogonal concerns:

```
<voting> or <default> [errors <error-handling>]
```

| Concern | Description | Options |
|---------|-------------|---------|
| **Voting** | How competing votes resolve | `deny-wins`, `permit-wins`, `first-vote`, `unanimous`, `unique-decision` |
| **Default** | Result when no policy votes | `permit`, `deny`, `abstain` |
| **Error handling** | How errors are treated | `errors abstain` (default), `errors propagate` |

The error handling clause is optional. When omitted, `errors abstain` applies.

### Combining Decisions with Constraints

A SAPL decision is more than just `PERMIT` or `DENY`. Policies may attach constraints to their decisions:

- **Obligations**: Actions the PEP must perform (e.g., log access, notify owner).
- **Advice**: Recommendations the PEP should follow (e.g., display warning).
- **Resource transformation**: A special case of obligation telling the PEP to replace the original resource with a new version, which may have information redacted for example.

When multiple policies vote `PERMIT`, the combining algorithm must merge these constraints.

**Obligations and advice** from all permit-voting policies are collected and included in the final decision. This is straightforward: the PEP receives the union of all obligations and advice.

**Resource transformations** cannot be merged. If multiple policies vote `PERMIT` and more than one includes a transformation, the algorithm faces *transformation uncertainty*. Since there is no way to combine two different transformed resources, the algorithm cannot return `PERMIT`.

How transformation uncertainty is handled depends on the error handling setting:

| Error Handling | Transformation Uncertainty Result |
|----------------|-----------------------------------|
| `errors abstain` | `DENY` |
| `errors propagate` | `INDETERMINATE` |

Algorithms where only one policy can contribute a permit vote (`unique-decision`, `first-vote`) cannot encounter transformation uncertainty.

### Vocabulary

#### Voting Styles

| Style | Meaning |
|-------|---------|
| `deny-wins` | Any deny vote results in deny; permits only win if no deny exists |
| `permit-wins` | Any permit vote results in permit; denies only win if no permit exists |
| `first-vote` | Policies vote in order; the first non-abstain vote wins |
| `unanimous` | All policies must agree |
| `unique-decision` | Exactly one policy must vote; multiple matches cause an error |

#### Default Decisions

| Default | Meaning |
|---------|---------|
| `permit` | Grant access when no policy votes |
| `deny` | Deny access when no policy votes |
| `abstain` | Return not-applicable when no policy votes |

#### Error Handling

| Handling | Meaning |
|----------|---------|
| `errors abstain` | Treat errors as abstain; they do not influence the outcome (default) |
| `errors propagate` | Errors bubble up; if any policy votes `INDETERMINATE`, the result is `INDETERMINATE` |

### Available Algorithms

SAPL supports all permutations of voting style, default, and error handling. The only restriction is that `first-vote` cannot be used at PDP level (document order is undefined).

**Examples:**

```
deny-wins or permit
deny-wins or deny
deny-wins or abstain
deny-wins or permit errors propagate
permit-wins or deny
permit-wins or abstain errors propagate
first-vote or deny
first-vote or abstain errors propagate
unanimous or deny
unanimous or abstain errors propagate
unique-decision or abstain errors propagate
```

### Choosing an Algorithm

The safest starting point is the most restrictive algorithm:

```
deny-wins or deny
```

This algorithm denies access unless a policy explicitly permits, and if any policy votes deny, that deny cannot be overridden. When no policy votes at all, access is denied.

For PDP-level configuration, this is the recommended default. Deviations should be justified by specific application requirements.

The following sections describe each voting style in detail, starting from the most restrictive and progressing to more permissive variants.

### `deny-wins`

The `deny-wins` voting style is conservative: any deny vote results in deny. Permits only win if no deny exists.

**`deny-wins or deny`**

The most restrictive algorithm. Access requires an explicit permit and no deny votes. When no policy votes, the result is deny.

**Behavior:**

1. If any policy votes `DENY`, or if transformation uncertainty exists, the result is `DENY`.
2. Otherwise, if any policy votes `PERMIT`, the result is `PERMIT`.
3. Otherwise, the result is `DENY`.

**Characteristics:**

- Never returns `NOT_APPLICABLE` or `INDETERMINATE`
- Errors are treated as abstain
- Recommended default for PDP-level configuration

**`deny-wins or permit`**

A permissive variant. Deny still wins over permit, but when no policy votes, the result is permit.

**Behavior:**

1. If any policy votes `DENY`, or if transformation uncertainty exists, the result is `DENY`.
2. Otherwise, the result is `PERMIT`.

**Characteristics:**

- Never returns `NOT_APPLICABLE` or `INDETERMINATE`
- Errors are treated as abstain
- Use with caution: forgetting a deny rule grants access

**`deny-wins or abstain errors propagate`**

A variant that preserves error information and distinguishes "no policy votes" from "access denied".

**Behavior:**

1. If any policy votes `DENY`, the result is `DENY`.
2. Otherwise, if any policy votes `INDETERMINATE` or transformation uncertainty exists, the result is `INDETERMINATE`.
3. Otherwise, if any policy votes `PERMIT`, the result is `PERMIT`.
4. Otherwise, the result is `NOT_APPLICABLE`.

**Characteristics:**

- Preserves error information
- Distinguishes `NOT_APPLICABLE` from `DENY`
- Suitable when the PEP must handle indeterminate states

### `permit-wins`

The `permit-wins` voting style is liberal: any permit vote results in permit. Denies only win if no permit exists.

**`permit-wins or deny`**

Access is granted if any policy votes permit, regardless of deny votes. When no policy votes, the result is deny.

**Behavior:**

1. If any policy votes `PERMIT` (without transformation uncertainty), the result is `PERMIT`.
2. Otherwise, the result is `DENY`.

**Characteristics:**

- Never returns `NOT_APPLICABLE` or `INDETERMINATE`
- Errors are treated as abstain
- A single permit overrides all denies

**`permit-wins or permit`**

The most permissive algorithm. Access is granted if any policy votes permit, and also when no policy votes at all.

**Behavior:**

1. If any policy votes `PERMIT` (without transformation uncertainty), the result is `PERMIT`.
2. Otherwise, if any policy votes `DENY`, the result is `DENY`.
3. Otherwise, the result is `PERMIT`.

**Characteristics:**

- Never returns `NOT_APPLICABLE` or `INDETERMINATE`
- Errors are treated as abstain
- Use with extreme caution: access is granted by default

**`permit-wins or abstain errors propagate`**

A variant that preserves error information and distinguishes "no policy votes" from "access granted".

**Behavior:**

1. If any policy votes `PERMIT` (without transformation uncertainty), the result is `PERMIT`.
2. Otherwise, if any policy votes `INDETERMINATE` or transformation uncertainty exists, the result is `INDETERMINATE`.
3. Otherwise, if any policy votes `DENY`, the result is `DENY`.
4. Otherwise, the result is `NOT_APPLICABLE`.

**Characteristics:**

- Preserves error information
- Any permit overrides denies
- Suitable when "at least one allows" semantics are required

### `unanimous`

The `unanimous` voting style requires all policies to agree. If any policy votes differently, the result depends on the default.

**`unanimous or deny`**

All policies must vote `PERMIT` for access to be granted. Any `DENY` vote, or lack of unanimous `PERMIT`, results in deny.

**Behavior:**

1. If any policy votes `DENY`, the result is `DENY`.
2. Otherwise, if all policies vote `PERMIT` (without transformation uncertainty), the result is `PERMIT`.
3. Otherwise, the result is `DENY`.

**Characteristics:**

- Never returns `NOT_APPLICABLE` or `INDETERMINATE`
- Errors are treated as abstain
- Use when all stakeholders must agree

**`unanimous or abstain errors propagate`**

A variant that preserves error information and distinguishes "no consensus" from "access denied".

**Behavior:**

1. If any policy votes `DENY`, the result is `DENY`.
2. Otherwise, if any policy votes `INDETERMINATE` or transformation uncertainty exists, the result is `INDETERMINATE`.
3. Otherwise, if all policies vote `PERMIT`, the result is `PERMIT`.
4. Otherwise, the result is `NOT_APPLICABLE`.

**Characteristics:**

- Preserves error information
- Requires explicit agreement from all policies
- Suitable for multi-stakeholder authorization

### `unique-decision`

The `unique-decision` voting style requires exactly one policy to vote. Multiple matching policies indicate a configuration error.

**`unique-decision or deny`**

Exactly one policy must match and vote. If no policy matches or multiple policies match, the result is deny.

**Behavior:**

1. If more than one policy has a matching target, the result is `DENY`.
2. If no policy matches, the result is `DENY`.
3. If exactly one policy matches, the result is that policy's vote.

**Characteristics:**

- Never returns `NOT_APPLICABLE` or `INDETERMINATE`
- Errors are treated as abstain
- Useful for mutually exclusive policy structures with safe default

**`unique-decision or abstain errors propagate`**

A variant that preserves error information and signals configuration errors as `INDETERMINATE`.

**Behavior:**

1. If any target evaluation results in an error, or if more than one policy has a matching target, the result is `INDETERMINATE`.
2. If no policy matches, the result is `NOT_APPLICABLE`.
3. If exactly one policy matches, the result is that policy's vote.

**Characteristics:**

- Detects ambiguous policy configurations
- Transformation uncertainty cannot occur (only one policy matches)
- Suitable when the PEP must handle configuration errors explicitly

### `first-vote`

The `first-vote` voting style is order-dependent: the first policy to vote `PERMIT` or `DENY` wins. Policy priority is determined by declaration order within the policy set.

**Not available at PDP level** (document order is undefined).

**`first-vote or deny`**

The first policy to vote `PERMIT` or `DENY` determines the result. If no policy votes, the result is deny.

**Behavior:**

1. Evaluate each policy in declaration order:
   - If it votes `PERMIT` or `DENY`, that is the result.
   - If it votes `NOT_APPLICABLE`, continue to the next policy.
2. If no policy votes `PERMIT` or `DENY`, the result is `DENY`.

**Characteristics:**

- Never returns `NOT_APPLICABLE` or `INDETERMINATE`
- Errors are treated as abstain
- Order-dependent: earlier policies have higher priority

**`first-vote or abstain errors propagate`**

A variant that preserves error information and stops evaluation on errors.

**Behavior:**

1. Evaluate each policy in declaration order:
   - If it votes `INDETERMINATE`, the result is `INDETERMINATE`.
   - If it votes `PERMIT` or `DENY`, that is the result.
   - If it votes `NOT_APPLICABLE`, continue to the next policy.
2. If no policy votes `PERMIT` or `DENY`, the result is `NOT_APPLICABLE`.

**Characteristics:**

- Preserves error information
- Errors in earlier policies prevent evaluation of later policies
- Allows "default" policies at the end of a policy set

### PDP Level vs. Policy Set Level

Combining algorithms apply at both levels, with one restriction:

| Level | Available Algorithms |
|-------|---------------------|
| Policy Set | All algorithms |
| PDP | All except `first-vote` |

The `first-vote` voting style requires a defined evaluation order. Within a policy set, the declaration order establishes this sequence. At the PDP level, the order of policy documents is undefined, making `first-vote` inapplicable.

### Comparison to XACML and SAPL 3.0.0

Up to version 3.0.0, SAPL algorithm names were modeled directly after XACML. XACML defines combining algorithms using URN identifiers:

```
urn:oasis:names:tc:xacml:3.0:policy-combining-algorithm:deny-overrides
urn:oasis:names:tc:xacml:3.0:rule-combining-algorithm:permit-unless-deny
```

SAPL 3.0.0 adopted the short form of these identifiers (e.g., `deny-overrides`, `permit-unless-deny`).

SAPL 4.0.0 introduces a new notation that makes algorithm behavior explicit:

| XACML 3.0 Identifier | SAPL 3.0.0 | SAPL 4.0.0 |
|----------------------|------------|------------|
| `urn:...:deny-overrides` | `deny-overrides` | `deny-wins or abstain errors propagate` |
| `urn:...:permit-overrides` | `permit-overrides` | `permit-wins or abstain errors propagate` |
| `urn:...:permit-unless-deny` | `permit-unless-deny` | `deny-wins or permit` |
| `urn:...:deny-unless-permit` | `deny-unless-permit` | `permit-wins or deny` |
| `urn:...:first-applicable` | `first-applicable` | `first-vote or abstain errors propagate` |
| `urn:...:only-one-applicable` | `only-one-applicable` | `unique-decision or abstain errors propagate` |

**Why a new notation?**

SAPL offers all permutations of voting style, default, and error handling, going beyond the fixed set of XACML algorithms. The composable notation makes these combinations expressible. SAPL also adds the `unanimous` voting style, which has no XACML equivalent.

Additionally, XACML's naming conflates multiple orthogonal concerns:

- `X-overrides` specifies the voting style but hides the default (`NOT_APPLICABLE`) and error behavior (propagate).
- `X-unless-Y` specifies the default but obscures the voting style. The name reads counterintuitively: `permit-unless-deny` means "deny wins, default permit."

These names are difficult to reason about:

| XACML Name | What Users Expect | Actual Behavior |
|------------|-------------------|-----------------|
| `permit-unless-deny` | "Permit, unless there's a deny" | Correct, but hides that errors become abstain |
| `deny-unless-permit` | "Deny, unless there's a permit" | Correct, but hides that errors become abstain |
| `deny-overrides` | "Deny overrides permit" | Correct, but hides default and error propagation |

SAPL 4.0.0 separates these concerns explicitly:

```
<voting> or <default> [errors <handling>]
```

Each component is visible. The algorithm `deny-wins or permit` reads naturally: "deny wins, or permit by default." The algorithm `deny-wins or abstain errors propagate` makes clear that errors are not swallowed.

