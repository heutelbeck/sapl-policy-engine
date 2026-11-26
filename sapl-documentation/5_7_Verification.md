---
layout: default
title: Verification
parent: Testing SAPL Policies
nav_order: 307
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

### Repeated Pure Function Calls

SAPLTest counts calls that reach the function broker. Within one evaluation round, repeated pure calls can share a successful result when their arguments are the same literals or the same subscription variables, such as `subject`.

Both comparisons below use the result of one `time.secondOf(subject)` call.

```sapl
policy "read" permit
    time.secondOf(subject) == 24 && time.secondOf(subject) > 0;
```

The scenario reaches both comparisons and verifies one call.

```sapltest
requirement "seconds" {
    scenario "read at second 24"
        given
            - document "read"
            - function time.secondOf(any) maps to 24
        when "2026-01-15T10:00:24Z" attempts "enter" on "office"
        expect permit
        verify - function time.secondOf(any) is called once;
}
```

Coverage records each comparison that evaluation reaches. A short-circuited branch contributes zero calls. When another round reaches the same function, the broker receives a new call and may serve it from its own cache.

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
