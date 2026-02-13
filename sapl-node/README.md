# SAPL Node

SAPL Node is a lightweight Policy Decision Point (PDP) server that provides authorization decisions via HTTP API using the Streaming Attribute Policy Language (SAPL). It supports hot-reloading of policies, signed policy bundles, multi-tenant routing, and multiple authentication schemes.

## Prerequisites

- JDK 21 or later
- Maven 3.9 or later (when building from source)

## Building from Source

```shell
git clone https://github.com/heutelbeck/sapl-policy-engine.git
cd sapl-policy-engine
mvn install -DskipTests
```

The executable JAR is at `sapl-node/target/sapl-node-4.0.0-SNAPSHOT.jar`.

## Quick Start

The following walkthrough uses the CLI tools to set up a working SAPL Node from scratch.

### 1. Write a Policy

Create a directory for your policies:

```shell
mkdir -p ~/sapl-node/policies
```

Create `~/sapl-node/policies/pdp.json`:

```json
{
  "algorithm": {
    "votingMode": "PRIORITY_PERMIT",
    "defaultDecision": "DENY",
    "errorHandling": "PROPAGATE"
  },
  "variables": {}
}
```

Create `~/sapl-node/policies/permit-read.sapl`:

```
policy "permit-read"
permit
    action == "read";
```

### 2. Create a Policy Bundle

```shell
java -jar sapl-node-4.0.0-SNAPSHOT.jar bundle create \
  -i ~/sapl-node/policies \
  -o ~/sapl-node/policies/default.saplbundle
```

Output:

```
Created bundle: /home/user/sapl-node/policies/default.saplbundle (1 policies)
```

### 3. Generate Signing Keys

```shell
java -jar sapl-node-4.0.0-SNAPSHOT.jar bundle keygen \
  -o ~/sapl-node/signing-key
```

Output:

```
Generated Ed25519 keypair:
  Private key: /home/user/sapl-node/signing-key.pem
  Public key:  /home/user/sapl-node/signing-key.pub
```

Keep the private key secure. The server uses the public key to verify bundles.

### 4. Sign the Bundle

```shell
java -jar sapl-node-4.0.0-SNAPSHOT.jar bundle sign \
  -b ~/sapl-node/policies/default.saplbundle \
  -k ~/sapl-node/signing-key.pem \
  --key-id prod-2026
```

Output:

```
Signed bundle: /home/user/sapl-node/policies/default.saplbundle (key-id: prod-2026)
```

### 5. Verify the Bundle

```shell
java -jar sapl-node-4.0.0-SNAPSHOT.jar bundle verify \
  -b ~/sapl-node/policies/default.saplbundle \
  -k ~/sapl-node/signing-key.pub
```

Output:

```
Verification successful
  Key ID: prod-2026
  Created: 2026-02-07T14:13:24.414521581Z
  Files verified: 2
```

### 6. Inspect a Bundle

```shell
java -jar sapl-node-4.0.0-SNAPSHOT.jar bundle inspect \
  -b ~/sapl-node/policies/default.saplbundle
```

Output:

```
Bundle: default.saplbundle

Signature:
  Status: SIGNED
  Algorithm: Ed25519
  Key ID: prod-2026
  Created: 2026-02-07T14:13:24.414521581Z

Configuration (pdp.json):
  {
    "algorithm": {
      "votingMode": "PRIORITY_PERMIT",
      "defaultDecision": "DENY",
      "errorHandling": "PROPAGATE"
    },
    "variables": {}
  }

Policies:
  - permit-read.sapl (50 bytes)
```

### 7. Generate Client Credentials

Generate an API key for a client application:

```shell
java -jar sapl-node-4.0.0-SNAPSHOT.jar generate apikey \
  --id my-client --pdp-id default
```

Output:

```
API Key
=======

User ID: my-client
PDP ID:  default
Key:     sapl_t18oOMEJp8_YN9QHFlgsbZ88lK82HyeoQbEAQU55vHV

Configuration (application.yml):
--------------------------------
io.sapl.node:
  allowApiKeyAuth: true
  users:
    - id: "my-client"
      pdpId: "default"
      apiKey: "$argon2id$v=19$m=16384,t=2,p=1$..."

Usage (curl):
-------------
curl -H 'Authorization: Bearer sapl_t18oOMEJp8_...' \
  -X POST https://localhost:8443/api/pdp/decide-once \
  -H 'Content-Type: application/json' \
  -d '{"subject":"alice","action":"read","resource":"document"}' \
  --cacert server.crt
```

Or generate Basic Auth credentials:

```shell
java -jar sapl-node-4.0.0-SNAPSHOT.jar generate basic \
  --id my-client --pdp-id default
```

Store the plaintext credentials securely. Only the Argon2-encoded values go into the server configuration.

### 8. Configure and Start the Server

Create a `config/application.yml` next to the JAR:

```yaml
io.sapl:
  pdp.embedded:
    pdp-config-type: BUNDLES
    policies-path: bundles
  node:
    allowNoAuth: true
    defaultPdpId: "default"

server:
  address: localhost
  port: 8080
  ssl:
    enabled: false
```

Place your `.saplbundle` file in the `bundles/` directory. The filename without extension becomes the PDP ID (e.g., `default.saplbundle` serves PDP ID `default`).

Start the server:

```shell
java -jar sapl-node-4.0.0-SNAPSHOT.jar
```

The server loads all bundles from the `bundles/` directory and watches for changes. When you replace a bundle file, the server reloads it automatically.

## CLI Reference

SAPL Node includes CLI tools that run without starting the server.

```
sapl-node [COMMAND]

Commands:
  bundle    Policy bundle operations
  generate  Generate authentication credentials
```

Check the version:

```shell
java -jar sapl-node-4.0.0-SNAPSHOT.jar --version
```

### bundle create

Create a policy bundle from a directory containing `.sapl` files and a `pdp.json`.

```
sapl-node bundle create -i <dir> -o <file>

Options:
  -i, --input   Input directory containing policies
  -o, --output  Output .saplbundle file
```

### bundle sign

Sign a bundle with an Ed25519 private key. Overwrites the input bundle unless `-o` is specified.

```
sapl-node bundle sign -b <file> -k <key> [options]

Options:
  -b, --bundle   Bundle file to sign
  -k, --key      Ed25519 private key file (PEM, PKCS8)
  -o, --output   Output file (default: overwrites input)
  --key-id       Key identifier (default: "default")
```

### bundle verify

Verify a signed bundle against an Ed25519 public key. Exit code 0 on success, 1 on failure.

```
sapl-node bundle verify -b <file> -k <key> [options]

Options:
  -b, --bundle          Bundle file to verify
  -k, --key             Ed25519 public key file (PEM, X.509)
  --check-expiration    Fail if signature has expired
```

### bundle inspect

Display bundle contents, signature status, configuration, and policy list.

```
sapl-node bundle inspect -b <file>

Options:
  -b, --bundle  Bundle file to inspect
```

### bundle keygen

Generate an Ed25519 keypair for bundle signing.

```
sapl-node bundle keygen -o <prefix> [options]

Options:
  -o, --output  Output prefix (creates <prefix>.pem and <prefix>.pub)
  --force       Overwrite existing files
```

### generate basic

Generate Basic Auth credentials with Argon2-encoded password.

```
sapl-node generate basic --id <id> --pdp-id <pdpId>

Options:
  -i, --id       Client identifier
  -p, --pdp-id   PDP ID for multi-tenant routing
```

### generate apikey

Generate an API key with Argon2-encoded hash.

```
sapl-node generate apikey --id <id> --pdp-id <pdpId>

Options:
  -i, --id       Client identifier
  -p, --pdp-id   PDP ID for multi-tenant routing
```

## Server Configuration

SAPL Node is configured via `application.yml`. Place it in a `config/` directory next to the JAR, or pass `--spring.config.location=file:/path/to/application.yml`.

### PDP Source Types

| Type | Description |
|------|-------------|
| `DIRECTORY` | Single directory with `pdp.json` and `.sapl` files. Hot-reloads on changes. |
| `MULTI_DIRECTORY` | Parent directory where each subdirectory is a tenant. Each subdirectory needs its own `pdp.json` and `.sapl` files. |
| `BUNDLES` | Watches a directory for `.saplbundle` files. Filename (minus extension) becomes the PDP ID. |

`RESOURCES` is not supported in SAPL Node (it loads from the classpath, which is for embedded use in Spring Boot applications).

```yaml
io.sapl.pdp.embedded:
  pdp-config-type: BUNDLES     # DIRECTORY, MULTI_DIRECTORY, or BUNDLES
  policies-path: bundles       # path to the policy source directory
```

### Authentication

SAPL Node supports four authentication modes. Enable one or more in `application.yml`:

```yaml
io.sapl.node:
  allowNoAuth: false       # allow unauthenticated requests
  allowBasicAuth: true     # HTTP Basic authentication
  allowApiKeyAuth: false   # Bearer token API keys
  allowOauth2Auth: false   # OAuth2/JWT tokens
  defaultPdpId: "default"  # PDP ID for unauthenticated requests
```

**Client credentials** are defined in the `users` list. Use the `generate` CLI commands to create Argon2-encoded secrets:

```yaml
io.sapl.node:
  users:
    - id: "my-client"
      pdpId: "default"
      basic:
        username: "my-client"
        secret: "$argon2id$v=19$m=16384,t=2,p=1$..."
      apiKey: "$argon2id$v=19$m=16384,t=2,p=1$..."
```

The `pdpId` field routes the client to a specific tenant's policies. For single-tenant deployments, use `"default"`.

**OAuth2/JWT** requires Spring Security's resource server configuration:

```yaml
io.sapl.node:
  allowOauth2Auth: true
  oauth:
    pdpIdClaim: "sapl_pdp_id"   # JWT claim for tenant routing

spring.security.oauth2:
  resourceserver:
    jwt:
      issuer-uri: https://auth.example.com/realm
```

### TLS

Use standard Spring Boot SSL properties:

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: file:/path/to/keystore.p12
    key-store-password: "changeit"
    key-store-type: PKCS12
```

### Bundle Security

Bundles can be signed with Ed25519 keys. Configure verification in `bundle-security`:

```yaml
io.sapl.pdp.embedded.bundle-security:
  # Option 1: Global public key (verifies all bundles)
  publicKeyPath: "/etc/sapl/public.key"
  # or inline: publicKey: "MCowBQYDK2VwAyEA..."

  # Option 2: Per-tenant key catalogue
  keys:
    prod-key: "MCowBQYDK2VwAyEA..."
    staging-key: "MCowBQYDK2VwAyEA..."
  tenants:
    production:
      - "prod-key"
    staging:
      - "staging-key"

  # Option 3: Allow unsigned bundles (development only)
  allowUnsigned: true
  acceptRisks: true
  unsignedTenants:
    - "development"
```

At least one option must be configured. For production, use signed bundles with key verification.

## Bundle Format

A `.saplbundle` file is a ZIP archive:

```
my-policies.saplbundle
+-- .sapl-manifest.json    (optional: signature + SHA-256 hashes)
+-- pdp.json               (PDP configuration)
+-- policy1.sapl           (policy files)
+-- policy2.sapl
+-- ...
```

Constraints: max 10 MB uncompressed, max 1000 entries, no subdirectories, no nested archives.

When signed, the manifest contains Ed25519 signatures over SHA-256 hashes of all files.

## PDP Configuration (pdp.json)

```json
{
  "algorithm": {
    "votingMode": "PRIORITY_PERMIT",
    "defaultDecision": "DENY",
    "errorHandling": "PROPAGATE"
  },
  "variables": {}
}
```

Voting modes: `PRIORITY_PERMIT`, `PRIORITY_DENY`, `ONLY_ONE_APPLICABLE`, `DENY_OVERRIDES`, `PERMIT_OVERRIDES`.

Default decision: `PERMIT` or `DENY`.

Error handling: `PROPAGATE` or `ABSTAIN`.

Variables defined here are available to all policies.
