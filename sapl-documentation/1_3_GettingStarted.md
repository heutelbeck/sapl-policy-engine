---
layout: default
title: Getting Started
parent: Introduction
grand_parent: SAPL Reference
nav_order: 3
---

## Getting Started

This guide presents two approaches to working with SAPL. Start with the playground to learn policy syntax, then run a local PDP server to experiment with the HTTP API.

### Learning Policy Syntax

The [SAPL Playground](https://playground.sapl.io/) runs entirely in your browser and requires no installation. Open the playground, write policies, create authorization subscriptions, and observe how the PDP evaluates them. The playground includes example policies demonstrating common authorization patterns.

The playground is primarily useful for learning the policy syntax and testing basic policy logic. The playground cannot connect to external attribute sources, and access to PIPs calling out to location tracking, HTTP servers, or MQTT brokers are present but calls are blocked. However, it is a useful tool to learn how a PDP works, how multiple policies and policy sets interact with each other, and how the streaming nature of SAPL works. It even allows you to graphically dig into traces of individual decisions for learning or debugging of your policies. You can also use it to share authorization scenarios with others.

### Running a Local PDP Server

SAPL Node is a lightweight, headless PDP server. Native binaries are available for Linux, Windows, and macOS.

1. Download the SAPL Node binary for your platform from the [releases page](https://github.com/heutelbeck/sapl-policy-engine/releases).
2. Create a folder for policies and configuration. For this tutorial, we use `~/sapl` on Linux/macOS or `C:\sapl` on Windows.
3. Start SAPL Node with TLS and authentication disabled for local experimentation:

**Linux/macOS:**
```bash
chmod +x sapl-node-linux-amd64
./sapl-node-linux-amd64 --server.ssl.enabled=false --server.port=8080 --io.sapl.node.allowNoAuth=true --io.sapl.pdp.embedded.config-path=~/sapl --io.sapl.pdp.embedded.policies-path=~/sapl
```

**Windows (PowerShell):**
```powershell
.\sapl-node-windows-amd64.exe --server.ssl.enabled=false --server.port=8080 --io.sapl.node.allowNoAuth=true --io.sapl.pdp.embedded.config-path=C:\sapl --io.sapl.pdp.embedded.policies-path=C:\sapl
```

> **Warning:** This configuration disables TLS and authentication. Use it only for local experimentation. See [SAPL Node](../7_1_SAPLNode/) for secure production setup.

If everything worked, you should see a line like `Started SaplNodeApplication ...` in the output.

4. Send an authorization request to the server:

```bash
curl -H 'Content-Type: application/json' -d '{"subject":"housemd","action":"use","resource":"MRT"}' http://localhost:8080/api/pdp/decide-once
```

The server should return `{"decision":"NOT_APPLICABLE"}`. As discussed in the introduction, `NOT_APPLICABLE` means the PDP has no policy that is applicable to the subscription. However, the reply shows that the PDP is set up properly and is answering authorization subscriptions and requests.

5. Define the PDP combining algorithm. Create a file `pdp.json` in the data folder (e.g., `~/sapl` or `C:\sapl`). Set its content to:
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

6. Send the same request as before to the server:

```bash
curl -H 'Content-Type: application/json' -d '{"subject":"housemd","action":"use","resource":"MRT"}' http://localhost:8080/api/pdp/decide-once
```

The request should now return `{"decision":"DENY"}`, as expected.

7. Now define a first policy. You can add arbitrary policies to the same folder where `pdp.json` is stored. Files with SAPL policies and policy set, i.e., SAPL documents, must end with the file extension `.sapl`. The SAPL Node monitors the folder for changes to files and automatically loads, reloads, and unloads the respective `pdp.json` or `*.sapl` files. Create the file `housemd_mrt.sapl`:

```sapl
policy "Dr. House is allowed to use the MRT!"
permit subject=="housemd" & action=="use" & resource=="MRT";
```

If you now send the same authorization request again to the PDP, it should return `{"decision":"PERMIT"}`.

8. So far we have just used a simple request-response pattern for answering authorization questions. Now let's move to a simple time-based publish-subscribe scenario.

Issue the following request to the PDP. Note: the API endpoint changed from `/decide-once` to `/decide`. This new endpoint returns server-sent events and the client stays subscribed to updates:

```bash
curl -H 'Content-Type: application/json' -d '{"subject":"housemd","action":"use","resource":"MRT"}' http://localhost:8080/api/pdp/decide
```

The server should now again return a `{"decision":"PERMIT"}`. However, note that the command does not immediately drop you back into your command line and curl stays connected. Keep it this way and do not interrupt the command.

9. Edit your policy file. For example, change the `subject=="housemd"` to `subject=="cuddy"` and save the file. Immediately, you should see `{"decision":"DENY"}` in the command line. Feel free to experiment with the authorization subscription in the curl command, or the policies. While you are connected, the curl command will always receive an updated decision representing the latest state of the policies.

10. In the last step, you could observe how decisions change dynamically based on changes of the policies. Now, create a new policy which changes dynamically based on attributes changing. We will use a simple time-based environment attribute for this, i.e., `<time.now>` which is a one-second time ticker. Delete the original policy file (now you should get a `DENY` if your subscription is still going) and create a new policy `time_based.sapl`:
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
* Inspect the log output. It is set up to verbose logging, and it will provide more information about how it calculated each individual decision.

The HTTP API works from any programming language that can make HTTP requests. See the [HTTP Server-Sent Events API](../6_1_HTTPApi/) documentation for the complete API specification including streaming authorization decisions.

For integrating SAPL directly into Java applications using an embedded PDP, see [Java API](../6_2_JavaApi/).
