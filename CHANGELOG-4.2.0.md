# SAPL 4.2.0

This release adds bundle carried extensions, sealed secrets, signed realm indexes, and optional admission limits on the SAPL Node transports.

- PDP configuration bundles can carry extension data that the host runtime deploys transactionally with each configuration update. The capability is host specific and intentionally not covered in detail in the reference documentation.
- Secrets in PDP configurations can be sealed as JWE tokens (ECDH-ES on X25519 with A256GCM) and are unsealed by the PDP at configuration load, so bundles at rest never contain cleartext secrets.
- Realm indexes sign the set of bundles a consumer tracks (EdDSA JWS) and carry a monotonic sequence that rejects rollback and replay of older configurations.
- The PDP configuration and bundle sources were reworked for robustness, with hardened parsing and consistent error handling across all source types.
- SAPL Node gained optional global admission limits, caps on concurrent SSE streams, RSocket connections and streams per connection, and request rate limits on the unary endpoints. All default to unbounded, rejections fail closed with retriable errors and are observable through the `sapl.limits.rejections` metric. See the configuration reference for the `io.sapl.node.limits` properties.
- SAPL Node JWT access can now be gated by scope via `io.sapl.node.oauth.required-scopes`, complementing the audience gate, and `oauth.pdp-id-claim` accepts dot-separated paths into nested claims, validated at startup.
