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

Secrets never live inside `pdp.json`. They are carried in a dedicated file next to it, and the file name states whether the content is sealed:

- **`secrets.json`**: cleartext secrets, for development setups only.
- **`secrets.sealed.json`**: sealed secrets. Every scalar leaf is replaced by an `ENC[...]` token encrypted to an X25519 recipient key, while the structure and key names stay readable. The recipient (the PDP, or the cluster sharing the key) restores the values at load time.

```json
{
  "externalApiKey": "ENC[eyJhbGciOiJFQ0RILUVTIi...]"
}
```

A directory or bundle never mixes cleartext and sealed secrets files. Either every secrets file is sealed or none is. A `pdp.json` containing an inline `secrets` section is rejected with an error pointing to the dedicated file. Use `sapl bundle seal` to seal a policy directory and `sapl bundle keygen-secrets` to generate the recipient keypair (see [SAPL Node](../7_0_SaplNode/)).

### The `pdp.json` Format

In SAPL Node and bundle-based deployments, the PDP configuration is stored as a `pdp.json` file alongside policy documents:

```json
{
  "requiredEngineVersion": ">=4.2.0 <5.0.0",
  "requiredExtensions": ["upstreams"],
  "algorithm": {
    "votingMode": "PRIORITY_DENY",
    "defaultDecision": "DENY",
    "errorHandling": "ABSTAIN"
  },
  "compilerOptions": {
    "indexing": "AUTO",
    "unrollInOperator": false,
    "minPoliciesForIndexing": 10,
    "maxIndexNodes": 500000
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
  }
}
```

The `algorithm` object is optional. When absent, the PDP uses the default combining algorithm (`PRIORITY_DENY`, `DENY`, `PROPAGATE`). The `variables` section is also optional. Secrets are not part of `pdp.json` (see [Secrets](#secrets) above). Unknown fields in `pdp.json` are rejected so misspelled requirements cannot be silently ignored.

The configuration id identifying a publication is not part of `pdp.json`. Since SAPL 4.2.0, a `pdp.json` containing a `configurationId` field is rejected fail-closed with a migration message: the configurationId moved to the bundle manifest and is derived from content for directory and resource sources. For bundles, the id is recorded in the signed `.sapl-manifest.json` (explicit or content-derived `bundle@<hash16>`). For directory sources, it is derived on every load as `dir:<dirName>@<hash16>`. For classpath resources, it is derived as `res:<name>@<hash16>`. For embedded builder configurations, it is derived as `embedded@<hash16>`. Identical content always yields the identical id. The id appears in health endpoints and decision logs, enabling operators to correlate authorization decisions with the exact publication that produced them.

#### Required Engine Version

<span id="requiredEngineVersion"></span>`requiredEngineVersion`: optional NPM style semantic version range that the running `sapl-pdp` version must satisfy before the configuration is accepted. Omitting the field or setting it to `null` skips this check. Blank strings, malformed ranges and values of other JSON types are rejected.

The engine version comes from the build metadata packaged with `sapl-pdp`, including prerelease identifiers such as `SNAPSHOT`. It is independent of the API serialization identifier. A declared requirement is rejected when the engine version is unavailable, invalid or outside the required range. The error identifies the PDP, the rejected configuration, the running version and the requirement.

Prerelease versions must be explicitly included for their major, minor and patch version. The range `*` accepts release versions but excludes prerelease versions and still requires a valid engine version.

`SaplBuildInfo.minimumVersionRequirement()` generates a requirement accepted by the installed engine. A release such as `4.2.0` produces `>=4.2.0`, while `4.2.0-SNAPSHOT` produces `>=4.2.0-0`. Build metadata is omitted. The generated range excludes prereleases of later base versions such as `4.3.0-SNAPSHOT`. An unavailable or invalid build version causes an `IllegalStateException`. SAPL Server EE uses this method as the default for new configurations and imports without a requirement. Existing explicit requirements are preserved.

| Engine version | Requirement | Accepted |
|:---------------|:------------|:---------|
| `4.2.0` | `>=4.2.0 <5.0.0` | Yes |
| `4.1.0` | `>=4.2.0 <5.0.0` | No |
| `4.2.0-SNAPSHOT` | `>=4.2.0 <5.0.0` | No |
| `4.2.0-SNAPSHOT` | `>=4.2.0-0 <5.0.0` | Yes |
| `4.2.0-SNAPSHOT` | `4.2.0-SNAPSHOT` | Yes |

A rejected update leaves the last working configuration active with status `STALE`. Without a working configuration, the status is `ERROR`. Rejection happens before extension processing and compilation. Existing authorization subscriptions stay open and can adopt a later compatible configuration. A rejected configuration is not retained for later plugin updates.

#### Required Extensions

<span id="requiredExtensions"></span>`requiredExtensions`: optional array of extension names the recipient must support and successfully prepare before accepting the configuration. Omitting the field or providing an empty array declares no required extensions. Null values, other JSON types and array entries that are not strings are rejected.

Each listed name must have configuration in `ext-<name>.json`, secrets in `ext-<name>-secrets.sealed.json` or both. For the example above, provide `ext-upstreams.json` or the matching secrets file alongside `pdp.json`. The same declaration applies to policy directories and bundles. Extension secrets follow the sealing rules described in [Secrets](#secrets).

A recipient may ignore unsupported extensions that are not listed. It rejects the whole configuration if a required extension is unsupported or cannot be prepared. A rejected update leaves the last working configuration active with status `STALE`. Without a working configuration, the status is `ERROR`. The declaration in `pdp.json` is covered by the bundle signature.

The Java configuration exposes these names through `PDPConfiguration.requiredExtensions()`. JSON serialization of the complete configuration uses the same `requiredExtensions` field. `BundleBuilder.withRequiredExtension(name)` adds a name to the declaration in `pdp.json` and retains names already declared there.

#### Compiler Options

The `compilerOptions` object and all its fields are optional. Omitted fields use the defaults listed below:

* <span id="indexing"></span>`indexing`: policy index strategy: `AUTO` (heuristic selection), `NAIVE` (linear scan), `CANONICAL` (count-and-eliminate algorithm), or `SMTDD` (semantic multi-terminal BDD with equality grouping). Default: `AUTO`. In AUTO mode, the PDP selects NAIVE for small policy sets and attempts SMTDD for larger ones, falling back to CANONICAL if the diagram exceeds the node limit.
* <span id="unrollInOperator"></span>`unrollInOperator`: when `true`, transforms `EXPR in [a, b, c]` into equality chains for improved index matching. Default: `false`.
* <span id="minPoliciesForIndexing"></span>`minPoliciesForIndexing`: minimum policy count before `AUTO` mode considers advanced indexing. Below this threshold, NAIVE is used. Default: `10`.
* <span id="maxIndexNodes"></span>`maxIndexNodes`: maximum number of diagram nodes for SMTDD index construction. If exceeded, AUTO falls back to CANONICAL. Default: `500000`.
* <span id="maxDnfClauses"></span>`maxDnfClauses`: maximum number of disjunctive normal form (DNF) clauses per policy applicability expression for the canonical index. Exceeding the limit falls back to the naive index. Default: `10000`.
* <span id="lowLatencyMode"></span>`lowLatencyMode`: when `true`, the compiler emits eager operator variants that subscribe all children in parallel for the lowest end-to-end decision latency, at the cost of subscribing to children whose values may turn out to be unneeded. When `false`, operators emit lazy variants that short-circuit on the first incomplete or error child, minimizing the per-pass subscription set. The observable decision is identical in both modes. Default: `true`.
* <span id="maxPolicyDocuments"></span>`maxPolicyDocuments`: maximum number of `*.sapl` documents loaded from a directory source. Loading stops once this cap is reached. Default: `10000`.
* <span id="maxTotalSizeMegabytes"></span>`maxTotalSizeMegabytes`: maximum combined size of `*.sapl` documents loaded from a directory, in MiB. Exceeding the limit rejects the configuration. Default: `1024`.

### Configuration Updates

The engine processes configuration changes in order. Each activation publishes the voter and its status before notifying listeners, then completes the extension commit before another change can run. Existing authorization subscriptions stay open and receive later configurations in order.

A configuration or plugin change requested from a listener or an extension callback is queued. Returning from that nested call confirms that the change was queued. It does not mean the change is already active. A status query inside the same callback still observes the current transition. Calls from outside the callbacks wait until their requested change has finished.

The same ordering applies to configuration removal, expiration, source errors and closing the configuration source. An initial subscription callback also defers changes until it returns. After the source closes, further updates are ignored.

Plugin updates migrate one PDP at a time. Waiting configuration changes can run between individual migrations. An ordinary plugin update call returns after its migrations finish. Each caller executes its own changes on its own thread under the source lock. Only nested changes from the same thread are queued. The queue does not create a background worker or transfer work between threads.

A failed configuration update retains the last working voter only when that voter belongs to the current plugin bundle. Otherwise the PDP serves an error voter with status `ERROR`, so a retired plugin bundle cannot remain active after migration.

An unexpected activation failure records `STALE` when a working voter from the current plugin bundle remains available, or `ERROR` otherwise. A direct load reports the original exception to its caller. The candidate remains available for a retry on a later plugin update. A successful retry activates its policies and deploys the matching extensions.

Configuration changes and new subscriptions ignored after the source closes produce a warning identifying the operation and PDP. Repeated closing and unsubscribing after close are silent, idempotent cleanup operations. Listener failures and extension lifecycle failures identify the operation, configuration when available, exception type and original cause in the log. Extension runtime failures remain the host's responsibility and do not change the PDP configuration status.

Fatal errors abort the current batch and are rethrown. The log identifies queued changes discarded by the abort. Update listeners must avoid requesting another update unconditionally for every notification, since that feedback keeps the requesting thread busy until the listener stops generating updates.

For deployment details, see [SAPL Node](../7_0_SaplNode/). For the bundle structure that packages `pdp.json` with policy documents, see [Bundle Wire Protocol](../7_5_BundleWireProtocol/).
