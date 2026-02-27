---
layout: default
title: PDP Configuration
parent: The SAPL Policy Language
nav_order: 102
---


## PDP Configuration

The PDP evaluates policies against authorization subscriptions. Its runtime behavior is governed by a configuration which is loaded alongside policy documents. This configuration is independent of any specific deployment model and defines what the PDP needs regardless of how it is deployed.

### Combining Algorithm

The combining algorithm is the only mandatory configuration element. It determines how votes from multiple matching policies resolve into a single authorization decision. Without a combining algorithm, the PDP returns `INDETERMINATE` for every subscription.

There is no default combining algorithm, not even a safe one. This is deliberate: if a default were silently applied and the configuration were accidentally deleted or corrupted, the PDP could produce decisions that appear correct but are not. By requiring explicit configuration, the PDP fails visibly when it is misconfigured rather than silently making wrong decisions.

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

The `algorithm` object is mandatory; `variables` and `secrets` are optional.

For deployment details, see [SAPL Node](../7_0_Deployment/). For the bundle structure that packages `pdp.json` with policy documents, see [Bundle Wire Protocol](../7_5_BundleWireProtocol/).
