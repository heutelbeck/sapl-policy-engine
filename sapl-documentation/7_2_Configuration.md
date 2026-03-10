---
layout: default
title: Configuration
parent: SAPL Node
nav_order: 702
---

## SAPL Node Configuration

SAPL Node is configured via Spring Boot's `application.yml`. This page is the reference for all runtime settings. For policy-level configuration (combining algorithm, variables, secrets), see [PDP Configuration](../2_2_PDPConfiguration/).

### Planned Topics

- **Property reference table:** All `io.sapl.pdp.embedded.*` properties (policy source type, policies path, metrics, bundle security) and all `io.sapl.node.*` properties (authentication modes, user credentials, OAuth2, default PDP ID). Include type, default value, and description for each property. Migrate from sapl-node README Server Configuration section.
- **Configuration file location:** Where to place `application.yml` (config/ directory, --spring.config.location, environment variables). Standard Spring Boot externalized configuration precedence.
- **CLI argument overrides:** Overriding properties via command-line arguments (--io.sapl.pdp.embedded.pdp-config-type=BUNDLES). Precedence: CLI args > environment variables > config file.
- **Spring profiles:** Using profiles for environment-specific configuration (e.g., development with allowNoAuth vs. production with TLS and API keys). Activating profiles via --spring.profiles.active.
- **Minimal vs. production configuration:** Two annotated examples side-by-side. Minimal: single directory, no auth, plain HTTP. Production: bundles, API key auth, TLS, metrics enabled. Cross-reference [Security](../7_6_Security/) for the full hardened example.

Related sections:
- [PDP Configuration](../2_2_PDPConfiguration/) for `pdp.json` settings (combining algorithm, variables, secrets)
- [Policy Sources](../7_3_PolicySources/) for policy source type details and hot-reload behavior
- [Security](../7_6_Security/) for authentication modes and TLS
- [Monitoring](../7_7_Monitoring/) for metrics-enabled and actuator settings

> **Planned content.** Property reference table will be migrated from the `sapl-node` README. Minimal vs. production examples are new content.
