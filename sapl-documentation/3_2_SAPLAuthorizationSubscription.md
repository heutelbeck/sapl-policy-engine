---
layout: default
title: SAPL Authorization Subscription
#permalink: /reference/SAPL-Authorization-Subscription/
parent: Publish/Subscribe Protocol
grand_parent: SAPL Reference
nav_order: 2
---

## SAPL Authorization Subscription

A SAPL authorization subscription is a JSON object that the PEP sends to the PDP to request an authorization decision. It contains the following fields:

**Required fields:**
- **`subject`**: Who is making the request (user, system, service)
- **`action`**: What operation is being attempted
- **`resource`**: What is being accessed

**Optional fields:**
- **`environment`**: Additional contextual information. Omitted when not needed.
- **`secrets`**: Sensitive credentials (API keys, tokens) needed by Policy Information Points during evaluation. Automatically redacted from all logging and serialization.

Each field value can be any JSON value: an object, an array, a number, a string, `true`, `false`, or `null`.

```json
{
  "subject": {
    "username": "alice",
    "role": "doctor"
  },
  "action": "read",
  "resource": {
    "type": "patient_record",
    "patientId": 123
  }
}
```

The `secrets` field provides a secure side-channel for passing credentials to PIPs without exposing them in policies, logs, or authorization decisions. See [Authorization Subscriptions](../1_2_AuthorizationSubscriptions/) for detailed guidance on structuring subscriptions and managing secrets.
