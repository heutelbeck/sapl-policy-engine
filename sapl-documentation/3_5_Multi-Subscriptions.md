---
layout: default
title: Multi Subscriptions
#permalink: /reference/Multi-Subscriptions/
parent: Publish/Subscribe Protocol
grand_parent: SAPL Reference
nav_order: 804
---

## Multi-Subscriptions

SAPL allows bundling multiple authorization subscriptions into a single multi-subscription. This is useful when a PEP needs to evaluate several authorization questions at once, for example when rendering a UI that shows multiple resources with different access levels.

### Multi-Subscription Format

A multi-subscription is a JSON object mapping subscription IDs to individual authorization subscriptions:

```json
{
  "read-patient-record": {
    "subject": "alice",
    "action": "read",
    "resource": "patient_record"
  },
  "write-clinical-notes": {
    "subject": "alice",
    "action": "write",
    "resource": "clinical_notes"
  }
}
```

Each key is a unique subscription ID chosen by the PEP. Each value is a standard authorization subscription with `subject`, `action`, `resource`, and optionally `environment` and `secrets`.

### Response Formats

The PDP provides three endpoints for multi-subscriptions, each returning decisions in a different format suited to different use cases.

#### Streaming Individual Decisions (`/api/pdp/multi-decide`)

Returns individual decisions as they change. Each decision is associated with the subscription ID it belongs to:

```json
{
  "subscriptionId": "read-patient-record",
  "decision": {
    "decision": "PERMIT"
  }
}
```

This format is efficient when only a few decisions change at a time, as the PDP only sends updates for subscriptions whose decisions actually changed.

#### Streaming Batch Decisions (`/api/pdp/multi-decide-all`)

Returns all decisions as a single object whenever any decision changes:

```json
{
  "read-patient-record": {
    "decision": "PERMIT"
  },
  "write-clinical-notes": {
    "decision": "DENY"
  }
}
```

This format is simpler to process because each message contains the complete current state of all decisions.

#### One-Shot Batch Decisions (`/api/pdp/multi-decide-all-once`)

Returns a single batch response and completes. The format is identical to the streaming batch, but the connection closes after the first response. Use this for request-response scenarios where continuous updates are not needed.

Decisions may include optional `resource`, `obligations`, and `advice` fields, as described in [SAPL Authorization Decision](../3_3_SAPLAuthorizationDecision/).
