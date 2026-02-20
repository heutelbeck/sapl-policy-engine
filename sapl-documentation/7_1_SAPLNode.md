---
layout: default
title: SAPL Node
parent: Deployment
grand_parent: SAPL Reference
nav_order: 701
---

## SAPL Node Deployment

SAPL Node is a standalone, headless PDP server that exposes the PDP via an HTTP API. It supports filesystem directories, signed bundles, and remote bundle fetching, and is designed for centralized policy management across multiple applications.

### Deployment Options

SAPL provides three ways to deploy a PDP:

- **Embedded PDP**: Runs inside a Java (or any other JVM language) application with policies loaded from the classpath, a filesystem directory, or signed bundles. Suitable for single-instance applications or microservices where policies are deployed alongside the application. See [Java API](../6_2_JavaApi/).
- **SAPL Node**: A standalone, headless PDP server that exposes the PDP via an HTTP API. Supports filesystem directories, signed bundles, and remote bundle fetching. Designed for centralized policy management across multiple applications.
- **Remote PDP client**: A lightweight client library that connects to a SAPL Node (or any SAPL-compatible server) via HTTP. Applications use this when policies are managed centrally rather than embedded.

### Planned Topics

- **Deployment formats:** Docker container, standalone JAR, GraalVM native image, cloud deployments
- **CLI reference:** `bundle create`, `bundle sign`, `bundle verify`, `bundle inspect`, `bundle keygen`, `generate` commands
- **Configuration reference:** All `application.yml` / `application.properties` settings for SAPL Node. For PDP configuration (combining algorithm, variables, secrets), see [PDP Configuration](../2_2_PDPConfiguration/). This section covers SAPL Node-specific deployment settings.
- **Authentication:** Basic auth, API keys, OAuth2 client credentials; interaction with `allowNoAuth` and health detail visibility (`show-details: when-authorized`)
- **Multi-tenant setup:** Running multiple tenants on a single SAPL Node instance
- **Policy sources:** Filesystem directories, signed bundles, remote bundle fetching
- **Health and monitoring:** PDP health indicator (`/actuator/health`) reports `DOWN` when no `pdp.json` is loaded (empty PDP map) or when PDP is in `ERROR` state; `STALE` state (failed hot-reload) reports `UP` with warning; detail visibility requires authentication by default; liveness and readiness probes are independent of PDP state; health details include configuration ID, combining algorithm, document count, and last load timestamps

> **Planned content.** This page will be populated by migrating and expanding content from the `sapl-node` README.
