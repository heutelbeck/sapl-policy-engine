---
layout: default
title: Introduction
has_children: true
parent: SAPL Reference
nav_order: 10
has_toc: false
---

> **Introduction Series:** **1. Overview** • [2. Subscriptions](../1_2_AuthorizationSubscriptions/) • [3. Policy Structure](../1_3_Structure_of_a_SAPL-Policy/) • [4. Decisions](../1_4_AuthorizationDecisions/) • [5. Attributes](../1_5_AccessingAttributes/) • [6. Getting Started](../1_6_GettingStarted/)

# SAPL - Streaming Attribute Policy Language

## Introduction

SAPL (Streaming Attribute Policy Language) is a domain-specific language and authorization engine for expressing and evaluating access control policies. It supports both request/response and publish/subscribe authorization protocols, both based on JSON. For the design decisions and architectural context behind SAPL, see [Why SAPL?](../0_1_WhySAPL/).

### SAPL at a Glance

First, here's what a SAPL policy looks like:

```sapl
policy "compartmentalize read access by department"
permit
    resource.type == "patient_record" & action == "read";
    subject.role == "doctor";
    resource.department == subject.department;
```

**In plain English:** *"Permit reading patient records if the reader is a doctor from the same department as the record."*

> **Attributes** 
> 
> In this policy, `subject.role`, `resource.type`, and `subject.department` are all so-called attributes. 
> The comparison `resource.department == subject.department` works for any department without modification. 
> This is an advantage of ABAC over RBAC: instead of creating separate roles like "cardiologyDoctor", 
> "radiologyDoctor", "neurologyDoctor" (and updating them with every new department), one policy handles all 
> departments by comparing attributes. Add ten new departments - the policy needs no changes. 
> Attributes are either sent with the authorization question or looked up dynamically. 
> In this example they come from the authorization question only.

> **Experiment hands-on:** Try modifying a policy in the [SAPL Playground](https://playground.sapl.io/) - no installation required.

Now let's see how SAPL policies work in practice.

### Data Flow

SAPL policies are written in the SAPL domain-specific language. These policies
evaluate **JSON authorization subscriptions** (input) to produce a sequence of **JSON authorization decisions**
(output). Internally, SAPL's data model extends JSON with `undefined` values and error states
to enable robust policy evaluation.

```mermaid
graph LR
    Subject[Subject<br/>User/System]
    PEP[Policy Enforcement Point<br/>PEP]
    RAP[Resource Access Point<br/>RAP]    
    PDP[Policy Decision Point<br/>PDP]
    PRP[Policy Retrieval Point<br/>PRP]
    PIP[Policy Information Point<br/>PIP]
    
    Subject -- 1\. Attempts Action<br/>e.g., read data --> PEP
    PEP -- 2\. Authorization Subscription<br/>JSON --> PDP
    PDP -- 3\. Retrieve Policies --> PRP
    PDP -- 4\. Fetch/Monitor<br/>Attributes  --> PIP
    PDP -- 5\. Authorization Decision<br/>JSON --> PEP
    PEP -- 6a\. Deny Access --> Subject
    PEP -- 6b\. Execute Action<br/>e.g., read data --> RAP
    PEP -- 7\. Deliver Action Result<br/>e.g., data --> Subject
```

A typical scenario for the application of SAPL would be a subject (e.g., a user or system) attempting to take action (e.g., read or cancel an order) on a protected resource (e.g., a domain object of an application or a file). The subject makes a subscription request to the system (e.g., an application) to execute the action with the resource. The system implements a **policy enforcement point (PEP)** protecting its resources. The PEP collects information about the subject, action, resource, and potential other relevant data in an authorization subscription request and sends it to a policy decision point (PDP) that checks SAPL policies to decide if it grants access to the resource. This decision is packed in an authorization decision object and sent back to the PEP, which either grants access or denies access to the resource depending on the decision. The PDP subscribes to all data sources for the decision, and new decisions are sent to the PEP whenever indicated by the policies and data sources.

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


### The "Streaming" in SAPL
Traditional access control systems follow a simple request-response pattern where the PEP
asks for a decision, the PDP provides an answer, and the interaction ends.

> Traditional access control: PEP asks -> PDP answers once -> done.

SAPL takes a fundamentally different approach through its publish/subscribe model. When a PEP subscribes
to an authorization decision, the PDP sends an initial decision and then continues to monitor
policy-relevant conditions. Whenever conditions change that affect the authorization decision, the
PDP automatically pushes a new decision to the PEP, which can then update its enforcement
accordingly.

> SAPL's approach: PEP subscribes -> PDP sends initial decision -> PDP pushes new decisions when conditions change -> PEP updates enforcement accordingly.

Decisions can change for multiple reasons:

**Attribute changes**: Policy Information Points (PIPs) return new values. Time passes (crossing business hours boundaries), user roles change (promotion to different department), resource status updates (document marked confidential), or external system data changes (quota exhausted).

**Policy changes**: Administrators update, add, or remove policies in the policy store. For example, adding a new time restriction or updating department access rules takes effect immediately for all active subscriptions without requiring application redeployment or users to log out and back in.

**Configuration changes**: Changes to how the PDP resolves conflicts between multiple policies or updates to shared policy variables trigger re-evaluation.

The PEP doesn't need to know WHY a decision changed, only that it should update enforcement accordingly. This enables continuous authorization that adapts to changing conditions in real-time and allows policy updates without application restarts or redeployment.

This streaming model enables continuous authorization for long-running operations
and allows policies to respond in real-time to changing conditions.

For a detailed comparison of SAPL with other authorization engines and the design decisions behind it, see [Why SAPL?](../0_1_WhySAPL/). The following sections explain the basic concepts of SAPL policies, authorization decisions, attribute access, and how to get started with a running PDP.
