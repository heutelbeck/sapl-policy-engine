---
layout: default
title: Introduction
has_children: true
parent: SAPL Reference
nav_order: 1
#grand_parent: UI Components
#permalink: /reference/Introduction/
has_toc: false
---

# SAPL - Streaming Attribute Policy Language

Dominic Heutelbeck Version 3.0.0-SNAPSHOT

## Introduction

SAPL (Streaming Attribute Policy Language) describes a **domain-specific language (DSL)** for expressing access control policies and a **publish/subscribe protocol** based on [JSON](http://json.org/). Policies expressed in SAPL describe conditions for access control in applications and distributed systems. The underlying policy engine implements a variant of Attribute-based Access control (ABAC) which enables processing of data streams and follows reactive programming patterns. Namely, the SAPL policy engine implements Attribute Stream-based Access Control (ASBAC).

A typical scenario for the application of SAPL would be a subject (e.g., a user or system) attempting to take action (e.g., read or cancel an order) on a protected resource (e.g., a domain object of an application or a file). The subject makes a subscription request to the system (e.g., an application) to execute the action with the resource. The system implements a **policy enforcement point (PEP)** protecting its resources. The PEP collects information about the subject, action, resource, and potential other relevant data in an authorization subscription request and sends it to a policy decision point (PDP) that checks SAPL policies to decide if it grants access to the resource. This decision is packed in an authorization decision object and sent back to the PEP, which either grants access or denies access to the resource depending on the decision. The PDP subscribes to all data sources for the decision, and new decisions are sent to the PEP whenever indicated by the policies and data sources.

There exist several proprietary platforms dependent or standardized languages, such as [XACML](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html), for expressing policies. SAPL brings several advantages over these solutions:

- **Universality**. SAPL offers a standard, generic, platform-independent language for expressing policies.
- **Separation of Concerns**. Applying SAPL to a domain model is relieved from modeling many aspects of access control. SAPL favors configuration at runtime over implementation and re-deployment of applications.
- **Modularity and Distribution**. SAPL allows managing policies in a modular fashion allowing the distribution of authoring responsibilities across teams.
- **Expressiveness**. SAPL provides access control schemata beyond the capabilities of most other practical languages. It allows for attribute-based access control (ABAC), role-based access control (RBAC), forms of entity-based access control (EBAC), and parameterized attribute access and attribute streaming.
- **Human Readability**. The SAPL syntax is designed from the ground up to be easily readable by humans. Basic SAPL is easy to pick up for getting started but offers enough expressiveness to address complex access control scenarios.
- **Transformation and Filtering**. SAPL allows transforming resources and filtering data from resources (e.g., blacken the first digits of a credit card number or hiding birth dates by assigning individuals into age groups).
- SAPL supports **session and data stream-based applications** and offers low-latency authorization for interactive applications and data streams.
- SAPL supports **JSON-driven APIs** and integrates easily with modern JSON-based APIs. The core data model of SAPL is JSON offering straightforward reasoning over such data and simple access to external attributes from RESTful JSON APIs.
- SAPL supports **Multi-Subscriptions**. SAPL allows bundling multiple authorization subscriptions into one multi-subscription, thus further reducing connection time and latency. The following sections will explain the basic concepts of SAPL policies and show how to integrate SAPL into a Java application easily. Afterward, this document explains the different parts of SAPL in more detail.
