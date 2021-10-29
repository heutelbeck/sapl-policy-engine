# SAPL Test Framework

This module contains the SAPL Test Framework for defining unit and policy integration tests of your SAPL policies.

For a detailed explanation of the features consult the [SAPL Docs](https://sapl.io/docs/sapl-reference.html#testing-your-sapl-policies).

## Top level packages

**integration**:
Contains the code for constructing a `SaplIntegrationTestFixture` for policy integration tests.

**lang**:
Contains the subclasses of `sapl-lang` classes. Here specific methods of the classes implementing the functionality behind the SAPL-DSL are overriden to record SAPL coverage hits using the [sapl-coverage-api](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-coverage-api).

**mocking**:
Contains an implementation of an `AttributeContext` and a `FunctionContext`. Via these implementations the policy tester is able to define mocks with return values for the attributes or functions in the SAPL policies under test.

**steps**:
Contains the Step-Interfaces used in the Step-Builder-Pattern to define a SAPL test case. In addition a default implementation of the pattern is provided and reused in the `integration` and `unit` packages in their test fixtures.

**unit**:
Contains the code for constructing a `SaplUnitTestFixture` for policy document unit tests.

**utils**:
Some Utility classes.

**verification**:
Contains the code enabling the verification of calls to the mocks.
