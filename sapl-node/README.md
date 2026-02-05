# SAPL Node - Lightweight Authorization Server

This server is a lightweight Policy Decision Point (PDP) that uses the Streaming Attribute Policy Language (SAPL) and provides authorization services through an HTTP API. SAPL is further explained on the [SAPL home page](https://sapl.io/).

The SAPL server LT monitors two directories for PDP settings and SAPL documents, allowing for runtime updating of policies that are reflected in decisions made for ongoing authorization subscriptions.

## Introduction

### Aim of the README

This README aims to describe the commissioning and correct operation of SAPL Node, providing an easy introduction to its use. Additionally, it presents important information security aspects of SAPL Node in a transparent manner.

### Target group

This documentation focuses on the operator and the knowledge required for operating. However, technical aspects of interest for further development of the software are also covered.

### Secure by Design

SAPL Node is a software product that follows secure information technology practices and multiple layers of defense based on Secure By Design Principles (also see [CISA Secure By Design](https://www.cisa.gov/sites/default/files/2023-10/SecureByDesign_1025_508c.pdf)).

It is a tool for users to implement Secure by Design systems by incorporating attribute-based access control (ABAC) into their own products and environments.

SAPL adheres to the secure by default principle. SAPL Node comes with a pre-configured basic setup that includes the essential security controls to ensure compliance with Germany's Federal Office for Information Security (BSI) [BSI Baseline Protection Compendium Edition 2022](https://www.bsi.bund.de/SharedDocs/Downloads/EN/BSI/Grundschutz/International/bsi_it_gs_comp_2022.pdf).

For SAPL Node, the binary software packages (OCI Container Images and Java applications delivered as a JAR) come with a strict configuration. Additional configuration is required for authentication and TLS to run the server. This documentation explains these configuration steps in detail.

Application security is a top priority in the development of SAPL. The SAPL project also embraces radical transparency and accountability. Security issues are reported and shared using the GitHub advisory feature. For more details, see the [SAPL Security Policy](https://github.com/heutelbeck/sapl-policy-engine/blob/master/SECURITY.md).

## System requirements

Requirements for local installation.

- Java Development Kit 17 or a newer version
- Operating system that is compatible with java
- Maven 3.9 or a newer version

## Prerequisites and download

SAPL Node comes in two forms: an executable Java JAR file for OpenJDK 17 (or later) and an OCI container. The server's full source code is also available for building, running from source, and auditing.

### Java OpenJDK

#### Prerequisites

Before running SAPL Node on your system, make sure that you have installed [OpenJDK 17](https://openjdk.org/projects/jdk/17/) or a newer version. [Eclipse Temurin](https://adoptium.net/de/temurin/releases/) is one of the available distributions that provides binaries for different platforms.

Ensure that the Java executables are added to the system path.

#### Download

To be done: setup download of release and snapshot via GitHub packages.

### Running from Source

#### Prerequisites

To build SAPL Node from source, first ensure that [OpenJDK 17](https://openjdk.org/projects/jdk/17/) or newer is installed. There are several distributions available, such as [Eclipse Temurin](https://adoptium.net/de/temurin/releases/) supplying binaries for different platforms.

SAPL uses Apache Maven as its build system, which must be installed and can be downloaded from the [Apache Maven Download Page](https://maven.apache.org/download.cgi).

Ensure the Java and Maven executables are on the system path.

#### Download

The source of the policy engine is found on the public [GitHub](https://github.com/) repository: <https://github.com/heutelbeck/sapl-policy-engine>.

You can either download the source as a ZIP file and unzip the archive, or clone the repository using git:

```
git clone https://github.com/heutelbeck/sapl-policy-engine.git
```

#### Build the Engine and Server locally

To build the engine including the server application go to the `sapl-policy-engine` folder and execute the following command:

```
mvn install
```

After completing the build process, you can run the SAPL Node locally using Maven or as a JAR.

## Configuration

To start up SAPL Node, basic configuration is required. This includes configuring [client authentication](#managing-client-authentications) and [TLS](#tls-configuration). SAPL Node is implemented using [Spring Boot](https://spring.io/projects/spring-boot/), which offers flexible tools for application configuration. The Spring Boot documentation for [Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config) provides helpful guidelines to follow. It is important to note the order in which configurations are loaded and can overwrite each other.

In summary, the application's configuration is controlled by key-value pairs known as properties. These properties are provided to the application through `.properties` files, `.yml` files, or environment variables. The method of providing these values depends on the specific target environment.

As a starting point, you can start by putting an `application.yml` file in a folder `config` within the folder from where the server is started. An annotated example configuration can be found [here](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-node/config).

**Note:** This example configuration is not intended for production. It contains secrets and certificates which are publicly known. Whenever you run a SAPL Node with this configuration **you** **accept the resulting risks** making the API publicly accessible via the provided credentials and that the server and its decisions cannot be properly authenticated by client applications because of the use of a publicly known self-signed TLS certificate.

### Configure the embedded PDP

The SAPL Node includes an [embedded PDP](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-pdp-embedded) that can be configured using the following settings.

#### Configuration Type

The `io.sapl.pdp.embedded.pdp-config-type` property allows you to set the source for the [configuration](#configuration-path) and [policies](#policy-storage-location). You can choose between the values `RESOURCES`, `DIRECTORY`, `MULTI_DIRECTORY`, and `BUNDLES`. This property defaults to the `RESOURCES` value if no configuration is provided. The `DIRECTORY` value is preconfigured by default.

If the property is set to `RESOURCES`, the system will load a predetermined set of documents and `pdp.json` from the bundled resource during runtime. These resources cannot be updated while the system is running.

If the property is set to `DIRECTORY`, the system will monitor a single directory for documents and configuration changes. Any updates made to the documents and configuration during runtime will be automatically reflected in existing subscriptions and new decisions will be sent if necessary.

If the property is set to `MULTI_DIRECTORY`, the system monitors subdirectories within a base directory. Each subdirectory name becomes the pdpId for multi-tenant routing. This allows different tenants to have separate policy sets.

If the property is set to `BUNDLES`, the system monitors a directory for `.saplbundle` files. Each bundle filename (without extension) becomes the pdpId for multi-tenant routing. Bundles can be signed for integrity verification.

#### Configuration File

The configuration file for the embedded PDP of the SAPL Node must be named `pdp.json`. This file allows for the definition of the combining algorithm and variables in JSON format.

The combining algorithm determines how the PDP determines the validity of multiple SAPL policies for a given subscription. The available options for the combining algorithm are: `DENY_UNLESS_PERMIT`, `PERMIT_UNLESS_DENY`, `ONLY_ONE_APPLICABLE`, `DENY_OVERRIDES`, and `PERMIT_OVERRIDES`. Refer to the [documentation](https://sapl.io/docs/latest/6_5_CombiningAlgorithm/) for more information on the combining algorithm.

If the algorithm is not configured, the value `DENY_OVERRIDES` will be selected.

All policies can access the variables specified in the `pdp.json` file, which are configured as key-value pairs. This file allows for the declaration of multiple variables. For instance, a configuration may resemble the following:

```json
{
	"algorithm": "DENY_UNLESS_PERMIT",
	"variables": {
		"person": "Willi"
	}
}
```

The combining algorithm for the embedded PDP of the SAPL Node is set to `DENY_UNLESS_PERMIT`. The variable `person` can be used in all policies without needing to declare it explicitly, thanks to its declaration in `pdp.json`.

```
policy "myfirstpolicy"
permit
	subject == person
```

#### Configuration Path

The property `io.sapl.pdp.embedded.config-path` specifies the folder path where the `pdp.json` file is saved. Any changes made to the file are immediately applied at runtime and for current subscriptions.

If [pdpConfigType](#configuration-type) is set to `RESOURCES`, the root of the context path is `/`. If [pdpConfigType](#configuration-type) is set to `DIRECTORY`, `MULTI_DIRECTORY`, or `BUNDLES`, it must be a valid path in the system's file system.

If the `io.sapl.pdp.embedded.config-path` property is not configured differently, the server will search for the `pdp.json` file in the `/policies` directory.

In the [preconfigured example](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-node/config), the server monitors the path `~/sapl`, which refers to the `sapl` folder in the current user's home directory, for the `pdp.json` file.

If the SAPL server runs in a [Docker container](#running-directly-as-a-docker-container), the path `/pdp/data` is monitored.


### Configure Bucket4j

To configure `Bucket4j` in your application, you'll need to adjust some properties in your configuration file.

Firstly, set `bucket4j.enabled=true` to activate Bucket4j's autoconfiguration feature.

Then set `bucket4j.cache-to-use=caffeine` to use Caffeine as the cache provider.

Use `bucket4j.filters.url` to define the path expression where the rate limit should be applied. The property `bucket4j.filters.strategy=first` ensures that the rate limit stops at the first matching configuration encountered.

To retrieve the key, use Spring Expression Language (SpEL) with `bucket4j.filters.rate-limits.cache-key`. Then, determine whether to execute the rate limit using SpEL with `bucket4j.filters.rate-limits.execute-condition`.

Lastly, define the rate limit parameters using `bucket4j.filters.rate-limits.bandwidths` to set up the Bucket4j rate limit. Adjust these settings according to your application's requirements.


### Managing SAPL Policies

#### Policy Storage Location

The property `io.sapl.pdp.embedded.policies-path` specifies the path of the folder where the `*.sapl` documents are saved. When specifying a path, only include the parent or main folder. Subfolders within the main folder will be automatically searched and monitored. Any changes made are immediately taken into account at runtime and for current subscriptions.

If [pdpConfigType](#configuration-type) is set to `RESOURCES`, the root of the context path is `/`. If [pdpConfigType](#configuration-type) is set to `DIRECTORY`, `MULTI_DIRECTORY`, or `BUNDLES`, it must be a valid path in the system's file system.

By default, the server monitors and searches for SAPL policies in the `/policies` directory. SAPL policies are documents that end with `*.sapl`.

In the [preconfigured example](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-node/config), the server monitors the path `~/sapl`, which refers to the `sapl` folder in the current user's home directory, for SAPL documents.

If the SAPL server runs in a [Docker container](#running-directly-as-a-docker-container), the path `/pdp/data` is monitored.

For test purposes, a simple policy `myfirstpolicy.sapl` can be created in the configured folder. The following policy always returns a permit as the decision:

```
policy "myfirstpolicy"
permit
```

To learn more about writing SAPL policies, refer to the [documentation](https://sapl.io/docs/latest/5_0_TheSAPLPolicyLanguage/).

#### Policy Indexing

The `io.sapl.pdp.embedded.index` property determines the indexing algorithm for the SAPL policies managed by the embedded PDP of the SAPL Node. The available options for this property are `NAIVE` and `CANONICAL`, with `NAIVE` being the default value.

Select the `NAIVE` value for systems with few documents and the `CANONICAL` value for systems  
with many documents. The `CANONICAL` algorithm is more time-consuming for initialization and updating, but it significantly reduces retrieval time.

#### Policy File Renaming

If an existing policy is to be changed, the following procedure is recommended:

1. create a new file that is semantically equivalent to the policy to be changed. The policy name must be changed in the new policy.
2. delete the old policy file
3. change the policy name of the new policy

**Why should I proceed in this way?** It is difficult to handle file renaming as an atomic process. The system deletes the policy at short notice and can make incorrect decisions during this period. The recommended procedure avoids potential errors by creating a new policy before deleting the old file.

### Managing Client Authentications

SAPL Node supports several authentication schemes for client applications. By default, none is activated and the server will deny to start up until at least one scheme is active. Multiple authentication mechanisms can be enabled simultaneously.

All authenticated clients are associated with a `pdpId` for multi-tenant PDP routing. The following global settings control how missing pdpId values are handled:

```yaml
io.sapl.node:
  rejectOnMissingPdpId: false  # If true, reject auth when pdpId is missing
  defaultPdpId: "default"      # Fallback pdpId when not configured
```

If `rejectOnMissingPdpId` is `true`, users without a configured `pdpId` will fail authentication at startup (for Basic/API Key) or at runtime (for OAuth2 JWT without the claim). If `false`, the `defaultPdpId` is used as fallback.

#### No Authentication

The server does not require authentication before responding to requests or subscriptions.

**Use case:** For testing, development, and deployments, API authentication is delegated to API gateways, Kubernetes ingress services, or similar situations.

To activate this authentication scheme set the property `io.sapl.node.allowNoAuth` to `true`.

**Note:** Do not use this option in production if access is not properly secured.  This option may open the server to malicious probing and exfiltration attempts through the authorization endpoints, potentially resulting in unauthorized access to your organization's data, depending on your policies.

#### Basic Authentication

SAPL Node supports multiple clients authenticated using Basic Authentication [RFC 7617](https://datatracker.ietf.org/doc/html/rfc7617).

**Use case:** The developers of the clients prefer this authentication method. For example most HTTP client libraries support basic authentication out of the box.

**Advantage:** The secret is not stored as plain text (Argon2 encoded).

**Disadvantage:** Authentication per request/subscription is costly and introduces significant latency.

To activate this authentication scheme set the property `io.sapl.node.allowBasicAuth` to `true`.

To add a client, add an entry to the `io.sapl.node.users` list with the following structure:

```yaml
io.sapl.node:
  allowBasicAuth: true
  users:
    - id: "my-client"
      pdpId: "default"
      basic:
        username: "myUsername"
        secret: "$argon2id$v=19$m=16384,t=2,p=1$..."  # Argon2 encoded
```

The `id` is a unique identifier for the client. The `pdpId` determines which PDP configuration to use for multi-tenant routing. The `basic.username` is used for the Basic Auth username, and `basic.secret` must be an Argon2 [RFC 9106](https://datatracker.ietf.org/doc/rfc9106/) encoded password.

If you do not have a tool at hand for Argon2 password hashing, the SAPL Node binary can be used to generate credentials:

```
java -jar sapl-node-4.0.0.jar generate basic --id "my-client" --pdp-id "default"
```

This will print the credentials to the console. Doing so will not start up an instance of the server.

Take note of the plain text of the secret as it will not be stored. Also make sure that the output of this tool is not visible in any logs, and you properly clear your screen.

#### API Keys

In SAPL Node, API keys provide a way of managing multiple client applications with individual secrets and low-latency authentication.

Each client is assigned an individual API key and can authenticate by providing the following HTTP header: `Authorization: Bearer <API KEY>`. For example: `Authorization: Bearer sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j`.

**Use Case:** Managing multiple clients with high traffic and low latency requirements.

**Advantage:** Low latency after initial verification.

**Disadvantage:** API keys must be securely distributed to clients.

To activate this authentication scheme set the property `io.sapl.node.allowApiKeyAuth` to `true`.

To add a client, add an entry to the `io.sapl.node.users` list with the following structure:

```yaml
io.sapl.node:
  allowApiKeyAuth: true
  users:
    - id: "my-api-client"
      pdpId: "default"
      apiKey: "$argon2id$v=19$m=16384,t=2,p=1$..."  # Argon2 encoded
```

The `id` is a unique identifier for the client. The `pdpId` determines which PDP configuration to use for multi-tenant routing. The `apiKey` must be an Argon2 encoded version of the API key that the client will use.

If you do not have a tool at hand to create a good random key, the SAPL Node binary can be used to generate a new API key:

```
java -jar sapl-node-4.0.0.jar generate apikey --id "my-api-client" --pdp-id "default"
```

This will print the API key to the console. Doing so will not start up an instance of the server.

The new API key can now be added in the configuration. If you want to test whether access via the API key works, you can use the following command:

CMD

```
curl -k -X POST -H "Authorization: Bearer <API Key>" -H "Content-Type: application/json" -d "{\"subject\":\"WILLI\",\"action\":\"read\",\"resource\":\"something\"}" https://localhost:8443/api/pdp/decide-once
```

PowerShell (Invoke-WebRequest)

```
Invoke-WebRequest -Uri "https://localhost:8443/api/pdp/decide-once" -Method Post -Headers @{"Authorization"="Bearer <API Key>";"Content-Type"="application/json"} -Body '{"subject":"WILLI","action":"read","resource":"something"}'
```

PowerShell (curl)

```
curl.exe -k -X POST -H 'Authorization: Bearer <API Key>' -H 'Content-Type: application/json' -d '{\"subject\":\"WILLI\",\"action\":\"read\",\"resource\":\"something\"}' https://localhost:8443/api/pdp/decide-once
```

Bash

```
curl -k -X POST -H "Authorization: Bearer <API Key>" -H "Content-Type: application/json" -d '{"subject":"WILLI","action":"read","resource":"something"}' https://localhost:8443/api/pdp/decide
```

You should receive something like this:

```
data:{"decision":"NOT_APPLICABLE"}
```

If you want to check whether your policies are working, you can create a test policy as described under [Managing SAPL Policies](#managing-sapl-policies) and check the server's decision. With the policy mentioned in the section, the following response should now appear:

```
data:{"decision":"PERMIT"}
```

#### JWT Token Authentication

To use JSON Web Tokens (JWT), you must activate the following property `io.sapl.node.allowOauth2Auth: true`. In addition, a valid URI to an identity provider (IDP) must be specified under `spring.security.oauth2.resourceserver.jwt.issuer-uri: <URI>`

For example, the URI for a local Keycloak instance with the realm 'SAPL' must look like this: `http://localhost:9000/realms/SAPL`

The pdpId for multi-tenant routing can be extracted from a JWT claim. Configure the claim name with:

```yaml
io.sapl.node:
  allowOauth2Auth: true
  oauth:
    pdpIdClaim: "sapl_pdp_id"  # The JWT claim containing the pdpId
```

You can check whether authentication with JWT works by including the bearer token from the oauth2 server in the HTTP header. To do this, you must first receive a token from your oauth2 server and then execute the following command:

CMD

```
curl -k -X POST -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d "{\"subject\":\"WILLI\",\"action\":\"read\",\"resource\":\"something\"}" https://localhost:8443/api/pdp/decide-once
```

PowerShell (Invoke-WebRequest)

```
Invoke-WebRequest -Uri "https://localhost:8443/api/pdp/decide-once" -Method Post -Headers @{"Authorization"="Bearer <token>";"Content-Type"="application/json"} -Body '{"subject":"WILLI","action":"read","resource":"something"}'
```

PowerShell (curl)

```
curl.exe -k -X POST -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' -d '{\"subject\":\"WILLI\",\"action\":\"read\",\"resource\":\"something\"}' https://localhost:8443/api/pdp/decide-once
```

Bash

```
curl -k -X POST -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d '{"subject": "WILLI", "action": "read", "resource": "something"}' https://localhost:8443/api/pdp/decide
```

### TLS Configuration

By default, the server will use a self-signed certificate and expose the PDP API under <https://localhost:8443/api/pdp> To override this certificate, use the matching Spring Boot settings, e.g.:

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store-type: PKCS12
    key-store: classpath:keystore.p12
    key-store-password: localhostpassword
    key-alias: tomcat
```

If you are unsure about the key alias, you can display it using the Java keytool via `keytool -list -v -keystore <Path to Keystore.p12>`. The output will show you the alias name for the certificate. You should see something like this:

```yaml
Alias name: tomcat
Creation date: Oct 21, 2023
Entry type: PrivateKeyEntry
Certificate chain length: 1
Certificate[1]:

[...]
```

You can also specify a list of permitted cipher suites that may be used by the client and server.

**Note**: The available cipher suites depend on the client and server implementation, i.e. not all configured cipher suites are available for every client. Nevertheless, more cipher suites can be configured, as the client and server automatically negotiate which cipher suites are available.

```yaml
server:
  ssl:
    ciphers:
      - TLS_AES_128_GCM_SHA256
      - TLS_AES_256_GCM_SHA384
      - TLS_AES_128_CCM_SHA256
      - TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384
      - TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256
      - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
      - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
      - TLS_DHE_RSA_WITH_AES_256_GCM_SHA384
      - TLS_DHE_RSA_WITH_AES_128_GCM_SHA256
      - TLS_DHE_DSS_WITH_AES_256_GCM_SHA384
      - TLS_DHE_DSS_WITH_AES_128_GCM_SHA256
      - TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384
      - TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256
      - TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384
      - TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256
      - TLS_DHE_RSA_WITH_AES_256_CBC_SHA256
      - TLS_DHE_RSA_WITH_AES_128_CBC_SHA256
      - TLS_DHE_DSS_WITH_AES_256_CBC_SHA256
      - TLS_DHE_DSS_WITH_AES_128_CBC_SHA256
    protocols:
      - TLSv1.3
      - TLSv1.2
```

For specific recommendations, please have a look at the [BSI technical guidelines](https://www.bsi.bund.de/DE/Themen/Unternehmen-und-Organisationen/Standards-und-Zertifizierung/Technische-Richtlinien/TR-nach-Thema-sortiert/tr02102/tr-02102.html).

### Logging Configuration

The SAPL Node uses the Simple Logging Facade for Java (SLF4J) and the logging framework Logback for logging. The annotation types for SLF4J are imported via Lombok.

Configuration of logging can be done in the `application.yml` file. Additional logging configurations can be found in the [Spring documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.logging).

#### Logging Level

To configure the logging level for the desired classes, use the `logging.level` property and select one of the following levels:

- `TRACE`:  Detailed information for troubleshooting and debugging during development.
- `DEBUG`: Logs useful information for development and debugging purposes.
- `INFO`: These messages provide updates on the application's progress and significant events.
- `WARN`: Records issues that do not result in errors.
- `ERROR`: The application's errors or exceptions that occurred during execution are logged.

The logging level for the `io.sapl` and `org.springframework` classes is set to `INFO` by default.

To enhance the performance of the SAPL Node, consider adjusting the logging level to `WARN`. This will reduce the number of log messages and improve latency. However, it is important to note that decisions made by the PDP will still be logged with the `INFO` level.

#### Logging specific to SAPL

Configuration options for logging PDP decisions are set under `io.sapl.pdp.embedded`. Boolean values determine which log formats are activated or deactivated for PDP decisions.

- `print-trace`: The PDP documents each individual calculation step in a fine-grained manner. The trace is provided in JSON format, which may become very large. This should only be considered as a final option for resolving issues.
- `print-json-report`: This JSON report summarizes the applied algorithms and results of each evaluated policy (set) in the decision-making process. It includes lists of all errors and values of policy information point attributes encountered during the evaluation of each policy (set).
- `print-text-report`: This will log a human-readable textual report based on the same data as the 'print-json-report' option generates.
- `pretty-print-reports`: This option can enable formatting of JSON data while printing JSON during reporting and tracing.

The following code example shows the format in which the information is logged when the `print-text-report` property is activated.

```
--- The PDP made a decision ---
Subscription: {"subject":"bs@simpsons.com","action":"read","resource":"file://example/med/record/patient/BartSimpson"}
Decision    : {"decision":"DENY"}
Timestamp   : 2024-01-12T09:48:19.668342200Z
Algorithm   : "DENY_OVERRIDES"
Matches     : ["policy 1"]
Policy Evaluation Result ===================
Name        : "policy 1"
Entitlement : "DENY"
Decision    : {"decision":"DENY"}
Target      : true
Where       : true
```

#### Log to a file

If you need to write the logs to a file, refer to the [Spring documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.logging.file-output) for the procedure.

## Running the Node

### JAVA OpenJDK

**Note:** Failure to configure the server will prevent it from starting. This may seem inconvenient, but it is necessary for security reasons. The default configuration does not include any well-known public secrets that could compromise the deployment's security. Please refer to the [Configuration](#configuration) section for proper server setup instructions.

Once configured, start the server using the following command:

```shell
java -jar sapl-node-4.0.0-SNAPSHOT.jar
```

### Running from Source

**Note:** Running from source is not intended for production environments. If doing so, **you accept the risk** of running with publicly known credentials and TLS certificates if you accidentally use the development configuration of the server.

It is likely that you only need to run the server from the source if you are a contributor to the policy engine project.

The source code includes a small development configuration of the server which contains some pre-configured credentials and a self-signed certificate for setting up TLS.

#### Running the Server using Maven

Change the current directory to `sapl-policy-engine/sapl-node` and execute the following command:

```shell
mvn spring-boot:run
```

If started from this folder, the application will start with a demonstration configuration located in  `sapl-policy-engine/sapl-node` which sets up TLS with a self-signed certificate. Further, the PDP API will require basic authentication. For the demonstration configuration a default client is configured with the key (username) `xwuUaRD65G` and the secret (password) `3j_PK71bjy!hN3*xq.xZqveU)t5hKLR_`. This means that any API request expects the header `Authorization: Basic eHd1VWFSRDY1Rzozal9QSzcxYmp5IWhOMyp4cS54WnF2ZVUpdDVoS0xSXw==` or else access to the PDP API will be denied. The demonstration configuration also sets up the PDP to monitor the folder `~/sapl`, i.e., the folder `sapl` in the users home folder, for PDP configuration and SAPL documents (i.e., policies).

#### Running the Server as a JAR

After the build concludes an executable JAR will be available in the folder `sapl-policy-engine/sapl-node/target`. This JAR can be used in the same way as a downloaded SAPL Node binary, as described under [Java OpenJDK](#java-openjdk).

**Note:** If the JAR is executed from within the folder `sapl-policy-engine/sapl-node` using the command `java -jar target/sapl-node-4.0.0.jar` the server will pick up the same demonstration configuration as described above.

### Kubernetes/Docker

The server application is available as container image. This container image is created using [Paketo Builder](https://paketo.io/docs/concepts/builders/) through the [Spring Boot Maven plugin](https://docs.spring.io/spring-boot/docs/current/maven-plugin/reference/htmlsingle/), eliminating the need for a Dockerfile.

Here, the server is not configured with any TLS security or authentication. It is expected that in deployment this responsibility is delegated to the infrastructure, e.g., a matching Kubernetes Ingress.

#### Running Directly as a Docker Container

In order to run the server locally for testing in an environment like Docker Desktop, you can run the current image as follows. To start the container for the SAPL Node based on the Docker image, follow these steps: If the image is not available locally, download it from [GitHub](https://github.com/heutelbeck/sapl-policy-engine/pkgs/container/sapl-node). If image-pull-on-run is enabled, as is the case with Docker Desktop, the image does not need to be downloaded beforehand.

In this example, an existing Docker volume with the name sapl-node is mounted in the `/pdp/data` path of the Docker container.

```
docker run -d --name sapl-node -p 8443:8443 --mount source=sapl-node,target=/pdp/data ghcr.io/heutelbeck/sapl-node:4.0.0-SNAPSHOT
```

This example demonstrates how the path of the host system is mounted onto the path `/pdp/data` of the Docker container.

```
docker run -d --name sapl-node -p 8443:8443 -v c:\sapl\policies:/pdp/data ghcr.io/heutelbeck/sapl-node:4.0.0-SNAPSHOT
```

If your server does not want to start, you will most likely have to specify a keystore. To do this, it is recommended that you first create a new Docker volume and store the keystore there. In the following example, we have created a Docker volume 'sapl-node', which is mounted on `/pdp/data` within the container. Alternatively, you can store the keystore in a path on your host system and then mount it under the path `/pdp/data` of your Docker container. 

The default value of the parameter `SPRING_CONFIG_ADDITIONAL_LOCATION` in the Docker image is set to `/pdp/data`. This parameter specifies the location where an additional `application.yml` file should be searched for. This parameter also makes it necessary to mount a volume under /pdp/data, as otherwise the SAPL server LT will not start in the container. The `application.yml` under `/pdp/data` also contains the configuration for your keystore:

```
docker run -d --name sapl-node -p 8443:8443 -v sapl-node:/pdp/data ghcr.io/heutelbeck/sapl-node:4.0.0-SNAPSHOT
```

Afterward you can check if the service is online under: <http://localhost:8080/actuator/health>.

Depending on your host OS and virtualization environment, these volumes may be located at:

- Docker Desktop on Windows WSL2: `\\wsl$\docker-desktop-data\version-pack-data\community\docker\volumes\sapl-node\_data`
- Docker Desktop on Windows Hyper-V: `C:\Users\Public\Documents\Hyper-V\Virtual hard disks\sapl-node\_data`
- Docker on Linux: `/var/lib/docker/volumes/sapl-node/_data`
- Docker Desktop on Windows with shared folder: `c:\sapl\policies` (or as changed)

#### Creating a Docker image using Maven

The [Spring Boot Maven plugin](https://docs.spring.io/spring-boot/docs/current/maven-plugin/reference/htmlsingle/) is utilized to generate a Docker image through [Paketo Builder](https://paketo.io/docs/concepts/builders/). Thus, the Docker image can also be produced by executing the following command in the SAPL Node Maven project directory.

```
mvn spring-boot:build-image
```

#### Running on Kubernetes

This section will describe the deployment on a bare metal Kubernetes installation which has the ports 80 and 443 exposed to the internet as well as Desktop Docker on Windows and will use the Kubernetes nginx-ingress-controller as well as cert-manager to manage the Let's Encrypt certificates (only if ports are exposed to the internet so Let's Encrypt can access the URL)

##### Prerequisites

Installed Kubernetes v1.23 Install NGINX Ingress Controller according to <https://kubernetes.github.io/ingress-nginx/deploy/>

```
helm upgrade --install ingress-nginx ingress-nginx --repo https://kubernetes.github.io/ingress-nginx --namespace ingress-nginx --create-namespace --set controller.hostNetwork=true,controller.kind=DaemonSet
```

Install Cert-Manager according to <https://cert-manager.io/docs/installation/kubernetes/> (only for use with exposed ports and matching DNS entries):

```
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.14.3/cert-manager.yaml
```

Download the configuration file for the [ClusterIssuer](https://cert-manager.io/docs/concepts/issuer/) in the Kubernetes environment. Choose one of the following commands to use.

CMD/PowerShell/Bash (curl)

```
curl -o clusterissuer.yml "https://raw.githubusercontent.com/heutelbeck/sapl-policy-engine/master/sapl-node/kubernetes/clusterissuer.yml"
```

CMD/Bash (wget)

```
wget https://raw.githubusercontent.com/heutelbeck/sapl-policy-engine/master/sapl-node/kubernetes/clusterissuer.yml
```

PowerShell (Invoke-WebRequest)

```
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/heutelbeck/sapl-policy-engine/master/sapl-node/kubernetes/clusterissuer.yml" -OutFile "clusterissuer.yml"
```

Change the email address in the `clusterissuer.yml` (Line email: user@email.com). Then execute the resources defined in `clusterissuer.yml` in the namespace you have defined.

```
kubectl apply -f clusterissuer.yml -n <namespace>
```

##### Bare Metal Kubernetes

This section assumes that Kubernetes is installed on a Linux operating system, such as Ubuntu. Alternatively, it is also possible for Windows to activate Kubernetes in Docker Desktop.

Create the namespace for sapl-node first.

```
kubectl create namespace sapl-node
```

Add the resource for configuring a persistent volume for the SAPL Node to the namespace.

The default `hostPath` for the persistent volume is `/sapl`. In addition, the persistent volume with the file `sapl-node-baremetal.yml` is mounted under `/pdp/data` by default. Saving the [required server configurations](#configuration) as `application.yml` under `/sapl` in the persistent volume is necessary for a proper server start.

```
kubectl apply -f https://raw.githubusercontent.com/heutelbeck/sapl-policy-engine/master/sapl-node/kubernetes/sapl-node-pv.yml -n sapl-node
```

Then download the bare metal yaml file. Choose one of the following commands to use.

CMD/PowerShell/Bash (curl)

```
curl -o sapl-node-baremetal.yml "https://raw.githubusercontent.com/heutelbeck/sapl-policy-engine/master/sapl-node/kubernetes/sapl-node-baremetal.yml"
```

CMD/Bash (wget)

```
wget https://raw.githubusercontent.com/heutelbeck/sapl-policy-engine/master/sapl-node/kubernetes/sapl-node-baremetal.yml
```

PowerShell (Invoke-WebRequest)

```
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/heutelbeck/sapl-policy-engine/master/sapl-node/kubernetes/sapl-node-baremetal.yml" -OutFile "sapl-node-baremetal.yml"
```

Open the file `sapl-node-baremetal.yml` and modify the URL in the Ingress section.

```
  tls:
    - hosts:
        - sapl.exampleurl.com
      secretName: sapl.lt.local-tls
  rules:
    - host: sapl.exampleurl.com
```

Add the resource for the SAPL Node to the namespace.

```
kubectl apply -f sapl-node-baremetal.yml -n sapl-node
```

Create a secret using `htpasswd` and enter the password when prompted. To create htpasswd secrets on Windows, you may need to use additional software like XAMPP to run the `htpasswd` command.

```
htpasswd -c auth <username>
```

Then create the secret `basic-auth` and fill it with the data from the `auth` file created via htpasswd

```
kubectl create secret generic basic-auth --from-file=auth -n sapl-node
```

The service should be reachable under the URL defined in the Ingress section of the sapl-node-baremetal.yml <https://sapl.exampleurl.com/actuator/health>.

## Testing the SAPL Node

A sample application that can be configured to use this PDP server (remote PDP) can be found in the [demo-applications](https://github.com/heutelbeck/sapl-demos) project for the SAPL Policy Engine in the module `sapl-demo-remote`.

A self-signed certificate for localhost testing can be generated using the JDKs keytool, or [mkcert](https://github.com/FiloSottile/mkcert). Examples:

```
keytool -genkeypair -keystore keystore.p12 -dname "CN=localhost, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown" -keypass changeme -storepass changeme -keyalg RSA -alias netty -ext SAN=dns:localhost,ip:127.0.0.1
```

```
mkcert -pkcs12 -p12-file self-signed-cert.p12 localhost 127.0.0.1 ::1
```

## Custom Policy Information Points (PIPs) or Function Libraries

To support new attributes and functions, the matching libraries have to be deployed alongside the server application. One way to do so is to create your own server project and add the libraries to the dependencies of the application via Maven dependencies and to add the matching packages to the component scanning of Spring Boot and/or to provide matching configurations. Alternatively, the SAPL Node supports side-loading of external JARs.

**Note:** Changes only take effect upon restart of the server application.

### Side-loading of external JARs

To load a custom PIP, the PIP has to be built as a JAR, and all dependencies not already provided by the server have to be provided as JARs as well. Alternatively, the PIP can be packaged as a so-called "fat JAR" including all dependencies. This can be achieved using the [Maven Dependency Plugin](https://maven.apache.org/plugins/maven-dependency-plugin/), and an example for this approach can be found here: <https://github.com/heutelbeck/sapl-demos/tree/master/sapl-demo-extension>.

The SAPL Node will scan all packages below `io.sapl.server` for Spring beans or configurations providing PIPs or Function libraries at startup and load them automatically. Thus, the custom libraries must provide at least a matching spring configuration under this package.

#### Side-loading with a locally running server

When attempting to perform a side-loading of an external JAR, starting the server locally using `mvn spring-boot:run` or the corresponding IDE tools is not possible due to how Spring sets up class loading. Instead, you must directly execute the JAR of the SAPL Node and specify the path to the folder containing the external JAR with dependencies that need to be loaded from the page.

The method for executing the SAPL Node command varies depending on the operating system and shell being used. While the basic command is universal, the method for entering certain character strings differs between shells.

CMD

```
java -Dloader.path="c:\PATH TO JAR WITH DEPENDECIES FOLDER" -jar .\sapl-node-4.0.0.jar
```

PowerShell

```
java -D'loader.path'='c:\PATH TO JAR WITH DEPENDECIES FOLDER' -jar .\sapl-node-4.0.0.jar
```

Bash

```
java -Dloader.path="file:/PATH_TO_JAR_WITH_DEPENDECIES_FOLDER" -jar .\sapl-node-4.0.0.jar
```

#### Side-loading with a Docker container

The Docker container searches for JARs for side-loading in the `/pdp/data/lib` folder by default.  
To avoid moving files directly into the container, you can mount a volume under the path `/pdp/data`, as explained in the chapter [Running Directly as a Docker container](#running-directly-as-a-docker-container). If you create a folder named `lib` in this volume, you can store the JARs that need to be considered for side-loading in it.
