---
layout: default
title: Mocking
parent: Testing SAPL Policies
grand_parent: SAPL Reference
nav_order: 304
---

## Mocking

Policies often depend on external data through functions and attribute finders (PIPs). In tests, these dependencies are replaced with mocks that return controlled values.

### Function Mocking

A function mock intercepts calls to a SAPL function and returns a predefined value:

```sapltest
given
    - function time.dayOfWeek("2026-01-15T10:00:00Z") maps to "WEDNESDAY"
```

**No parameters:**

```sapltest
- function system.timestamp() maps to "2026-01-15T10:00:00Z"
```

**Matching any argument:**

```sapltest
- function time.dayOfWeek(any) maps to "FRIDAY"
```

**Multiple parameters with mixed matchers:**

```sapltest
- function string.concat("hello", " ", any) maps to "hello world"
```

**Typed matchers:**

```sapltest
- function time.dayOfWeek(matching text) maps to "MONDAY"
- function math.process(matching number) maps to 100
- function logic.check(matching boolean) maps to true
- function json.transform(matching object) maps to {}
- function collection.process(matching array) maps to []
- function util.handle(matching null) maps to "handled"
```

**Matching a specific value within a type:**

```sapltest
- function time.dayOfWeek(matching text "2026-01-15T10:00:00Z") maps to "WEDNESDAY"
```

### Error and Undefined Returns

Mocks can return error or undefined values:

```sapltest
- function service.fetch(any) maps to error
- function service.fetch(any) maps to error("Service unavailable")
- function lookup.find(any) maps to undefined
```

An error return causes the enclosing policy condition to evaluate to `INDETERMINATE`. An undefined return behaves like a missing value.

### Environment Attribute Mocking

Environment attributes are accessed in policies as `<pip.attribute>`. Each mock requires a unique **mock ID** (a string you choose) that identifies the mock for later operations like streaming and verification.

```sapltest
given
    - attribute "timeMock" <time.now> emits "2026-01-15T10:00:00Z"
```

The `emits` clause sets the initial value. Omit it to create a mock without an initial value (useful when the first value is emitted in a `then` block):

```sapltest
- attribute "statusMock" <system.status>
```

**With argument matchers:**

```sapltest
- attribute "timeMock" <time.now(any)> emits "2026-01-15T10:00:00Z"
- attribute "configMock" <system.config("timeout")> emits 30
```

**Error and undefined:**

```sapltest
- attribute "failMock" <service.status> emits error("Connection refused")
- attribute "failMock" <service.status> emits error
- attribute "missingMock" <data.value> emits undefined
```

### Entity Attribute Mocking

Entity attributes are accessed in policies as `value.<pip.attribute>`. The mock specifies a matcher for the entity (left-hand) value:

**Any entity:**

```sapltest
- attribute "upperMock" any.<string.upper> emits "HELLO"
```

**Exact entity value:**

```sapltest
- attribute "upperMock" "hello".<string.upper> emits "HELLO"
- attribute "profileMock" 42.<user.profile> emits { "name": "Alice" }
- attribute "flagMock" true.<config.flag> emits "enabled"
- attribute "dataMock" { "id": 1 }.<record.details> emits { "status": "active" }
```

**Typed entity matcher:**

```sapltest
- attribute "validMock" matching text.<x509.isValid> emits true
- attribute "processMock" matching object.<data.process> emits { "result": "ok" }
```

**Entity attribute with parameters:**

```sapltest
- attribute "qualMock" "Alice".<employee.qualification("IT")> emits ["Java", "Python"]
- attribute "msgMock" any.<mqtt.messages(1, any)> emits { "payload": "data" }
```

### Mock ID

The mock ID is a string that uniquely identifies an attribute mock within a scenario. It serves two purposes:

1. **Streaming**: The `then` block uses the mock ID to emit new values mid-test (see [Streaming Tests](../5_6_StreamingTests/)).
2. **Verification**: The `verify` block can reference mocks by their attribute name to check call counts (see [Verification](../5_7_Verification/)).

Choose descriptive IDs that reflect what the mock represents:

```sapltest
- attribute "currentTime" <time.now> emits "2026-01-15T10:00:00Z"
- attribute "userLocation" any.<geo.location> emits { "lat": 48.1, "lon": 11.6 }
```
