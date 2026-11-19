# SAPL 4.2.0

This release adds bundle carried extensions, sealed secrets, signed realm indexes, and optional admission limits on the SAPL Node transports.

- PDP configuration bundles can carry extension data that the host runtime deploys transactionally with each configuration update. The capability is host specific and intentionally not covered in detail in the reference documentation.
- Secrets in PDP configurations can be sealed as JWE tokens (ECDH-ES on X25519 with A256GCM) and are unsealed by the PDP at configuration load, so bundles at rest never contain cleartext secrets.
- Realm indexes sign the set of bundles a consumer tracks (EdDSA JWS) and carry a monotonic sequence that rejects rollback and replay of older configurations.
- The PDP configuration and bundle sources were reworked for robustness, with hardened parsing and consistent error handling across all source types.
- SAPL Node gained optional global admission limits, caps on concurrent SSE streams, RSocket connections and streams per connection, and request rate limits on the unary endpoints. All default to unbounded, rejections fail closed with retriable errors and are observable through the `sapl.limits.rejections` metric. See the configuration reference for the `io.sapl.node.limits` properties.
- SAPL Node JWT access can now be gated by scope via `io.sapl.node.oauth.required-scopes`, complementing the audience gate, and `oauth.pdp-id-claim` accepts dot-separated paths into nested claims, validated at startup.
- The HTTP policy information point now binds credentials to destinations. A credential is a named `secrets.http.<key>` entry carrying its headers and an `allowedBaseUrls` allowlist; policies can no longer supply credential headers, and a secret is sent only to a URL matching its allowlist by scheme, host, port, and path prefix. Fail closed, no unnamed default credential.

## Breaking changes

- The `secrets` of an authorization subscription are an object on the RSocket wire (protobuf field 5 is now `ObjectValue`). RSocket clients and SDKs of 4.1.x cannot talk to a 4.2 node. Upgrade clients and node together.
- A user secret or API key in the SAPL Node configuration that is not an Argon2 hash refuses the start. A `{argon2id}` prefix is no longer accepted. Regenerate such entries with `sapl generate basic` or `sapl generate apikey`.
- An unknown key under `io.sapl.node` refuses the start, so a misspelt key no longer goes unnoticed.
- An OAuth2 issuer must use https, and so must the key address its discovery document names. Plain http needs `io.sapl.node.oauth.allow-insecure-issuer=true` and logs a warning at start.
- SAPL Node requires TLS on HTTP and RSocket, on every address, loopback included. Without TLS it refuses to start unless plaintext is allowed with `sapl server --no-tls` or `io.sapl.node.allow-no-tls=true`, for example where TLS is terminated upstream. Containers and scripts that serve plaintext set `IO_SAPL_NODE_ALLOWNOTLS=true`.
- `io.sapl.node.tls.certificate-chain` and `io.sapl.node.tls.private-key` name one PEM certificate for both APIs, served again without a restart when the files change. `server.ssl.bundle` and `sapl.pdp.rsocket.ssl.bundle` override it per API, and a renewed bundle with `reload-on-update` reaches both APIs. A certificate is reported in the log from 14 days before it expires.
- The shipped `application.yml` no longer sets `server.ssl.enabled: false` nor a cipher list, so naming an SSL bundle in `server.ssl.bundle` switches HTTPS on with the JDK defaults. A bundle named while `server.ssl.enabled` is false refuses the start.
- The package configuration in `/etc/sapl/application.yml` no longer sets `allow-no-auth: true` and sets no opt-in at all. A package installation needs TLS and an authentication mode, or the explicit opt-ins, before the service starts.
- Each opt-in warning names where the opt-in was set: the command-line option, the key in its file, or the environment variable. Anonymous access logs a warning at every start, not only on a non-loopback address.
- A request with two `Authorization` headers is refused with 401.
- A tenant claim that is an object or a list counts as no tenant.
- Bundles are read strictly. Duplicate entry names, entries the archive's central directory hides, inconsistent archives, names or content that are not UTF-8, a creation time not in the form `YYYY-MM-DDTHH:MM:SS[.fraction]Z`, an empty or incomplete signature block and weak (small order) Ed25519 keys are refused. The CLI reads bundles with the same hardened reader as the PDP.
- A recipient key whose public part does not belong to its private part is refused when it is loaded.
- The subject of a schema statement must be `subject`, `action`, `resource` or `environment`.
- Regular expressions are matched with RE2/J in time linear in the length of the text, never backtracking, so that the Java engine and the other implementations of SAPL accept the same patterns. This applies to the `=~` operator, the `patterns` function library, the `pattern` and `patternProperties` keywords of JSON schemas, and the `=~` conditions of the Spring content filter constraints. Patterns follow the RE2 syntax instead of `java.util.regex`. Look-around, backreferences, atomic groups, possessive quantifiers and syntax only Java knows (`\h`, `\R`, `\X`, `\G`, `\Z`, `\e`, `\cX`, `\p{javaLowerCase}` and the like) make a pattern invalid. `\d`, `\w`, `\s` and `\b` stay ASCII, `(?i)` now folds case by Unicode, `.` matches every character except the line feed, `\v` is the vertical tab only, and `(?U)` makes repetitions lazy instead of making classes Unicode aware. An invalid pattern is reported with the reason RE2/J gives.
- Because matching is linear, the time budget for regular expressions and the blacklist of dangerous patterns in the `patterns` library are removed, and with them the errors "Regular expression evaluation exceeded its time budget." and "Invalid or dangerous regex pattern.". The limit of 100 alternations in the `patterns` library is gone. Instead a pattern may compile to at most 10000 instructions and nest its groups at most 250 levels deep. The limits of the `patterns` library on the length of patterns, inputs, results and the number of matches stay.
- The diagnostics of the parser use a new problem catalogue with new messages, and positions follow the LSP specification. In the Java API, `Document` carries these diagnostics and no longer has `syntaxErrors`, `validationErrors` and `errorMessage`. `TracedValue` and `AttributeRecord` are removed from `sapl-api`.

## Other changes

- The SAPL Node authentication cache (`io.sapl.node.http.auth-cache.*`) now also covers RSocket connections. The keys keep their names.
- A stack overflow while evaluating a policy answers INDETERMINATE for that subscription, and the decision stream stays open.
- Unsealing errors now tell a malformed token apart from a key that does not fit the token.
