---
layout: default
title: Spring Security
parent: Integration
grand_parent: SAPL Reference
nav_order: 604
---

## Spring Security

### Policy Enforcement Points

In practice, the PEP and RAP are components of the system the user is currently interacting with. For example, some interaction by the user triggers a call to a domain-specific method or function which would then on behalf of the user access some resource and deliver the result. In this case the function is the RAP, and the code wrapping the function which is performing the access control logic is the PEP.

```java
RiskAssessment estimateRiskForCustomer(UUID customerId) {
    // Here the policy enforcement point code starts
    var subscription = AuthorizationSubscription.of("johnDoe", "estimate customer risk", customerId);
    var decision = pdp.decideOnceBlocking(subscription);

    if (decision.decision() != Decision.PERMIT) {
        throw new AccessDeniedException();
    }
    // Perform additional access-control logic here
    // (e.g., handle obligations, advice, transformations)

    // Here the resource access point logic starts
    // Call database, perform risk assessment ...
    return riskAssessment;
    // Here the PEP ends. Note that a PEP typically wraps a RAP
}
```

Think of it this way: if your business method is `RiskAssessment estimateRisk(UUID id)`, then a PEP wraps it to produce a method that checks authorization first and only calls the original if access is permitted.

More formally:
- Let `A` be the domain of input parameters (e.g., resource identifiers)
- Let `B` be the codomain of results (e.g., domain objects, computed values)
- Let `Error` be the type representing access denial

If the Resource Access Point is a function `RAP: A -> B`, then the Policy Enforcement Point is a higher-order function that takes the RAP and returns a new function: `PEP: (A -> B) -> (A -> (Error ∪ B))`

The PEP transforms the RAP by wrapping it with authorization logic, returning a new function that either yields an error (access denied) or the original result from the RAP.

At first glance, this appears to be significant overhead. However, this is how a lot of real-world code looks when dealing with complex access control requirements that go beyond simple role-based access control.

**The problem without SAPL:**
* The result often is code where the separation of concerns between domain logic and access control is not made explicit.
* It makes both the domain logic and the access control harder to test, as they always have to be tested together, making wiring up tests complicated and exploding the test space.
* The code is harder to maintain as access control code needs to be wired up manually, and changes to access control policies often need to be reflected in many places at once.

**The SAPL solution:**

SAPL ships with several framework integrations which support developers to achieve a clean separation of concerns and a more declarative style of access control.

A typical pattern in different languages is to add annotations or decorators to functions. The language or framework will then automatically wire up the PEP logic, wrapping the function in question. For example in Java, using the Spring Framework with the SAPL integration library, the example above would look like this:

```java
@PreEnforce(action = "estimate customer risk") // Automatically wraps method in PEP logic
public RiskAssessment estimateRiskForCustomer(UUID customerId) {
    // Call database, perform risk assessment ...
    return riskAssessment;
}
```

The `@PreEnforce` annotation tells the framework to automatically wrap this method with PEP logic. The authorization decision is made before the method executes, and access is only granted if the decision is `PERMIT`.

### Planned Topics

- **Spring Security annotations:** `@PreEnforce`, `@PostEnforce`, `@EnforceTillDenied`, `@EnforceDropWhileDenied`, `@EnforceRecoverableIfDenied`
- **Constraint handlers:** Implementing and registering obligation and advice handlers
- **Method security:** Securing service methods with SAPL policies
- **HTTP request security:** Securing HTTP endpoints with SAPL policies
- **Query manipulation:** Filtering query results with SAPL (MongoDB, R2DBC)
- **Configuration properties:** All `io.sapl.*` properties reference
- **Health indicator:** Spring Boot Actuator health endpoint for the PDP
- **JWT and OAuth2:** Injecting JWT claims into authorization subscriptions

> **Planned content.** This page will be populated by migrating and expanding content from the `sapl-spring-boot-starter` README.
