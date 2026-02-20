---
layout: default
title: Unit Tests and Integration Tests
parent: Testing SAPL Policies
grand_parent: SAPL Reference
nav_order: 308
---

## Unit Tests and Integration Tests

The test DSL supports two testing modes: **unit tests** that evaluate a single policy document in isolation, and **integration tests** that evaluate multiple documents together through the PDP's combining algorithm.

### Unit Tests

A unit test loads a single SAPL document (policy or policy set) using the `document` directive:

```sapltest
requirement "patient access policy" {
    given
        - document "patient-access"

    scenario "doctor can read"
        when "Dr. Smith" attempts "read" on "patient_record"
        expect permit;
}
```

The document name matches the policy or policy set name declared in the `.sapl` file. The test framework automatically uses a combining algorithm that requires exactly one matching document.

Unit tests verify a single document's behavior in isolation. They are fast and make failures easy to diagnose.

### Integration Tests

An integration test loads multiple documents and evaluates them together. This requires specifying a combining algorithm:

**Explicit document list:**

```sapltest
requirement "combined access control" {
    given
        - documents "policy_A", "policy_B", "policy_C"
        - priority deny or deny

    scenario "all policies agree"
        when "admin" attempts "manage" on "system"
        expect permit;

    scenario "deny overrides permit"
        when "guest" attempts "manage" on "system"
        expect deny;
}
```

**Loading from a configuration directory:**

The `configuration` directive loads all `.sapl` files and the `pdp.json` from a directory:

```sapltest
requirement "full PDP integration" {
    given
        - configuration "policiesIT"

    scenario "PDP behavior matches production"
        when "user" attempts "read" on "resource"
        expect permit;
}
```

The directory path is relative to the test resources. This is the closest to production behavior, as it uses the same policy set and PDP configuration.

**Loading pdp.json separately:**

The `pdp-configuration` directive loads only the PDP configuration (combining algorithm, variables) from a file, while documents are specified separately:

```sapltest
requirement "custom PDP config" {
    given
        - pdp-configuration "policiesIT/pdp.json"
        - documents "policy_A", "policy_B"

    scenario "uses configured algorithm"
        when "user" attempts "read" on "resource"
        expect permit;
}
```

### Combining Algorithms

Integration tests require a combining algorithm. It can be specified in the `given` block or loaded from `pdp.json`:

```sapltest
- priority deny or deny
- priority permit or deny
- priority deny or abstain errors propagate
- priority permit or abstain errors propagate
- unanimous or deny
- unanimous strict or deny
- unique or abstain errors propagate
- first or deny
- first or abstain errors propagate
```

See [Combining Algorithm](../2_5_CombiningAlgorithms/) for details on each algorithm.

### Variables and Secrets

PDP-level variables and secrets can be defined in the `given` block:

```sapltest
given
    - document "tenant-policy"
    - variables { "tenant": "hospital-north", "maxSessionMinutes": 30 }
    - secrets { "api_key": "sk-test-key", "db_password": "test-password" }
```

Variables are accessible in policies through the `environment` object. Secrets are accessible to PIPs through `AttributeAccessContext.pdpSecrets()` but are not visible to policy expressions.

### Validation Rules

The test framework enforces the following rules:

- Unit tests (`document`) cannot specify a combining algorithm (it is set automatically).
- The `document` directive must appear in the requirement-level `given` block, not in scenario-level blocks.
- `configuration` cannot be combined with `document`, `documents`, or `pdp-configuration`.
- `pdp-configuration` cannot be combined with `configuration`.
