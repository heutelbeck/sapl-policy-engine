---
layout: default
title: Writing test cases
#permalink: /reference/Writing-test-cases/
parent: Testing SAPL policies
grand_parent: SAPL Reference
nav_order: 7
---

## Writing test cases

The Step-Builder-Pattern is used for defining the concrete test case. It consists of the following four steps:

- Given-Step: Define mocks for attributes and functions
- When-Step: Specify the `AuthorizationSubscription`
- Expect-Step: Define expectations for generated `AuthorizationDecision`
- Verify-Step: Verify the generated `AuthorizationDecision`
<br><br>

![StepBuilderPatternForSaplTest_English](/docs/XXXSAPLVERSIONXXX/assets/sapl_reference_images/StepBuilderPatternForSaplTest_English.svg)

<br><br>
Starting with constructTestCaseWithMocks() or constructTestCase() called on the fixture, the test case definition process is started at the Given-Step or the When-Step.

### Given-Step

**Mocking of functions**:

- the `givenFunction` methods can be used to mock a function returning a `Val` specified in the method parameters for every call. 
  -- a single value can be specified

    ```java
    .givenFunction("time.dayOfWeek", Val.of("SATURDAY"))
    ```
  -- a single value only returned when the parameters of the function call match some expectations

    ```java
    .givenFunction("corp.subjectConverter",
        whenFunctionParams(is(Val.of("USER")), is(Val.of("nikolai"))), Val.of("ROLE_ADMIN"))
    ```
  -- or a Lambda-Expression evaluating the parameters of the function call

    ```java
    .givenFunction("company.complexFunction", (FunctionCall call) -> {
    
        //probably one should check for number and type of parameters first
        Double param0 = call.getArgument(0).get().asDouble();
        Double param1 = call.getArgument(1).get().asDouble();
    
        return param0 % param1 == 0 ? Val.of(true) : Val.of(false);
    })
    ```
  -- and verify the number of calls to this mock

    ```java
    .givenFunction("time.dayOfWeek", Val.of("SATURDAY"), times(1))
    ```
- `givenFunctionOnce` can specify a `Val` or multiple `Val`\-Objects which are emitted once (in a sequence) when this mocked function is called 
  -- a single value

    ```java
    .givenFunctionOnce("time.secondOf", Val.of(4))
    .givenFunctionOnce("time.secondOf", Val.of(5))
    ```
  -- or a sequence of values

    ```java
    .givenFunctionOnce("time.secondOf", Val.of(3), Val.of(4), Val.of(5))
    ```

**Mocking of attributes**:

- `givenAttribute` methods can mock attributes 
  -- to return one or more `Val`

    ```java
    .givenAttribute("time.now", timestamp0, timestamp1, timestamp2)
    ```
  -- to return a sequence of `Val` in an interval of `Duration`. Using `withVirtualTime` activates the virtual time feature of Project Reactor

    ```java
    .withVirtualTime()
    .givenAttribute("time.now", Duration.ofSeconds(10), timestamp0, timestamp1, timestamp2, timestamp3, timestamp4, timestamp5)
    ```

{: .warning }
> The virtual time feature can be used with real time-based PIPs registered the fixture level. Virtual time is "no silver bullet" to cite the [Project Reactor Reference Guide](https://projectreactor.io/docs/core/release/reference/#_manipulating_time). It says further that "\[v\]irtual time also gets very limited with infinite sequences, which might hog the thread on which both the sequence and its verification run."


  - to mark an attribute to be mocked and specify return values in a sequence next to expectations

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
  - to mock an attribute depending on the parent value

    ```java
    .givenAttribute("test.upper", whenParentValue(val("willi")), thenReturn(Val.of("WILLI")))
    ```
  - to mock an attribute depending on the parent value and every value the arguments are called for

    ```java
    .givenAttribute("pip.attributeWithParams", whenAttributeParams(parentValue(val(true)), arguments(val(2), val(2))), thenReturn(Val.of(true)))
    ```

Further mock types or overloaded methods are available [here](https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-test/src/main/java/io/sapl/test/steps/GivenStep.java).

### When-Step

The next defines the `AuthorizationSubscription` for the policy evaluation.

- pass an `AuthorizationSubscription` created by it’s factory methods

  ```java
  .when(AuthorizationSubscription.of("willi", "read", "something"))
  ```
- pass a JSON-String to be parsed to an `AuthorizationSubscription` via the framework

  ```java
  .when("{\"subject\":\"willi\", \"action\":\"read\", \"resource\":\"something\", \"environment\":{}}")
  ```
- pass a `JsonNode` object of the Jackson-Framework ([Reference](https://fasterxml.github.io/jackson-databind/javadoc/2.7/com/fasterxml/jackson/databind/JsonNode.html))

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

This step defines the expected `AuthorizationDecision`.

- check only the Decision via

  ```java
  .expectPermit()
  .expectDeny()
  .expectIndeterminate()
  .expectNotApplicable()
  ```
- pass a `AuthorizationDecision` object to be checked for equality

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
- use a predicate function to manually define checks

  ```java
  .expect((AuthorizationDecision dec) -> {
      // some complex and custom assertions
      if(dec.getObligations().isEmpty()) {
          return true;
      }
      return false;
  })
  ```
- Hamcrest matchers provided by the sapl-hamcrest module can be used to express complex expectations on the decision, obligations, advice or resources of the `AuthorizationDecision`

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

  All available `Matcher<AuthorizationDecision>` can be found [here](https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-hamcrest/src/main/java/io/sapl/hamcrest/Matchers.java)

These methods come with additional methods (e.g., `expectNextPermit`) to define multiple expectations for testing stream-based policies.

```java
.expectNextPermit(3)
```

More available methods are documented [here](https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-test/src/main/java/io/sapl/test/steps/ExpectStep.jav).

### Verify-Step

The `verify()` method completes the test definition, and triggers the evaluation of the policy/policies and verifies the expectations.
