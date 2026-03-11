---
layout: default
title: Getting Started
parent: SAPL Node
nav_order: 701
---

## Getting Started

SAPL Node is both a PDP server and a CLI tool in a single binary. Applications query it for authorization decisions via HTTP. You use the same binary on your workstation to create, sign, and inspect policy bundles and to generate client credentials. Whether you run the server directly or in Docker, you will need the binary locally for these operations.

### Getting the Binary

Download the archive for your platform from the [releases page](https://github.com/heutelbeck/sapl-policy-engine/releases).

On Linux or macOS, extract the archive:

```shell
tar xzf sapl-node-*-linux-amd64.tar.gz
```

On Windows, extract the `.zip` file using Explorer or any archive tool.

Each archive contains the `sapl-node` binary (ready to run, no runtime dependencies), the `LICENSE`, and a `README.md`. Place the binary somewhere on your `PATH` or in the directory where you plan to run the server.

Verify the installation:

```shell
./sapl-node --version
```

### Quick Start

This walkthrough sets up a working node from scratch, deploys a policy, and queries the PDP with curl.

Create a working directory with the required structure:

```shell
mkdir -p my-node/config my-node/policies
```

Create `my-node/config/application.yml`. This disables TLS and authentication for a quick local test:

```yaml
io.sapl:
  pdp.embedded:
    config-path: policies
    policies-path: policies
  node:
    allowNoAuth: true

server:
  port: 8080
  ssl:
    enabled: false
```

Create `my-node/policies/pdp.json`. This tells the PDP which combining algorithm to use when multiple policies apply:

```json
{
  "algorithm": {
    "votingMode": "PRIORITY_PERMIT",
    "defaultDecision": "DENY",
    "errorHandling": "ABSTAIN"
  },
  "variables": {}
}
```

Create `my-node/policies/tick.sapl`. This policy uses the built in time PIP to grant access only when the current second is divisible by 5:

```
policy "tick"
permit
  time.secondOf(<time.now>) % 5 == 0
```

The `<time.now>` attribute is a stream. It emits the current UTC timestamp once per second. Every time a new value arrives, the PDP re evaluates the policy and pushes an updated decision to all connected clients.

Start the server:

```shell
cd my-node && ./sapl-node
```

In a separate terminal, request a one shot decision:

```shell
curl -s http://localhost:8080/api/pdp/decide-once -H 'Content-Type: application/json' -d '{"subject":"anyone","action":"read","resource":"clock"}'
```

The response is a single JSON object. Depending on the current second, the decision is either `PERMIT` or `NOT_APPLICABLE`.

Now try the streaming endpoint. This is where SAPL shows its strength. The PDP holds the connection open and pushes a new decision every time the policy evaluation result changes:

```shell
curl -N http://localhost:8080/api/pdp/decide -H 'Content-Type: application/json' -d '{"subject":"anyone","action":"read","resource":"clock"}'
```

Watch the output. Every few seconds, the decision flips between `PERMIT` and `NOT_APPLICABLE` as the current time crosses a multiple of five. The application does not need to poll. The PDP pushes changes as they happen.

Press `Ctrl+C` to stop the stream. The PDP cleans up the subscription automatically.

Try editing `tick.sapl` while the streaming curl is running. Change `% 5` to `% 10` and save. The PDP detects the change, recompiles the policy, and pushes an updated decision on the same connection. No restart needed.

While the server is running, you can also check its operational state. The health endpoint shows whether policies loaded successfully:

```shell
curl -s http://localhost:8080/actuator/health | python3 -m json.tool
```

You should see `"status": "UP"` with a `pdps` detail block showing the state `LOADED`, the active combining algorithm, and the number of loaded documents. If a policy has a syntax error, the state changes to `ERROR` and the health status drops to `DOWN`.

The info endpoint shows PDP configuration (this endpoint requires authentication in production, but works unauthenticated in this setup since `allowNoAuth` is enabled):

```shell
curl -s http://localhost:8080/actuator/info | python3 -m json.tool
```

For Prometheus metrics, Kubernetes probes, and decision logging, see [Monitoring](../7_7_Monitoring/).

### Directory Layout

The working directory you just created follows the standard layout:

```
my-node/
  sapl-node
  config/
    application.yml
  policies/
    pdp.json
    tick.sapl
```

The server looks for `config/application.yml` on startup. The `config-path` and `policies-path` properties point to the policy directory. For bundle based deployments, the policies directory holds `.saplbundle` files instead of raw `.sapl` files. See [Policy Sources](../7_3_PolicySources/) for the different source types and [Configuration](../7_2_Configuration/) for the full property reference.

### Running with Docker

For container deployments, the server runs inside Docker while you use the local `sapl-node` binary for CLI operations like bundle creation and credential generation.

The container image is `ghcr.io/heutelbeck/sapl-node`. Mount your policies directory into the container and configure the server via environment variables:

```shell
docker run -p 8080:8080 -v ./policies:/pdp/data:ro -e SERVER_SSL_ENABLED=false -e SERVER_PORT=8080 -e SERVER_ADDRESS=0.0.0.0 -e IO_SAPL_NODE_ALLOWNOAUTH=true -e IO_SAPL_PDP_EMBEDDED_POLICIESPATH=/pdp/data ghcr.io/heutelbeck/sapl-node:4.0.0-SNAPSHOT
```

The `-v ./policies:/pdp/data:ro` flag maps your local `policies/` directory into the container at `/pdp/data`. The `:ro` suffix makes the mount read only, which is good practice since the server only reads policies.

Environment variables follow Spring Boot's naming convention: dots become underscores, camelCase becomes uppercase. For example, `io.sapl.node.allowBasicAuth` becomes `IO_SAPL_NODE_ALLOWBASICAUTH`. See [Configuration](../7_2_Configuration/) for all available properties.

You can also mount a full `application.yml` instead of using individual environment variables:

```shell
docker run -p 8443:8443 -v ./config:/pdp/config:ro -v ./policies:/pdp/data:ro ghcr.io/heutelbeck/sapl-node:4.0.0-SNAPSHOT
```

### CLI Reference

The `sapl-node` binary doubles as a CLI tool. These commands run locally without starting the server. Use them to manage bundles and generate credentials for your `application.yml`.

#### bundle create

Creates a `.saplbundle` archive from a directory of `.sapl` files and a `pdp.json`.

```
sapl-node bundle create -i <dir> -o <file>
```

| Option | Description |
|--------|-------------|
| `-i`, `--input` | Input directory containing policies. |
| `-o`, `--output` | Output `.saplbundle` file. |

#### bundle sign

Signs a bundle with an Ed25519 private key. Overwrites the input bundle unless `-o` is specified.

```
sapl-node bundle sign -b <file> -k <key> [options]
```

| Option | Description |
|--------|-------------|
| `-b`, `--bundle` | Bundle file to sign. |
| `-k`, `--key` | Ed25519 private key file (PEM, PKCS8). |
| `-o`, `--output` | Output file. Defaults to overwriting the input. |
| `--key-id` | Key identifier stored in the manifest. Defaults to `"default"`. |

#### bundle verify

Verifies a signed bundle against an Ed25519 public key. Returns exit code 0 on success and 1 on failure.

```
sapl-node bundle verify -b <file> -k <key> [options]
```

| Option | Description |
|--------|-------------|
| `-b`, `--bundle` | Bundle file to verify. |
| `-k`, `--key` | Ed25519 public key file (PEM, X.509). |
| `--check-expiration` | Fails if the signature has expired. |

#### bundle inspect

Displays bundle contents, signature status, configuration, and policy list.

```
sapl-node bundle inspect -b <file>
```

| Option | Description |
|--------|-------------|
| `-b`, `--bundle` | Bundle file to inspect. |

#### bundle keygen

Generates an Ed25519 keypair for bundle signing.

```
sapl-node bundle keygen -o <prefix> [options]
```

| Option | Description |
|--------|-------------|
| `-o`, `--output` | Output prefix. Creates `<prefix>.pem` (private key) and `<prefix>.pub` (public key). |
| `--force` | Overwrites existing files. |

#### generate basic

Generates Basic Auth credentials. Prints the plaintext password and a ready to paste YAML block for `application.yml`.

```
sapl-node generate basic --id <id> --pdp-id <pdpId>
```

| Option | Description |
|--------|-------------|
| `-i`, `--id` | Client identifier. |
| `-p`, `--pdp-id` | PDP identifier for multi tenant routing. |

#### generate apikey

Generates an API key. Prints the plaintext key and a ready to paste YAML block for `application.yml`.

```
sapl-node generate apikey --id <id> --pdp-id <pdpId>
```

| Option | Description |
|--------|-------------|
| `-i`, `--id` | Client identifier. |
| `-p`, `--pdp-id` | PDP identifier for multi tenant routing. |

For authentication and TLS setup, see [Security](../7_6_Security/). For health checks and metrics, see [Monitoring](../7_7_Monitoring/).
