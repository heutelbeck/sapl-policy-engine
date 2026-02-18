---
layout: default
title: Spring Integration
parent: SAPL Reference
nav_order: 600
has_children: true
has_toc: false
---

## Spring Integration

This section will document SAPL's integration with Spring Boot and Spring Security.

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
