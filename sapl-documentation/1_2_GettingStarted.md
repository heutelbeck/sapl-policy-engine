---
layout: default
title: Getting Started
nav_order: 2
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

<details open markdown="1">
<summary>Bash</summary>

```bash
mkdir -p ~/.sapl
```

</details>
<details markdown="1">
<summary>PowerShell</summary>

```powershell
mkdir ~\.sapl
```

</details>

Create `~/.sapl/allow-mrt.sapl`:

```sapl-demo
policy "Dr. House is allowed to use the MRT!"
permit
  subject == "housemd" & action == "use" & resource == "MRT";
```
{: data-subject="housemd" data-action="use" data-resource="MRT" }

{: .note }
> If you are reading this on the documentation site, the embedded playground above shows the result of evaluating this single policy in isolation. It displays the outcome of the one matching policy only. A full PDP additionally applies a *combining algorithm* on top that merges the results of all loaded policies into a final decision. For example, when no policy matches, the embedded playground shows `NOT_APPLICABLE`, while a PDP configured with a deny-by-default algorithm returns `DENY`. The CLI examples below use a full PDP with such a configuration.

#### Evaluate with decide-once

The `decide-once` command evaluates the subscription against all loaded policies, prints the result, and exits. A subscription has three required components: `--subject`, `--action`, and `--resource`, each a JSON value:

<details open markdown="1">
<summary>Bash</summary>

```bash
sapl decide-once --subject '"housemd"' --action '"use"' --resource '"MRT"'
```

</details>
<details markdown="1">
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

<details open markdown="1">
<summary>Bash</summary>

```bash
sapl decide-once -s '"cuddy"' -a '"use"' -r '"MRT"'
```

</details>
<details markdown="1">
<summary>PowerShell</summary>

```powershell
sapl decide-once -s '\"cuddy\"' -a '\"use\"' -r '\"MRT\"'
```

</details>

This returns `{"decision":"DENY"}`. No policy matches subject `cuddy`, so the PDP denies the request. When multiple policies exist, a *combining algorithm* determines how their individual results are merged into a final decision. The default algorithm denies access unless a policy explicitly permits it (see [PDP Configuration](../2_2_PDPConfiguration/)).

#### Streaming with decide

The `decide` command holds the subscription open and prints a new decision whenever the result changes:

<details open markdown="1">
<summary>Bash</summary>

```bash
sapl decide -s '"housemd"' -a '"use"' -r '"MRT"'
```

</details>
<details markdown="1">
<summary>PowerShell</summary>

```powershell
sapl decide -s '\"housemd\"' -a '\"use\"' -r '\"MRT\"'
```

</details>

The CLI prints `{"decision":"PERMIT"}` and keeps running. Now edit `~/.sapl/allow-mrt.sapl` while the command is running: change `"housemd"` to `"cuddy"` and save. The PDP detects the change, recompiles the policy, and immediately prints `{"decision":"DENY"}`. Change it back and `PERMIT` returns. No restart, no polling.

Press `Ctrl+C` to stop the stream.

#### Time-based Streaming

The PDP loads all `.sapl` files from the policy directory. Create a second policy file `~/.sapl/cuddy-time-limited.sapl` that gives Cuddy time-limited access:

```sapl-demo
policy "Dr. Cuddy has time-limited MRT access"
permit
  subject == "cuddy" & action == "use" & resource == "MRT";
  time.secondOf(<time.now>) % 10 < 5;
```
{: data-subject="cuddy" data-action="use" data-resource="MRT" }

`<time.now>` is an *attribute stream*. It emits the current UTC timestamp once per second. The `time.secondOf` function extracts the seconds component. The modulo expression makes the policy applicable only when the current second is 0-4 within each 10-second window.

Start a streaming subscription for Cuddy:

<details open markdown="1">
<summary>Bash</summary>

```bash
sapl decide -s '"cuddy"' -a '"use"' -r '"MRT"'
```

</details>
<details markdown="1">
<summary>PowerShell</summary>

```powershell
sapl decide -s '\"cuddy\"' -a '\"use\"' -r '\"MRT\"'
```

</details>

Watch the decision flip between `PERMIT` and `DENY` every five seconds. The PDP re-evaluates the policy each time `<time.now>` emits a new timestamp and pushes the updated decision. The application does not poll.

#### Using Policies in Scripts

The `check` command evaluates a subscription and exits with a code that encodes the decision: `0` for PERMIT, `2` for DENY. No output is written to stdout, making it ideal for shell scripts. Here is a script that checks authorization before starting the MRT:

<details open markdown="1">
<summary>Bash</summary>

```bash
if sapl check -s '"housemd"' -a '"use"' -r '"MRT"'; then
    echo "Access granted. Starting MRT..."
    # start-mrt
else
    echo "Access denied."
fi
```

</details>
<details markdown="1">
<summary>PowerShell</summary>

```powershell
sapl check -s '\"housemd\"' -a '\"use\"' -r '\"MRT\"'
if ($LASTEXITCODE -eq 0) {
    Write-Host "Access granted. Starting MRT..."
    # Start-MRT
} else {
    Write-Host "Access denied."
}
```

</details>

Try it with `'"cuddy"'`. The script denies access. Change Cuddy's policy and run again. No code change needed.

### Next Steps

You now have policies that grant and deny access, react to live changes, and can be used from shell scripts. From here you can go in several directions:

**Learn the policy language.** The examples above use simple equality checks. SAPL supports pattern matching, arithmetic, functions, and attribute streams for expressing complex authorization logic. See [The SAPL Policy Language](../2_0_TheSAPLPolicyLanguage/).

**Test and validate policies.** Write unit tests for your policies with `sapl test` (see [Testing SAPL Policies](../5_0_TestingSAPLPolicies/)). Use `sapl check` in CI pipelines to verify that policy changes do not break expected decisions.

**Run the PDP as a server.** So far you used the CLI to evaluate policies locally. The same `sapl` binary can run as an HTTP server that applications query for authorization decisions over the network. The CLI commands `decide`, `decide-once`, and `check` also work as clients against a remote PDP server using `--remote`. See [SAPL Node](../7_1_GettingStarted/).

**Integrate into your application.** SAPL provides SDKs for Java ([Java API](../6_2_JavaApi/)), Spring ([Spring Security](../6_4_SpringIntegration/)), NestJS ([NestJS](../6_5_NestJS/)), Python ([Django](../6_6_PythonDjango/), [Flask](../6_7_PythonFlask/), [FastAPI](../6_8_PythonFastAPI/)), and [.NET](../6_11_DotNet/). See [Integration](../6_0_Integration/) for the full list.

**Build your own integration.** If no SDK exists for your language or framework, you can implement a client against the [HTTP API](../6_1_HTTPApi/) directly. For authors building reusable PEP libraries, the [PEP Implementation Specification](../8_1_PEPImplementationSpecification/) defines the requirements.

**Explore demo applications.** Complete working examples show how SAPL integrates with real frameworks. Each demo includes policies, Docker infrastructure, and runnable endpoints:

* [Java/Spring demos](https://github.com/heutelbeck/sapl-demos). Embedded and remote PDP usage, Spring MVC and WebFlux method security, database query rewriting for row-level security with JPA, R2DBC, and MongoDB, OAuth2/JWT integration, and MQTT as a Policy Information Point.
* [NestJS demo](https://github.com/heutelbeck/sapl-nestjs-demo). All seven constraint handler types, content filtering, resource replacement, streaming SSE with continuous authorization, service-level enforcement, and JWT-based ABAC with Keycloak.
* [Node.js demos](https://github.com/heutelbeck/sapl-nodejs-demos). An Express demo covering basic enforcement and constraint handling, and a NestJS demo with external data source integration.
* [Python demos](https://github.com/heutelbeck/sapl-python-demos). Four demos covering FastAPI, Django, Flask, and FastMCP (Model Context Protocol). The FastAPI and Django demos include JWT authentication, SSE streaming, and all constraint handler types. The Flask demo covers basic pre/post enforcement.
* [.NET demo](https://github.com/heutelbeck/sapl-dotnet-demos). ASP.NET Core with attribute-driven enforcement, all constraint handler types, service-layer enforcement, JWT export endpoints, and SSE streaming with till-denied, drop-while-denied, and recoverable patterns.

**AI and LLM authorization.** SAPL can enforce access control on AI tool calls, RAG pipelines, and MCP servers. These demos show how policies control what an LLM is allowed to see and do:

* [RAG clinical trial](https://github.com/heutelbeck/sapl-demos/tree/main/rag-clinical-trial) (Java/Spring AI). Document-level access control in a RAG pipeline. SAPL obligations modify pgvector search filters before retrieval, ensuring the LLM never sees unauthorized documents. Access is controlled by role, site assignment, and declared purpose, including GDPR purpose limitation for personal data.
* [MCP tool-calling](https://github.com/heutelbeck/sapl-demos/tree/main/mcp-clinical-trial) (Java/Spring AI). Policy-driven access control on Spring AI `@Tool` methods. SAPL policies decide per tool and per user which clinical trial data the LLM can access.
* [Human-in-the-loop](https://github.com/heutelbeck/sapl-demos/tree/main/hitl-clinical-trial) (Java/Spring AI). Policy-driven human approval for safety-critical AI tool calls. SAPL obligations trigger blocking approval dialogs with configurable timeouts and mandatory review flags before the LLM can execute high-risk actions.
* [FastMCP](https://github.com/heutelbeck/sapl-python-demos/tree/main/fastmcp_demo) (Python). SAPL authorization for MCP servers, showing both global middleware and per-component `auth=sapl()` approaches. Policies control tool visibility and access with JWT authentication via Keycloak.
