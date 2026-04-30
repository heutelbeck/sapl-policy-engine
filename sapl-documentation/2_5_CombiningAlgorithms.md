---
layout: default
title: Combining Algorithms
parent: The SAPL Policy Language
nav_order: 105
---

## Combining Algorithms

When evaluating an authorization subscription, multiple policies may vote differently. A combining algorithm defines how individual votes are combined into a single result.

Combining algorithms apply at two levels:

- **Policy set level**: A policy set contains multiple policies. The combining algorithm specified in the policy set determines the policy set's vote.
- **PDP level**: The PDP evaluates multiple top-level policy documents (policies and policy sets). The PDP-level combining algorithm determines the authorization decision returned to the PEP. The PDP-level combining algorithm is part of the [PDP configuration](../2_2_PDPConfiguration/#combining-algorithm).

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

| Style              | Resolution                                                                                      |
|--------------------|-------------------------------------------------------------------------------------------------|
| `priority deny`    | Any deny wins over any number of permits or suspends                                            |
| `priority permit`  | Any permit wins over any number of denies or suspends                                           |
| `priority suspend` | Any suspend wins over any number of permits or denies                                           |
| `first`            | Policies are evaluated in order; the first non-abstain vote wins                                |
| `unanimous`        | All applicable policies must agree on effect; constraints are merged                            |
| `unanimous strict` | All applicable policies must return equal decisions including obligations, advice, and resource |
| `unique`           | Exactly one policy must match; multiple matches are a configuration error                       |

**Default** determines the result when no policy votes:

| Default   | No-vote result   |
|-----------|------------------|
| `deny`    | `DENY`           |
| `permit`  | `PERMIT`         |
| `abstain` | `NOT_APPLICABLE` |

**Error handling** determines how the final `INDETERMINATE` result of the combining process is presented. The clause is optional; when omitted, `errors abstain` applies.

| Handling           | Effect on final result                                                                                                                                                                  |
|--------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `errors abstain`   | If the combining algorithm's accumulated result is `INDETERMINATE`, it is converted to `NOT_APPLICABLE` at the end. The configured default decision then applies as for a no-vote case. |
| `errors propagate` | If the combining algorithm's accumulated result is `INDETERMINATE`, it is returned as-is.                                                                                               |

{: .info }
> The clause governs the **final** disposition of `INDETERMINATE`. It does not filter erroring policies out of the combining process. Erroring policies still participate as `INDETERMINATE` votes inside the algorithm, where they may block a priority decision (see [Extended Indeterminate](#extended-indeterminate-and-criticality)). Reading this clause as "errors are invisible" or "treated as if did not vote" is a misread of the algorithm.

These three concerns interact to determine the possible result space:

| Default            | Error handling     | Possible results                                              |
|--------------------|--------------------|---------------------------------------------------------------|
| `deny` or `permit` | `errors abstain`   | `PERMIT`, `DENY`, `SUSPEND`                                   |
| `abstain`          | `errors abstain`   | `PERMIT`, `DENY`, `SUSPEND`, `NOT_APPLICABLE`                 |
| `deny` or `permit` | `errors propagate` | `PERMIT`, `DENY`, `SUSPEND`, `INDETERMINATE`                  |
| `abstain`          | `errors propagate` | `PERMIT`, `DENY`, `SUSPEND`, `NOT_APPLICABLE`, `INDETERMINATE` |

If you choose a non-abstain default and omit the error handling clause, the PEP sees concrete decisions only (`PERMIT`, `DENY`, or `SUSPEND`). Errors and missing policies are absorbed into the default. Add `errors propagate` when the PEP must distinguish errors from normal denials.

### Extended Indeterminate and Criticality

Every vote carries an `Outcome` field that records which effects the vote represents. For a concrete vote (`PERMIT`, `DENY`, `SUSPEND`), the outcome is just the vote's own decision. For an `INDETERMINATE` vote, the outcome records which decisions the policy *could have produced* had it not errored — the **extended indeterminate marker** from XACML 3.0.

Priority-based combining algorithms use this marker to decide whether an error blocks an otherwise-winning concrete decision. An error is **critical** if its outcome includes the priority decision: the policy that errored could have voted the priority, and the algorithm cannot safely return any non-priority decision while that uncertainty exists.

For example, under `priority deny`:

- Concrete `PERMIT` + `INDETERMINATE` whose outcome includes `DENY` → result is `INDETERMINATE` (the error could have been the priority deny that should win).
- Concrete `PERMIT` + `INDETERMINATE` whose outcome is `PERMIT` only → the error could not have produced a deny, so the concrete `PERMIT` survives.

Under `errors abstain` this `INDETERMINATE` result is converted to `NOT_APPLICABLE` at the end, then the default decision applies. Under `errors propagate` the `INDETERMINATE` reaches the PEP directly.

### Trace and short-circuit notes

- **`contributingVotes`** in a result captures the votes the algorithm actually observed during folding. Algorithms may short-circuit (stop folding additional votes once the result is determined). A short-circuited vote does not appear in `contributingVotes`. This is intentional: the trace records what the algorithm did, not what it might have done.
- **Errors** propagated into an `INDETERMINATE` result preserve the first-observed error. Subsequent errors observed before short-circuit are not retained.
- **Short-circuit applies to** `priority` (on critical `INDETERMINATE`), `unanimous` (once disagreement becomes ambiguous and irrecoverable), and `unique` (on any `INDETERMINATE`, since uniqueness is broken). It does not apply to `first` (which evaluates in declaration order until a non-`NOT_APPLICABLE` vote is found).

### Combining Decisions with Constraints

A SAPL decision is more than just `PERMIT` or `DENY`. Policies may attach constraints:

- **Obligations**: Actions the PEP must perform (for example, log access, notify owner).
- **Advice**: Recommendations the PEP should follow (for example, display warning).
- **Resource transformation**: A replacement for the original resource, which may have information redacted.

When the combining algorithm produces a `PERMIT` or `DENY`, it collects **obligations and advice** from all policies that voted for that result. The PEP receives the union of all collected constraints.

Collection works at both levels:

- **Policy set level**: Obligations and advice from all contained policies voting for the winning effect are bundled as the policy set's constraints. For the `first` voting style, only evaluated policies contribute.
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

{: .info }
> When no PDP-level combining algorithm is configured, the default is `priority deny or deny errors propagate`. This denies by default and propagates errors so that misconfigurations fail visibly. See [PDP Configuration](../2_2_PDPConfiguration/) for details.

For other scenarios:

| Scenario                                   | Recommended algorithm                | Rationale                                                  |
|--------------------------------------------|--------------------------------------|------------------------------------------------------------|
| Fail-closed default                        | `priority deny or deny`              | Deny wins, missing policies denied                         |
| One permit is sufficient                   | `priority permit or deny`            | A single permit overrides all denies                       |
| Maintenance window or temporary block      | `priority suspend or deny`           | Any policy that votes `suspend` overrides permits and denies; useful for explicit pause/maintenance scenarios |
| All stakeholders must agree                | `unanimous or deny`                  | Disagreement results in deny                               |
| Business-priority ordering                 | `first or deny`                      | Declaration order determines priority (policy set only)    |
| Exactly one policy per request             | `unique or abstain errors propagate` | Detects ambiguous configurations                           |
| Errors must be visible to PEP              | Add `errors propagate`               | `INDETERMINATE` signals errors instead of hiding them      |

### Voting Styles

#### `priority deny`

Any `DENY` vote wins over any number of `PERMIT` or `SUSPEND` votes. This is the conservative choice: a single deny is enough to block access.

If no policy votes `DENY`, the result depends on what other concrete votes were cast:

- All concrete votes agree (all `PERMIT`, or all `SUSPEND`) → that decision wins, with merged constraints.
- Concrete votes disagree on the non-priority decisions (`PERMIT` and `SUSPEND` in different policies) → the per-priority chain `DENY > SUSPEND > PERMIT` decides: `SUSPEND` wins. Only the winner's constraints survive; the loser's vote remains in `contributingVotes` but its obligations/advice are dropped.
- No concrete vote → default applies.

**With `errors propagate`:** An error whose outcome marker includes `DENY` is critical and blocks any non-`DENY` concrete result; the algorithm returns `INDETERMINATE`. An error whose outcome cannot include `DENY` does not block. See [Extended Indeterminate](#extended-indeterminate-and-criticality).

#### `priority permit`

The mirror of `priority deny`. Any `PERMIT` vote wins over any number of `DENY` or `SUSPEND` votes.

If no policy votes `PERMIT`, the algorithm resolves the remaining concretes by chain `PERMIT > SUSPEND > DENY`. With `DENY` and `SUSPEND` both voted, `SUSPEND` wins (closer to the permit intent: the door stays open and may resume). Transformation uncertainty blocks a `PERMIT`: if multiple permits have conflicting transformations, the permit cannot be returned.

**With `errors propagate`:** An error whose outcome marker includes `PERMIT` is critical and blocks the algorithm from returning a non-`PERMIT` concrete result.

#### `priority suspend`

Any `SUSPEND` vote wins over any number of `PERMIT` or `DENY` votes. Use this when an explicit suspension policy must override otherwise-applicable permits and denies, for example a maintenance-window policy that pauses all access.

If no policy votes `SUSPEND`, the chain `SUSPEND > DENY > PERMIT` decides among the remaining concretes: `DENY` wins over `PERMIT`. Both `SUSPEND` and `DENY` are denial-flavoured outcomes; under suspend-priority, denial outranks permission.

**With `errors propagate`:** An error whose outcome marker includes `SUSPEND` is critical.

#### `unanimous`

All applicable policies must agree on effect. If every applicable policy votes the same concrete decision (`PERMIT`, `DENY`, or `SUSPEND`), the result is that decision with merged constraints. If policies disagree, the disagreement is treated according to the error handling setting: as abstain (falling through to the default) or as `INDETERMINATE`.

Transformation uncertainty applies: if all policies vote `PERMIT` but more than one includes a transformation, the unanimous permit cannot be returned. The same applies for `SUSPEND` with conflicting transformations.

**`unanimous strict`** is a stricter variant. Instead of requiring agreement on effect and merging constraints, it requires all applicable policies to return *equal* decisions: same effect, same obligations, same advice, same resource transformation. No constraint merging occurs. If decisions differ in any way, it is treated as disagreement.

#### `unique`

Exactly one policy must have a matching target expression. If no policy matches, the default applies. If more than one policy matches, this is a configuration error: with `errors abstain`, the result is the default; with `errors propagate`, the result is `INDETERMINATE`.

When exactly one policy matches, the result is that policy's vote (`PERMIT`, `DENY`, `SUSPEND`, or `INDETERMINATE` if the policy itself errors). `SUSPEND` and `INDETERMINATE` both count as applicable for the uniqueness check.

Transformation uncertainty cannot occur since only one policy contributes.

#### `first`

Policies are evaluated in declaration order. The first policy to vote `PERMIT`, `DENY`, or `SUSPEND` determines the result. Policies that vote `NOT_APPLICABLE` are skipped. With `errors abstain`, an `INDETERMINATE` vote does NOT skip the policy: it is the chosen vote, and the set-level `errors abstain` then converts the INDETERMINATE to NOT_APPLICABLE at the end. With `errors propagate`, an `INDETERMINATE` vote is the chosen vote and propagates as-is.

If no policy produces a non-`NOT_APPLICABLE` vote, the default applies.

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

`priority suspend` and the `unanimous` / `unanimous strict` voting styles have no SAPL 3.x equivalent.

#### Why the names were replaced

The old names created an unnecessary cognitive load in two ways.

First, the leading word changed meaning between naming patterns. In `X-overrides`, the leading word is the **priority** (what wins). In `X-unless-Y`, the leading word is the **default** (what happens when nothing votes). This means `deny-overrides` and `deny-unless-permit` both start with "deny" but have opposite priorities. To find all algorithms where deny wins, you need `deny-overrides` (obvious) and `permit-unless-deny` (counterintuitive, since it starts with "permit").

Second, each name hides one or two of the three orthogonal concerns. `X-overrides` hides the default (`NOT_APPLICABLE`) and error behavior (`propagate`). `X-unless-Y` hides the error behavior (`abstain`) and disguises the priority as the subordinate clause.

The composable notation eliminates both problems. `priority deny or permit` reads naturally: "priority to deny, or permit by default." `priority deny or abstain errors propagate` makes clear that errors are not swallowed.

Beyond clarity, the composable notation gives policy authors more fine-grained control. The old fixed set of six algorithms left gaps. For example, there was no way to express "priority deny, but return NOT_APPLICABLE when no policy votes" or "unanimous agreement required." SAPL 4.0 closes these gaps with the `unanimous` and `unanimous strict` voting styles and by making all permutations of voting style, default, and error handling available.
