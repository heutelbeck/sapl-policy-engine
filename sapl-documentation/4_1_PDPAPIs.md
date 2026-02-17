---
layout: default
title: PDP APIs
#permalink: /reference/PDP-APIs/
has_children: true
parent: SAPL Reference
nav_order: 4
has_toc: false
---

## PDP APIs

SAPL defines two APIs for interacting with the PDP:

- **HTTP API**: A network API for deploying a standalone PDP server (SAPL Node). Supports both streaming (NDJSON over long-lived HTTP connections) and one-shot (standard JSON request-response) endpoints. Any programming language that can make HTTP requests can use this API.
- **Java API**: A reactive API based on Project Reactor for embedding a PDP directly in Java applications or connecting to a remote PDP server. Spring Boot applications can use the SAPL starter for automatic configuration and Spring Security integration.

Both APIs expose the same authorization semantics: single subscriptions (streaming and one-shot) and multi-subscriptions (streaming and one-shot batch).
