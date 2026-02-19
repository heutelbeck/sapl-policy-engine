---
layout: default
title: Combining Algorithms
parent: The SAPL Policy Language
grand_parent: SAPL Reference
nav_order: 104
---

## Combining Algorithms

When evaluating an authorization subscription, multiple policies may vote differently. A combining algorithm defines how individual votes are combined into a single result.

Combining algorithms apply at two levels:

- **Policy set level**: A policy set contains multiple policies. The combining algorithm specified in the policy set determines the policy set's vote.
- **PDP level**: The PDP evaluates multiple top-level policy documents (policies and policy sets). The PDP-level combining algorithm determines the authorization decision returned to the PEP. How this algorithm is configured depends on the deployment model; see [SAPL Node](../7_1_SAPLNode/) for details.

### Algorithm Notation

A combining algorithm declaration in SAPL looks like this:

```
priority deny or permit
```

This reads naturally: "priority to deny, or permit by default." The notation separates three orthogonal concerns:

```
<voting style> or <default> [errors <error-handling>]
```

**Voting style** determines how competing votes resolve:

| Style              | Resolution                                                                         |
|--------------------|------------------------------------------------------------------------------------|
| `priority deny`    | Any deny wins over any number of permits                                           |
| `priority permit`  | Any permit wins over any number of denies                                          |
| `first`            | Policies are evaluated in order; the first non-abstain vote wins                   |
| `unanimous`        | All applicable policies must agree on entitlement; constraints are merged          |
| `unanimous strict` | All applicable policies must return equal decisions including obligations, advice, and resource |
| `unique`           | Exactly one policy must match; multiple matches are a configuration error          |

**Default** determines the result when no policy votes:

| Default   | No-vote result   |
|-----------|------------------|
| `deny`    | `DENY`           |
| `permit`  | `PERMIT`         |
| `abstain` | `NOT_APPLICABLE` |

**Error handling** determines how errors (`INDETERMINATE` votes) are treated. The clause is optional; when omitted, `errors abstain` applies.

| Handling           | Effect                                                                                  |
|--------------------|-----------------------------------------------------------------------------------------|
| `errors abstain`   | Errors are invisible. An erroring policy is treated as if it did not vote.              |
| `errors propagate` | Errors are visible. If any policy votes `INDETERMINATE`, the result is `INDETERMINATE`. |

These three concerns interact to determine the possible result space:

| Default            | Error handling     | Possible results                          |
|--------------------|--------------------|-------------------------------------------|
| `deny` or `permit` | `errors abstain`   | `PERMIT`, `DENY` only                     |
| `abstain`          | `errors abstain`   | `PERMIT`, `DENY`, `NOT_APPLICABLE`        |
| `deny` or `permit` | `errors propagate` | `PERMIT`, `DENY`, `INDETERMINATE`         |
| `abstain`          | `errors propagate` | All four                                  |

If you choose a non-abstain default and omit the error handling clause, the PEP will only ever see `PERMIT` or `DENY`. Errors and missing policies are absorbed into the default. Add `errors propagate` when the PEP must distinguish errors from normal denials.

### Combining Decisions with Constraints

A SAPL decision is more than just `PERMIT` or `DENY`. Policies may attach constraints:

- **Obligations**: Actions the PEP must perform (for example, log access, notify owner).
- **Advice**: Recommendations the PEP should follow (for example, display warning).
- **Resource transformation**: A replacement for the original resource, which may have information redacted.

When the combining algorithm produces a `PERMIT` or `DENY`, it collects **obligations and advice** from all policies that voted for that result. The PEP receives the union of all collected constraints.

Collection works at both levels:

- **Policy set level**: Obligations and advice from all contained policies voting for the winning entitlement are bundled as the policy set's constraints. For the `first` voting style, only evaluated policies contribute.
- **PDP level**: Obligations and advice from all top-level documents voting for the final decision are collected.

**Resource transformations** cannot be merged. If multiple policies vote `PERMIT` and more than one includes a transformation, the algorithm faces *transformation uncertainty*. Since there is no way to combine two different transformed resources, the algorithm cannot return `PERMIT`. How this is handled depends on the error handling setting:

| Error handling     | Transformation uncertainty result |
|--------------------|-----------------------------------|
| `errors abstain`   | `DENY`                            |
| `errors propagate` | `INDETERMINATE`                   |

Algorithms where only one policy can contribute a permit vote (`unique`, `first`) cannot encounter transformation uncertainty.

### Choosing an Algorithm

The safest starting point is:

```
priority deny or deny
```

This denies access unless a policy explicitly permits, deny votes cannot be overridden, and missing policies result in denial. For PDP-level configuration, this is the recommended default. Deviations should be justified by specific application requirements.

{: .warning }
> The PDP-level combining algorithm is a mandatory configuration. Without one, the PDP returns `INDETERMINATE` for every subscription. See [SAPL Node](../7_1_SAPLNode/) for configuration details.

For other scenarios:

| Scenario                            | Recommended algorithm                | Rationale                                               |
|-------------------------------------|--------------------------------------|---------------------------------------------------------|
| Fail-closed default                 | `priority deny or deny`              | Deny wins, missing policies denied                      |
| One permit is sufficient            | `priority permit or deny`            | A single permit overrides all denies                    |
| All stakeholders must agree         | `unanimous or deny`                  | Disagreement results in deny                            |
| Business-priority ordering          | `first or deny`                      | Declaration order determines priority (policy set only) |
| Exactly one policy per request      | `unique or abstain errors propagate` | Detects ambiguous configurations                        |
| Errors must be visible to PEP       | Add `errors propagate`               | `INDETERMINATE` signals errors instead of hiding them   |

### Voting Styles

#### `priority deny`

Any deny vote wins over any number of permits. This is the conservative choice: a single deny is enough to block access.

If no policy votes deny and at least one votes permit, the result is permit. If no policy votes at all, the default applies.

**With `errors propagate`:** An error from a policy that *might have voted* deny prevents returning a definitive `DENY`. The algorithm cannot safely merge constraints from the concrete deny-voting policies with constraints that the erroring policy might have produced. The result is `INDETERMINATE` instead.

#### `priority permit`

The mirror of `priority deny`. Any permit vote wins over any number of denies.

If no policy votes permit and at least one votes deny, the result is deny. If no policy votes at all, the default applies. Transformation uncertainty blocks a permit: if multiple permits have conflicting transformations, the permit cannot be returned.

**With `errors propagate`:** An error from a policy that *might have voted* permit prevents returning a definitive `PERMIT`, for the same constraint-merging reason as `priority deny`.

#### `unanimous`

All applicable policies must agree on entitlement. If every applicable policy votes `PERMIT`, the result is `PERMIT` with merged constraints. If every applicable policy votes `DENY`, the result is `DENY` with merged constraints. If policies disagree, the disagreement is treated according to the error handling setting: as abstain (falling through to the default) or as `INDETERMINATE`.

Transformation uncertainty applies: if all policies vote `PERMIT` but more than one includes a transformation, the unanimous permit cannot be returned.

**`unanimous strict`** is a stricter variant. Instead of requiring agreement on entitlement and merging constraints, it requires all applicable policies to return *equal* decisions - same entitlement, same obligations, same advice, same resource transformation. No constraint merging occurs. If decisions differ in any way, it is treated as disagreement.

#### `unique`

Exactly one policy must have a matching target expression. If no policy matches, the default applies. If more than one policy matches, this is a configuration error: with `errors abstain`, the result is the default; with `errors propagate`, the result is `INDETERMINATE`.

When exactly one policy matches, the result is that policy's vote (`PERMIT`, `DENY`, or `INDETERMINATE` if the policy itself errors).

Transformation uncertainty cannot occur since only one policy contributes.

#### `first`

Policies are evaluated in declaration order. The first policy to vote `PERMIT` or `DENY` determines the result. Policies that vote `NOT_APPLICABLE` are skipped. With `errors abstain`, policies that vote `INDETERMINATE` are also skipped. With `errors propagate`, an `INDETERMINATE` vote stops evaluation immediately.

If no policy produces a definitive vote, the default applies.

**Not available at PDP level.** The `first` voting style requires a defined evaluation order. Within a policy set, declaration order establishes this sequence. At the PDP level, the order of policy documents is undefined.

### Appendix: Migration from SAPL 3.x

{: .info }
> This section is for teams upgrading from SAPL 3.x. New users can skip it.

SAPL 3.x used algorithm names inspired by XACML (for example, `deny-overrides`, `permit-unless-deny`). SAPL 4.0 replaces these with the composable notation described above:

| SAPL 3.x              | SAPL 4.0                                      |
|-----------------------|-----------------------------------------------|
| `deny-overrides`      | `priority deny or abstain errors propagate`   |
| `permit-overrides`    | `priority permit or abstain errors propagate` |
| `permit-unless-deny`  | `priority deny or permit`                     |
| `deny-unless-permit`  | `priority permit or deny`                     |
| `first-applicable`    | `first or abstain errors propagate`           |
| `only-one-applicable` | `unique or abstain errors propagate`          |

#### Why the names were replaced

The old names created an unnecessary cognitive load in two ways.

First, the leading word changed meaning between naming patterns. In `X-overrides`, the leading word is the **priority** (what wins). In `X-unless-Y`, the leading word is the **default** (what happens when nothing votes). This means `deny-overrides` and `deny-unless-permit` both start with "deny" but have opposite priorities. To find all algorithms where deny wins, you need `deny-overrides` (obvious) and `permit-unless-deny` (counterintuitive - it starts with "permit").

Second, each name hides one or two of the three orthogonal concerns. `X-overrides` hides the default (`NOT_APPLICABLE`) and error behavior (`propagate`). `X-unless-Y` hides the error behavior (`abstain`) and disguises the priority as the subordinate clause.

The composable notation eliminates both problems. `priority deny or permit` reads naturally: "priority to deny, or permit by default." `priority deny or abstain errors propagate` makes clear that errors are not swallowed.

Beyond clarity, the composable notation gives policy authors more fine-grained control. The old fixed set of six algorithms left gaps - for example, there was no way to express "priority deny, but return NOT_APPLICABLE when no policy votes" or "unanimous agreement required." SAPL 4.0 closes these gaps with the `unanimous` and `unanimous strict` voting styles and by making all permutations of voting style, default, and error handling available.
