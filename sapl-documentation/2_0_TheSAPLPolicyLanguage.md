---
layout: default
title: The SAPL Policy Language
has_children: true
nav_order: 100
has_toc: false
---

## The SAPL Policy Language

SAPL (Streaming Attribute Policy Language) is a domain-specific language for expressing and evaluating access control policies. It supports both request/response and publish/subscribe authorization protocols, both based on JSON. The architecture follows the terminology defined by [RFC 2904 "AAA Authorization Framework"](https://tools.ietf.org/html/rfc2904).

![SAPL_Architecture.svg](/docs/XXXSAPLVERSIONXXX/assets/sapl_reference_images/SAPL_Architecture.svg)

### SAPL at a Glance

Here is what a SAPL policy looks like:

```sapl-demo
policy "compartmentalize read access by department"
permit
    resource.type == "patient_record" & action == "read";
    subject.role == "doctor";
    resource.department == subject.department;
```
<!-- sapl-subscription
{"subject":{"role":"doctor","department":"cardiology"},"action":"read","resource":{"type":"patient_record","department":"cardiology"}}
-->

**In plain English:** *"Permit reading patient records if the reader is a doctor from the same department as the record."*

> **Attributes**
>
> In this policy, `subject.role`, `resource.type`, and `subject.department` are all so-called attributes.
> The comparison `resource.department == subject.department` works for any department without modification.
> This is an advantage of ABAC over RBAC: instead of creating separate roles like "cardiologyDoctor",
> "radiologyDoctor", "neurologyDoctor" (and updating them with every new department), one policy handles all
> departments by comparing attributes. Add ten new departments and the policy needs no changes.
> Attributes are either sent with the authorization question or looked up dynamically.
> In this example they come from the authorization question only.

### Data Flow

SAPL policies evaluate **JSON authorization subscriptions** (input) to produce a sequence of **JSON authorization decisions** (output). Internally, SAPL's data model extends JSON with `undefined` values and error states to enable robust policy evaluation.

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

A typical scenario: a subject (e.g., a user or system) attempts to take action (e.g., read or cancel an order) on a protected resource (e.g., a domain object or a file). The system implements a **policy enforcement point (PEP)** protecting its resources. The PEP collects information about the subject, action, resource, and potential other relevant data in an authorization subscription and sends it to a **policy decision point (PDP)** that evaluates SAPL policies to decide if it grants access. The decision is sent back to the PEP, which either grants or denies access. The PDP subscribes to all data sources referenced by the policies, and new decisions are sent to the PEP whenever indicated by the policies and data sources.

### Streaming and One-Shot Evaluation

SAPL's authorization protocol operates in two modes: **streaming** and **one-shot**.

In **streaming mode**, the PEP subscribes to a decision stream by sending an authorization subscription to the PDP. The PDP evaluates the subscription against all applicable policies, returns an initial authorization decision, and then keeps the subscription open. Whenever policies change, external attributes update, or environment conditions shift, the PDP automatically pushes a new decision to the PEP. The PEP does not need to re-request authorization; updates arrive as they happen.

In **one-shot mode**, the PEP sends the same authorization subscription but receives a single decision and the connection closes. This is the traditional request-response pattern, suitable for use cases where continuous updates are not needed. For policies that do not access external attributes (PIPs), the PDP evaluates the subscription on a fully synchronous code path with no reactive or asynchronous overhead.

The same policies serve both modes. The PDP does not require separate policies for streaming and one-shot evaluation.

### Reference

Start with [Authorization Subscriptions](../2_1_AuthorizationSubscriptions/) and [Policy Structure](../2_4_PolicyStructure/) for the fundamentals, then explore the detailed pages for each language feature.
