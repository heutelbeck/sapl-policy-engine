<h1 align="center">
  <a href="https://sapl.io"><img src="https://sapl.io/assets/favicon.png" alt="SAPL" width="48" valign="middle"></a>
  &nbsp;SAPL - Streaming Attribute Policy Language
</h1>

<p align="center">
  Authorization you can read, test, and trust.<br>
  Policies that stay current. Decisions that stream. A testing DSL that proves correctness.
</p>

<p align="center">
  <a href="https://sapl.io"><strong>sapl.io</strong></a>
  &middot;
  <a href="https://sapl.io/docs/latest/1_2_GettingStarted/">Get Started</a>
  &middot;
  <a href="https://playground.sapl.io/">Playground</a>
  &middot;
  <a href="https://sapl.io/scenarios/spring/">Scenarios</a>
  &middot;
  <a href="https://sapl.io/docs/latest/">Docs</a>
  &middot;
  <a href="https://github.com/heutelbeck/sapl-demos">Demos</a>
  &middot;
  <a href="https://discord.gg/pRXEVWm3xM">Discord</a>
</p>

<p align="center">

[![Build Status][build-status-shield]][build-status-url]
[![SonarCloud Status][sonarcloud-status-shield]][sonarcloud-status-url]
[![Security Rating][security-rating-shield]][security-rating-url]
[![Maven Central][maven-central-shield]][maven-central-url]
[![OpenSSF Scorecard](https://api.securityscorecards.dev/projects/github.com/heutelbeck/sapl-policy-engine/badge)](https://securityscorecards.dev/viewer/?uri=github.com/heutelbeck/sapl-policy-engine)
[![OpenSSF Best Practices](https://www.bestpractices.dev/projects/8298/badge?cache-control=no-cache)](https://www.bestpractices.dev/projects/8298)

</p>

## What SAPL does

SAPL is an authorization engine with a human-readable policy language, streaming decisions that update in real time when context changes, and a dedicated testing DSL with coverage reporting. It runs embedded in your application or as a standalone server.

```
policy "freeze during peak hours"
deny
    subject.role == "engineer";
    action == "deploy";
    resource.environment == "production";
    <time.localTimeIsBetween("09:00", "17:00", "Europe/Berlin")>;
obligation {
    "type": "notify",
    "channel": "ops-alerts",
    "message": "Deployment blocked: peak hours"
}
```

Policies can do more than allow or deny. Obligations and advice attach machine-readable instructions to decisions: redact fields, rewrite queries, log access, require human approval, or trigger notifications. The framework enforces them automatically.

## Why SAPL

- **Streaming authorization.** Subscribe to decisions. The PDP pushes updates when attributes change. No polling, no stale decisions.
- **Human-readable policies.** Not Datalog, not YAML, not XML. Policies read like structured English.
- **Testing DSL.** The only authorization engine with a dedicated test language. Mock attribute sources, emit streaming changes, assert decision sequences, enforce coverage thresholds in CI.
- **Obligations and advice.** Every decision can carry structured instructions that the framework executes automatically.
- **AI agent authorization.** Control tool calls, RAG retrieval, and MCP operations with per-tool policies and human-in-the-loop approval workflows.

## Quick start

**Try in the browser.** The [Playground](https://playground.sapl.io/) runs entirely in your browser. Write policies, create subscriptions, observe decisions. No install needed.

**Try on the command line.** Download the `sapl` binary from the [releases page](https://github.com/heutelbeck/sapl-policy-engine/releases). Write a policy:

```
policy "Dr. House is allowed to use the MRT!"
permit
    subject == "housemd" & action == "use" & resource == "MRT";
```

Evaluate it:

```bash
sapl decide-once -s '"housemd"' -a '"use"' -r '"MRT"'
# {"decision":"PERMIT"}

sapl decide-once -s '"cuddy"' -a '"use"' -r '"MRT"'
# {"decision":"DENY"}
```

The [Getting Started guide](https://sapl.io/docs/latest/1_2_GettingStarted/) covers the full CLI workflow including streaming decisions and testing.

<details>
<summary>Spring Boot integration</summary>

Add the starter:

```xml
<dependency>
    <groupId>io.sapl</groupId>
    <artifactId>sapl-spring-boot-starter</artifactId>
    <version>4.0.0</version>
</dependency>
```

Annotate a method:

```java
@PreEnforce(action = "'read'", resource = "'patient'")
public Patient getPatient(String id) {
    return repository.findById(id);
}
```

Write a policy in `src/main/resources/policies/`:

```
policy "doctors read patients"
permit
    subject.role == "doctor";
    action == "read";
    resource == "patient";
```

The [Spring scenario](https://sapl.io/scenarios/spring/) walks through a complete application step by step.

</details>

## Use it with any stack

| Integration | How |
|-------------|-----|
| [Spring Boot](https://sapl.io/docs/latest/6_3_Spring/) | Annotations (`@PreEnforce`, `@PostEnforce`), embedded PDP, reactive support |
| [FastAPI](https://sapl.io/docs/latest/6_7_FastAPI/) | Decorators with lambda resource builders |
| [Django](https://sapl.io/docs/latest/6_5_Django/) | Decorators with request context |
| [NestJS](https://sapl.io/docs/latest/6_4_NestJS/) | Guards and interceptors |
| [.NET](https://sapl.io/docs/latest/6_10_DotNet/) | Attributes with subscription customizers |
| [Flask](https://sapl.io/docs/latest/6_6_Flask/) | Decorators |
| [FastMCP](https://sapl.io/docs/latest/6_9_FastMCP/) | MCP server authorization |
| [Java API](https://sapl.io/docs/latest/6_2_JavaApi/) | Programmatic, no framework required |

## Test policies like you test code

SAPL is the only authorization engine with a dedicated testing language. Mock attribute sources, emit streaming changes, assert decision sequences, and enforce coverage thresholds in CI.

```
policy "permit on emergency"
permit
    action == "read" & resource == "time";
    "status".<mqtt.messages> == "emergency";
```

```
scenario "decision changes when emergency status changes"
    given
        - attribute "statusMock" "status".<mqtt.messages> emits "emergency"
    when "user" attempts "read" on "time"
    expect permit
    then
        - attribute "statusMock" emits "ok"
    expect not-applicable
    then
        - attribute "statusMock" emits "emergency"
    expect permit;
```

```
> sapl test --policy-hit-ratio 100 --condition-hit-ratio 100

  permit_on_emergency.sapltest
    PASS  permit when MQTT status is emergency
    PASS  not-applicable when MQTT status is ok
    PASS  alternating permit and not-applicable with multiple status changes

Tests:    10 passed, 10 total
Coverage: Policy Hit 100.00%  Condition Hit 100.00%
```

## Scenarios

Working demos with code walkthroughs:

- [Spring Security](https://sapl.io/scenarios/spring/) -- method-level ABAC in a Spring Boot application
- [AI Tool Authorization](https://sapl.io/scenarios/ai-tools/) -- per-tool gating for Spring AI
- [RAG Pipeline](https://sapl.io/scenarios/ai-rag/) -- dynamic query rewriting for retrieval-augmented generation
- [Human-in-the-Loop](https://sapl.io/scenarios/ai-hitl/) -- policy-driven approval workflows for AI tool calls
- [MCP Server](https://sapl.io/scenarios/ai-mcp/) -- authorize tool calls, resources, and prompts in MCP servers

## Get involved

- **[Playground](https://playground.sapl.io/)** -- try SAPL policies in the browser, no install needed
- **[Get Started](https://sapl.io/docs/latest/1_2_GettingStarted/)** -- add SAPL to your first project
- **[Documentation](https://sapl.io/docs/latest/)** -- language reference, functions, integrations
- **[Demos](https://github.com/heutelbeck/sapl-demos)** -- runnable example projects
- **[Discord](https://discord.gg/pRXEVWm3xM)** -- questions, ideas, help from the team

## IDE and editor support

The SAPL language server provides syntax highlighting, diagnostics, content assist, formatting, and more for `.sapl` and `.sapltest` files in any LSP-compatible editor. See the [IDE setup guide](https://sapl.io/docs/latest/9_0_IDESetup/) for IntelliJ, VS Code, Neovim, and other editors.

## Compatibility

| SAPL  | Java  | Spring Boot |
|-------|-------|-------------|
| 4.0.x | 21+   | 4.0.x       |
| 3.0.x | 17+   | 3.x         |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Security

See [SECURITY.md](SECURITY.md).

## Code of Conduct

See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## License

Apache 2.0. See [LICENSE](./LICENSE).

<details>
<summary>Using snapshots</summary>

Snapshots provide the newest development state. Add the snapshot repository to your build:

**Maven**

```xml
<repositories>
    <repository>
        <id>central-portal-snapshots</id>
        <url>https://central.sonatype.com/repository/maven-snapshots/</url>
        <releases><enabled>false</enabled></releases>
        <snapshots><enabled>true</enabled></snapshots>
    </repository>
</repositories>
```

**Gradle**

```gradle
repositories {
    maven { url = uri("https://central.sonatype.com/repository/maven-snapshots") }
}
```

</details>

<details>
<summary>SBOM</summary>

Need a [Software Bill of Materials](https://www.cisa.gov/sbom)? See [dependency graph](https://github.com/heutelbeck/sapl-policy-engine/network/dependencies).

</details>

<!-- MARKDOWN LINKS -->
[build-status-shield]: https://github.com/heutelbeck/sapl-policy-engine/actions/workflows/build.yml/badge.svg?branch=master
[build-status-url]: https://github.com/heutelbeck/sapl-policy-engine/actions/workflows/build.yml?branch=master
[sonarcloud-status-shield]: https://sonarcloud.io/api/project_badges/measure?project=heutelbeck_sapl-policy-engine&metric=alert_status
[sonarcloud-status-url]: https://sonarcloud.io/dashboard?id=heutelbeck_sapl-policy-engine
[security-rating-shield]: https://sonarcloud.io/api/project_badges/measure?project=heutelbeck_sapl-policy-engine&metric=security_rating
[security-rating-url]: https://sonarcloud.io/summary/new_code?id=heutelbeck_sapl-policy-engine
[maven-central-shield]: https://img.shields.io/maven-central/v/io.sapl/sapl-spring-boot-starter
[maven-central-url]: https://mvnrepository.com/artifact/io.sapl
