# SAPL Test Framework

This module contains the SAPL Test Framework for defining unit and policy integration tests of your SAPL policies.

## Testing SAPL policies

The SAPL policy engine provides a framework to test SAPL policies. This framework supports unit tests of a single SAPL
document or policy integration tests of all SAPL policies of an application via the PDP interface.

### Usage scenarios

With the SAPL test framework, developers can test SAPL policies whether they use SAPL via an embedded PDP in an
application or via a central SAPL server.

### Embedded PDP

If an application uses an embedded PDP, SAPL policy tests are treated like traditional unit and integration tests.
Developers can deploy policy tests alongside these tests and execute them identically via the Maven lifecycle on a local
workstation or in a CI pipeline.

### SAPL-Server

The following repository GitOps Demo showcases a deployment pipeline with SAPL policy tests in a GitOps-Style for the
headless SAPL-Server-LT. Here every change to the policies is introduced via a pull request on the main branch. The CI
pipeline executes the policy tests for every pull request and breaks the pipeline run if policy tests are failing.
Merging a pull request on the main branch triggers automatic synchronization of the policies to a SAPL-Server-LT
instance.

SAPL tests use Java. Therefore, it is impossible to use the SAPL test framework when deploying
SAPL-Server-Implementations with GUI-based PAP (i.e., SAPL-Server-CE or SAPL-Server-EE).

### Unit-Tests

SAPL tests use JUnit for executing SAPL unit test cases. Each test is prepared by creating SaplUnitTestFixture. This can
be done in the @BeforeEachStep of a JUnit test case.

The SaplUnitTestFixture defines the name of the SAPL document under test or the path to its file. In addition, the
fixture sets up PIPs and Function Libraries to be used during test execution.

```java
    private SaplTestFixture fixture;

@BeforeEach
void setUp() throws InitializationException {
    fixture = new SaplUnitTestFixture("policyStreaming")
            //.registerPIP(...)
            .registerFunctionLibrary(new TemporalFunctionLibrary());
}
```

### Policy-Integration-Tests

Instead of testing a single SAPL document, all policies can be tested together using the PDP interface, just like when
an application uses an embedded PDP or a SAPL server.

The SaplIntegrationTestFixture manages these kinds of integrations tests.

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

### Writing test cases

The Step-Builder-Pattern is used for defining the concrete test case. It consists of the following four steps:

* Given-Step: Define mocks for attributes and functions
* When-Step: Specify the AuthorizationSubscription
* Expect-Step: Define expectations for generated AuthorizationDecision
* Verify-Step: Verify the generated AuthorizationDecision

![TestCase Structure](https://sapl.io/docs/latest/assets/sapl_reference_images/StepBuilderPatternForSaplTest_English.svg)

Starting with constructTestCaseWithMocks() or constructTestCase() called on the fixture, the test case definition
process is started at the Given-Step or the When-Step.

### Given-Step

**Mocking of functions:**

* the givenFunction methods can be used to mock a function returning a Val specified in the method parameters for every
  call.
    * a single value can be specified
        ```java
        .givenFunction("time.dayOfWeek", Val.of("SATURDAY"))
        ```
    * a single value only returned when the parameters of the function call match some expectations
        ```java
        .givenFunction("corp.subjectConverter", whenFunctionParams(is(Val.of("USER")), is(Val.of("nikolai"))), Val.of("ROLE_ADMIN"))
        ```
    * or a Lambda-Expression evaluating the parameters of the function call
        ```java
      .givenFunction("company.complexFunction", (FunctionCall call) -> {

      //probably one should check for number and type of parameters first
      Double param0 = call.getArgument(0).get().asDouble();
      Double param1 = call.getArgument(1).get().asDouble();

      return param0 % param1 == 0 ? Val.of(true) : Val.of(false);
      })
        ```
    * and verify the number of calls to this mock
        ```java
        .givenFunction("time.dayOfWeek", Val.of("SATURDAY"), times(1))
        ```
* `givenFunctionOnce` can specify a Val or multiple Val-Objects which are emitted once (in a sequence) when this mocked
  function is called
    * a single value
      ```java
      .givenFunctionOnce("time.secondOf", Val.of(4))
      .givenFunctionOnce("time.secondOf", Val.of(5))
      ```
    * or a sequence of values
      ```java
      .givenFunctionOnce("time.secondOf", Val.of(3), Val.of(4), Val.of(5))
      ```

**Mocking of attributes:**

* `givenAttribute` methods can mock attributes
    * to return one or more `Val`
      ```java
      .givenAttribute("time.now", timestamp0, timestamp1, timestamp2)
      ```
    * to return a sequence of `Val` in an interval of `Duration`. Using `withVirtualTime` activates the virtual time
      feature of Project Reactor
      ```java
      .withVirtualTime()
      .givenAttribute("time.now", Duration.ofSeconds(10), timestamp0, timestamp1, timestamp2, timestamp3, timestamp4, timestamp5)
      ```

      > The virtual time feature can be used with real time-based PIPs registered the fixture level.
      > Virtual time is "no silver bullet" to cite
      the [Project Reactor Reference Guide](https://projectreactor.io/docs/core/release/reference/#_manipulating_time).
      > It says further that "virtual time also gets very limited with infinite sequences, which might hog the thread on
      which both the sequence and its verification run.

    * to mark an attribute to be mocked and specify return values in a sequence next to expectations
        ```java
        .givenAttribute("company.pip1")
        .givenAttribute("company.pip2")
        .when(AuthorizationSubscription.of("User1", "read", "heartBeatData"))
        .thenAttribute("company.pip1", Val.of(1))
        .thenAttribute("company.pip2", Val.of("foo"))
        .expectNextPermit()
        .thenAttribute("company.pip2", Val.of("bar"))
        .expectNextNotApplicable()
        ```
    * to mock an attribute depending on the parent value
        ```java
        .givenAttribute("test.upper", whenParentValue(val("willi")), thenReturn(Val.of("WILLI")))
        ```
    * to mock an attribute depending on the parent value and every value the arguments are called for
      ```java
      .givenAttribute("pip.attributeWithParams", whenAttributeParams(parentValue(val(true)), arguments(val(2), val(2))), thenReturn(Val.of(true)))
      ```

Further mock types or overloaded methods are
available [here](https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-test/src/main/java/io/sapl/test/steps/GivenStep.java).

### When-Step

The next Step defines the AuthorizationSubscription for the policy evaluation.

* pass an AuthorizationSubscription created by its factory methods
  ```java
  .when(AuthorizationSubscription.of("willi", "read", "something"))
  ```
* pass a JSON-String to be parsed to an AuthorizationSubscription via the framework
  ```java
  .when("{\"subject\":\"willi\", \"action\":\"read\", \"resource\":\"something\", \"environment\":{}}")
  ```
* pass a JsonNode object of
  the [Jackson-Framework](https://fasterxml.github.io/jackson-databind/javadoc/2.7/com/fasterxml/jackson/databind/JsonNode.html)
  ```java
  JsonNode authzSub = mapper.createObjectNode()
    .put("subject", "willi")
    .put("action", "read")
    .put("resource", "something")
    .put("environment", "test");
    ...
    .when(authzSub)
  ```

### Expect-Step

This step defines the expected AuthorizationDecision.

* check only the Decision via
  ```java
  .expectPermit()
  .expectDeny()
  .expectIndeterminate()
  .expectNotApplicable()
  ```
* pass a AuthorizationDecision object to be checked for equality
  ```java
    ObjectNode obligation = mapper.createObjectNode();
    obligation.put("type", "logAccess");
    obligation.put("message", "Willi has accessed patient data (id=56) as an administrator.");
    ArrayNode obligations = mapper.createArrayNode();
    obligations.add(obligation);
    AuthorizationDecision decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);
  
    ...
    .expect(decision)
  ```
* use a predicate function to manually define checks
  ```java
    .expect((AuthorizationDecision dec) -> {
    // some complex and custom assertions
    if(dec.getObligations().isEmpty()) {
    return true;
    }
    return false;
    })
  ```
* Hamcrest matchers provided by the sapl-hamcrest module can be used to express complex expectations on the decision,
  obligations, advice or resources of the AuthorizationDecision
  ```java
    .expect(
    allOf(
    isPermit(),
    hasObligationContainingKeyValue("type", "logAccess"),
    isResourceMatching((JsonNode resource) -> resource.get("id").asText().equals("56"))
    ...
    )
  )
  ```

  All available Matcher<AuthorizationDecision> can be
  found [here](https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-hamcrest/src/main/java/io/sapl/hamcrest/Matchers.java)

These methods come with additional methods (e.g. `expectNextPermit`) to define multiple expectations for testing
stream-based policies.

  ```java
    .expectNextPermit(3)
  ```

More available methods are
documented [here](https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-test/src/main/java/io/sapl/test/steps/ExpectStep.java).

### Verify-Step

The verify() method completes the test definition, and triggers the evaluation of the policy/policies and verifies the
expectations.

### Examples

The following example constitutes a full minimal SAPL unit test:

  ```java
    public class B_PolicyWithSimpleFunctionTest {

    private SaplTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new SaplUnitTestFixture("policyWithSimpleFunction.sapl");
    }

    @Test
    void test() {
        fixture.constructTestCaseWithMocks()
                .givenFunction("time.dayOfWeek", Val.of("SATURDAY"), times(1))
                .when(AuthorizationSubscription.of("willi", "read", "something"))
                .expectPermit()
                .verify();
    }
}
  ```

A lot of additional examples showcasing the various features of this SAPL test framework can be found in the demo
project [here](https://github.com/heutelbeck/sapl-demos/tree/master/sapl-demo-testing/src/test/java/io/sapl/test).

### Code Coverage Reports via the SAPL Maven Plugin

For measuring the policy code coverage of SAPL policies, developers can use the SAPL Maven Plugin to analyze the
coverage and generate reports in various formats.

Currently, three coverage criteria are supported:

* **PolicySet Hit Coverage**: Measures the percentage of PolicySets that were at least once applicable to an
  AuthorizationSubscription in the tests.
* **Policy Hit Coverage**: Measures the percentage of Policies that were at least once applicable to an
  AuthorizationSubscription in the tests.
* **Condition Hit Coverage**: Measures the percentage of conditions evaluated to true or false during the tests. The
  number of conditions times two is compared with the number of positively and negatively evaluated conditions.

### Plugin Goals

| Goal                             | Description                                                                                    |
|----------------------------------|------------------------------------------------------------------------------------------------|
| sapl:enable-coverage-collection  | No description                                                                                 |
| sapl:report-coverage-information | Collect coverage information and generate reports. Print path to HTML report in the Maven log. |

### Usage

The SAPL Maven Plugin can be added to the Maven project by adding the following configuration to the `pom.xml`:

```xml

<plugin>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-maven-plugin</artifactId>
    <configuration>
        <policyHitRatio>100</policyHitRatio>
        <policyConditionHitRatio>50</policyConditionHitRatio>
    </configuration>
    <executions>
        <execution>
            <id>coverage</id>
            <goals>
                <goal>enable-coverage-collection</goal>
                <goal>report-coverage-information</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

The SAPL Maven Plugin can be invoked by calling the verify phase of the Maven build lifecyle.

`mvn verify`

### Configuration

You can specify the configuration of the SAPL Maven Plugin in the configuration section of the plugin in the pom.xml.
The plugin can be configured via the following parameters:

* **coverageEnabled**: When set to false, this parameter disables the execution of the SAPL Maven Plugin (defaultValue =
  true).
* **policyPath**: Defines the path in the classpath to the folder containing the policies under test. Specify the same
  path used in the SaplIntegrationTestFixture or the parent folder of the path to the SAPL documents in the
  SaplUnitTestFixture (defaultValue = policies).
* **outputDir**: Set this parameter to the path where generated reports should be written (per default, the Maven build
  output directory is used).
* **policySetHitRatio**: A value between 0 - 100 to define the ratio of PolicySets the tests should cover. If this ratio
  isn’t fulfilled, the SAPL Maven Plugin is going to stop the Maven lifecycle (defaultValue = 0).
* **policyHitRatio**: A value between 0 - 100 to define the ratio of Policies the tests should cover. If this ratio
  isn’t fulfilled, the SAPL Maven Plugin is going to stop the Maven lifecycle (defaultValue = 0).
* **policyConditionHitRatio**: A value between 0 - 100 to define the ratio of condition results the tests should cover.
  If this ratio isn’t fulfilled, the SAPL Maven Plugin is going to stop the Maven lifecycle (defaultValue = 0).
* **enableSonarReport**: When set to true, a coverage report with
  the [SonarQube/SonarCloud Generic Coverage Format](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/test-coverage/generic-test-data/)
  is generated. To use this coverage report in the SonarQube/SonarCloud analysis, a workaround currently needs to be
  applied, since SonarQube and SonarCloud do not import generic coverage data for languages unknown to them. Currently,
  there is also no SonarQube Language plugin for SAPL. The workaround consists of adding files with the .sapl file
  extension to a language known to SonarQube/SonarCloud and, at the same time, to ignore any issues raised in these
  files.

  First add the Sonar Maven Plugin to your Maven project (see the documentation
  from [SonarQube](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-maven/)
  and [SonarCloud](https://docs.sonarsource.com/sonarcloud/advanced-setup/ci-based-analysis/sonarscanner-for-maven/) for
  details).

  To add the coverage report, add the following parameters when you call the Maven sonar goal on your project:

    1. By default, the Sonar Maven Plugin only collects files at the paths "pom.xml,src/main/java". To collect the SAPL
       policies in the src/main/resources directory you have to specify the following two parameters:
          ```
          -Dsonar.sources=. \
          -Dsonar.inclusions=pom.xml,src/main/java/**,src/main/resources/**
          ```
    2. To tell SonarQube or SonarCloud to collect the generic coverage report you have to set the following parameter:
         ```
        -Dsonar.coverageReportPaths=target/sapl-coverage/sonar/sonar-generic-coverage.xml
        ```
    3. To add files with the .sapl file extension to a language known to SonarQube/SonarCloud you could add it for
       example
       to the yaml language with the following parameter:
        ```
       -Dsonar.yaml.file.suffixes=.yaml,.yml,.sapl
       ```
    4. To ignore any issues raised in the .sapl files, add these last three parameters:
        ```
        -Dsonar.issue.ignore.multicriteria=e1 \
        -Dsonar.issue.ignore.multicriteria.e1.ruleKey=* \
        -Dsonar.issue.ignore.multicriteria.e1.resourceKey=**/*.sapl
        ```
    5. Complete example:
        ```
       mvn clean verify sonar:sonar \
        -Dsonar.sources=. \
        -Dsonar.inclusions=pom.xml,src/main/java/**,src/main/resources/** \
        -Dsonar.coverageReportPaths=target/sapl-coverage/sonar/sonar-generic-coverage.xml \
        -Dsonar.yaml.file.suffixes=.yaml,.yml,.sapl \
        -Dsonar.issue.ignore.multicriteria=e1 \
        -Dsonar.issue.ignore.multicriteria.e1.ruleKey=* \
        -Dsonar.issue.ignore.multicriteria.e1.resourceKey=**/*.sapl
       ```

  These parameters could also be configured in your pom.xml or the global settings.xml. See
  the [Sonar Maven Plugin documentation](https://docs.sonarsource.com/sonarcloud/advanced-setup/ci-based-analysis/sonarscanner-for-maven/#configuration)
  for details (defaultValue = false).

* **enableHtmlReport**: When set to true an HTML coverage report is created. This report is similar to JaCoCo reports
  showing colorized line coverage and the number of covered branches for conditions in a line. The path to the
  index.html on the filesystem is printed in the Maven log. Terminals like Powershell allow clicking on these paths and
  opening the report directly in the browser (defaultValue = true).
* **failOnDisabledTests**: When set to true the Maven build will fail if the build has been run with -DskipTests or
  -Dmaven.skip.tests=true. When set to false coverage validation will be skipped if tests are skipped, but the build
  will not fail (defaultValue = true).

## SAPLTest DSL

There is also a DSL (Domain Specific Language) that is designed to simplify testing SAPL Policies. The DSL supports both
Unit Tests (single Policy) and Integration Tests (set of policies with a pdp configuration) and makes testing SAPL
Policies more approachable for users without a software development background.

The [SAPL Vaadin Editor](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-vaadin-editor) contains a
fragment that provides syntax highlighting, autocompletion and validation for SAPLTest Definitions. A dedicated Editor
Tab, based on this fragment, that can be used for writing tests is contained in
the [SAPL Demo Web Editor Module](https://github.com/heutelbeck/sapl-demos/tree/master/sapl-demo-web-editor).

### Executing Tests

The SAPLTest DSL aims to provide maximum flexibility regarding how tests are executed. The whole Test setup is
framework-agnostic and allows to define custom logic to use any test execution framework of your choice.
The [BaseTestAdapter](src/main/java/io/sapl/test/dsl/setup/BaseTestAdapter.java) provides all required methods to
execute tests programmatically. Examples for setups with different frameworks can be found in
the [SAPL Demo Testing DSL Module](https://github.com/heutelbeck/sapl-demos/tree/master/sapl-demo-testing-dsl).

Since JUnit is the default choice for most java based projects a dedicated Framework adapter is provided by
the [SAPL Test JUnit Module](../sapl-test-junit). In this case no custom Extension of the BaseTestAdapter is required,
and you can just include sapl-test-junit in the dependency section and create an otherwise empty TestAdapter that
extends the [JUnitTestAdapter](../sapl-test-junit/src/main/java/io/sapl/test/junit/JUnitTestAdapter.java) like shown in
the [TestAdapter](https://github.com/heutelbeck/sapl-demos/blob/master/sapl-demo-testing-dsl/junit/src/test/java/io/sapl/demo/testing/dsl/junit/TestAdapter.java)
in the sapl-demos repository.

A minimal setup would look like this:

```xml

<dependencies>
    <dependency>
        <groupId>io.sapl</groupId>
        <artifactId>sapl-test-junit</artifactId>
        <version>${sapl.version}</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
<plugins>
    <plugin>
        <artifactId>maven-surefire-plugin</artifactId>
        <executions>
            <execution>
                <phase>test</phase>
            </execution>
        </executions>
        <configuration>
            <argLine>-Dfile.encoding=UTF-8</argLine>
        </configuration>
    </plugin>
</plugins>
</build>
```

> create a TestAdapter instance in `src/test/java` that extends the [JUnitTestAdapter](../sapl-test-junit/src/main/java/io/sapl/test/junit/JUnitTestAdapter.java).
> the minimal setup is also shown in the [corresponding subfolder](https://github.com/heutelbeck/sapl-demos/tree/master/sapl-demo-testing-dsl/junit) of the sapl-demos project.

afterward any file located in `src/test/resources` (and all nested folders) ending with the SAPLTest file
extension `.sapltest` will be automatically discovered, executed and reported in the test result report.

### Advanced Configuration

A concrete implementation of the abstract [BaseTestAdapter](src/main/java/io/sapl/test/dsl/setup/BaseTestAdapter.java) also allows you to provide custom logic
to resolve Policies via an identifier (which is then used in the test definition instead of a filename) by implementing
[UnitTestPolicyResolver](src/main/java/io/sapl/test/dsl/interfaces/UnitTestPolicyResolver.java)
and [IntegrationTestPolicyResolver](src/main/java/io/sapl/test/dsl/interfaces/IntegrationTestPolicyResolver.java) and
passing them to the constructor of the BaseTestAdapter.
This enables use cases where the policies are retrieved e.g. retrieved from a remote server or a database.
Furthermore, it is possible to provide a custom implementation of
the [SaplTestInterpreter](src/main/java/io/sapl/test/dsl/interfaces/SaplTestInterpreter.java) to define how
a [SAPLTest](../sapl-test-lang/src/main/java/io/sapl/test/grammar/SAPLTest.xtext) is derived from a given input.
Last but not least it is also possible to provide a
custom [StepConstructor](src/main/java/io/sapl/test/dsl/interfaces/StepConstructor.java) to completely customize how the
single Test Steps are derived from a [SAPLTest](../sapl-test-lang/src/main/java/io/sapl/test/grammar/SAPLTest.xtext).

### Test Definition

To make the possible syntax combinations easier to understand they are presented in the following Notation:

`keyword1 keyword2 [VALUE]? keyword3 (keyword4 "[VALUE2]")*`

* keywords are required by the language itself (e.g. `using`, `with`)
* [Placeholder]() indicates that an expression of a specific type is required here
* in some places JSON is used for value definitions, the Definition is identical to
  the [SAPL Value Definition](https://sapl.io/docs/3.0.0-SNAPSHOT/sapl-reference.html#json-data-types).
  The names defined in the documentation will be used here to indicate that a JSON Value is
  required: [Number], [String], [Boolean], [Null], [Object], [Array] (if [Value] is specified any can be used)
* expressions enclosed in braces are grouped, and they symbol behind the closing brace is applied to the group
* a `?` indicates that this expression is expected 0 - 1 times
* a `*` indicates that this expression is expected 0 - n times
* a `+` indicates that this expression is expected 1 - n times

e.g.

* `keyword1 keyword2 [String] keyword3` -> valid since the part in braces is postfixed with a `*` so can be omitted
* `keyword1 keyword2 keyword3` -> valid since `?` indicates that VALUE is optional
* `keyword1 keyword2 [String] keyword3 keyword4 [Object] keyword4 [Object]` -> valid since keyword4 and [Object] build a
  group that is expected 0 - n times
* `keyword1 keyword2 keyword3 keyword4` -> invalid since [String] is not contained in group

> A extensive set of example test definitions can be found in the sapl-demos repository in the corresponding 
> [Test Definition Subfolder](https://github.com/heutelbeck/sapl-demos/tree/master/sapl-demo-testing-dsl/junit/src/test/resources).
> It contains various definitions showcasing different usecases for unit and integration tests. It also contains a lot 
> of comments to explain the different definitions, so it is a good starting point to learn the SAPLTest DSL.

#### Requirement

A SAPLTest Definition has to contain 1:n Definitions of Type [Requirement](#Requirement).
Within the [Requirement](#Requirement) there can be 1:n [Scenario](#Scenario) Definitions. It may also contain a central
[Given](#Given) Definition, that is then merged with the [Given](#Given) Definition within the [Scenario](#Scenario).
If they are identical the Scenario [Given](#Given) Definition can be omitted, to avoid redundant definitions.

> requirement "[REQUIREMENT_IDENTIFIER]" { [Given](#Given)? [Scenario](#Scenario)+ }

#### Given

Contains all the information, that is necessary to do the initial setup for the test including which Policy document(s) to
test, the PDP configuration, the Environment setup and also mocking information.

> given
> [Document](#Document)?
> [PdpVariables](#PdpVariables)?
> [PdpCombiningAlgorithm](#PdpCombiningAlgorithm)?
> [Environment](#Environment)?
> [GivenStep](#GivenStep)*

#### Document

Allows to select the Policy document(s) that should be used for the test. This definition is mandatory in either
[Requirement](#Requirement) or [Scenario](#Scenario). If none is given an error will occur. When both are defined the
[Scenario](#Scenario) overwrites the one in the [Requirement](#Requirement). There are 3 different types of Document
definitions depending on the use case.

* Use a single policy definition for testing
  > policy "[POLICY_IDENTIFIER]"
* Use a single identifier to refer to a number of policy documents and a pdp configuration, which is then resolved
depending on your overall configuration
  > set "[IDENTIFIER]"
* Use a set of identifiers for policies and an additional optional identifier for the pdp configuration
  > 'policies' "[POLICY_IDENTIFIER]" (, "[POLICY_IDENTIFIER]")+ ("with pdp configuration" [PDP_IDENTIFIER])?

#### PdpVariables

Allows to pass an JSON object that defines the environment Variables for the PDP. This overwrites the one that might be
loaded from a PDP configuration file.

> 'pdp variables [Object]'

#### PdpCombiningAlgorithm

Allows to set the [CombiningAlgorithm](#CombiningAlgorithm) to use for the test. This overwrites the one that might be
loaded from a PDP configuration file.

> 'pdp combining-algorithm [CombiningAlgorithm](#CombiningAlgorithm)'

#### Environment

Defines additional environment variables that should be available in the evaluation context. Given as an JSON object as
well

> 'environment [Object]'

#### GivenStep

Defines mocking information for attributes and functions via [MockDefinition](#MockDefinition) and PIP and
FunctionLibrary Imports via the [Import](#Import) Definition.

#### MockDefinition

* Mocking of a function optionally matching parameters and a timesCalledVerification
  > function "[NAME]" (parameters matching [FunctionParameters](#FunctionParameters)) returns [Value] (
  called [NumericAmount](#NumericAmount))
* Mocking of a function that returns a stream of values and is only invoked once
  > function "[NAME]" returns stream [Value] (, [Value])*`
* Mocking of an attribute with an optional list of return values and a timing to define when each value is emitted
  > attribute "[NAME]" (returns [Value] (, [Value])*) (with timing [Duration](#Duration))?
* Mocking of an attribute with a given parent value and optional parameters and a return value
  > attribute "[NAME]" with parent value [ValMatcher](#ValMatcher) (and
  parameters [ValMatcher](#ValMatcher) (, [ValMatcher](#ValMatcher))*)?
  returns [Value]
* Specify that virtual-time should be used
  see [Project Reactor Reference Guide](https://projectreactor.io/docs/core/release/reference/#_manipulating_time)
  > virtual-time

#### Import

allows to reference programmatically defined Imports in the concrete Implementation of the
[BaseTestAdapter](src/main/java/io/sapl/test/dsl/setup/BaseTestAdapter.java) via a name and the 
[ImportType](#ImportType). Those Imports are then registered in the PDP. For an example how to register imports in the
test adapter see the 
[TestAdapter](https://github.com/heutelbeck/sapl-demos/blob/master/sapl-demo-testing-dsl/junit/src/test/java/io/sapl/demo/testing/dsl/junit/TestAdapter.java)
in the sapl-demos repository.

> '[#ImportType] "[IMPORT_IDENTIFIER]"'

#### ImportType

Define the Type of Import you want to use. The following ones are supported:

* Import a pip given by an object instance
  > 'pip'
* Import a pip given by its class type (all methods need to be static)
  > 'static-pip'
* Import a FunctionLibrary given by an object instance
  > 'function-library'
* Import a FunctionLibrary given by its class type (all methods need to be static)
  > 'static-function-library'

#### Scenario

Defines one actual TestCase in a [Requirement](#Requirement). The Name of the TestCase has to be unique within a
[Requirement](#Requirement).

* A name for the case/scenario to be tested (will be displayed in the test result report)
  > scenario "[NAME]"
* (Optional) [Given](#Given) Definition (can be omitted when it is defined in the surrounding [Requirement](#Requirement))
* a WhenStep to define the AuthorizationSubscription to use
  > when subject [Value] attempts action [Value] on resource [Value] (with environment [Object])?
* The expectation that is tested using an [Expectation](#Expectation) Definition.

### Available Types for Test Definition

#### CombiningAlgorithm

* `deny-overrides`
* `permit-overrides`
* `only-one-applicable`
* `deny-unless-permit`
* `permit-unless-deny`

#### FunctionParameters

* A list of matchers to match against parameters (one matcher per parameter)
  > [ValMatcher](#ValMatcher) (, [ValMatcher](#ValMatcher))*

#### NumericAmount

* Expecting something once
  > 'once'
* Excepting something at least 2 times
  > [Number] times

#### Duration

* A String that represents a
  parseable [Java Duration String](https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-)
  > [String]

#### ValMatcher

* Matches any given Val
  > any
* Exact Match
  > [Value]
* Matches a val using a specific matcher
  > matching [JsonNodeMatcher](#JsonNodeMatcher)
* Matches a Val with an error (optionally with a specific matcher)
  > with error [StringOrStringMatcher](#StringOrStringMatcher)?

#### JsonNodeMatcher

* a null node
  > null
* a text node (optionally with a specific text)
  > text [StringOrStringMatcher](#StringOrStringMatcher)?
* a number node (optionally with a specific number)
  > number [Number]?
* a boolean node (optionally specify if true or false is expected)
  > boolean [Boolean]?
* an array node (optionally with a matcher)
  > array (where [JsonArrayMatcher](#JsonArrayMatcher))?
* an object node (optionally with a matcher)
  > object (where [JsonObjectMatcher](#JsonObjectMatcher))?

#### StringOrStringMatcher

allows for an exact match with a string or using a matcher

* exact match
  > [String]
* a null string
  > null
* a blank string
  > blank
* an empty string
  > empty
* a null or empty string
  > null-or-empty
* a null or blank string
  > null-or-blank
* equal to a string with compressed whitespaces
  > equal to [String] with compressed whitespace
* equal to a string case-insensitive
  > equal to [String] case-insensitive
* matches a given regex
  > with regex [String]
* matches the start of the string (optionally case-insensitive)
  > starting with [String] (case-insensitive)?
* matches the end of the string (optionally case-insensitive)
  > ending with [String] (case-insensitive)?
* checks if the string contains the given string (optionally case-insensitive)
  > containing [String] (case-insensitive)?
* checks if the string contains the given substrings in order
  > containing stream [String] (, [String])* in order
* checks if the string has a specific length
  > with length [Number]

#### JsonArrayMatcher

* a set of matchers where each matcher is applied to an array element in order
  > [ [JsonNodeMatcher](#JsonNodeMatcher) (, [JsonNodeMatcher](#JsonNodeMatcher))* ]

#### JsonObjectMatcher

* a matcher used to match a json object
  > { [JsonObjectMatcherPair](#JsonObjectMatcherPair) (and [JsonObjectMatcherPair](#JsonObjectMatcherPair))* }

#### JsonObjectMatcherPair

* specifies which matcher should be used for a given key
  > [String] is [JsonNodeMatcher](#JsonNodeMatcher)

#### Expectation

* expect a single AuthorizationDecision
  > expect [AuthorizationDecision](#AuthorizationDecision)
* expect a single AuthorizationDecision that matches a set of matchers
  > expect decision [AuthorizationDecisionMatcher](#AuthorizationDecisionMatcher) (, [AuthorizationDecisionMatcher](#AuthorizationDecisionMatcher))*
* build a chain of alternating expects and adjustments to verify behavior (applied in order)
  > [ExpectOrAdjustBlock](#ExpectOrAdjustBlock)+

#### ExpectOrAdjustBlock

Defines a Block that either contains a number of expect steps or adjustment steps. The blocks need to alternate and
the chain has to end with a expect block.

* ExpectBlock
  > expect (- [ExpectStep](#ExpectStep))+
* AdjustBlock
  > then (- [AdjustStep](#AdjustStep))+

#### AuthorizationDecisionType

* `permit`
* `deny`
* `indeterminate`
* `notApplicable`

#### AuthorizationDecision

* a single AuthorizationDecision with optional exact match for Obligations, Resource and Advice
  > [AuthorizationDecisionType](#AuthorizationDecisionType) (with obligations [Value] (, [Value]) *) (with
  resource [Value] (, [Value]) *) (with advice [Value] (, [Value]) *)

#### AuthorizationDecisionMatcher

* matches any decision
  > any
* matches a specific type of the AuthorizationDecision
  > is [AuthorizationDecisionType](#AuthorizationDecisionType)
* checks for an obligation or advice in the AuthorizationDecision (optionally with a specific matcher)
  > with [AuthorizationDecisionMatcherType](#AuthorizationDecisionMatcherType) [ExtendedObjectMatcher](#ExtendedObjectMatcher)?
* checks for a resource in the AuthorizationDecision (optionally with a specific matcher)
  > with resource [DefaultObjectMatcher](#DefaultObjectMatcher)?

#### AuthorizationDecisionMatcherType

* `obligation`
* `advice`

#### DefaultObjectMatcher

* exact match
  > equals [Value]
* match with matcher
  > matching [JsonNodeMatcher](#JsonNodeMatcher)

#### ExtendedObjectMatcher

* DefaultObjectMatcher can be used here as well
  > [DefaultObjectMatcher](#DefaultObjectMatcher)
* checks if a key is present (optionally with a specific value matcher)
  > containing key (with value matching [JsonNodeMatcher](#JsonNodeMatcher))?

#### ExpectStep

Defines the current expect step to be used for evaluating the current [AuthorizationDecision](#AuthorizationDecision).

* expect a AuthorizationDecision with a specific type and how often it is emitted in sequence
  > [AuthorizationDecisionType](#AuthorizationDecisionType) [NumericAmount](#NumericAmount)
* expect a specific AuthorizationDecision
  > [AuthorizationDecision](#AuthorizationDecision)
* expect a decision matching a matcher
  > decision [AuthorizationDecisionMatcher](#AuthorizationDecisionMatcher) (, [AuthorizationDecisionMatcher](#AuthorizationDecisionMatcher))*
* expect no event for a specific duration
  > no-event for [Duration](#Duration)

#### AdjustStep

Used for the Adjustment of a mocked attribute in between expect steps.

* adjust the return value of a previously mocked attribute (via [MockDefinition](#MockDefinition))
  > let "[NAME]" return [Value]
* wait for a specific duration
  > wait [Duration]

## Top-level packages

**dsl**
Contains all the code necessary for interpreting the SAPLTest DSL defined in [sapl-test-lang](../sapl-test-lang) and
provides functionality to execute the generated tests.

**integration**:
Contains the code for constructing a `SaplIntegrationTestFixture` for policy integration tests.

**lang**:
Contains the subclasses of `sapl-lang` classes. Here specific methods of the classes implementing the functionality
behind the SAPL-DSL are overridden to record SAPL coverage hits using
the [sapl-coverage-api](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-coverage-api).

**mocking**:
Contains an implementation of an `AttributeContext` and a `FunctionContext`. Via these implementations, the policy
tester can define mocks with return values for the attributes or functions in the SAPL policies under test.

**steps**:
Contains the Step-Interfaces used in the Step-Builder-Pattern to define a SAPL test case. In addition, a default
implementation of the pattern is provided and reused in the `integration` and `unit` packages in their test fixtures.

**unit**:
Contains the code for constructing a `SaplUnitTestFixture` for policy document unit tests.

**utils**:
Some Utility classes.

**verification**:
Contains the code enabling the verification of calls to the mocks.
