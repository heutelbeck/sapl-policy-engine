---
layout: default
title: Getting Started
#permalink: /reference/Getting-Started/
parent: Introduction
grand_parent: SAPL Reference
nav_order: 6
---

## Getting Started

This guide presents three approaches to working with SAPL, each suited to different goals and technical environments. Start with the playground to learn policy syntax, use Docker for local experimentation with a running PDP, or integrate SAPL directly into applications using the embedded PDP or HTTP API.

### Learning Policy Syntax

The [SAPL Playground](https://playground.sapl.io/) runs entirely in your browser and requires no installation. Open the playground, write policies, create authorization subscriptions, and observe how the PDP evaluates them. The playground includes example policies demonstrating common authorization patterns.

The playground is primarily useful for learning the policy syntax and testing basic policy logic. The playground cannot connect to external attribute sources, and access to PIPs calling out to location tracking, HTTP servers, or MQTT brokers are present but calls are blocked. However, it is a useful tool to learn how a PDP works, how multiple policies and policy sets interact with each other, and how the streaming nature of SAPL works. It even allows you to graphically dig into traces of individual decisions for learning or debugging of your policies. You can also use it to share authorization scenarios with others.

### Running a Local PDP Server

SAPL 4.0 can be used with an embedded PDP by importing the matching dependencies, or by deploying a `SAPL Node`, a lightweight and headless implementation PDP Server.

To get up and running quickly, it is straightforward to set up as a Docker container.

The SAPL Server strictly follows a secure-by-default philosophy. This means that the application comes with secure default settings, and it intentionally makes it difficult to operate without secure settings. Any SAPL Server by default expects to be set up with TLS enabled for transport-level encryption of access tokens, authorization subscriptions, and authorization decisions. 

For a quickstart, we provide a simple pre-configuration to use which can be dropped into the volume of the Docker container.

1. Have Docker installed locally as a runtime environment for the SAPL Node
2. Install curl to test the server
3. Select a local folder to be used as a volume for the Docker container where the configuration files and policies will be located. For this tutorial, we use `~/sapl` on Linux/macOS or `C:\sapl` on Windows. Adjust the paths in the following steps to your local environment.
4. Download the two files `application.yml` and `keystore.p12` to that folder. [Download here](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-documentation/configs) You can inspect the `.yml` file for the documentation of the different configuration parameters.
5. Start the SAPL Node container, mounting the folder to the container path `/pdp/data`:

**Bash (Linux/macOS):**
```bash
docker run -d --name sapl-node -e SERVER_ADDRESS=0.0.0.0 -p 8443:8443 -v ~/sapl:/pdp/data ghcr.io/heutelbeck/sapl-node:4.0.0-SNAPSHOT
```

**PowerShell (Windows):**
```powershell
docker run -d --name sapl-node -e SERVER_ADDRESS=0.0.0.0 -p 8443:8443 -v C:\sapl:/pdp/data ghcr.io/heutelbeck/sapl-node:4.0.0-SNAPSHOT
```
The `-e SERVER_ADDRESS=0.0.0.0` flag is required because the bundled configuration binds the server to `localhost`, which would be unreachable from outside the container. Setting it to `0.0.0.0` makes the server listen on all network interfaces.

If everything worked as expected, you should now see a line like `Started SaplNodeApplication ...` in the container logs.
6. Send the following authorization request to the server:

**Bash (Linux/macOS):**
```bash
curl -v -k -H 'Authorization: Basic eHd1VWFSRDY1Rzozal9QSzcxYmp5IWhOMyp4cS54WnF2ZVUpdDVoS0xSXw==' -H 'Content-Type: application/json' -d '{"subject":"housemd","action":"use","resource":"MRT"}' https://localhost:8443/api/pdp/decide-once
```

**PowerShell (Windows):**
```powershell
curl.exe -v -k -H "Authorization: Basic eHd1VWFSRDY1Rzozal9QSzcxYmp5IWhOMyp4cS54WnF2ZVUpdDVoS0xSXw==" -H "Content-Type: application/json" -d '{\"subject\":\"housemd\",\"action\":\"use\",\"resource\":\"MRT\"}' https://localhost:8443/api/pdp/decide-once
```

> **Note:** On Windows, use `curl.exe` (not `curl`, which is a PowerShell alias for `Invoke-WebRequest`). In PowerShell, JSON strings require escaped double quotes (`\"`). All subsequent curl commands in this tutorial follow the same pattern.

The server should return `{"decision":"NOT_APPLICABLE"}`. As discussed in the introduction, `NOT_APPLICABLE` means the PDP has no policy that is applicable to the subscription. However, the reply shows that the PDP is set up properly and is answering authorization subscriptions and requests. 

7. Define the PDP combining algorithm. Create a file `pdp.json` in the data folder (e.g., `~/sapl` or `C:\sapl`). Set its content to:
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
This instructs the server to evaluate policies by priority (highest priority first), return `DENY` when no policy issues a `PERMIT`, and abstain from errors (treat them as not applicable). It also sets up an empty object to store environment variables in the future.

8. Send the same request as before to the server:

**Bash:**
```bash
curl -v -k -H 'Authorization: Basic eHd1VWFSRDY1Rzozal9QSzcxYmp5IWhOMyp4cS54WnF2ZVUpdDVoS0xSXw==' -H 'Content-Type: application/json' -d '{"subject":"housemd","action":"use","resource":"MRT"}' https://localhost:8443/api/pdp/decide-once
```

**PowerShell:**
```powershell
curl.exe -v -k -H "Authorization: Basic eHd1VWFSRDY1Rzozal9QSzcxYmp5IWhOMyp4cS54WnF2ZVUpdDVoS0xSXw==" -H "Content-Type: application/json" -d '{\"subject\":\"housemd\",\"action\":\"use\",\"resource\":\"MRT\"}' https://localhost:8443/api/pdp/decide-once
```

The request should now return `{"decision":"DENY"}`, as expected.

9. Now define a first policy. You can add arbitrary policies to the same folder where `pdp.json` is stored. Files with SAPL policies and policy set, i.e., SAPL documents, must end with the file extension `.sapl`. The SAPL Node monitors the folder for changes to files and automatically loads, reloads, and unloads the respective `pdp.json` or `*.sapl` files. Create the file `housemd_mrt.sapl`:

```sapl
policy "Dr. House is allowed to use the MRT!"
permit subject=="housemd" & action=="use" & resource=="MRT";
```

If you now send the same authorization request again to the PDP, it should return `{"decision":"PERMIT"}`.

10. So far we have just used a simple request-response pattern for answering authorization questions. Now let's move to a simple time-based publish-subscribe scenario.

Issue the following request to the PDP. Note: the API endpoint changed from `/decide-once` to `/decide`. This new endpoint returns server-sent events and the client stays subscribed to updates:

**Bash:**
```bash
curl -v -k -H 'Authorization: Basic eHd1VWFSRDY1Rzozal9QSzcxYmp5IWhOMyp4cS54WnF2ZVUpdDVoS0xSXw==' -H 'Content-Type: application/json' -d '{"subject":"housemd","action":"use","resource":"MRT"}' https://localhost:8443/api/pdp/decide
```

**PowerShell:**
```powershell
curl.exe -v -k -H "Authorization: Basic eHd1VWFSRDY1Rzozal9QSzcxYmp5IWhOMyp4cS54WnF2ZVUpdDVoS0xSXw==" -H "Content-Type: application/json" -d '{\"subject\":\"housemd\",\"action\":\"use\",\"resource\":\"MRT\"}' https://localhost:8443/api/pdp/decide
```

The server should now again return a `{"decision":"PERMIT"}`. However, note that the command does not immediately drop you back into your command line and curl stays connected. Keep it this way and do not interrupt the command.

11. Edit your policy file. For example, change the `subject=="housemd"` to `subject=="cuddy"` and save the file. Immediately, you should see `{"decision":"DENY"}` in the command line. Feel free to experiment with the authorization subscription in the curl command, or the policies. While you are connected, the curl command will always receive an updated decision representing the latest state of the policies.

12. In the last step, you could observe how decisions change dynamically based on changes of the policies. Now, create a new policy which changes dynamically based on attributes changing. We will use a simple time-based environment attribute for this, i.e., `<time.now>` which is a one-second time ticker. Delete the original policy file (now you should get a `DENY` if your subscription is still going) and create a new policy `time_based.sapl`:
```sapl
policy "time demo"
permit 
  subject=="housemd";
  action=="use";
  resource=="MRT";
  time.secondOf(<time.now>) % 10 < 5;
```

**Note:** the string in double quotes following `policy` is the policy name. For each policy file this name must be unique!

Now, with the same subscription as before, the decision will change from `PERMIT` to `DENY` and back every five seconds. What happens here is that `<time.now>` turns into a subscription to the clock. The function `time.secondOf` calculates the current second of the time emitted from `<time.now>`. Then, the policy applies a modulo `10` operator, resulting in a stream of the numbers `0` through `9`. This number is compared to `5`. Thus, for `0` to `4`, all body conditions evaluate to `true` and the policy is applicable. Applicable policies emit their entitlement, which is `PERMIT` in this case. For the rest of the time, the policy emits `NOT_APPLICABLE`, which is turned into `DENY` by the combining algorithm configured in `pdp.json`.

**Next Steps**

You now have a working test setup. There are many experiments you can make now:
* Change the combining algorithm in the `pdp.json`.
* Experiment with different authorization subscriptions and policies.
* Add more conditions to the policy body.
* Start reading about the language features and experiment. 
* Try to model your own business rules.
* Create more than one policy at a time.
* Inspect the log files of the Docker container. It is set up to verbose logging, and it will provide more information about how it calculated each individual decision.

The HTTP API works from any programming language that can make HTTP requests. See the [HTTP Server-Sent Events API](../4_2_HTTPServer-SentEventsAPI/) documentation for the complete API specification including streaming authorization decisions.

### Integrating SAPL into Applications

Applications can integrate SAPL authorization either through an embedded PDP or by connecting to a remote SAPL server via HTTP. The embedded approach works well for single-instance applications or microservices, while the remote approach supports centralized policy management across multiple applications.

#### Embedded PDP for Java Applications

SAPL requires Java 21 or newer and is compatible with Java 25.

Configure a Java version in your project:

```xml
<properties>
  <java.version>21</java.version>
  <maven.compiler.source>${java.version}</maven.compiler.source>
  <maven.compiler.target>${java.version}</maven.compiler.target>
</properties>
```

Add the SAPL embedded PDP dependency:

```xml
<dependency>
  <groupId>io.sapl</groupId>
  <artifactId>sapl-pdp-embedded</artifactId>
  <version>4.0.0-SNAPSHOT</version>
</dependency>
```

Add the Maven Central snapshot repository:

```xml
<repositories>
  <repository>
    <name>Central Portal Snapshots</name>
    <id>central-portal-snapshots</id>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <releases>
      <enabled>false</enabled>
    </releases>
    <snapshots>
      <enabled>true</enabled>
    </snapshots>
  </repository>
</repositories>
```

For projects using multiple SAPL dependencies, use the bill of materials POM:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.sapl</groupId>
      <artifactId>sapl-bom</artifactId>
      <version>4.0.0-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Build a PDP using `PolicyDecisionPointBuilder`. For policies bundled in your application's resources (the `src/main/resources/policies` folder), use `withResourcesSource()`. For policies on the filesystem (with live-reload on changes), use `withDirectorySource()`:

```java
import io.sapl.pdp.PolicyDecisionPointBuilder;

// Option A: Load policies from application resources (src/main/resources/policies)
var components = PolicyDecisionPointBuilder.withDefaults()
        .withResourcesSource()
        .build();
var pdp = components.pdp();

// Option B: Load policies from a filesystem directory (with live-reload)
var components = PolicyDecisionPointBuilder.withDefaults()
        .withDirectorySource(Path.of("~/sapl/policies"))
        .build();
var pdp = components.pdp();
```

Create the configuration file `pdp.json` in the policies directory:

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

Add a policy file `test_policy.sapl` in the same directory:

```sapl
policy "permit reading"
permit
  action == "read";
  subject == "willi" & resource =~ "some.+";
```

Request authorization decisions using the PDP. For a single blocking decision:

```java
var subscription = AuthorizationSubscription.of("willi", "read", "something");
var decision     = pdp.decideOnceBlocking(subscription);
System.out.println(decision.decision()); // PERMIT
```

For reactive streaming decisions that update when policies or attributes change:

```java
pdp.decide(subscription).subscribe(decision ->
    System.out.println(decision.decision())
);
```

When the PDP is no longer needed, release its resources:

```java
components.dispose();
```

The [Java API](../4_3_JavaAPI/) documentation covers the complete PDP interface including streaming decisions, multi-subscriptions, and Spring Security integration. Example applications demonstrating different integration patterns are available in the [SAPL demos repository](https://github.com/heutelbeck/sapl-demos). Start with the [Embedded PDP Demo](https://github.com/heutelbeck/sapl-demos/tree/master/embedded-pdp) for basic usage, or explore the [Spring MVC Project](https://github.com/heutelbeck/sapl-demos/tree/master/web-mvc-app) and [Webflux Application](https://github.com/heutelbeck/sapl-demos/tree/master/webflux) for framework integration.
