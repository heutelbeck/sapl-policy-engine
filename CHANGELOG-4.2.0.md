# SAPL 4.2.0

This release adds bundle carried extensions, sealed secrets, signed realm indexes, and optional admission limits on the SAPL Node transports.

- PDP configuration bundles can carry extension data that the host runtime deploys transactionally with each configuration update. The capability is host specific and intentionally not covered in detail in the reference documentation.
- Secrets in PDP configurations can be sealed as JWE tokens (ECDH-ES on X25519 with A256GCM) and are unsealed by the PDP at configuration load, so bundles at rest never contain cleartext secrets.
- Realm indexes sign the set of bundles a consumer tracks (EdDSA JWS) and carry a monotonic sequence that rejects rollback and replay of older configurations.
- The PDP configuration and bundle sources were reworked for robustness, with hardened parsing and consistent error handling across all source types.
- SAPL Node gained optional global admission limits, caps on concurrent SSE streams, RSocket connections and streams per connection, and request rate limits on the unary endpoints. All default to unbounded, rejections fail closed with retriable errors and are observable through the `sapl.limits.rejections` metric. See the configuration reference for the `io.sapl.node.limits` properties.
- SAPL Node JWT access can now be gated by scope via `io.sapl.node.oauth.required-scopes`, complementing the audience gate, and `oauth.pdp-id-claim` accepts dot-separated paths into nested claims, validated at startup.
- The HTTP policy information point now binds credentials to destinations. A credential is a named `secrets.http.<key>` entry carrying its headers and an `allowedBaseUrls` allowlist; policies can no longer supply credential headers, and a secret is sent only to a URL matching its allowlist by scheme, host, port, and path prefix. Fail closed, no unnamed default credential.

## Security

- A JSON schema could make the engine read resources of the Java class path. A schema naming `classpath:` or `resource:` URIs, or paths below `https://json-schema.org/` other than the meta-schemas, in `$schema` or `$ref` was loaded from the class path, and a missing resource was logged at ERROR with the name the schema gave. Schemas reach the engine in schema statements, in the `jsonschema` function library, whose schemas may come from an authorization subscription, and in the field validation obligations of the Vaadin integration. A schema may now name only the meta-schemas of the drafts 4, 6, 7, 2019-09 and 2020-12 that ship with the validator, and the schemas of the `schemas` variable or of the `externalSchemas` argument by their `$id`. Any other reference, whatever its scheme (`http`, `https`, `file`, `jar`, `classpath` or another), makes the schema invalid: an enforced schema statement refuses the policy at compile time, the library functions return an error, and a Vaadin field validation obligation naming such a schema fails when it is handled. Schemas were never fetched over the network nor read from files. The same fix is in 4.1.4.

## Breaking changes

- CSV output cells must be text, numbers, booleans or null, including cells overwritten by normalized keys. Arrays, objects, undefined and errors are rejected. Public CSV SDK calls return an error for unsupported cells instead of throwing an argument exception.

- The Base64 and Base64 URL functions in `encoding` require zero unused pad bits. For example, use `Zg==` for `f`. Both strict and lenient functions accept the empty string. Only the lenient functions accept omitted padding. Decoding errors for invalid Base64 text now use SAPL messages.

- `time.timeBetween` recognizes English unit names independently of the JVM locale. For example, `minutes` also works under a Turkish default locale.

- `time.durationFromISO` reads whole weeks and mixed calendar and clock components. It uses exactly 31,556,952,000 milliseconds per year and 2,629,746,000 per month. A negative duration has one minus before `P`. Component signs such as `PT-1S` are rejected. `time.durationToISOCompact(-1500)` now returns `-PT1.5S`. `time.durationToISOVerbose` uses the same exact year and month lengths.

- Glob character classes keep escaped hyphens literal and retain commas and braces inside alternative groups.

- Time functions use UTC for local date-times, blank zone arguments and calendar-unit `timeBetween` calculations, independently of the JVM default zone.

- Explicit time-library zone arguments refuse `ZoneId.SHORT_IDS` aliases such as `EST` and `PST`. Use region IDs, UTC or fixed offsets.

- Quantity unit suffixes use locale-independent casing. For example, `2Kİ` and `2KİB` now report an unknown unit under a Turkish JVM locale as well.

- Scalar permission values accept signed 64-bit whole numbers written with decimal places, such as `6.00`.

- Bitwise functions accept signed 64-bit whole numbers written with decimal places, including `2.0` as an operand or bit position.

- Head-only attribute reads release their finder after the first observation. The frozen decision stays current. Later finder events no longer produce traces for this policy.

- Invalid compiler limits reject a configuration during loading. After a plugin change the PDP recompiles the last configuration with valid options.

- Semantic versions accept numeric prerelease and build identifiers of any length. The limit of 9007199254740991 applies to major, minor and patch.

- `toml.tomlToVal` preserves signed 64-bit integer endpoints and rejects integers outside that range.
- `toml.valToToml` requires an object, rejects null values, and writes an empty object as an empty document.

- JSON readers require a string `subscriptionId` on identified authorization decisions. Clients that send numeric IDs must quote them.

- `yaml.valToYaml` writes block YAML with two-space indentation, JSON-quoted strings, and SAPL wire numbers. Documents omit the leading marker and retain long strings on one line.

- `yaml.yamlToVal` reads YAML 1.2 core scalars and exact decimals, resolves aliases to their values, and rejects unsupported tags. Empty YAML values now produce SAPL null.

- Schema preparation checks reachable dynamic reference targets, including conditional branches. A broken ordinary reference in such a target rejects the whole schema at load time.

- Use `graph.reachablePaths` for shortest paths. The deprecated `graph.reachable_paths` alias remains available. Path-limit errors now identify the maximum number of path steps.

- Graph closures now list reachable nodes in graph key order, followed by neighbor-only nodes in first-mention order. This changes closure arrays, the key order of closure sets, and the order of projected attributes.

- String lengths, search and substring indices, padding, and `standard.length` on text count Unicode code points. Supplementary characters count once and are not split. Explicit text size limits still count UTF-16 code units.
- Indeterminate `unique`, `unanimous`, and `unanimous strict` policy sets retain applicable effects across constant, subscription, and streaming evaluation. An enclosing priority voter can now classify a previously missed effect as critical.
- Java record patterns for `EvaluationContext`, `BranchHit`, the AST record `PolicySet`, and the `Coverage` records `PolicyCoverage`, `PolicySetCoverage`, `BodyCoverage` and `ConditionHit` must include their new components. The previous constructors remain available.
- The expression compiler rejects `<`, `<=`, `>` and `>=` when an operand folds to a boolean. The diagnostic points to that operand. Operand types that depend on the request are checked during evaluation.
- SAPL number-to-text conversion is exact and independent of decimal scale. `standard.toString(1.50)` now yields `"1.5"` and `"n=" + 1.50` yields `"n=1.5"`. Nonzero magnitudes below `1e-6` or at least `1e21` use scientific notation.
- Bundle manifests require a JSON string for `configurationId`. Producers with numeric internal IDs must emit, for example, `"configurationId": "42"`.
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
- HTTP, JWT key-server, MQTT and Traccar endpoints can select a shared CA bundle from `variables.tlsTrustProfiles` with `trustProfile`. A profile replaces default roots unless `includeDefaultTrustRoots` is true. MQTT and Traccar now use named entries under `variables.mqtt.brokers` and `variables.traccar.servers`, an optional `default` name and matching named credentials. MQTT file truststores and flat credentials are no longer supported. JWT key servers require a fixed host.
- Bundles are read strictly. Duplicate entry names, entries the archive's central directory hides, inconsistent archives, names or content that are not UTF-8, a creation time not in the form `YYYY-MM-DDTHH:MM:SS[.fraction]Z`, an empty or incomplete signature block and weak (small order) Ed25519 keys are refused. The CLI reads bundles with the same hardened reader as the PDP.
- A recipient key whose public part does not belong to its private part is refused when it is loaded.
- The subject of a schema statement must be `subject`, `action`, `resource` or `environment`.
- Regular expressions are matched by the Rust `regex` crate 1.13.2 in time linear in the length of the text, never backtracking. The engine uses it through the library `com.heutelbeck:rustregex4j`. This applies to the `=~` operator, the `patterns` function library, the `pattern` and `patternProperties` keywords of JSON schemas, and the `=~` conditions of the Spring content filter constraints. Patterns follow the syntax of the `regex` crate instead of `java.util.regex`. Look-around, backreferences, atomic groups and syntax only Java knows (`\h`, `\R`, `\X`, `\G`, `\Z`, `\e`, `\cX`, `\p{javaLowerCase}`, the quoted section `\Q...\E` and the like) make a pattern invalid, and `a*+` is a repetition of `a*`, not a possessive quantifier. `\d`, `\w`, `\s` and `\b` follow Unicode, as they did in `java.util.regex` only with `(?U)`; `(?-u:\d)` or `[0-9]` restrict them to ASCII. `(?i)` folds case by Unicode, `.` matches every character except the line feed, and `(?U)` makes repetitions lazy instead of making classes Unicode aware. Within a class, `&&`, `--` and `~~` are set operations. An invalid pattern is reported with the reason the parser of the `regex` crate gives.
- The regex engine is native code: the jar of rustregex4j carries it for Linux x86_64 and aarch64 (glibc 2.17 or newer, and musl), macOS aarch64 and x86_64, and Windows x86_64. On other platforms, the system property `rustregex4j.library` names an engine library built for the platform. On JDK 22 and newer, the JVM warns about the native access unless it is started with `--enable-native-access=ALL-UNNAMED`. The fully static native images of the SAPL node and language server link the engine in.
- Because matching is linear, the time budget for regular expressions and the blacklist of dangerous patterns in the `patterns` library are removed, and with them the errors "Regular expression evaluation exceeded its time budget." and "Invalid or dangerous regex pattern.". The limit of 100 alternations in the `patterns` library is gone. Instead a pattern may be at most 1024 bytes long in UTF-8, nest at most 64 levels deep and compile to at most 256 KiB, with a lazy DFA cache of at most 2 MiB, and a text matched against it may be at most 64 KiB long in UTF-8. Bounded repetitions of large Unicode classes copy the class, so a pattern such as `\w{1,32}` exceeds the default size limit, while `\w+` is small and `[A-Za-z0-9_]{3,32}` or `(?-u:\w){3,32}` express a bound. The compiler options `regexMaxPatternBytes`, `regexSizeLimitKibibytes` and `regexMaxTextBytes` raise or lower the limits of a PDP configuration for the `=~` operator and the patterns of enforced schemas; the function libraries, the content filter and the test language keep the defaults. A text beyond the limit, or one holding an unpaired surrogate, makes the match an error, also under a negated schema pattern, and makes a content filter condition deny access. The limits of the `patterns` library on the length of patterns and inputs and on the number of matches stay; the input of the regular expression functions and the result of `replaceAll` may be at most 64 KiB long in UTF-8 instead of 100000 and 10000000 characters.
- `patterns.replaceAll` and `patterns.split` behave as `replace_all` and `splitn` of the `regex` crate. In a replacement, `$1` and `${1}` insert a group by number, `$name` and `${name}` by name, and `$$` inserts `$`; a backslash no longer escapes the next character but stands for itself. A group that does not exist inserts nothing instead of making the replacement an error, and a name takes all following letters, digits and `_`, so `$10` is group 10 and `${1}0` is group 1 followed by `0`. `split` keeps an empty first segment also when an empty match lies at the start of the text, so that `patterns.split("x*", "ab")` is `["", "a", "b", ""]`.
- The SMTDD policy index groups the `=~` conditions that match the same expression against constant patterns, as it groups equality conditions: it evaluates the expression once and finds all matching patterns with one search, instead of one search per policy. A policy set with a thousand such policies is decided in under a microsecond instead of a few hundred. Patterns under a negation or a disjunction stay single conditions. Building the index for many conditions that cannot be grouped no longer takes seconds before it falls back to the canonical index. The compiler option `maxIndexNodes` now counts every node of the index, also the branch nodes of grouped conditions, and applies to an index of a single condition as well, so a small limit refuses indexes it let through before.
- JSON schemas follow the syntax of the `regex` crate as well, although the JSON Schema specification names ECMA-262 for `pattern` and `patternProperties`. A schema whose pattern uses look-ahead, look-behind or backreferences, or is too large, never lets a subscription through: an enforced schema statement with such a schema is refused when the policy is compiled, also if the pattern sits in a branch a subscription might never reach or in a schema of the `schemas` variable reached through `$ref`, and the functions of the `jsonschema` library return an error, never `true`. Password rules and similar checks written with look-ahead are rewritten as several `pattern` keywords under `allOf`, and a negative look-ahead as `not`.
- The `text with regex` matcher of the test language and `Matchers.textMatching` of the Java fixture API use the syntax of policies instead of `java.util.regex` and match the whole text. The editor marks a pattern that a policy would refuse, and the length limit of 1000 characters for these patterns is replaced by the limits of policy patterns. `Matchers.textMatching` refuses an invalid pattern with an `io.sapl.compiler.util.RegexRefusedException` when the matcher is created.
- JSON and Protobuf may use a shorter equivalent decimal notation. Both readers accept a numeric token of up to 1010 characters when its value fits the arithmetic range exactly.
- Arithmetic results must have an exact representation with at most 1000 coefficient digits and a scale from -1000 to 1000. For example, `1E-1000 * 2.0` gives `2E-1000`, while `1E-1000 * 0.1` gives the error "Numeric operation exceeded the supported range."
- The operands of `==`, `!=`, `*`, `&&`, `||`, `&`, `|` and `^` are evaluated in a canonical order within constants, pure expressions and attribute streams, so `a == b` and `b == a` are the same expression and share one entry of the policy index. When several operands are errors, the first in the canonical order is reported, which need not be the first one written. `+` keeps the order written, as it concatenates texts.
- The diagnostics of the parser use a new problem catalogue with new messages, and positions follow the LSP specification. In the Java API, `Document` carries these diagnostics and no longer has `syntaxErrors`, `validationErrors` and `errorMessage`. `TracedValue` and `AttributeRecord` are removed from `sapl-api`.

### Function libraries and policy information points

- An unresolved ordinary `$ref` now invalidates a schema during preparation, including a reference inside an unused definition. Supply its target or remove the definition.
- A supplied schema without `$schema` now inherits the main schema's draft. Declare `$schema` on a supplied schema that requires its own draft.
- Absolute checkpoints in `time.nowIsBefore`, `time.nowIsAfter` and `time.nowIsBetween` accept written years from -9999 to 9999 and require a digit after any decimal separator. Write `12:30:00Z` for an exact second.
- The `time` attributes accept English weekday and month names with ASCII letters in either case. Replace Unicode lookalikes such as `ſunday` with `Sunday`.
- `array.sum` and `array.multiply` now return an error when the exact final result exceeds the range of SAPL arithmetic. Intermediate results may exceed that range.

- `x509.isExpired` becomes true strictly after `notAfter`. Before `notBefore` both certificate attributes return false. A certificate is valid at both endpoints.
- The local time before, after and between attributes follow the displayed clock through daylight saving gaps and overlaps. Local ranges include their start and exclude their end.

- Replace `semver.isAtLeast` with `semver.isHigherOrEqual` and `semver.isAtMost` with `semver.isLowerOrEqual`. Rewrite Ivy ranges such as `[1.0.0,2.0.0)` as `>=1.0.0 <2.0.0`. Invalid range tokens now produce errors.
- Use dotted-quad IPv4 or RFC 4291 IPv6 in `cidr`, without whitespace, zone IDs or zero-padded IPv4 octets or prefixes. `cidr.anonymizeIp` now includes the prefix length. Recompute stored `cidr.hashIpPrefix` hashes.
- Pass each GeoJSON Feature's geometry to `geo` functions. Use a supported geographic CRS such as `EPSG:4326` for `geo.geoDistance`. Remove `crs` from expected `geo` geometry results and `traccar.traccarGeofenceToGeoJson` results.
- For instant arguments to the `time` function library and `x509.isValidAt`, write `24:00:00Z` as `00:00:00Z` on the following day. Handle `false` from `signature.isValidEd25519` for a decoded signature of the wrong length.
- Move the contents of `pdp.json`'s `secrets` object, including MQTT and Traccar credentials, into a sibling `secrets.json` and seal it or enable cleartext explicitly. For HTTP, set `secretsKey` to the name of the credential whose `allowedBaseUrls` covers the request.
- Rename invalid Java function and finder names such as `item-count` to `item_count`. Give ambiguous finders within a PIP distinct names. Custom `AttributeRepository` implementations must implement atomic `publish(List<AttributeAssignment>)` and `observeState(pdpId, configurationId, onUpdate)`, registering the observer and capturing its initial view atomically.

## Policy evaluation fixes

- XOR now reports a known non-boolean operand even while another operand is pending. A constant or subscription element of the wrong type can report the error before any attributes are requested.
- Double negation now checks that its operand is boolean. For example, with a numeric subject, `obligation !!subject` makes the policy indeterminate. A boolean obligation keeps its original value.
- Coverage observes the same compiled voter as an ordinary decision, with the same attribute requests and votes for either value of `lowLatencyMode`. A pending `decideOnceWithCoverage` request follows a replacement PDP configuration.
- Coverage reports place each completed member policy's decision at its own declaration and preserve separate source hits for conditions that share an index predicate. A set's target result includes its enforced schema and any `for` expression.
- Compiling an enforced JSON Schema checks the schema against its declared dialect. An invalid schema rejects the configuration. The `jsonschema` functions return an error value for an invalid schema supplied at evaluation time.
- If a new configuration becomes active for the same PDP while `decideOnce` is waiting for an attribute, the request uses the new configuration. A retired configuration can no longer release a decision after its replacement.
- A policy set's target or enforced schema could read a variable declared in its last policy, changing which requests matched the set. Policy-local variables are now visible only inside the declaring policy.
- SMTDD now reports a type error when an object-key check with `has` receives a defined non-string key. Equality checks on the same value still match normally.
- CANONICAL keeps a shared condition available to every policy that still needs it when another policy's target encounters an error. This preserves the decision and its obligations when several policy targets overlap.
- CANONICAL and SMTDD preserve errors in complementary conditions. For example, with a text subject, `subject && !subject` produces a type error just as it does with NAIVE. Boolean subjects still make this condition false.
- A priority combiner remembers incompatible resource transformations from votes of the same effect. A further vote of that effect preserves the conflict. A higher-priority effect can still determine the result.

## Other changes

- Shared text-format size errors report their limits using the same digits in every JVM locale.

- The return schemas for `semver.minSatisfying` and `semver.maxSatisfying` include `null` when no version matches.

- Temporal functions and absolute time attributes accept historical ISO years from -9999 to 9999. Both `-0043` and `-000043` denote 44 BCE. Date and instant results use `-0043`.
- Rust PDPs now verify Ed448-signed JWTs by default, matching Java PDPs.

- `sapl.info()` returns the engine neutral keys `engine` and `engineVersion`, which every SAPL engine reports. The keys `jdkVersion`, `javaVersion` and `javaVendor` are deprecated and will be removed in the next release.
- The SAPL Node authentication cache (`io.sapl.node.http.auth-cache.*`) now also covers RSocket connections. The keys keep their names.
- A stack overflow while evaluating a policy answers INDETERMINATE for that subscription, and the decision stream stays open.
- Unsealing errors now tell a malformed token apart from a key that does not fit the token.
- An invalid or too large pattern in a `=~` condition of the Spring content filter constraints denies access with an `AccessDeniedException` that names the reason, like every other invalid condition, instead of escaping as a `PatternSyntaxException`.
