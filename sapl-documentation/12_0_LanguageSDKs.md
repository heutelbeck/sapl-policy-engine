---
layout: default
title: Language SDKs and Clients
parent: SAPL Reference
nav_order: 650
has_children: true
has_toc: false
---

## Language SDKs and Clients

SAPL provides multiple ways to connect applications to a Policy Decision Point (PDP). This section covers the available client APIs and language SDKs.

### Available Interfaces

- **[Java API](../4_3_JavaAPI/):** A reactive API based on Project Reactor for embedding a PDP directly in Java applications or connecting to a remote PDP server. Spring Boot applications can use the SAPL starter for automatic configuration and Spring Security integration.
- **[HTTP API](../4_2_HTTPServer-SentEventsAPI/):** A network API for any programming language that can make HTTP requests. Supports both streaming (NDJSON) and one-shot (JSON) endpoints.

### Planned SDKs

- **C# / .NET SDK:** For integrating SAPL authorization into .NET applications.
- **Python SDK:** For integrating SAPL authorization into Python applications.

All client interfaces expose the same authorization semantics: single subscriptions (streaming and one-shot) and multi-subscriptions (streaming and one-shot batch).
