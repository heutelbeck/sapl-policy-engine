---
layout: default
title: Verification
parent: Testing SAPL Policies
grand_parent: SAPL Reference
nav_order: 8
---

## Verification

The optional `verify` block at the end of a scenario checks how many times functions and attributes were called during policy evaluation. This is useful for ensuring that policies access the expected data sources and that mocks are actually exercised.

### Function Call Verification

```sapltest
verify
    - function time.dayOfWeek("2026-01-15T10:00:00Z") is called once;
```

**Verify with argument matchers:**

```sapltest
verify
    - function time.dayOfWeek(any) is called once;
```

**Verify exact call count:**

```sapltest
verify
    - function logger.log(any) is called 3 times;
```

**Verify never called:**

```sapltest
verify
    - function expensive.compute(any) is called 0 times;
```

### Attribute Call Verification

**Environment attributes:**

```sapltest
verify
    - attribute <time.now> is called once;
```

**Environment attributes with parameters:**

```sapltest
verify
    - attribute <time.now(any)> is called 2 times;
```

**Entity attributes:**

```sapltest
verify
    - attribute any.<user.profile> is called once;
```

**Entity attributes with specific entity:**

```sapltest
verify
    - attribute "Alice".<employee.qualification("IT")> is called once;
```

### Multiple Verifications

A single `verify` block can contain multiple assertions:

```sapltest
verify
    - function time.dayOfWeek(any) is called once
    - function time.secondOf(any) is called 4 times
    - attribute <geo.location> is called 0 times;
```

### Amount Syntax

| Syntax    | Meaning                                                       |
|-----------|---------------------------------------------------------------|
| `once`    | Exactly 1 time                                                |
| `N times` | Exactly N times (N must be 0 or 2 or more; use `once` for 1) |

### Complete Example

```sapltest
requirement "audit trail verification" {
    scenario "read access logs the request"
        given
            - document "audited-access"
            - function audit.log(any) maps to true
            - attribute "timeMock" <time.now> emits "2026-01-15T10:00:00Z"
        when "Dr. Smith" attempts "read" on "patient_record"
        expect decision is permit, with obligation containing key "type"
        verify
            - function audit.log(any) is called once
            - attribute <time.now> is called once;
}
```
