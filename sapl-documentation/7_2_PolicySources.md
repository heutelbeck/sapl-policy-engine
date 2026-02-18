---
layout: default
title: Policy Sources
parent: Deployment
grand_parent: SAPL Reference
nav_order: 702
---

## Policy Administration Point (PAP)

The PAP manages the policies in the policy store. How policies are authored, reviewed, and deployed depends on the policy source type configured for the PDP.

### Policy Source Types

The PDP supports five policy source types, configured via `io.sapl.pdp.embedded.pdp-config-type`:

| Source Type       | Description                                                                                            | Hot-Reload | Multi-Tenant |
|-------------------|--------------------------------------------------------------------------------------------------------|------------|--------------|
| `RESOURCES`       | Loads policies from the Java classpath (`src/main/resources/policies`). Fixed at build time.           | No         | No           |
| `DIRECTORY`       | Monitors a filesystem directory for `.sapl` files and `pdp.json`. Changes are detected and reloaded automatically. | Yes        | No           |
| `MULTI_DIRECTORY` | Monitors subdirectories within a base directory. Each subdirectory name becomes a tenant ID.           | Yes        | Yes          |
| `BUNDLES`         | Monitors a directory for `.saplbundle` files (signed ZIP archives). Each bundle filename becomes a tenant ID. | Yes        | Yes          |
| `REMOTE_BUNDLES`  | Fetches `.saplbundle` files from a remote HTTP server using ETag-based polling or long-polling.        | Yes        | Yes          |

For the `RESOURCES` source, the policy store is part of the application build. Policies are authored alongside the application code and deployed together. This is suitable for applications where policies change infrequently and are tested as part of the build process.

For filesystem-based sources (`DIRECTORY`, `MULTI_DIRECTORY`, `BUNDLES`), the PDP monitors the configured path and automatically reloads policies when files change. Any tool that modifies files in the monitored directory acts as a PAP: a text editor, a Git checkout, a CI/CD pipeline, or a management script.

For `REMOTE_BUNDLES`, the PDP periodically fetches bundles from an HTTP server. The server can be any HTTP endpoint that serves `.saplbundle` files at the expected paths. This enables centralized policy management where a dedicated policy server distributes bundles to multiple PDP instances.

### Bundle Security

The `BUNDLES` and `REMOTE_BUNDLES` source types support Ed25519 signature verification. Bundles can be signed with `sapl-node bundle sign` and verified against a configured public key or per-tenant key catalogue. By default, signature verification is mandatory. Unsigned bundles are only accepted in development environments with an explicit opt-in (`allow-unsigned: true` and `accept-risks: true`).

See [Getting Started](../1_3_GettingStarted/) for a quickstart with the `DIRECTORY` source, and the SAPL Node documentation for bundle management and remote bundle configuration.
