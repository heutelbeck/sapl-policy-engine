---
layout: default
title: Policy Evaluation
#permalink: /reference/Policy-Evaluation/
parent: Publish/Subscribe Protocol
grand_parent: SAPL Reference
nav_order: 4
---

## Policy Evaluation

The PDP evaluates all policy sets and top-level policies (policies not contained in a policy set) against the authorization subscription and combines the results. Each policy or policy set evaluates to one of four outcomes: `PERMIT`, `DENY`, `NOT_APPLICABLE`, or `INDETERMINATE`.

When multiple policies produce different outcomes, the **combining algorithm** determines the final decision. SAPL's combining algorithm model has three dimensions:

- **Voting mode**: How individual policy votes are aggregated
- **Default decision**: The decision when no policy casts a decisive vote (`PERMIT` or `DENY`)
- **Error handling**: How `INDETERMINATE` votes (from evaluation errors) are treated

### Configuring the Combining Algorithm

The PDP-level combining algorithm is configured in `pdp.json`:

```json
{
  "algorithm": {
    "votingMode": "PRIORITY_PERMIT",
    "defaultDecision": "DENY",
    "errorHandling": "ABSTAIN"
  }
}
```

Available voting modes at PDP level:

| Voting Mode        | Behavior                                                                                          |
|--------------------|---------------------------------------------------------------------------------------------------|
| `PRIORITY_PERMIT`  | If any policy votes `PERMIT`, the result is `PERMIT`. Otherwise, the default decision applies.    |
| `PRIORITY_DENY`    | If any policy votes `DENY`, the result is `DENY`. Otherwise, the default decision applies.        |
| `UNANIMOUS`        | All decisive votes must agree. Any disagreement results in the default decision.                  |
| `UNIQUE`           | Exactly one policy must be applicable. Zero or multiple applicable policies result in the default. |

The `FIRST` voting mode evaluates policies in document order and uses the first applicable vote. Because the PDP's collection of policies is an unordered set, `FIRST` is only available within policy sets where document order is defined.

Error handling options:

- `PROPAGATE`: Errors produce an `INDETERMINATE` final decision
- `ABSTAIN`: Errors are treated as `NOT_APPLICABLE` (the erroring policy abstains from voting)

Within policy sets, the combining algorithm uses a natural language syntax: `priority permit or deny`, `unanimous or deny, errors propagate`, etc. See [Combining Algorithm](../6_5_CombiningAlgorithm/) for the complete reference.
