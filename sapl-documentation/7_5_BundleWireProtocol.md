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
and track that set from a **signed index**. The client learns which bundles exist, loads
them, and reacts to additions, removals, and version changes without reconfiguration.

#### URL Convention

A realm exposes three URL families, all derived from one server-side "current" pointer per
`pdpId`:

| URL | Mutability | Purpose |
|-----|------------|---------|
| `{baseUrl}/{indexPath}` | mutable | The signed realm index. |
| `{baseUrl}/bundles/{pdpId}/{configId}.saplbundle` | immutable | One exact bundle version. The index points here. Cacheable forever. |
| `{baseUrl}/bundles/{pdpId}` and `.../{pdpId}/latest` | mutable | The current bundle for a `pdpId` (single-mode endpoint). |

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
    { "pdpId": "orders", "configId": "orders@2026-07-04T10:15:00Z",
      "url": "https://pap.example.com/realms/acme/bundles/orders/orders@2026-07-04T10-15-00Z.saplbundle" }
  ]
}
```

| Field | Description |
|-------|-------------|
| `realm` | The realm identifier. The client refuses an index whose realm does not match its own. |
| `sequence` | A monotonic counter. The client refuses any index whose sequence is not strictly greater than the last accepted, defeating rollback/replay of an old, validly-signed index. |
| `bundles[].pdpId` | Stable identity, the key the client loads and removes under. |
| `bundles[].configId` | Version signal, equal to the bundle's own `configurationId`. A change triggers a refetch. |
| `bundles[].url` | Absolute URL of the **immutable** bundle for this `configId`, so the fetched bytes match exactly the version the signature attests. |

The server returns `304 Not Modified` when the index is unchanged. Under `Prefer: wait`,
it holds the request until the realm changes or the wait elapses.

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

On each verified, newer index the client diffs the listed bundles against what it has
loaded:

| Case | Action |
|------|--------|
| New `pdpId` | Fetch the bundle URL, verify, load. |
| Same `pdpId`, changed `configId` | Fetch and reload (atomic replace). |
| `pdpId` absent from the index | Remove. |
| Same `pdpId` and `configId` | No fetch. |

An unverifiable, wrong-realm, or stale index is a no-op. The client keeps its current
configuration and never mass-removes on a malformed index. A single failed bundle fetch
drops only that entry.

#### Single vs Realm Integrity

The mutable `latest` endpoint (single mode) authenticates bundle *content* via the manifest
signature, but which version is current is only server-asserted via ETag. Realm mode's
signed index adds a monotonic, signed attestation of the set and its versions, closing
rollback of the pointer itself. Choose realm mode when membership discovery or rollback
protection matters.

### Error Responses

| Status | Meaning | Client Behavior |
|--------|---------|-----------------|
| `200` | Bundle returned | Parse, verify signature, load configuration. |
| `304` | Not modified | Keep current bundle, re-poll after interval. |
| `301`, `302`, `307`, `308` | Redirect | Follow redirect (if enabled in client config). |
| `401`, `403` | Authentication failure | Log error, retry with exponential backoff. |
| `404` | pdpId not found on server | Log error, retry with exponential backoff. |
| `5xx` | Server error | Log error, retry with exponential backoff. |

All error responses trigger retry with exponential backoff. The client never stops
retrying. After the server recovers, the client resumes normal operation.

### Bundle Format

The response body for `200 OK` is a `.saplbundle` file: a ZIP archive containing:

- **`pdp.json`** (required): PDP configuration including combining algorithm and
  a `configurationId` field that uniquely identifies this configuration version.
- **`*.sapl`** files: SAPL policy documents.
- **`.sapl-manifest.json`** (if signed): Cryptographic manifest with SHA-256 content hashes and Ed25519 signature.

See the SAPL bundle documentation for the full archive format specification.

### Security Considerations

#### Transport Security

Servers SHOULD use HTTPS. The client supports TLS via standard Spring Boot SSL
configuration.

#### Bundle Signatures

Bundles fetched over HTTP are verified using Ed25519 signatures by default. The
signature verification is performed client-side using the public key configured on the
node. This provides end-to-end integrity verification independent of transport security.

A server serving unsigned bundles to a client with mandatory signature verification will
cause the client to reject the bundle and retry. The client never loads an unsigned
bundle when signatures are required.

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

8. Serve immutable bundles at `{baseUrl}/bundles/{pdpId}/{configId}.saplbundle` and the
   current bundle at `{baseUrl}/bundles/{pdpId}` (and `/latest`).
9. Serve a compact-JWS realm index at `{baseUrl}/{indexPath}`, signed with `EdDSA` using
   the same key as the bundle manifests, whose `bundles[].url` reference the immutable URLs.
10. Increase the index `sequence` monotonically on every change, and keep old `configId`
    values addressable so the index can reference or roll back to them.

Static file servers (Nginx, S3, CDN) inherently support the regular polling mode with
ETag-based conditional requests.
