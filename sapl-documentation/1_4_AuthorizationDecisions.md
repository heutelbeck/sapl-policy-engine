---
layout: default
title: Authorization Decisions
#permalink: /reference/Authorization-Decisions/
parent: Introduction
grand_parent: SAPL Reference
nav_order: 4
---

> **Introduction Series:** [1. Overview](../1_1_Introduction/) • [2. Subscriptions](../1_2_AuthorizationSubscriptions/) • [3. Policy Structure](../1_3_Structure_of_a_SAPL-Policy/) • **4. Decisions** • [5. Attributes](../1_5_AccessingAttributes/) • [6. Getting Started](../1_6_GettingStarted/)

## Authorization Decisions

The SAPL authorization decision in response to an authorization subscription is a JSON object. It contains the attribute `decision` as well as the optional attributes `resource`, `obligation`, and `advice`.

For the introductory sample authorization subscription with the preceding policy, a SAPL authorization decision would look as follows:

*Introduction - Sample Authorization Decision*

```json
{
  "decision": "PERMIT"
}
```

### Decision Values

The `decision` attribute can have one of four values, each with specific meaning for the PEP:

**`PERMIT`**: Access must be granted. This is the only decision value that should result in granting access to the requested resource.

**`DENY`**: Access must be explicitly denied. This indicates that a policy actively prohibits the requested action. The PEP must deny access.

**`NOT_APPLICABLE`**: No policy was applicable to the authorization subscription. This means the PDP could not find any matching policies for the request. The PEP should deny access in this case (fail-safe default).

**`INDETERMINATE`**: An error occurred during policy evaluation. This could be due to network failures, malformed policies, PIP unavailability, or other technical issues. The PEP should deny access in this case (fail-safe default).

The PEP evaluates the authorization decision and grants or denies access accordingly. **Only a `PERMIT` decision should result in granting access.**

### Why Four Decision Values?

SAPL distinguishes four decision values rather than simply PERMIT/DENY because SAPL PEPs typically integrate with existing application frameworks that coordinate multiple authorization mechanisms. Many frameworks use voting-based patterns where different authorization components contribute to the final access decision.

**NOT_APPLICABLE enables composability**: When no SAPL policy matches an authorization subscription, NOT_APPLICABLE allows the PEP to signal "I have no opinion on this request" rather than forcing a DENY. This enables SAPL to coexist with other authorization mechanisms (framework ACLs, role-based checks, etc.) rather than requiring SAPL policies for every access decision. Organizations can adopt SAPL incrementally, writing policies for complex scenarios while relying on existing authorization for simple cases.

**INDETERMINATE distinguishes errors from policy decisions**: When policy evaluation fails due to technical issues (network failures, unavailable PIPs, malformed policies), INDETERMINATE signals a system failure rather than a policy denial. This is important for a number of reasons. E.g., **operational response** trigger failure investigation or security monitoring, **retry logic** where technical failures may resolve on a retry, while policy denials will not.

This four-value model enables **compositional authorization** where SAPL integrates as one component in a larger authorization ecosystem rather than replacing all existing mechanisms. The distinction between explicit policy decisions (PERMIT/DENY), absence of policy coverage (NOT_APPLICABLE), and technical failures (INDETERMINATE) allows frameworks and PEPs to handle each case appropriately.

### Optional Attributes

The authorization decision may include additional attributes beyond `decision`:

**`resource`**: Contains a transformed or filtered version of the requested resource when the policy includes a `transform` statement. This allows policies to redact sensitive information or modify the resource before it is returned.

**`obligation`**: An array of tasks that the PEP **must** fulfill before granting or denying access. If the PEP cannot fulfill these obligations, access must not be granted even with a `PERMIT` decision. Examples include logging requirements or sending notifications.

**`advice`**: An array of tasks that the PEP **should** perform, but their fulfillment is not mandatory for granting access. These are optional recommendations from policies.

For more detailed information about authorization decisions, see [SAPL Authorization Decision](../3_3_SAPLAuthorizationDecision/).
