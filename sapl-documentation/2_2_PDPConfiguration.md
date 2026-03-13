---
layout: default
title: PDP Configuration
parent: The SAPL Policy Language
nav_order: 102
---


## PDP Configuration

The PDP evaluates policies against authorization subscriptions. Its runtime behavior is governed by a configuration which is loaded alongside policy documents. This configuration is independent of any specific deployment model and defines what the PDP needs regardless of how it is deployed.

### Combining Algorithm

The combining algorithm determines how votes from multiple matching policies resolve into a single authorization decision. When no algorithm is configured, the PDP uses a default of `PRIORITY_DENY` with `DENY` default decision and `PROPAGATE` error handling. This default is deliberately restrictive: it denies by default and propagates errors so that misconfigurations fail visibly rather than silently granting access.

For a detailed explanation of each algorithm and guidance on choosing one, see [Combining Algorithms](../2_5_CombiningAlgorithms/).

### Variables

Variables are optional key-value pairs available to all policies during evaluation. They are constant-folded into the constants stratum at compilation time (see [Evaluation Semantics](../2_11_EvaluationSemantics/#the-three-strata)), making them as cheap to evaluate as literal values.

Common uses include:

- **Feature flags and tenant identifiers**: Values that policies need but that do not belong in the authorization subscription.
- **Attribute finder option defaults** (`attributeFinderOptions`): Global defaults for stream behavior such as timeouts, retries, and polling intervals. See [The Attribute Broker](../2_8_FunctionsAndAttributes/#the-attribute-broker) for the full option reference.

Variables are not to be confused with the `environment` field of the authorization subscription. The `environment` carries request-scoped context sent by the PEP, while variables are operator-configured constants shared across all evaluations.

### Secrets

Secrets are optional PDP-level credentials available to PIPs during evaluation. They complement subscription-level secrets (see [Authorization Subscriptions](../2_1_AuthorizationSubscriptions/#secrets)) and are the right choice for infrastructure credentials shared across all evaluations, such as database connection strings or API keys for external services.

PDP-level secrets are configured once and automatically available to all PIPs via the `AttributeAccessContext`.

### The `pdp.json` Format

In SAPL Node and bundle-based deployments, the PDP configuration is stored as a `pdp.json` file alongside policy documents:

```json
{
  "configurationId": "my-app-v1",
  "algorithm": {
    "votingMode": "PRIORITY_DENY",
    "defaultDecision": "DENY",
    "errorHandling": "ABSTAIN"
  },
  "variables": {
    "tenantId": "acme-corp",
    "featureFlags": {
      "experimentalPipEnabled": false
    },
    "attributeFinderOptions": {
      "initialTimeOutMs": 5000,
      "retries": 3
    }
  },
  "secrets": {
    "externalApiKey": "sk-..."
  }
}
```

The `algorithm` object is optional. When absent, the PDP uses the default combining algorithm (`PRIORITY_DENY`, `DENY`, `PROPAGATE`). The `variables` and `secrets` sections are also optional.

The `configurationId` is a version identifier for the configuration. It appears in health endpoints and decision logs, enabling operators to correlate authorization decisions with the exact policy set that produced them. For bundles, this field is **required**. For directory and resource sources, it is optional and auto-generated from the source path and content hash when absent.

For deployment details, see [SAPL Node](../7_0_Deployment/). For the bundle structure that packages `pdp.json` with policy documents, see [Bundle Wire Protocol](../7_5_BundleWireProtocol/).
