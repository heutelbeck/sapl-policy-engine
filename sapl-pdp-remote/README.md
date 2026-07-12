## Remote SAPL Policy Decision Point (PDP)

This module implements the PDP API in the form of a client library for a dedicated SAPL Server. This implements only the PDP itself, without any support for writing Policy Enforcement Points (PEPs). PEPs are implemented in their respective framework integration modules for the specific framework (e.g., Spring Boot, Axon, Vaadin).

### Configuration

The client is created via `RemotePolicyDecisionPoint.builder().http()` and supports the following settings after construction:

| Setting | Default | Description |
|---------|---------|-------------|
| `timeoutMillis` | 5000 | Request-response timeout for `decideOnce()`. Also used as the connect timeout for streaming connections (time to first decision). |
| `firstBackoffMillis` | 500 | Initial delay for exponential backoff on streaming reconnection. |
| `maxBackOffMillis` | 60000 | Maximum delay cap for exponential backoff. A sustained outage settles into a ~1 minute reconnect heartbeat rather than a tight retry loop. |
| `maxRetries` | unlimited | Maximum number of streaming reconnection attempts before giving up. |

Streaming connections use exponential backoff with jitter. Log severity escalates from WARN to ERROR after 5 consecutive failures. All communication failures result in `INDETERMINATE` (fail-closed).

### Authentication

The builder supports three mutually exclusive authentication methods:

- `basicAuth(key, secret)` - HTTP Basic Authentication
- `apiKey(key)` - Bearer token (`Authorization: Bearer <key>`)
- `oauth2(repository, registrationId)` - OAuth2 Client Credentials

### SSL/TLS

An `https://` base URL uses TLS automatically (Reactor Netty applies a default SSL context for the secure scheme). The builder default is `https://localhost:8443`; the SAPL Node ships plain HTTP on `http://localhost:8080`, so most consumers set `baseUrl` explicitly. To customize certificate trust:

- `secure(sslContext)` - Use a custom `SslContext`
- `withUnsecureSSL()` - Disable certificate verification (development only, logs a warning)
- `allowInsecureTransport()` - Permit sending credentials over a plaintext (non-https) connection (development only)

Sending credentials over a plaintext `http://` connection is refused at `build()` time. Set `allowInsecureTransport()` to accept that risk for local development, or use an `https://` base URL in production.
