---
layout: default
title: SDKs and APIs
nav_order: 600
has_children: true
has_toc: false
---

## SDKs and APIs

SAPL provides multiple ways to connect applications to a Policy Decision Point (PDP).

### APIs

- **[HTTP and RSocket API](../6_1_HTTPApi/):** Network APIs for any programming language. HTTP uses JSON over REST; RSocket uses protobuf over persistent TCP or Unix domain sockets for high-throughput workloads. Both offer the same five operations.
- **[Java API](../6_2_JavaApi/):** A reactive API based on Project Reactor for embedding a PDP directly in Java applications or connecting to a remote PDP server via HTTP or RSocket.

### Framework SDKs

- **[Spring](../6_3_Spring/):** Annotation-driven and filter-based authorization for Spring Security and Spring WebFlux.
- **[NestJS](../6_4_NestJS/):** Guards and decorators for NestJS applications.
- **[Django](../6_5_Django/):** Decorators for Django views and services.
- **[Flask](../6_6_Flask/):** Decorators for Flask routes and services.
- **[FastAPI](../6_7_FastAPI/):** Decorators for FastAPI endpoints with async and streaming support.
- **[Tornado](../6_8_Tornado/):** Decorators for Tornado handlers with async and streaming support.
- **[FastMCP](../6_9_FastMCP/):** Middleware and per-component authorization for MCP servers.
- **[.NET](../6_10_DotNet/):** Attributes and customizers for ASP.NET Core applications.

All SDKs and APIs expose the same authorization semantics: single subscriptions (streaming and one-shot) and multi-subscriptions (streaming and one-shot batch).
