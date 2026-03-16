---
layout: default
title: Getting Started
parent: Introduction
nav_order: 3
---

## Getting Started

SAPL is a reactive authorization engine: it evaluates access control policies and pushes updated decisions whenever policies, attributes, or subscriptions change. This guide introduces the policy syntax through the browser-based playground, then walks through hands-on policy evaluation using the CLI.

### Learning Policy Syntax

The [SAPL Playground](https://playground.sapl.io/) runs entirely in your browser and requires no installation. Open the playground, write policies, create authorization subscriptions, and observe how the PDP evaluates them. The playground includes example policies demonstrating common authorization patterns.

The playground is primarily useful for learning the policy syntax and testing basic policy logic. The playground cannot connect to external attribute sources, and access to PIPs calling out to location tracking, HTTP servers, or MQTT brokers are present but calls are blocked. However, it is a useful tool to learn how a PDP works, how multiple policies and policy sets interact with each other, and how the streaming nature of SAPL works. It even allows you to graphically dig into traces of individual decisions for learning or debugging of your policies. You can also use it to share authorization scenarios with others.

### Evaluating Policies

The `sapl` CLI lets you evaluate policies locally without starting a server. Download the binary for your platform from the [releases page](https://github.com/heutelbeck/sapl-policy-engine/releases) and extract it. Each archive contains the `sapl` binary, the `LICENSE`, and a `README.md`. On Linux, DEB and RPM packages are also available (see [SAPL Node Getting Started](../7_1_GettingStarted/#installing-with-deb-or-rpm)).

Verify the installation:

```bash
sapl --version
```

#### Write a Policy

By default, the CLI loads policies from `~/.sapl/`. Create the directory and a policy file:

<details open>
<summary>Bash</summary>

```bash
mkdir -p ~/.sapl
```

</details>
<details>
<summary>PowerShell</summary>

```powershell
mkdir ~\.sapl
```

</details>

Create `~/.sapl/allow-mrt.sapl`:

```sapl
policy "Dr. House is allowed to use the MRT!"
permit subject == "housemd" & action == "use" & resource == "MRT";
```

#### Evaluate with decide-once

The `decide-once` command evaluates the subscription against all loaded policies, prints the result, and exits. A subscription has three required components: `--subject`, `--action`, and `--resource`, each a JSON value:

<details open>
<summary>Bash</summary>

```bash
sapl decide-once --subject '"housemd"' --action '"use"' --resource '"MRT"'
```

</details>
<details>
<summary>PowerShell</summary>

```powershell
sapl decide-once --subject '\"housemd\"' --action '\"use\"' --resource '\"MRT\"'
```

</details>

Alternatively, pass the subscription as a JSON file with `--file` (`-f`), or pipe it from stdin with `-f -`:

```bash
echo '{"subject":"housemd","action":"use","resource":"MRT"}' | sapl decide-once -f -
```

This prints `{"decision":"PERMIT"}`. The policy matches: subject `housemd`, action `use`, resource `MRT`.

> **Quoting:** Because subscription components are JSON values, strings must include double quotes. In Bash, wrap them in single quotes: `--subject '"housemd"'`. In PowerShell, use backslash-escaped quotes: `--subject '\"housemd\"'`. The remaining examples use the short flags `-s`, `-a`, `-r`.

Try a request that does not match:

<details open>
<summary>Bash</summary>

```bash
sapl decide-once -s '"cuddy"' -a '"use"' -r '"MRT"'
```

</details>
<details>
<summary>PowerShell</summary>

```powershell
sapl decide-once -s '\"cuddy\"' -a '\"use\"' -r '\"MRT\"'
```

</details>

This returns `{"decision":"DENY"}`. No policy matches subject `cuddy`, so the PDP denies the request. When multiple policies exist, a *combining algorithm* determines how their individual results are merged into a final decision. The default algorithm denies access unless a policy explicitly permits it (see [PDP Configuration](../2_2_PDPConfiguration/)).

#### Streaming with decide

The `decide` command holds the subscription open and prints a new decision whenever the result changes:

<details open>
<summary>Bash</summary>

```bash
sapl decide -s '"housemd"' -a '"use"' -r '"MRT"'
```

</details>
<details>
<summary>PowerShell</summary>

```powershell
sapl decide -s '\"housemd\"' -a '\"use\"' -r '\"MRT\"'
```

</details>

The CLI prints `{"decision":"PERMIT"}` and keeps running. Now edit `~/.sapl/allow-mrt.sapl` while the command is running: change `"housemd"` to `"cuddy"` and save. The PDP detects the change, recompiles the policy, and immediately prints `{"decision":"DENY"}`. Change it back and `PERMIT` returns. No restart, no polling.

Press `Ctrl+C` to stop the stream.

#### Time-based Streaming

The PDP loads all `.sapl` files from the policy directory. Create a second policy file `~/.sapl/cuddy-time-limited.sapl` that gives Cuddy time-limited access:

```sapl
policy "Dr. Cuddy has time-limited MRT access"
permit subject == "cuddy" & action == "use" & resource == "MRT"
  time.secondOf(<time.now>) % 10 < 5;
```

`<time.now>` is an *attribute stream* -- it emits the current UTC timestamp once per second. The `time.secondOf` function extracts the seconds component. The modulo expression makes the policy applicable only when the current second is 0-4 within each 10-second window.

Start a streaming subscription for Cuddy:

<details open>
<summary>Bash</summary>

```bash
sapl decide -s '"cuddy"' -a '"use"' -r '"MRT"'
```

</details>
<details>
<summary>PowerShell</summary>

```powershell
sapl decide -s '\"cuddy\"' -a '\"use\"' -r '\"MRT\"'
```

</details>

Watch the decision flip between `PERMIT` and `DENY` every five seconds. The PDP re-evaluates the policy each time `<time.now>` emits a new timestamp and pushes the updated decision. The application does not poll.

### Next Steps

You now have a working local setup. Some things to explore:

* Run policy unit tests with `sapl test` (see [Testing](../5_0_SAPLTest/)).
* Validate policies in CI with `sapl check` (see [SAPL Node](../7_0_SaplNode/)).
* Add a `pdp.json` to configure the combining algorithm (see [PDP Configuration](../2_2_PDPConfiguration/)).
* Learn the policy language in depth (see [SAPL Reference](../3_0_SAPLReference/)).
* Run the PDP as a server for HTTP-based authorization (see [SAPL Node Getting Started](../7_1_GettingStarted/)).
