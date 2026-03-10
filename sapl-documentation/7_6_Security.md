---
layout: default
title: Security
parent: SAPL Node
nav_order: 706
---

## Security

This section covers securing the SAPL Node HTTP API and managing secrets within policies. For bundle signing and verification, see [Policy Sources](../7_3_PolicySources/).

### Planned Topics

- **Authentication modes:** Configuring Basic Auth, API key (Bearer token), and OAuth2/JWT authentication. Each mode's properties, how credentials are defined in the users list, and how `pdpId` routes clients to tenant-specific policies. Generating credentials with `sapl-node generate basic` and `sapl-node generate apikey`. Interaction between `allowNoAuth` and the other modes. Migrate from sapl-node README Authentication section.
- **OAuth2 and JWT integration:** Configuring Spring Security's resource server for JWT validation. The `oauth.pdpIdClaim` property for tenant routing from JWT claims. Issuer URI, JWKS endpoint, and token validation.
- **TLS configuration:** Enabling HTTPS via Spring Boot's `server.ssl.*` properties. Keystore formats (PKCS12, JKS). Certificate renewal without downtime (Spring Boot's certificate reload support).
- **Secrets management:** The three-layer secrets precedence chain: PDP-level secrets (pdp.json variables) < policy-level parameters < subscription-level secrets (AuthorizationSubscription). How PIPs access secrets via `AttributeAccessContext.pdpSecrets()` and `ctx.subscriptionSecrets()`. Named credential selection (e.g., `secretsKey` for HTTP PIP header injection). Cross-reference [PDP Configuration](../2_2_PDPConfiguration/) for the variables section of pdp.json.
- **Interface binding:** Binding the server to localhost vs. a network interface. When to use `server.address: 0.0.0.0` (container) vs. `server.address: 127.0.0.1` (sidecar behind a reverse proxy).
- **Hardened production example:** A single complete annotated `application.yml` showing all security settings together: TLS enabled, Basic + API key auth, no unauthenticated access, signed bundles with key catalogue, metrics behind auth, health details behind auth. This is the copy-paste starting point for production deployments.

> **Planned content.** Authentication modes and TLS will be migrated from the `sapl-node` README. The hardened production example and secrets management guide are new content.
