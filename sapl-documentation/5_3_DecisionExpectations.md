---
layout: default
title: Decision Expectations
parent: Testing SAPL Policies
nav_order: 303
---

## Decision Expectations

The `expect` clause defines what authorization decision the test expects from the PDP.

### Simple Expectations

The simplest form checks only the decision type:

```sapltest
expect permit;
expect deny;
expect indeterminate;
expect not-applicable;
```

### Decision with Obligations

Policies can attach obligations to their decisions. The `expect` clause can verify their presence and content.

**Check that any obligation is present:**

```sapltest
expect decision is permit, with obligation;
```

**Check for a specific obligation (exact match):**

```sapltest
expect decision is permit, with obligation equals { "type": "logAccess", "user": "Dr. Smith" };
```

**Check obligation type with matchers:**

```sapltest
expect decision is permit, with obligation matching object;
```

**Check obligation by key presence:**

```sapltest
expect decision is permit, with obligation containing key "type";
```

**Check obligation by key and value:**

```sapltest
expect decision is permit, with obligation containing key "type" with value matching text "logAccess";
```

**Check obligation with structured matcher:**

```sapltest
expect decision is permit, with obligation matching object where {
    "type" is text "logAccess" and "user" is text
};
```

### Decision with Advice

Advice uses the same syntax as obligations:

```sapltest
expect decision is permit, with advice equals { "notify": "admin" };
expect decision is permit, with advice containing key "channel" with value matching text "email";
```

### Decision with Resource

Policies can include a transformed resource in the decision:

```sapltest
expect decision is permit, with resource equals { "id": 42, "diagnosis": "REDACTED" };
expect decision is permit, with resource matching object;
expect decision is permit, with resource matching text "filtered-content";
```

### Combined Assertions

Multiple assertions can be combined in a single `expect` clause:

```sapltest
expect decision is permit,
    with obligation containing key "type",
    with resource matching object,
    with advice;
```

### Inline Syntax

Obligations and resource can also be specified directly after the decision type:

```sapltest
expect permit
    with obligations { "type": "logAccess", "message": "accessed patient data" }
    with resource { "id": 42, "diagnosis": "REDACTED" }
    with advice { "display": "Access logged for compliance" };
```
