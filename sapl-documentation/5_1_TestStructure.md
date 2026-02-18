---
layout: default
title: Test Structure
parent: Testing SAPL Policies
grand_parent: SAPL Reference
nav_order: 301
---

## Test Structure

A `.sapltest` file contains one or more **requirements**. Each requirement contains one or more **scenarios**. A scenario is a single test case that submits an authorization subscription and checks the decision.

```sapltest
requirement "access control for medical records" {
    scenario "authorized doctor can read"
        when "Dr. Smith" attempts "read" on "patient_record"
        expect permit;

    scenario "unauthorized user is denied"
        when "guest" attempts "read" on "patient_record"
        expect deny;
}

requirement "audit logging" {
    scenario "access is logged"
        when "Dr. Smith" attempts "read" on "patient_record"
        expect decision is permit, with obligation;
}
```

Requirement and scenario names must be unique within their scope. Requirement names must be unique within a file. Scenario names must be unique within a requirement.

### Scenarios

Each scenario follows a **Given-When-Expect** structure:

```sapltest
scenario "name"
    given
        - <preconditions>
    when <authorization subscription>
    expect <decision expectation>
    verify
        - <call count assertions>;
```

The `given` and `verify` blocks are optional. The `when` and `expect` blocks are required. The scenario ends with a semicolon.

### Central Given Blocks

A `given` block at the requirement level applies to all scenarios in that requirement. Scenario-level `given` blocks extend the central configuration with additional preconditions.

```sapltest
requirement "patient record access" {
    given
        - document "patient-access"
        - attribute "timeMock" <time.now> emits "2026-01-15T10:00:00Z"

    scenario "doctor can read during business hours"
        when "Dr. Smith" attempts "read" on "patient_record"
        expect permit;

    scenario "doctor denied after hours"
        given
            - attribute "timeMock" <time.now> emits "2026-01-15T23:00:00Z"
        when "Dr. Smith" attempts "read" on "patient_record"
        expect deny;
}
```

The central `given` block is the right place for the document specification and shared mocks. Scenario-level blocks add or override mocks for specific test cases.
