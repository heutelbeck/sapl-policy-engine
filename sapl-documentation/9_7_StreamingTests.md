---
layout: default
title: Streaming Tests
parent: Testing SAPL Policies
grand_parent: SAPL Reference
nav_order: 506
---

## Streaming Tests

SAPL policies can depend on attribute values that change over time. When an attribute emits a new value, the PDP re-evaluates the policy and may produce a different decision. Streaming tests verify this behavior by emitting values to attribute mocks between expectations.

### Then Blocks

A `then` block emits a new value to an attribute mock, identified by its mock ID. The PDP re-evaluates the policy, and the next `expect` clause checks the resulting decision.

```sapltest
requirement "time-based access control" {
    scenario "access changes when time passes"
        given
            - document "office-hours"
            - attribute "timeMock" <time.now> emits "2026-01-15T10:00:00Z"
            - function time.hourOf(any) maps to 10
        when "employee" attempts "enter" on "office"
        expect permit
        then
            - attribute "timeMock" emits "2026-01-15T23:00:00Z"
        expect deny;
}
```

The flow is:
1. The mock emits `"2026-01-15T10:00:00Z"` as its initial value.
2. The policy evaluates to `PERMIT`.
3. The `then` block emits `"2026-01-15T23:00:00Z"` to the mock.
4. The PDP re-evaluates the policy with the new attribute value.
5. The policy now evaluates to `DENY`.

### Multiple Steps

Tests can chain multiple `then`/`expect` pairs to verify a sequence of decisions:

```sapltest
scenario "sensor status changes"
    given
        - document "emergency-access"
        - attribute "sensorMock" <iot.sensorStatus> emits "normal"
    when "operator" attempts "override" on "valve"
    expect deny
    then
        - attribute "sensorMock" emits "warning"
    expect deny
    then
        - attribute "sensorMock" emits "critical"
    expect permit
    then
        - attribute "sensorMock" emits "normal"
    expect deny;
```

### Multiple Emissions Per Step

A single `then` block can emit values to multiple attribute mocks:

```sapltest
scenario "combined status change"
    given
        - document "access-policy"
        - attribute "timeMock" <time.now> emits "morning"
        - attribute "statusMock" <system.status> emits "online"
    when "user" attempts "access" on "resource"
    expect permit
    then
        - attribute "timeMock" emits "night"
        - attribute "statusMock" emits "maintenance"
    expect deny;
```

### Error Emissions

Attribute mocks can emit errors to test how policies handle PIP failures:

```sapltest
scenario "PIP failure causes indeterminate"
    given
        - document "data-access"
        - attribute "dbMock" <database.status> emits "connected"
    when "user" attempts "read" on "data"
    expect permit
    then
        - attribute "dbMock" emits error("Connection lost")
    expect indeterminate;
```
