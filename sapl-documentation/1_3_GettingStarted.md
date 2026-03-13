---
layout: default
title: Getting Started
parent: Introduction
nav_order: 3
---

## Getting Started

This guide presents two approaches to working with SAPL. Start with the playground to learn policy syntax, then run a local PDP server to experiment with the HTTP API.

### Learning Policy Syntax

The [SAPL Playground](https://playground.sapl.io/) runs entirely in your browser and requires no installation. Open the playground, write policies, create authorization subscriptions, and observe how the PDP evaluates them. The playground includes example policies demonstrating common authorization patterns.

The playground is primarily useful for learning the policy syntax and testing basic policy logic. The playground cannot connect to external attribute sources, and access to PIPs calling out to location tracking, HTTP servers, or MQTT brokers are present but calls are blocked. However, it is a useful tool to learn how a PDP works, how multiple policies and policy sets interact with each other, and how the streaming nature of SAPL works. It even allows you to graphically dig into traces of individual decisions for learning or debugging of your policies. You can also use it to share authorization scenarios with others.

### Running a Local PDP Server

SAPL Node is a lightweight PDP server. Native binaries are available for Linux, Windows, and macOS. No runtime dependencies are needed.

1. Download the archive for your platform from the [releases page](https://github.com/heutelbeck/sapl-policy-engine/releases) and extract it. Each archive contains the `sapl` binary, the `LICENSE`, and a `README.md`. On Linux, DEB and RPM packages are also available (see [Installing with DEB or RPM](../7_1_GettingStarted/#installing-with-deb-or-rpm)).

2. Create a working directory and write a policy. SAPL policy files use the `.sapl` extension. The PDP monitors the directory and loads all `.sapl` files automatically.

```bash
mkdir my-pdp
```

Create `my-pdp/allow-mrt.sapl`:

```sapl
policy "Dr. House is allowed to use the MRT!"
permit subject == "housemd" & action == "use" & resource == "MRT";
```

3. Copy or move the `sapl` binary into the working directory and start the server:

**Linux/macOS:**
```bash
cd my-pdp && ./sapl
```

**Windows (PowerShell):**
```powershell
cd my-pdp; .\sapl.exe
```

The server starts on `localhost:8443`. By default, it binds to `127.0.0.1` only, does not accept external connections, and runs without TLS and authentication. No configuration files are needed. The PDP loads all `.sapl` files from the current directory and watches for changes.

> **Note:** Since the server only listens on localhost, the lack of TLS and authentication is safe for local development. For network-accessible deployments, see [SAPL Node](../7_0_SaplNode/) to configure TLS, authentication, and signed policy bundles.

4. In a separate terminal, send an authorization request:

```bash
curl -s http://localhost:8443/api/pdp/decide-once -H 'Content-Type: application/json' -d '{"subject":"housemd","action":"use","resource":"MRT"}'
```

The server returns `{"decision":"PERMIT"}`. The policy matches the subscription: subject `housemd`, action `use`, resource `MRT`.

Try a request that does not match:

```bash
curl -s http://localhost:8443/api/pdp/decide-once -H 'Content-Type: application/json' -d '{"subject":"cuddy","action":"use","resource":"MRT"}'
```

This returns `{"decision":"DENY"}`. No policy matches subject `cuddy`, so the default combining algorithm applies its default decision: `DENY`.

5. Now try the streaming endpoint. This is where SAPL differs from traditional authorization systems. The PDP holds the connection open and pushes a new decision whenever the evaluation result changes:

```bash
curl -N http://localhost:8443/api/pdp/decide -H 'Content-Type: application/json' -d '{"subject":"housemd","action":"use","resource":"MRT"}'
```

The server returns `{"decision":"PERMIT"}` and keeps the connection open. Now edit `allow-mrt.sapl` while the curl is running: change `"housemd"` to `"cuddy"` and save. The PDP detects the change, recompiles the policy, and immediately pushes `{"decision":"DENY"}` on the same connection. Change it back and `PERMIT` returns. No restart, no polling.

Press `Ctrl+C` to stop the stream.

6. To see streaming driven by external data, create a new policy `time-demo.sapl` that uses the built-in time PIP:

```sapl
policy "time demo"
permit
  time.secondOf(<time.now>) % 10 < 5;
```

The `<time.now>` attribute is a stream that emits the current UTC timestamp once per second. The `time.secondOf` function extracts the seconds component. The modulo expression makes the policy applicable only when the current second is 0-4 within each 10-second window.

Start a streaming subscription:

```bash
curl -N http://localhost:8443/api/pdp/decide -H 'Content-Type: application/json' -d '{"subject":"anyone","action":"read","resource":"clock"}'
```

Watch the decision flip between `PERMIT` and `DENY` every five seconds. The application does not poll. The PDP pushes changes as they happen.

**Next Steps**

You now have a working PDP. Some things to try:

* Experiment with different authorization subscriptions and policies.
* Add a `pdp.json` to configure the combining algorithm (see [PDP Configuration](../2_2_PDPConfiguration/)).
* Create multiple policies and observe how they interact.
* Check the health endpoint at `http://localhost:8443/actuator/health`.
* Read about the [SAPL language](../3_0_SAPLReference/) to model your own authorization rules.

The HTTP API works from any programming language that can make HTTP requests. See the [HTTP API](../6_1_HTTPApi/) documentation for the complete specification including multi-subscriptions and streaming.

For integrating SAPL directly into Java applications using an embedded PDP, see [Java API](../6_2_JavaApi/). For production deployment with TLS, authentication, and signed bundles, see [SAPL Node](../7_0_SaplNode/).
