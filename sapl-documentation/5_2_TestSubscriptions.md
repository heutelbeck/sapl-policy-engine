---
layout: default
title: Test Subscriptions
parent: Testing SAPL Policies
grand_parent: SAPL Reference
nav_order: 302
---

## Authorization Subscriptions

The `when` clause defines the authorization subscription that the test submits to the PDP. It specifies the subject, action, and resource, with optional environment and secrets.

### Basic Form

```sapltest
when "Dr. Smith" attempts "read" on "patient_record"
```



The keywords `subject`, `action`, and `resource` can be added for readability but are optional:

```sapltest
when subject "Dr. Smith" attempts action "read" on resource "patient_record"
```

### Structured Values

The subject, action, and resource can be any JSON value, not just strings:

```sapltest
when
    { "name": "Dr. Smith", "role": "doctor", "department": "cardiology" }
attempts
    { "java": { "name": "findById" } }
on
    { "type": "patient_record", "id": 42 }
```

### Environment

The optional `in` clause adds environment data to the subscription:

```sapltest
when "Dr. Smith" attempts "read" on "patient_record"
    in { "tenant": "hospital-north", "region": "eu-west" }
```

### Secrets

The optional `with secrets` clause adds per-subscription secrets:

```sapltest
when "Dr. Smith" attempts "read" on "patient_record"
    with secrets { "oauth_token": "eyJhbGciOi..." }
```

Secrets are available to PIPs through `AttributeAccessContext.subscriptionSecrets()` but are not accessible from within policies.

### Complete Example

```sapltest
when
    subject { "name": "Dr. Smith", "role": "doctor" }
attempts
    action "read"
on
    resource { "type": "patient_record", "id": 42 }
in
    environment { "time": "2026-01-15T10:00:00Z" }
with
    secrets { "api_key": "sk-..." }
```
