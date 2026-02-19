---
layout: default
title: Authorization Decisions
parent: The SAPL Policy Language
grand_parent: SAPL Reference
nav_order: 102
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

The `decision` attribute can have one of four values, each with specific meaning for the PEP:

| Decision | Meaning | PEP Action |
|----------|---------|------------|
| `PERMIT` | A policy explicitly grants access. | Grant access. |
| `DENY` | A policy explicitly prohibits the action. | Deny access. |
| `NOT_APPLICABLE` | No policy matched the authorization subscription. | Deny access (fail-safe). |
| `INDETERMINATE` | An error occurred during evaluation (network failures, unavailable PIPs, malformed policies). | Deny access (fail-safe). |

**Only a `PERMIT` decision should result in granting access.** All other values must be treated as access denied.

### Why Four Decision Values?

SAPL distinguishes four decision values rather than simply PERMIT/DENY because SAPL PEPs typically integrate with existing application frameworks that coordinate multiple authorization mechanisms. Many frameworks use voting-based patterns where different authorization components contribute to the final access decision.

**NOT_APPLICABLE enables composability**: When no SAPL policy matches an authorization subscription, NOT_APPLICABLE allows the PEP to signal "I have no opinion on this request" rather than forcing a DENY. This enables SAPL to coexist with other authorization mechanisms (framework ACLs, role-based checks, etc.) rather than requiring SAPL policies for every access decision. Organizations can adopt SAPL incrementally, writing policies for complex scenarios while relying on existing authorization for simple cases.

**INDETERMINATE distinguishes errors from policy decisions**: When policy evaluation fails due to technical issues (network failures, unavailable PIPs, malformed policies), INDETERMINATE signals a system failure rather than a policy denial. This is important for operational reasons: failures can trigger investigation or security monitoring, and technical failures may resolve on a retry while policy denials will not.

This four-value model enables **compositional authorization** where SAPL integrates as one component in a larger authorization ecosystem rather than replacing all existing mechanisms. The distinction between explicit policy decisions (PERMIT/DENY), absence of policy coverage (NOT_APPLICABLE), and technical failures (INDETERMINATE) allows frameworks and PEPs to handle each case appropriately.

### Optional Attributes

The authorization decision may include additional attributes beyond `decision`:

- **`resource`**: Contains a transformed or filtered version of the requested resource when the policy includes a `transform` statement. This allows policies to redact sensitive information or modify the resource before it is returned.
- **`obligations`**: An array of tasks that the PEP **must** fulfill before granting or denying access. If the PEP cannot fulfill these obligations, access must not be granted even with a `PERMIT` decision. Examples include logging requirements or sending notifications.
- **`advice`**: An array of tasks that the PEP **should** perform, but their fulfillment is not mandatory for granting access. These are optional recommendations from policies.

> **Note:** An obligation in a `DENY` decision effectively acts like advice - the unsuccessful handling of the obligation cannot change the overall decision outcome, since access is already denied.

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
