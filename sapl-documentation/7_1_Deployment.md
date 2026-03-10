---
layout: default
title: Deployment
parent: SAPL Node
nav_order: 701
---

## SAPL Node Deployment

SAPL Node is a standalone, headless PDP server that exposes the PDP via an HTTP API. It supports filesystem directories, signed bundles, and remote bundle fetching, and is designed for centralized policy management across multiple applications.

### Choosing a Deployment Model

SAPL provides three ways to deploy a PDP. The right choice depends on how policies are managed and how many applications need authorization decisions.

- **Embedded PDP**: The PDP runs inside a Java application. Policies are loaded from the classpath, a filesystem directory, or signed bundles. No network hop for decisions. Suitable when policies are deployed alongside the application and each service manages its own policies. See [Java API](../6_2_JavaApi/).
- **SAPL Node (standalone PDP server)**: A dedicated server process that multiple applications query via HTTP. Policies are managed in one place and served to all consumers. Suitable when policies are shared across services, need centralized management, or must be updated independently of application deployments.
- **Remote PDP client**: A lightweight client library that connects to a SAPL Node via HTTP. Applications use this library to query a centrally managed PDP instead of evaluating policies locally.

These models can be combined: some services may embed their own PDP for low-latency local decisions while also querying a SAPL Node for shared cross-service policies.

### Planned Topics

- **Deployment formats:** Running SAPL Node as a standalone JAR, Docker container, or GraalVM native image. Prerequisites, directory layout, and startup.
- **CLI reference:** The `bundle` subcommands (create, sign, verify, inspect, keygen) and the `generate` subcommands (basic, apikey) for credential management. Migrate from sapl-node README.
- **Choosing embedded vs. standalone:** Decision guide -- when to embed the PDP, when to run SAPL Node, and when to use both. Trade-offs: latency (local vs. network), operational complexity, policy update independence, multi-language support.

For configuration, see [Configuration](../7_2_Configuration/). For authentication and TLS, see [Security](../7_6_Security/). For health checks and metrics, see [Monitoring](../7_7_Monitoring/).

> **Planned content.** Deployment formats and CLI reference will be migrated from the `sapl-node` README. The decision guide is new content.
