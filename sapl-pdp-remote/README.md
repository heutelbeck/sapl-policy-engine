## Remote SAPL Policy Decision Point (PDP)

This module implements the PDP API in the form of a client library for a dedicated SAPL Server. This implements only the PDP itself, without any support for writing Policy Enforcement Points (PEPs). PEPs are implemented in their respective framework integration modules for the specific framework (e.g., Spring Boot, Axon, Vaadin).

### Configuration

The client is created via `RemotePolicyDecisionPoint.builder().http()` and supports the following settings after construction:

| Setting | Default | Description |
|---------|---------|-------------|
| `timeoutMillis` | 5000 | Request-response timeout for `decideOnce()`. Also used as the connect timeout for streaming connections (time to first decision). |
| `firstBackoffMillis` | 500 | Initial delay for exponential backoff on streaming reconnection. |
| `maxBackOffMillis` | 5000 | Maximum delay cap for exponential backoff. |
| `maxRetries` | unlimited | Maximum number of streaming reconnection attempts before giving up. |

Streaming connections use exponential backoff with jitter. Log severity escalates from WARN to ERROR after 5 consecutive failures. All communication failures result in `INDETERMINATE` (fail-closed).

### Authentication

The builder supports three mutually exclusive authentication methods:

- `basicAuth(key, secret)` - HTTP Basic Authentication
- `apiKey(key)` - Bearer token (`Authorization: Bearer <key>`)
- `oauth2(repository, registrationId)` - OAuth2 Client Credentials

### SSL/TLS

The default base URL is `https://localhost:8443`. For custom certificate handling:

- `secure()` - Use JVM default SSL context
- `secure(sslContext)` - Use a custom `SslContext`
- `withUnsecureSSL()` - Disable certificate verification (development only, logs a warning)
