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
tar xzf sapl-*-linux-amd64.tar.gz
```

On Windows, extract the `.zip` file using Explorer or any archive tool.

Each archive contains the `sapl` binary (ready to run, no runtime dependencies), the `LICENSE`, and a `README.md`. Place the binary somewhere on your `PATH` or in the directory where you plan to run the server.

Verify the installation:

```shell
./sapl --version
```

### Quick Start

This walkthrough sets up a working node from scratch, deploys a policy, and queries the PDP with curl. No configuration files are needed. The built-in defaults work out of the box: no TLS, no authentication, policies loaded from the current directory.

Create a working directory and a policy file:

```shell
mkdir my-node
```

Create `my-node/tick.sapl`. This policy uses the built in time PIP to grant access only when the current second is divisible by 5:

```
policy "tick"
permit
  time.secondOf(<time.now>) % 5 == 0
```

The `<time.now>` attribute is a stream. It emits the current UTC timestamp once per second. Every time a new value arrives, the PDP re evaluates the policy and pushes an updated decision to all connected clients.

Start the server:

```shell
cd my-node && ./sapl
```

The node starts on `localhost:8443` with no TLS and no authentication required. No `pdp.json` is needed. When absent, the PDP uses the default combining algorithm (`PRIORITY_DENY` with `DENY` default and `PROPAGATE` error handling).

In a separate terminal, request a one shot decision:

```shell
curl -s http://localhost:8443/api/pdp/decide-once -H 'Content-Type: application/json' -d '{"subject":"anyone","action":"read","resource":"clock"}'
```

The response is a single JSON object. Depending on the current second, the decision is either `PERMIT` or `NOT_APPLICABLE`.

Now try the streaming endpoint. This is where SAPL shows its strength. The PDP holds the connection open and pushes a new decision every time the policy evaluation result changes:

```shell
curl -N http://localhost:8443/api/pdp/decide -H 'Content-Type: application/json' -d '{"subject":"anyone","action":"read","resource":"clock"}'
```

Watch the output. Every few seconds, the decision flips between `PERMIT` and `NOT_APPLICABLE` as the current time crosses a multiple of five. The application does not need to poll. The PDP pushes changes as they happen.

Press `Ctrl+C` to stop the stream. The PDP cleans up the subscription automatically.

Try editing `tick.sapl` while the streaming curl is running. Change `% 5` to `% 10` and save. The PDP detects the change, recompiles the policy, and pushes an updated decision on the same connection. No restart needed.

While the server is running, you can also check its operational state. The health endpoint shows whether policies loaded successfully:

```shell
curl -s http://localhost:8443/actuator/health | jq .
```

You should see `"status": "UP"` with a `pdps` detail block showing the state `LOADED`, the active combining algorithm, and the number of loaded documents. If a policy has a syntax error, the state changes to `ERROR` and the health status drops to `DOWN`.

The info endpoint shows PDP configuration (this endpoint requires authentication in production, but works unauthenticated in this setup since `allow-no-auth` is enabled by default):

```shell
curl -s http://localhost:8443/actuator/info | jq .
```

For Prometheus metrics, Kubernetes probes, and decision logging, see [Monitoring](../7_7_Monitoring/).

### Directory Layout

The minimal working directory is simply the binary and your policy files:

```
my-node/
  sapl
  tick.sapl
```

For more complex setups, add a `pdp.json` to configure the combining algorithm and a `config/application.yml` to override defaults:

```
my-node/
  sapl
  pdp.json          (optional)
  tick.sapl
  config/
    application.yml  (optional, overrides built-in defaults)
```

Spring Boot automatically loads `config/application.yml` on startup. The `config-path` and `policies-path` properties default to `.` (the working directory). For bundle based deployments, the working directory holds `.saplbundle` files instead of raw `.sapl` files. See [Policy Sources](../7_3_PolicySources/) for the different source types and [Configuration](../7_2_Configuration/) for the full property reference.

### Installing with DEB or RPM

Download the package for your distribution from the [releases page](https://github.com/heutelbeck/sapl-policy-engine/releases).

```shell
sudo dpkg -i sapl_4.0.0_amd64.deb
```

Or for RPM-based distributions:

```shell
sudo rpm -i sapl-4.0.0.x86_64.rpm
```

The package installs the binary to `/usr/bin/sapl`, the configuration to `/etc/sapl/application.yml`, a systemd service, and example policies in `/var/lib/sapl/example/`.

The service is configured in `BUNDLES` mode with signature verification enabled. The node will not start until bundle security is configured.

To deploy your first bundle using the included example policies:

```shell
sudo sapl bundle keygen -o /etc/sapl/signing
sudo sapl bundle create -i /var/lib/sapl/example -o /var/lib/sapl/default.saplbundle -k /etc/sapl/signing.pem
```

Configure the public key in `/etc/sapl/application.yml`:

```yaml
io.sapl.pdp.embedded:
  bundle-security:
    public-key-path: /etc/sapl/signing.pub
```

Start or restart the service:

```shell
sudo systemctl enable --now sapl
```

Verify:

```shell
curl -s http://localhost:8443/actuator/health | jq .
```

You should see `"status": "UP"`. The PDP watches `/var/lib/sapl/` for bundle changes and reloads automatically.

Replace the example policies with your own by creating `.sapl` files in a directory and rebuilding the bundle. See `/var/lib/sapl/README` for the full workflow.

### Running with Docker

For container deployments, the server runs inside Docker while you use the local `sapl` binary for CLI operations like bundle creation and credential generation.

The container image is `ghcr.io/heutelbeck/sapl-node`. The Docker image defaults to `BUNDLES` mode with signature verification enabled. The node will not start until bundle security is configured.

To get started with signed bundles:

```shell
mkdir policies
echo '{"configurationId":"v1","algorithm":{"votingMode":"PRIORITY_DENY","defaultDecision":"DENY","errorHandling":"PROPAGATE"}}' > policies/pdp.json
echo 'policy "allow-all" permit' > policies/allow-all.sapl
sapl bundle keygen -o signing
sapl bundle create -i ./policies -o ./bundles/default.saplbundle -k signing.pem
```

Run the container, mounting the bundles directory and the public key:

```shell
docker run -p 8443:8443 -v ./bundles:/pdp/data:ro -v ./signing.pub:/pdp/signing.pub:ro -e SERVER_ADDRESS=0.0.0.0 -e IO_SAPL_PDP_EMBEDDED_BUNDLESECURITY_PUBLICKEYPATH=/pdp/signing.pub ghcr.io/heutelbeck/sapl-node:4.0.0-SNAPSHOT
```

For development or evaluation without signing, disable signature verification:

```shell
docker run -p 8443:8443 -v ./bundles:/pdp/data:ro -e SERVER_ADDRESS=0.0.0.0 -e IO_SAPL_PDP_EMBEDDED_BUNDLESECURITY_ALLOWUNSIGNED=true ghcr.io/heutelbeck/sapl-node:4.0.0-SNAPSHOT
```

To use raw `.sapl` files instead of bundles (for learning or demos), override the policy source type:

```shell
docker run -p 8443:8443 -v ./policies:/pdp/data:ro -e SERVER_ADDRESS=0.0.0.0 -e IO_SAPL_PDP_EMBEDDED_PDPCONFIGTYPE=DIRECTORY ghcr.io/heutelbeck/sapl-node:4.0.0-SNAPSHOT
```

The `SERVER_ADDRESS=0.0.0.0` override is required so Docker's port mapping can reach the server. The default `127.0.0.1` only accepts connections from within the container.

Environment variables follow Spring Boot's naming convention: dots become underscores, camelCase becomes uppercase. For example, `io.sapl.node.allow-basic-auth` becomes `IO_SAPL_NODE_ALLOWBASICAUTH`. See [Configuration](../7_2_Configuration/) for all available properties.

You can also mount a full `application.yml` instead of using individual environment variables:

```shell
docker run -p 8443:8443 -v ./config:/pdp/config:ro -v ./bundles:/pdp/data:ro -e SERVER_ADDRESS=0.0.0.0 ghcr.io/heutelbeck/sapl-node:4.0.0-SNAPSHOT
```

### CLI Reference

The `sapl` binary doubles as a CLI tool. These commands run locally without starting the server. Use them to manage bundles and generate credentials for your `application.yml`.

#### bundle create

Creates a `.saplbundle` archive from a directory of `.sapl` files and a `pdp.json`. The `pdp.json` must contain a `configurationId` field. If a signing key is provided, the bundle is signed in the same step.

```
sapl bundle create -i <dir> -o <file> [-k <key>]
```

| Option           | Description                                                   |
|------------------|---------------------------------------------------------------|
| `-i`, `--input`  | Input directory containing policies.                          |
| `-o`, `--output` | Output `.saplbundle` file.                                    |
| `-k`, `--key`    | Ed25519 private key file (PEM format) for signing. Optional.  |
| `--key-id`       | Key identifier for rotation support. Defaults to `"default"`. |

#### bundle sign

Signs a bundle with an Ed25519 private key. Overwrites the input bundle unless `-o` is specified.

```
sapl bundle sign -b <file> -k <key> [options]
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
sapl bundle verify -b <file> -k <key> [options]
```

| Option | Description |
|--------|-------------|
| `-b`, `--bundle` | Bundle file to verify. |
| `-k`, `--key` | Ed25519 public key file (PEM, X.509). |
| `--check-expiration` | Fails if the signature has expired. |

#### bundle inspect

Displays bundle contents, signature status, configuration, and policy list.

```
sapl bundle inspect -b <file>
```

| Option | Description |
|--------|-------------|
| `-b`, `--bundle` | Bundle file to inspect. |

#### bundle keygen

Generates an Ed25519 keypair for bundle signing.

```
sapl bundle keygen -o <prefix> [options]
```

| Option | Description |
|--------|-------------|
| `-o`, `--output` | Output prefix. Creates `<prefix>.pem` (private key) and `<prefix>.pub` (public key). |
| `--force` | Overwrites existing files. |

#### generate basic

Generates Basic Auth credentials. Prints the plaintext password and a ready to paste YAML block for `application.yml`.

```
sapl generate basic --id <id> --pdp-id <pdpId>
```

| Option | Description |
|--------|-------------|
| `-i`, `--id` | Client identifier. |
| `-p`, `--pdp-id` | PDP identifier for multi tenant routing. |

#### generate apikey

Generates an API key. Prints the plaintext key and a ready to paste YAML block for `application.yml`.

```
sapl generate apikey --id <id> --pdp-id <pdpId>
```

| Option | Description |
|--------|-------------|
| `-i`, `--id` | Client identifier. |
| `-p`, `--pdp-id` | PDP identifier for multi tenant routing. |

For authentication and TLS setup, see [Security](../7_6_Security/). For health checks and metrics, see [Monitoring](../7_7_Monitoring/).
