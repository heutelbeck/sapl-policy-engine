---
layout: default
title: Bundle Wire Protocol
parent: SAPL Node
grand_parent: SAPL Reference
nav_order: 705
---

## Remote Bundle Wire Protocol

This document specifies the HTTP protocol used by SAPL nodes to fetch `.saplbundle`
files from remote servers. Third-party servers can implement this protocol to serve
bundles to SAPL nodes.

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
  configuration ID.
- **`*.sapl`** files: SAPL policy documents.
- **`MANIFEST.json`** (if signed): Cryptographic manifest with content hashes.
- **`MANIFEST.json.sig`** (if signed): Ed25519 signature of the manifest.

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
2. Keep bundle responses under 16 MB (the client rejects larger responses).
3. Return `404` for unknown pdpIds.
4. Optionally: support `If-None-Match` / `ETag` for conditional requests with `304`
   responses.

For long-poll support, the server must additionally:

5. Hold the connection open when the bundle has not changed.
6. Respond with `200 OK` when the bundle changes during the hold.
7. Respond with `304 Not Modified` when the hold timeout expires.

Static file servers (Nginx, S3, CDN) inherently support the regular polling mode with
ETag-based conditional requests.
