---
layout: default
title: HTTP API
parent: SDKs and APIs
nav_order: 601
---

## HTTP API

The SAPL PDP server exposes HTTP endpoints for authorization decisions. Any application that can make HTTP requests can use the PDP -- no SDK required.

All endpoints accept `POST` requests with `application/json` bodies. Streaming endpoints return `text/event-stream` (Server-Sent Events); one-shot endpoints return `application/json`. All endpoints are located under a shared base URL, typically `https://<host>:<port>/api/pdp/`.

### Endpoint Overview

| Endpoint | Method | Response Content-Type | Behavior |
|----------|--------|----------------------|----------|
| `/api/pdp/decide` | POST | `text/event-stream` | Streaming decisions for a single subscription |
| `/api/pdp/decide-once` | POST | `application/json` | One-shot decision for a single subscription |
| `/api/pdp/multi-decide` | POST | `text/event-stream` | Streaming individual decisions for multiple subscriptions |
| `/api/pdp/multi-decide-all` | POST | `text/event-stream` | Streaming batch decisions for multiple subscriptions |
| `/api/pdp/multi-decide-all-once` | POST | `application/json` | One-shot batch decisions for multiple subscriptions |

### Authentication

All endpoints require authentication. SAPL Node supports four authentication modes that can be combined:

| Mode | Header | Configuration |
|------|--------|--------------|
| Unauthenticated | (none) | `allow-no-auth: true` (development only) |
| Basic Auth | `Authorization: Basic ...` | `allow-basic-auth: true` + user entries |
| API Key | `Authorization: Bearer sapl_...` | `allow-api-key-auth: true` + user entries |
| OAuth2 / JWT | `Authorization: Bearer <jwt>` | `allow-oauth2-auth: true` + issuer URI |

Generate credentials with the SAPL CLI:

```shell
sapl generate basic --id service-a --pdp-id default
sapl generate apikey --id service-b --pdp-id production
```

For full authentication configuration, TLS setup, and multi-tenant routing, see [Security](../7_6_Security/).

### Authorization Subscription Format

A single authorization subscription is a JSON object with three required fields and two optional fields:

```json
{
  "subject": {
    "username": "alice",
    "role": "doctor",
    "department": "cardiology"
  },
  "action": "read",
  "resource": {
    "type": "patient_record",
    "patientId": 123
  },
  "environment": {
    "timestamp": "2025-10-06T14:30:00Z",
    "ipAddress": "192.168.1.42"
  },
  "secrets": {
    "jwt": "eyJhbGciOi..."
  }
}
```

- **subject** (required): Who is making the request. Any JSON value (string, number, object, array, boolean, or null).
- **action** (required): What operation is being attempted. Any JSON value.
- **resource** (required): What is being accessed. Any JSON value.
- **environment** (optional): Additional context such as time, location, or IP address. Any JSON value.
- **secrets** (optional): Sensitive data for Policy Information Points (tokens, API keys, credentials). Any JSON value. Not included in logs or traces.

For the full subscription format, see [Authorization Subscriptions](../2_1_AuthorizationSubscriptions/).

### Authorization Decision Format

Every endpoint returns authorization decisions as JSON objects:

```json
{
  "decision": "PERMIT",
  "obligations": [
    {
      "type": "log_access",
      "message": "Patient record accessed by alice"
    }
  ],
  "advice": [
    {
      "type": "notify",
      "channel": "audit"
    }
  ],
  "resource": {
    "type": "patient_record",
    "patientId": 123,
    "name": "***REDACTED***"
  }
}
```

- **decision** (always present): One of `PERMIT`, `DENY`, `INDETERMINATE`, or `NOT_APPLICABLE`.
- **obligations** (optional): An array of JSON objects. Instructions the PEP **must** enforce before granting access. If a PEP cannot fulfill any obligation, it must deny access regardless of the decision.
- **advice** (optional): An array of JSON objects. Suggestions the PEP **should** follow but may ignore without affecting the authorization outcome.
- **resource** (optional): A JSON value that replaces the original resource data (e.g., with fields redacted or transformed).

A minimal decision contains only the `decision` field:

```json
{
  "decision": "DENY"
}
```

For details on how PEPs must handle obligations and advice, see [Authorization Decisions](../2_3_AuthorizationDecisions/).

### Single Subscription Endpoints

#### Decide (Streaming)

```
POST {baseURL}/decide
Content-Type: application/json
Accept: text/event-stream
```

Returns an initial decision, then pushes updated decisions whenever policies, attributes, or conditions change. Each SSE event contains a complete authorization decision in its `data` field. The server may send SSE comment events (`: keep-alive`) to keep the connection alive. The client must close the connection to stop receiving updates.

**Request body:**

```json
{
  "subject": "alice",
  "action": "read",
  "resource": "document"
}
```

**Response** (Server-Sent Events, one event per decision change):

```
data: {"decision":"PERMIT"}

data: {"decision":"DENY","obligations":[{"type":"log_access","reason":"policy changed"}]}

: keep-alive
```

**Example with curl:**

```shell
curl -N -X POST https://localhost:8443/api/pdp/decide \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer sapl_..." \
  -d '{"subject":"alice","action":"read","resource":"document"}'
```

**Example with the SAPL CLI** (streams decisions as NDJSON):

```shell
sapl decide --remote --url https://localhost:8443 --token sapl_... \
  -s '"alice"' -a '"read"' -r '"document"'
```

#### Decide Once (One-Shot)

```
POST {baseURL}/decide-once
Content-Type: application/json
Accept: application/json
```

Returns a single authorization decision and closes the connection. Use this for request-response scenarios where continuous updates are not needed.

**Request body:**

```json
{
  "subject": { "username": "alice", "role": "doctor" },
  "action": "read",
  "resource": { "type": "patient_record", "patientId": 123 }
}
```

**Response:**

```json
{
  "decision": "PERMIT",
  "obligations": [
    {
      "type": "log_access",
      "message": "Patient record accessed"
    }
  ]
}
```

**Example with the SAPL CLI:**

```shell
sapl decide-once --remote --url https://localhost:8443 --token sapl_... \
  -s '{"username":"alice","role":"doctor"}' -a '"read"' -r '{"type":"patient_record","patientId":123}'
```

The `sapl check` command returns an exit code instead of JSON output, making it suitable for shell scripts and CI/CD pipelines:

```shell
sapl check --remote --url https://localhost:8443 --token sapl_... \
  -s '"alice"' -a '"read"' -r '"document"' && echo "PERMIT"
```

For the full CLI reference, see [Command Line](../7_8_CommandLine/).

### Multi-Subscription Endpoints

Multi-subscriptions bundle multiple authorization subscriptions into a single request. This is useful when a PEP needs to evaluate several authorization questions at once, for example when rendering a UI that shows multiple resources with different access levels.

A multi-subscription is a JSON object mapping client-chosen subscription IDs to individual authorization subscriptions:

```json
{
  "read-patient-record": {
    "subject": { "username": "alice", "role": "doctor" },
    "action": "read",
    "resource": { "type": "patient_record", "patientId": 123 }
  },
  "write-clinical-notes": {
    "subject": { "username": "alice", "role": "doctor" },
    "action": "write",
    "resource": { "type": "clinical_notes", "patientId": 123 }
  },
  "delete-audit-log": {
    "subject": { "username": "alice", "role": "doctor" },
    "action": "delete",
    "resource": { "type": "audit_log" }
  }
}
```

Each key is a unique subscription ID chosen by the PEP. Each value is a standard authorization subscription with `subject`, `action`, `resource`, and optionally `environment` and `secrets`.

#### Multi Decide (Streaming Individual)

```
POST {baseURL}/multi-decide
Content-Type: application/json
Accept: text/event-stream
```

Returns individual decisions as they change, each tagged with its subscription ID. Only subscriptions whose decisions actually changed emit updates. This is efficient when most decisions remain stable.

**Response** (Server-Sent Events, one event per changed decision):

```
data: {"subscriptionId":"read-patient-record","decision":{"decision":"PERMIT"}}

data: {"subscriptionId":"write-clinical-notes","decision":{"decision":"PERMIT","obligations":[{"type":"log_access"}]}}

data: {"subscriptionId":"delete-audit-log","decision":{"decision":"DENY"}}

data: {"subscriptionId":"write-clinical-notes","decision":{"decision":"DENY"}}
```

Each event contains a `subscriptionId` identifying which subscription the decision belongs to, and a `decision` object with the authorization decision including any obligations, advice, or resource transformations.

#### Multi Decide All (Streaming Batch)

```
POST {baseURL}/multi-decide-all
Content-Type: application/json
Accept: text/event-stream
```

Returns all decisions as a single object whenever any decision changes. Each message contains the complete current state of all decisions.

**Response** (Server-Sent Events, one event per change to any decision):

```
data: {"read-patient-record":{"decision":"PERMIT"},"write-clinical-notes":{"decision":"PERMIT","obligations":[{"type":"log_access"}]},"delete-audit-log":{"decision":"DENY"}}

data: {"read-patient-record":{"decision":"PERMIT"},"write-clinical-notes":{"decision":"DENY"},"delete-audit-log":{"decision":"DENY"}}
```

This format is simpler to process than individual updates because each message is a complete snapshot. The trade-off is that every message repeats all decisions, even those that have not changed.

#### Multi Decide All Once (One-Shot Batch)

```
POST {baseURL}/multi-decide-all-once
Content-Type: application/json
Accept: application/json
```

Returns all decisions as a single JSON object and closes the connection. The format is identical to the streaming batch endpoint, but the connection closes after the first response.

**Response:**

```json
{
  "read-patient-record": {
    "decision": "PERMIT"
  },
  "write-clinical-notes": {
    "decision": "PERMIT",
    "obligations": [
      {
        "type": "log_access",
        "message": "Clinical notes accessed"
      }
    ]
  },
  "delete-audit-log": {
    "decision": "DENY"
  }
}
```

All multi-subscription decisions may include optional `resource`, `obligations`, and `advice` fields, as described in [Authorization Decisions](../2_3_AuthorizationDecisions/).

### Error Handling

A PEP encountering connectivity issues or errors with the PDP server must treat this as an `INDETERMINATE` decision and deny access. The PEP should reconnect using an exponential backoff strategy to avoid overloading the PDP.

### Keep-Alive

Streaming connections use periodic SSE comment events (`: keep-alive`) to prevent firewalls and proxies from closing idle connections. A PEP should treat a prolonged absence of any events (decisions or keep-alives) as a connection failure.

### Reverse Proxy Configuration

The streaming endpoints (`/api/pdp/decide`, `/api/pdp/multi-decide`, `/api/pdp/multi-decide-all`) use SSE over long-lived HTTP POST connections. Default proxy configurations buffer responses and time out idle connections, both of which break SSE streaming.

Requirements for any reverse proxy in front of SAPL Node:

1. **Disable response buffering.** SSE events must be flushed immediately.
2. **Set a long read timeout.** Streaming connections stay open indefinitely.
3. **Preserve chunked transfer encoding.** Do not add `Content-Length` headers to streaming responses.
4. **Forward the HTTP method.** All PDP endpoints use POST.

SAPL Node can send periodic keep-alive frames on idle connections:

```yaml
io.sapl.node:
  keep-alive: 15
```

Set the proxy read timeout above this interval (e.g., 60 seconds). See [Configuration](../7_2_Configuration/) for the property reference.

#### nginx

```nginx
location /api/pdp/ {
    proxy_pass http://127.0.0.1:8443;
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 3600s;
    proxy_set_header Connection '';
    proxy_http_version 1.1;
    chunked_transfer_encoding on;
}

location /actuator/ {
    proxy_pass http://127.0.0.1:8443;
}
```

#### Apache

Enable `mod_proxy` and `mod_proxy_http`. Disable response buffering for the PDP path:

```apache
ProxyPass /api/pdp/ http://127.0.0.1:8443/api/pdp/
ProxyPassReverse /api/pdp/ http://127.0.0.1:8443/api/pdp/
SetEnv proxy-sendchunked 1
SetEnv proxy-sendcl 0
ProxyTimeout 3600

ProxyPass /actuator/ http://127.0.0.1:8443/actuator/
ProxyPassReverse /actuator/ http://127.0.0.1:8443/actuator/
```

The one-shot endpoints (`/api/pdp/decide-once`, `/api/pdp/multi-decide-all-once`) and actuator endpoints work with default proxy settings.

### Server Implementation

The SAPL Policy Engine ships with **SAPL Node**, a standalone PDP server. SAPL Node supports filesystem directories, signed bundles, and remote bundle fetching as policy sources. It is available as a Docker container and as a native binary. See [SAPL Node](../7_0_SaplNode/) for deployment and configuration.
