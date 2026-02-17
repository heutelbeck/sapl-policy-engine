---
layout: default
title: PEP
#permalink: /reference/pep/
parent: Reference Architecture
grand_parent: SAPL Reference
nav_order: 2
---

## Policy Enforcement Point (PEP)

The PEP is a software entity that intercepts actions taken by users within an application. Its task is to obtain a decision on whether the requested action should be allowed and accordingly either let the application process the action or deny access. For this purpose, the PEP includes data describing the subscription context (like the subject, the resource, the action, and other environment information) in an authorization subscription object which the PEP hands over to a PDP. The PEP subsequently receives an authorization decision object containing a decision and optionally a resource, obligations, and advice.

The PEP must let the application process the action if the decision is `PERMIT`. If the authorization decision object also contains `obligations`, the PEP must fulfill these obligations. Proper fulfillment is an additional requirement for granting access. If the decision is not `PERMIT` or the obligation cannot be fulfilled, the PEP must deny access. Policies may contain instructions to alter the resource (like blackening certain information, e.g., credit card numbers). If present, the PEP should ensure that the application only reveals the resource contained in the authorization decision object.

> A PEP strongly depends on the application domain. SAPL comes with a default PEP implementation using a passed in constraint handler service to handle obligations and advice contained in an authorization decision. Developers should integrate PEPs with the platforms and frameworks they are using. SAPL ships with a set of modules for deep integration with Spring Security and Spring Boot.

