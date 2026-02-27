---
layout: default
title: Integration
nav_order: 600
has_children: true
has_toc: false
---

## Integration

SAPL provides multiple ways to connect applications to a Policy Decision Point (PDP). This section covers the available client APIs and framework integrations.

### Available Interfaces

- **[HTTP API](../6_1_HTTPApi/):** A network API for any programming language that can make HTTP requests. Supports both streaming (Server-Sent Events) and one-shot (JSON) endpoints.
- **[Java API](../6_2_JavaApi/):** A reactive API based on Project Reactor for embedding a PDP directly in Java applications or connecting to a remote PDP server. Spring Boot applications can use the SAPL starter for automatic configuration and Spring Security integration.
- **[Multi-Subscriptions](../6_3_MultiSubscriptions/):** Batching multiple authorization subscriptions into a single request for efficient bulk authorization.
- **[Spring Security](../6_4_SpringIntegration/):** Integrating SAPL with Spring Security for annotation-driven and filter-based authorization.
- **[NestJS](../6_5_NestJS/):** Guards and decorators for integrating SAPL authorization into NestJS applications.

All client interfaces expose the same authorization semantics: single subscriptions (streaming and one-shot) and multi-subscriptions (streaming and one-shot batch).
