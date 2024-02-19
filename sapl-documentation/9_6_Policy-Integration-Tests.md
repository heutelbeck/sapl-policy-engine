---
layout: default
title: Policy-Integration-Tests
#permalink: /reference/Policy-Integration-Tests/
parent: Testing SAPL policies
grand_parent: SAPL Reference
nav_order: 6
---

## Policy-Integration-Tests

Instead of testing a single SAPL document, all policies can be tested together using the PDP interface, just like when an application uses an embedded PDP or a SAPL server.

The `SaplIntegrationTestFixture` manages these kinds of integrations tests.

```java
    private SaplTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new SaplIntegrationTestFixture("policiesIT")
            .withPDPPolicyCombiningAlgorithm(
                PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY
            );
    }
```
