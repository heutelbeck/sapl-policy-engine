---
layout: default
title: Authorization Decisions
parent: The SAPL Policy Language
nav_order: 103
---


## Authorization Decisions

The SAPL authorization decision in response to an authorization subscription is a JSON object. It contains the attribute `decision` as well as the optional attributes `resource`, `obligations`, and `advice`.

For example, given an authorization subscription requesting read access to a patient record, a simple SAPL authorization decision would look as follows:

*Introduction - Sample Authorization Decision*

```json
{
  "decision": "PERMIT"
}
```

### Decision Values

The `decision` attribute can have one of five values, each with specific meaning for the PEP:

| Decision | Meaning | PEP Action |
|----------|---------|------------|
| `PERMIT` | A policy explicitly grants access. | Grant access. |
| `DENY` | A policy explicitly prohibits the action. | Deny access. |
| `SUSPEND` | A policy explicitly pauses access. The subscription remains alive; a later decision may resume it. | Streaming PEPs: suspend data forwarding, retain the subscription, resume on a later `PERMIT`. One-shot PEPs that cannot suspend treat `SUSPEND` as `DENY`. |
| `NOT_APPLICABLE` | No policy matched the authorization subscription. | Deny access (fail-safe). |
| `INDETERMINATE` | An error occurred during evaluation (network failures, unavailable PIPs, malformed policies). | Deny access (fail-safe). |

**Only a `PERMIT` decision should result in granting access.** All other values must be treated as access denied.

How a final decision arises from individual policy votes is the job of the [combining algorithm](../2_5_CombiningAlgorithms/). For how each policy maps its body evaluation to a vote (`PERMIT`, `DENY`, `SUSPEND`, `INDETERMINATE`, or `NOT_APPLICABLE`), see [Policy Structure](../2_4_PolicyStructure/#policy-evaluation-result).

### Why Five Decision Values?

SAPL distinguishes these decision values rather than simply PERMIT/DENY because SAPL PEPs typically integrate with existing application frameworks that coordinate multiple authorization mechanisms, and because streaming use cases need a denial form that does not terminate the subscription.

**NOT_APPLICABLE enables composability**: When no SAPL policy matches an authorization subscription, NOT_APPLICABLE allows the PEP to signal "I have no opinion on this request" rather than forcing a DENY. This enables SAPL to coexist with other authorization mechanisms (framework ACLs, role-based checks, etc.) rather than requiring SAPL policies for every access decision. Organizations can adopt SAPL incrementally, writing policies for complex scenarios while relying on existing authorization for simple cases.

**INDETERMINATE distinguishes errors from policy decisions**: When policy evaluation fails due to technical issues (network failures, unavailable PIPs, malformed policies), INDETERMINATE signals a system failure rather than a policy denial. This is important for operational reasons: failures can trigger investigation or security monitoring, and technical failures may resolve on a retry while policy denials will not.

**SUSPEND distinguishes pause from terminal denial**: A `SUSPEND` decision tells a streaming PEP to stop forwarding data without terminating the subscription. The PEP keeps the authorization stream alive, so when a later evaluation produces `PERMIT`, data forwarding resumes. This supports scenarios like maintenance windows, rate limits, or per-user temporary blocks. One-shot PEPs (those that resolve a single decision and exit, like `@PreEnforce`) cannot suspend; they treat `SUSPEND` as `DENY`.

This decision model enables **compositional authorization** where SAPL integrates as one component in a larger authorization ecosystem. The distinction between explicit policy decisions (PERMIT/DENY/SUSPEND), absence of policy coverage (NOT_APPLICABLE), and technical failures (INDETERMINATE) allows frameworks and PEPs to handle each case appropriately.

### Optional Attributes

The authorization decision may include additional attributes beyond `decision`:

- **`resource`**: Contains a transformed or filtered version of the requested resource when the policy includes a `transform` statement. This allows policies to redact sensitive information or modify the resource before it is returned.
- **`obligations`**: An array of tasks that the PEP **must** fulfill before acting on the decision. If the PEP cannot fulfill these obligations, access must not be granted on a `PERMIT` decision; on a `SUSPEND`, the PEP must apply the obligations (e.g., logging the suspension) before pausing. Examples include logging requirements or sending notifications.
- **`advice`**: An array of tasks that the PEP **should** perform, but their fulfillment is not mandatory for granting access. These are optional recommendations from policies.

> **Note:** An obligation in a `DENY` decision effectively acts like advice because the unsuccessful handling of the obligation cannot change the overall decision outcome, since access is already denied. The same applies to `SUSPEND` for one-shot PEPs that treat `SUSPEND` as `DENY`. For streaming PEPs that honour `SUSPEND` as a pause, the obligation is binding (the PEP must execute it before suspending).

Here is an example of a decision with all optional attributes present. It corresponds to a policy that permits access but redacts the patient's SSN via a `transform` statement, requires audit logging via an `obligation`, and suggests notifying the data owner via `advice`:

```json
{
  "decision": "PERMIT",
  "resource": {
    "type": "patient_record",
    "patientId": 123,
    "ssn": "XXX-XX-6789"
  },
  "obligations": [
    { "type": "logAccess", "level": "audit" }
  ],
  "advice": [
    { "type": "notifyDataOwner" }
  ]
}
```

The PEP receiving this decision must grant access (PERMIT), return the transformed `resource` (with the redacted SSN) instead of the original, execute the `logAccess` obligation (and deny access if it cannot), and optionally perform the `notifyDataOwner` advice.
