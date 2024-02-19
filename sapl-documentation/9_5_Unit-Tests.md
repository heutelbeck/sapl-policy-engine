---
layout: default
title: Unit-Tests
#permalink: /reference/Unit-Tests/
parent: Testing SAPL policies
grand_parent: SAPL Reference
nav_order: 5
---

## Unit-Tests

SAPL tests use JUnit for executing SAPL unit test cases. Each test is prepared by creating `SaplUnitTestFixture`. This can be done in the `@BeforeEachStep` of a JUnit test case.

The `SaplUnitTestFixture` defines the name of the SAPL document under test or the path to its file. In addition, the fixture sets up PIPs and FunctionLibrarys to be used during test execution.

```java
    private SaplTestFixture fixture;

    @BeforeEach
    void setUp() throws InitializationException {
        fixture = new SaplUnitTestFixture("policyStreaming")
                //.registerPIP(...)
                .registerFunctionLibrary(new TemporalFunctionLibrary());
    }
```
