---
layout: default
title: Bundle Wire Protocol
parent: SAPL Node
nav_order: 705
---

## Remote Bundle Wire Protocol

This document specifies the HTTP protocol used by SAPL nodes to fetch `.saplbundle`
files from remote servers. Third-party servers can implement this protocol to serve
bundles to SAPL nodes.

There are two consumption modes over the same bundle format and trust model. **Single
mode** (regular polling or long-poll) fetches one client-declared `pdpId`. **Realm mode**
points the client at a server-managed *realm* and lets it discover a dynamic set of
bundles from a signed index.

### URL Convention

Bundles are addressed by convention:

```
{baseUrl}/{pdpId}
```

Example: `https://pap.example.com/bundles/production`

The `pdpId` is a path segment appended to the configured base URL. No query parameters
are used.

### Regular Polling

#### Request

```http
GET {baseUrl}/{pdpId} HTTP/1.1
Host: pap.example.com
If-None-Match: "v42-sha256-abc123"
Accept: application/octet-stream
Authorization: Bearer eyJhbGciOiJSUz...
```

| Header | Required | Description |
|--------|----------|-------------|
| `If-None-Match` | After first fetch | ETag from the previous response. Omitted on first request. |
| `Accept` | Yes | Always `application/octet-stream`. |
| Auth header | If configured | Custom header name and value (e.g., `Authorization: Bearer ...`). |

#### Response: Bundle Changed (200)

```http
HTTP/1.1 200 OK
Content-Type: application/octet-stream
ETag: "v43-sha256-def456"
Content-Length: 12345

<.saplbundle ZIP bytes>
```

| Header | Required | Description |
|--------|----------|-------------|
| `Content-Type` | Recommended | Should be `application/octet-stream`. The client does not validate this header. |
| `ETag` | Recommended | Opaque version identifier. Used by the client for `If-None-Match` on subsequent requests. |
| `Content-Length` | Recommended | Size of the response body in bytes. |

The response body is the raw `.saplbundle` ZIP archive bytes.

#### Response: Not Modified (304)

```http
HTTP/1.1 304 Not Modified
ETag: "v42-sha256-abc123"
```

The server returns 304 when the bundle has not changed since the ETag provided in
`If-None-Match`. No response body is sent.

### Long-Poll Mode

Long-poll uses the same request format as regular polling. The server behavior differs:

1. If the bundle has changed since the provided ETag: respond immediately with
   `200 OK` and the new bundle.
2. If unchanged: hold the connection open until either:
   - The bundle changes: respond `200 OK` with the new bundle.
   - The server's hold timeout expires: respond `304 Not Modified`.

The client reconnects immediately after receiving either response.

#### Server Timeout Advertisement (Optional)

Servers supporting long-poll MAY include an informational header:

```http
X-Long-Poll-Timeout: 30
```

This indicates the maximum hold time in seconds. It is informational for client-side
logging and diagnostics only. The client does not use this value to configure its own
timeouts.

### Realm Mode (Multi-Bundle)

Regular and long-poll modes fetch a single, client-declared `pdpId`. **Realm mode** points
the client at a *realm*, a server-managed dynamic set of bundles, and lets it discover
and track that set from a **signed index**. The index attests *membership and binding*:
which pdpIds exist and which URL each one is bound to. For every listed entry the client
runs an autonomous single-mode fetch loop against the bound URL, so version updates flow
through the ordinary bundle endpoints without any index change.

#### URL Convention

A realm exposes three URL families, all derived from one server-side "current" pointer per
`pdpId`:

| URL | Mutability | Purpose |
|-----|------------|---------|
| `{baseUrl}/{indexPath}` | mutable | The signed realm index. |
| `{baseUrl}/bundles/{pdpId}` and `.../{pdpId}/latest` | mutable | The current bundle for a `pdpId`. The index normally binds here. |
| `{baseUrl}/bundles/{pdpId}/{configId}.saplbundle` | immutable | One exact bundle version. Used by the index to pin or roll back. Cacheable forever. |

A bundle is signed once per `(pdpId, configId)` and served byte-for-byte from both the
immutable and `latest` URLs, so ETags stay stable and immutable URLs cache forever.

#### The Realm Index

##### Request

```http
GET {baseUrl}/{indexPath} HTTP/1.1
Accept: application/jose
If-None-Match: "1751632200000"
Authorization: Bearer eyJhbGci...
Prefer: wait=25
```

The `Prefer: wait=<seconds>` header is optional and requests long-poll of the index.

##### Response (200)

The body is a **compact JWS** signed with `EdDSA`.

```http
HTTP/1.1 200 OK
Content-Type: application/jose
ETag: "1751632260000"

<base64url(header)>.<base64url(payload)>.<base64url(signature)>
```

Decoded protected header and payload:

```json
{ "alg": "EdDSA", "kid": "prod-2026", "typ": "sapl-realm-index" }
```

```json
{
  "realm": "acme",
  "sequence": 1751632260000,
  "issuedAt": "2026-07-04T12:31:00Z",
  "bundles": [
    { "pdpId": "orders",
      "url": "https://pap.example.com/realms/acme/bundles/orders" }
  ]
}
```

| Field | Description |
|-------|-------------|
| `realm` | The realm identifier. The client refuses an index whose realm does not match its own. |
| `sequence` | A monotonic counter. The client refuses any index whose sequence is not strictly greater than the last accepted, defeating rollback/replay of an old, validly-signed index. |
| `bundles[].pdpId` | Stable identity, the key the client loads and removes under. |
| `bundles[].url` | Absolute URL of the bundle endpoint the client monitors for this `pdpId`. |

The URL is the binding, and its mutability class is the policy. Binding to the mutable
`latest` endpoint means the client tracks whatever the server publishes there. Binding to
an immutable version URL pins the `pdpId` to that exact version, which is how a deliberate
rollback is expressed: it arrives as a signed rebinding through the index.

The server returns `304 Not Modified` when the index is unchanged. Under `Prefer: wait`,
it holds the request until the realm changes or the wait elapses. The index only changes
when membership or a binding changes, so index traffic is light.

#### Index Signature and Trust

The index verifier pins the algorithm exactly as bundle manifests do:

1. The JWS `alg` MUST be `EdDSA`. Any other value, including `none`, is refused.
2. The `kid` resolves the trusted public key through the same node key configuration used
   for bundle manifests, so one trust anchor covers both.
3. After the signature verifies, the client enforces the `realm` match and a strictly
   greater `sequence`.

When signature verification is disabled (development), the signature check is skipped but
the realm and sequence checks still apply.

#### Reconciliation

On each verified, newer index the client diffs the listed bindings against its running
fetch loops:

| Case | Action |
|------|--------|
| New `pdpId` | Start a fetch loop on the bound URL, load the bundle. |
| Same `pdpId`, changed `url` | Replace the fetch loop with one bound to the new URL and reset its conditional-request and freshness state, so a pin to an older version loads. |
| `pdpId` absent from the index | Stop the fetch loop and remove the configuration. |
| Same `pdpId` and `url` | Nothing, the running loop keeps monitoring. |

An unverifiable, wrong-realm, or stale index is a no-op. The client keeps its current
configuration and never mass-removes on a malformed index. A valid index with an empty
bundle list is a legitimate operation and empties the realm. Each fetch loop retries
transport failures with bounded exponential backoff independently, so one unreachable
bundle never blocks the others.

#### Version Freshness

Within one binding, the client rejects any bundle whose manifest signing time (`created`,
which is covered by the bundle signature) is older than the currently loaded bundle's.
This defeats the replay of an older, validly signed bundle at a mutable URL. A signed
rebinding through the index resets the check, so deliberate pins and rollbacks load. The
consequence is a clean discipline: the `latest` endpoint only ever moves forward, and
going backwards requires the signed index.

#### Single vs Realm Integrity

Single mode authenticates bundle *content* via the manifest signature, but membership is
client-configured and version currency is only server-asserted via ETag. Realm mode adds
a monotonic, signed attestation of membership and binding, plus the version freshness
check within each binding. Choose realm mode when the set of bundles is server-managed or
when replay and rollback protection matter.

### Error Responses

| Status | Meaning | Client Behavior |
|--------|---------|-----------------|
| `200` | Bundle returned | Parse, verify signature, load configuration. |
| `304` | Not modified | Keep current bundle, re-poll after interval. |
| `301`, `302`, `307`, `308` | Redirect | Follow redirect (if enabled in client config). |
| `401`, `403` | Authentication failure | Log error, retry with exponential backoff. |
| `404` | pdpId not found on server | Log error, retry with exponential backoff. |
| `5xx` | Server error | Log error, retry with exponential backoff. |

Transport-level error responses (`401`, `403`, `404`, `5xx`, and connection timeouts)
are transient. They trigger retry with exponential backoff, the client never stops
retrying, and it resumes normal operation after the server recovers. A `200 OK` that
carries a definitively invalid bundle is not a transport error. It is handled as a
configuration error rather than retried in silence (see [Bundle Validity](#bundle-validity)).

### Bundle Format

The response body for `200 OK` is a `.saplbundle` file: a ZIP archive with only
root-level files. Subdirectories and unknown file names are rejected.

| File | Required | Purpose |
|------|----------|---------|
| `pdp.json` | Yes | PDP configuration: combining algorithm and variables. Never contains secrets. Since SAPL 4.2.0 it must not contain a `configurationId` field; a bundle carrying one is rejected with a migration message. |
| `*.sapl` | Yes | SAPL policy documents. |
| `secrets.sealed.json` | No | Sealed PDP-level secrets. Scalar leaves are `ENC[...]` tokens encrypted to an X25519 recipient key, structure and key names stay readable. |
| `ext-<name>.json` | No | Cleartext extension data. Named JSON for consumers other than the PDP, for example gateway upstream configuration. |
| `ext-<name>-secrets.sealed.json` | No | Sealed extension secrets for the same `<name>`. |
| `critical-extensions.json` | No | A JSON array of extension names the consumer MUST be able to process, e.g. `["upstreams"]`. |
| `.sapl-manifest.json` | Yes | Manifest carrying the bundle's packaging metadata and SHA-256 content hashes, plus an Ed25519 signature covering every other file when signed. See [Manifest Schema](#manifest-schema). |

#### Secrets Sealing

A secrets file carries its sealing state in its name: a `.sealed.json` name holds
sealed content, a plain `.json` name holds cleartext. A bundle never mixes both
states, and a sealed-named file whose content is not sealed is rejected. Cleartext
variants (`secrets.json`, `ext-<name>-secrets.json`) exist for development setups
only and require the consumer's explicit unencrypted-secrets opt-in. Sealing runs
before signing, so the manifest signature covers the ciphertext.

#### Extensions and Criticality

Extension files let a bundle carry configuration for consumers other than the PDP.
The consumer receives them alongside the policy configuration, keyed by extension
name. `critical-extensions.json` follows the JOSE `crit` idea: a consumer without
support for a listed extension MUST reject the whole configuration rather than
silently ignore it, while unlisted extensions it does not know are ignored. Every
listed name must have a payload in the bundle (`ext-<name>.json` or
`ext-<name>-secrets.sealed.json` or both), otherwise the bundle is rejected. The
critical set is covered by the manifest signature, so it cannot be stripped in
transit.

#### Manifest Schema

The manifest is a strict, typed JSON document. Unknown top-level fields and
missing required fields are rejected fail-closed; this schema contract protects
against format skew in both directions (a newer manifest fails unknown-field
validation on old engines; an older manifest fails required-field validation with
a migration message on new engines).

```json
{
  "version": "4.2.0",
  "hashAlgorithm": "SHA-256",
  "created": "2026-07-15T10:30:00Z",
  "configurationId": "release-77",
  "attribution": "sapl-node/4.2.0",
  "audience": {
    "sealingRecipient": "recipient-key-2026"
  },
  "files": {
    "pdp.json": "sha256:...",
    "policy.sapl": "sha256:..."
  },
  "signature": {
    "algorithm": "Ed25519",
    "keyId": "prod-2026",
    "value": "base64-signature"
  }
}
```

| Field | Required | Purpose |
|-------|----------|---------|
| `version` | Yes | Recorded provenance: the engine library version that wrote the manifest, minted at build time. Publishers cannot set it, and it is not validated on load. |
| `hashAlgorithm` | Yes | Always `SHA-256`. |
| `created` | Yes | Build timestamp. |
| `configurationId` | Yes | The publication identity of this bundle: explicit (set at build time) or content-derived (`bundle@<hash16>`). 1 to 256 printable ASCII characters without whitespace, `/` or `\`. Signed and tamper-evident. |
| `attribution` | Yes | An arbitrary JSON string or object (at most 16 KiB serialized), signed, never interpreted by the engine. The single opaque metadata slot: a builder tags a plain string, a publisher can nest a full trust chain. |
| `audience.sealingRecipient` | Iff sealed content present | The single X25519 recipient key id the bundle's sealed content is sealed to. Consumers fail fast before any unseal attempt when they do not hold this key. One PDP in N realms means N publications, each re-sealed and re-signed. |
| `files` | Yes | SHA-256 hash per bundle file (the manifest itself excluded). |
| `signature` | If signed | Ed25519 signature over the canonical manifest bytes. |

### Bundle Validity

A bundle that is fetched successfully but is definitively invalid is handled as a
configuration error rather than being retried in silence. This covers an invalid or
missing signature, a malformed archive, a missing or unreadable `pdp.json`, secrets
that cannot be unsealed, and a missing payload for a critical extension.

For each such bundle the client reports a configuration error for the affected pdpId.
It keeps serving the last successfully loaded configuration and marks that pdpId
`STALE`. When there is no last-good configuration to keep, the pdpId goes to `ERROR`
and fails closed, so its decisions are `INDETERMINATE`. The failure and a
human-readable reason appear in the client health status, and the bundle is
re-evaluated when its content changes (a new ETag). This is distinct from a transient
transport error such as a timeout or a `5xx`, which the client retries silently and
which does not emit a configuration error.

### Security Considerations

#### Transport Security

Servers SHOULD use HTTPS. The client supports TLS via standard Spring Boot SSL
configuration.

#### Bundle Signatures

Bundles fetched over HTTP are verified using Ed25519 signatures by default. The
signature verification is performed client-side using the public key configured on the
node. This provides end-to-end integrity verification independent of transport security.

A server serving an unsigned or wrongly signed bundle to a client with mandatory
signature verification causes the client to reject that bundle. This is a definitive
configuration error, not a transient transport failure. The client never loads the
bundle, keeps serving its last-good configuration, marks the pdpId `STALE` (or `ERROR`
and fails closed if there is no last-good), and reports the reason in its health status.
See [Bundle Validity](#bundle-validity).

#### Authentication

The protocol supports a single configurable HTTP header for authentication. The header
name and value are sent on every request. Common patterns:

| Pattern | Header Name | Header Value |
|---------|------------|--------------|
| OAuth2 Bearer Token | `Authorization` | `Bearer eyJhbGci...` |
| Static API Key | `X-Api-Key` | `sk-abc123...` |
| Basic Auth | `Authorization` | `Basic dXNlcjpwYXNz...` |

### Implementing a Compatible Server

A minimal compatible server must:

1. Serve `.saplbundle` ZIP files at `{baseUrl}/{pdpId}` with `200 OK`.
2. Keep bundle responses under 256 MiB (the client rejects larger responses).
3. Return `404` for unknown pdpIds.
4. Optionally: support `If-None-Match` / `ETag` for conditional requests with `304`
   responses.

For long-poll support, the server must additionally:

5. Hold the connection open when the bundle has not changed.
6. Respond with `200 OK` when the bundle changes during the hold.
7. Respond with `304 Not Modified` when the hold timeout expires.

For realm mode, the server must additionally:

8. Serve the current bundle at `{baseUrl}/bundles/{pdpId}` (and `/latest`), and immutable
   bundles at `{baseUrl}/bundles/{pdpId}/{configId}.saplbundle` for pinning and rollback.
9. Serve a compact-JWS realm index at `{baseUrl}/{indexPath}`, signed with `EdDSA` using
   the same key as the bundle manifests, whose `bundles[].url` bind each `pdpId` to the
   endpoint the client monitors, normally the `latest` URL.
10. Increase the index `sequence` monotonically on every membership or binding change, and
    keep old `configId` versions addressable so the index can pin or roll back to them.

Static file servers (Nginx, S3, CDN) inherently support the regular polling mode with
ETag-based conditional requests.
