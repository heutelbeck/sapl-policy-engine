# SAPL Server LT - Lightweight Authorization Server

This server is a lightweight Policy Decision Point (PDP) that uses the Streaming Attribute Policy Language (SAPL) and provides authorization services through an HTTP and RSocket API. SAPL is further explained on the [SAPL home page](https://sapl.io/).

The SAPL server LT monitors two directories for PDP settings and SAPL documents, allowing for runtime updating of policies that are reflected in decisions made for ongoing authorization subscriptions.

## Introduction

### Aim of the README

This README aims to describe the commissioning and correct operation of SAPL Server LT, providing an easy introduction to its use. Additionally, it presents important information security aspects of SAPL Server LT in a transparent manner.

### Target group

This documentation focuses on the operator and the knowledge required for operating. However, technical aspects of interest for further development of the software are also covered.

### Secure by Design

SAPL Server LT is a software product that follows secure information technology practices and multiple layers of defense based on Secure By Design Principles (also see [CISA Secure By Design](https://www.cisa.gov/sites/default/files/2023-10/SecureByDesign_1025_508c.pdf)).

It is a tool for users to implement Secure by Design systems by incorporating attribute-based access control (ABAC) into their own products and environments.

SAPL adheres to the secure by default principle. SAPL Server LT comes with a pre-configured basic setup that includes the essential security controls to ensure compliance with Germany's Federal Office for Information Security (BSI) [BSI Baseline Protection Compendium Edition 2022](https://www.bsi.bund.de/SharedDocs/Downloads/EN/BSI/Grundschutz/International/bsi_it_gs_comp_2022.pdf).

For SAPL Server LT, the binary software packages (OCI Container Images and Java applications delivered as a JAR) come with a strict configuration. Additional configuration is required for authentication and TLS to run the server. This documentation explains these configuration steps in detail.

Application security is a top priority in the development of SAPL. The SAPL project also embraces radical transparency and accountability. Security issues are reported and shared using the GitHub advisory feature. For more details, see the [SAPL Security Policy](https://github.com/heutelbeck/sapl-policy-engine/blob/master/SECURITY.md).

## System requirements

Requirements for local installation.

- min. Java Development Kit 17 and Java compatible Operating System
- min. Maven 3.9 

## Implementation of the Server LT

SAPL Server LT comes in two forms: an executable Java JAR file for OpenJDK 17 (or later) and an OCI container. The server's full source code is also available for building, running from source, and auditing.

### Java OpenJDK

#### Prerequisites

Before running SAPL Server LT on your system, make sure that you have installed [OpenJDK 17](https://openjdk.org/projects/jdk/17/) or a newer version. [Eclipse Temurin](https://adoptium.net/de/temurin/releases/) is one of the available distributions that provides binaries for different platforms.

Ensure that the Java executables are added to the system path.

#### Download

To be done: setup download of release and snapshot via GitHub packages.

#### Server Execution

**Note:** Failure to configure the server will prevent it from starting. This may seem inconvenient, but it is necessary for security reasons. The default configuration does not include any well-known public secrets that could compromise the deployment's security. Please refer to the [Configuration](https://nextcloud.ftk.de/index.php/apps/files?dir=/2023%20-%20Fachpraktikum/Teams/IT_Grundschutz/Dokumentation/GitHub_Readmes/SAPL-Policy-Engine&fileid=581409#configuration) section for proper server setup instructions.

Once configured, start the server using the following command:

```shell
java -jar sapl-server-lt-3.0.0-SNAPSHOT.jar
```

### Running from Source

**Note:** Running from source is not intended for production environments. If doing so, **you accept the risk** of running with publicly known credentials and TLS certificates if you accidently use the development configuration of the server.

It is likely that you only need to run the server from the source if you are a contributor to the policy engine project.

The source code includes a small development configuration of the server which contains some pre-configured credentials and a self-signed certificate for setting up TLS.

#### Prerequisites

To build SAPL Server LT from source, first ensure that [OpenJDK 17](https://openjdk.org/projects/jdk/17/) or newer is installed. There are several distributions available, such as [Eclipse Temurin](https://adoptium.net/de/temurin/releases/) suppling binaries for different platforms.

SAPL uses Apache Maven as its build system, which must be installed and can be downloaded from the [Apache Maven Download Page](https://maven.apache.org/download.cgi).

Ensure the Java and Maven executables are on the system path.

#### Download

The source of the policy engine is found on the public [GitHub](https://github.com/) repository: <https://github.com/heutelbeck/sapl-policy-engine>.

You can either download the source as a ZIP file and unzip the archive, or clone the repository using git:

```shell
git clone https://github.com/heutelbeck/sapl-policy-engine.git
```

#### Build the Engine and Server locally

To build the engine including the server application go to the `sapl-policy-engine`  folder and execute the following command:

```shell
mvn install
```

After a few minutes the complete engine and server should be built. There are two options to run the server after the build concluded.

#### Running the Server using Maven

Change the current directory to `sapl-policy-engine/sapl-server-lt` and execute the following command:

```shell
mvn spring-boot:run
```

If started from this folder, the application will start with a demonstration configuration located in  `sapl-policy-engine/sapl-server-lt` which sets up TLS with a self-signed certificate. Further, the PDP API will require basic authentication. For the demonstration configuration a default client is configured with the key (username) `xwuUaRD65G` and the secret (password) `3j_PK71bjy!hN3*xq.xZqveU)t5hKLR_`. This means that any API request expects the header `Authorization: Basic eHd1VWFSRDY1Rzozal9QSzcxYmp5IWhOMyp4cS54WnF2ZVUpdDVoS0xSXw==` or else access to the PDP API will be denied. The demonstration configuration also sets up the PDP to monitor the folder `~/sapl`, i.e., the folder `sapl` in the users home folder, for PDP configuration and SAPL documents (i.e., policies).

#### Running the Server as a JAR

After the build concludes an executable JAR will be available in the folder `sapl-policy-engine/sapl-server-lt/target`. This JAR can be used in the same way as a downloaded SAPL Server LT binary, as descibed under [Java OpenJDK](#java-openjdk).

**Note:** If the JAR is executed from within the folder `sapl-policy-engine/sapl-server-lt` using the command `java -jar target/sapl-server-lt-3.0.0-SNAPSHOT.jar` the server will pick up the same demonstration configuration as described above.

### Kubernetes/Docker

The server application is available as container image. Here, the server is not configured with any TLS security or authentication. It is expected that in deployment this responsibility is delegated to the infrastructure, e.g., a matching Kubernetes Ingress.

#### Running Directly as a Docker Container

In order to run the server locally for testing in an environment like Docker Desktop, you can run the current image as follows:

```shell
docker run -d --name sapl-server-lt -p 8080:8080 --mount source=sapl-server-lt,target=/pdp/data ghcr.io/heutelbeck/sapl-server-lt:3.0.0-snapshot
```

```shell
docker run -d --name sapl-server-lt -p 8080:8080 -v c:\sapl\policies:/pdp/data ghcr.io/heutelbeck/sapl-server-lt:3.0.0-snapshot
```

If your server does not want to start, you will most likely have to specify a keystore. To do this, it is recommended that you first create a new Docker volume and store the keystore there. In the following example, we have created a volume 'config', which is mounted on /pdp/data within the container. We use the start parameter SPRING_CONFIG_ADDITIONAL_LOCATION to inform the application that an application.yml should also be searched for in this folder. The application.yml under /pdp/data also contains the configuration for your keystore:

```
docker run -d --name sapl-server-lt -p 8080:8080 -v config:/pdp/data -e SPRING_CONFIG_ADDITIONAL_LOCATION=file:/pdp/data/ ghcr.io/heutelbeck/sapl-server-lt:3.0.0-snapshot
```

Afterward you can check if the service is online under: http://localhost:8080/actuator/health.

Depending on your host OS and virtualization environment, these volumes may be located at:

- Docker Desktop on Windows WSL2: `\\wsl$\docker-desktop-data\version-pack-data\community\docker\volumes\sapl-server-lt\_data`
- Docker Desktop on Windows Hyper-V: `C:\Users\Public\Documents\Hyper-V\Virtual hard disks\sapl-server-lt\_data`
- Docker on Linux: `/var/lib/docker/volumes/sapl-server-lt/_data`
- Docker Desktop on Windows with shared folder: `c:\sapl\policies` (or as changed)

#### Running on Kubernetes

This section will describe the deployment on a bare metal Kubernetes installation which has Port 80 and 443 exposed to the Internet as well as Desktop Docker on Windows and will use the Kubernetes nginx-ingress-controller as well as cert-manager to manage the Let's Encrypt certificates (Only if Ports are exposed to the Internet so Let's Encrypt can access the URL)

##### Prerequisites

Installed Kubernetes v1.23 Install NGINX Ingress Controller according to https://kubernetes.github.io/ingress-nginx/deploy/

```shell
helm upgrade --install ingress-nginx ingress-nginx --repo https://kubernetes.github.io/ingress-nginx --namespace ingress-nginx --create-namespace --set controller.hostNetwork=true,controller.kind=DaemonSet
```

Install Cert-Manager according to https://cert-manager.io/docs/installation/kubernetes/ (Only for Use with exposed Ports and matching DNS Entries):

```shell
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.7.2/cert-manager.yaml
```

Change the Email address in the Clusterissuer.yaml (Line email: user@email.com):

```shell
wget https://raw.githubusercontent.com/heutelbeck/sapl-policy-engine/master/sapl-server-lt/kubernetes/clusterissuer.yml
kubectl apply -f clusterissuer.yml -n your-namespace
```

##### Bare Metal Kubernetes

This section assumes that the Kubernetes is installed on a Linux OS i.e. Ubuntu

First apply the Persistent Volume yaml

```shell
kubectl create namespace sapl-server-lt
kubectl apply -f https://raw.githubusercontent.com/heutelbeck/sapl-policy-engine/master/sapl-server-lt/kubernetes/sapl-server-lt-pv.yml -n sapl-server-lt
```

Then download the Baremetal yaml file

```shell
wget https://raw.githubusercontent.com/heutelbeck/sapl-policy-engine/master/sapl-server-lt/kubernetes/sapl-server-lt-baremetal.yml
```

change the URL in the Ingress section

```
  tls:
    - hosts:
        - sapl.exampleurl.com
      secretName: sapl.lt.local-tls
  rules:
    - host: sapl.exampleurl.com
```

then apply the yaml file

```shell
kubectl apply -f sapl-server-lt-baremetal.yml -n sapl-server-lt
```

Create the secret with htpasswd, you will be asked to enter the password:

```shell
htpasswd -c auth Username
kubectl create secret generic basic-auth --from-file=auth -n sapl-server-lt
```

The service should be reachable under the URL defined in the Ingress section of the sapl-server-lt-baremetal.yml <https://sapl.exampleurl.com/actuator/health>.

## Configuration

To start up SAPL Server LT, basic configuration is required. This includes configuring [client application authentication](#Managing%20Client%20Applications) and [TLS](#TLS%20Configuration). SAPL Server LT is implemented using [Spring Boot](https://spring.io/projects/spring-boot/), which offers flexible tools for application configuration. The Spring Boot documentation for [Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config) provides helpful guidelines to follow. It is important to note the order in which configurations are loaded and can overwrite each other.

In summary, the application's configuration is controlled by key-value pairs known as properties. These properties are provided to the application through `.properties` files, `.yml` files, or environment variables. The method of providing these values depends on the specific target environment.

As a starting point, you can start by putting an `application.yml` file in a folder `config` within the folder from where the server is started. An annotated example configuration can be found [here](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-server-lt/config).

**Note:** This example configuration is not intended for production. It contains secrets and certificates which are publicly known. Whenever you run a SAPL Server LT with this configuration **you** **accept the resulting risks** making the API publicly accessible via the provided credentials and that the server and its decisions cannot be properly authenticated by client applications because of the use of a publicly known self-signed TLS certificate.

### Configure the embedded PDP

#### Configuration Type

The `io.sapl.pdp.embedded.pdp-config-type` property allows you to set the source for the [configuration](#Configuration%20Path) and [policies](#Policy%20Storage%20Location). You can choose between the values `RESOURCES` and `FILESYSTEM`. This property defaults to the `RESOURCES` value if no configuration is provided. The `FILESYSTEM` value is preconfigured by default.

If the property is set to `RESOURCES`, the system will load a predetermined set of documents and `pdp.json` from the bundled resource during runtime. These resources cannot be updated while the system is running.

If the property is set to `FILESYSTEM`, the system will monitor directories for documents and configuration changes. Any updates made to the documents and configuration during runtime will be automatically reflected in existing subscriptions and new decisions will be sent if necessary.

#### Configuration File

The configuration file for the embedded PDP of the SAPL Server LT must be named `pdp.json`. This file allows for the definition of the combining algorithm and variables in JSON format.

The combining algorithm determines how the PDP determines the validity of multiple SAPL policies for a given subscription. The available options for the combining algorithm are: `DENY_UNLESS_PERMIT`, `PERMIT_UNLESS_DENY`, `ONLY_ONE_APPLICABLE`, `DENY_OVERRIDES`, and `PERMIT_OVERRIDES`. Refer to the [documentation](https://sapl.io/docs/latest/sapl-reference.html#combining-algorithm-2) for more information on the combining algorithm.

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

The combining algorithm for the embedded PDP of the SAPL Server LT is set to `DENY_UNLESS_PERMIT`. The variable `person` can be used in all policies without needing to declare it explicitly, thanks to its declaration in `pdp.json`.

```
policy "myfirstpolicy"
permit
	subject == person
```

#### Configuration Path

The property `io.sapl.pdp.embedded.config-path` specifies the folder path where the `pdp.json` file is saved. If [pdpConfigType](#Configuration%20Type) is set to `RESOURCES`, the root of the context path is `/`. If [pdpConfigType](#Configuration%20Type) is set to `FILESYSTEM`, it must be a valid path in the system's file system.

**Note:** When specifying a path, ensure that the backslash character `\` is replaced with the forward slash character `/`.

The server monitors and searches the `/policies` directory for the `pdp.json` file without requiring any additional configuration. In the preconfigured example, the server monitors the path `~/sapl`, which refers to the `sapl` folder in the current user's home directory, for the `pdp.json` file. Any changes made to the file are immediately applied at runtime and for current subscriptions.

### Managing SAPL Policies

#### Policy Storage Location

The property `io.sapl.pdp.embedded.policies-path` specifies the folder path where the `*.sapl` documents are stored. If [pdpConfigType](#Configuration%20Type) is set to `RESOURCES`, the root of the context path is `/`. If [pdpConfigType](#Configuration%20Type) is set to `FILESYSTEM`, it must be a valid path in the system's file system.

**Note:** When specifying a path, ensure that the backslash character `\` is replaced with the forward slash character `/`.

The server monitors and searches the `/policies` directory for SAPL policies without requiring any configuration. SAPL policies are documents that end with `*.sapl`. In the preconfigured example, the server monitors the path `~/sapl`, which refers to the `sapl` folder in the current user's home directory, for SAPL documents. Any changes made are immediately taken into account at runtime and for current subscriptions.

For test purposes, a simple policy `myfirstpolicy.sapl` can be created in the configured folder. The following policy always returns a permit as the decision:

```
policy "myfirstpolicy"
permit
```

To learn more about writing SAPL policies, refer to the [documentation](https://sapl.io/docs/latest/sapl-reference.html#the-sapl-policy-language).

#### Policy Indexing

The `io.sapl.pdp.embedded.index` property determines the indexing algorithm for the SAPL policies managed by the embedded PDP of the SAPL Server LT. The available options for this property are `NAIVE` and `CANONICAL`, with `NAIVE` being the default value.

Select the `NAIVE` value for systems with few documents and the `CANONICAL` value for systems  
with many documents. The `CANONICAL` algorithm is more time-consuming for initialization and updating, but it significantly reduces retrieval time.

#### Policy File Renaming

If an existing policy is to be changed, the following procedure is recommended:

1. create a new file that is semantically equivalent to the policy to be changed. The policy name must be changed in the new policy.
2. delete the old policy file
3. change the policy name of the new policy

**Why should I proceed in this way?** It is difficult to handle file renaming as an atomic process. The system deletes the policy at short notice and can make incorrect decisions during this period. The recommended procedure avoids potential errors by creating a new policy before deleting the old file.

### Managing Client Applications

SAPL Server LT supports several authentication schemes for client applications. By default none is activated and the server will deny to start up until at least one scheme is active. Multiple authentication mechanisms can be enabled simultaneously.

#### No Authentication

The server does not require authentication before responding to requests or subscriptions.

**Use case:** For testing, development, and deployments, API authentication is delegated to API gateways, Kubernetes ingress services, or similar situations.

To activate this authentication scheme set the property `io.sapl.server-lt.allowNoAuth` to `true`.

**Note:** Do not use this option in production if access is not properly secured.  This option may open the server to malicious probing and exfiltration attempts through the authorization endpoints, potentially resulting in unauthorized access to your organization's data, depending on your policies.

#### Basic Authentication

SAPL Server LT allows for a single client to be authenticated using Basic Authentication [RFC 7617](https://datatracker.ietf.org/doc/html/rfc7617).

**Use case:** The developers of the clients prefer this authentication method. For example most HTTP client libraries support basic authentication out of the box.

**Advantage:** The secret is not stored as plain text.

**Disadvantage:** Authentication per request/subscription is costly and introduces significant latency.

To activate this authentication scheme set the property `io.sapl.server-lt.allowBasicAuth` to `true`.

To add the client, set the property `io.sapl.server-lt.key` to an arbitrary name for the client `true`. And set the property `io.sapl.server-lt.secret` to an Argon2 [RFC 9106](https://datatracker.ietf.org/doc/rfc9106/) encoded secret. SAPL Server LT does not allow for the assignment of different keys with associated secrets. Please follow best practices also applicable for strong passwords before hashing the secret.

If you do not have a tool at hand for Argon2 password hashing, the SAPL Server LT binary can be used to generate a new reasonably secure random key and secret pair:

```shell
 java -jar sapl-server-lt-3.0.0-SNAPSHOT.jar -basicCredentials
```

This will print the pair to the console. Doing so will not start up an instance of the server.

Example Output:

```java
17:14:34.571 [main] INFO io.sapl.server.lt.SAPLServerLTApplication -- Generating new Argon2 encoded secret...
17:14:34.636 [main] INFO io.sapl.server.lt.SAPLServerLTApplication -- Key             : IV73cRiudj
17:14:34.644 [main] INFO io.sapl.server.lt.SAPLServerLTApplication -- Secret Plaintext: FZdvjLKSu*Q'7+4!'zXIC694,a3sY9Sm
17:14:34.788 [main] INFO io.sapl.server.lt.SAPLServerLTApplication -- Secret Encoded  : $argon2id$v=19$m=16384,t=2,p=1$0shkuN10K05DsOPQVgm/Sw$FrWeTPKCfqkUcJ6u0DNKkaKDqkIZC7NefwzW5XYkoRA
```

In this case you would set `io.sapl.server-lt.key` to `IV73cRiudj` and `io.sapl.server-lt.secret` to `$argon2id$v=19$m=16384,t=2,p=1$0shkuN10K05DsOPQVgm/Sw$FrWeTPKCfqkUcJ6u0DNKkaKDqkIZC7NefwzW5XYkoRA`.

Also take note of the plain text of the secret as it will not be stored. Also make sure that the output of this tool is not visible in any logs and you properly clear your screen.

#### API Keys

In SAPL Server LT, API keys are a way of managing more than one client application with individual secrets.

Each clients is assigned to an individual API key and can authenticate by providing the following HTTP header: `API_KEY: <API KEY>`. For example: `API_KEY: phsNZdvQAFX9P2jgGzq9TrzUecQhsnHc`.

**Use Case:** Managing multiple clients with high traffic and low latency requirements.

**Advantage:** Low latency, no cryptography per request necessary.

**Disadvantage:** Credentials are stored in plain text on the server.

By using this authentication scheme you **accept the risk** that anyone with read access to the configuration file will be able to access the authorization API and issue arbitrary authorization requests and subscriptions. Structured probing of the API may leak information depending on your PDP configuration and policies.

To activate this authentication scheme set the property `io.sapl.server-lt.allowApiKeyAuth` to `true`.

To add a client, create a random key with at least a length of 32 characters. And add it to the list under the property `io.sapl.server-lt.allowedApiKeys`. In case of a `.properties` file the list is comma separated. For `.yml` files you can use a YAML list notation, such as separate lines starting with a dash.

If you do not have a tool at hand to create a good random key, the SAPL Server LT binary can be used to generate a new reasonably secure random key:

```shell
 java -jar sapl-server-lt-3.0.0-SNAPSHOT.jar -newKey
```

This will print the pair to the console. Doing so will not start up an instance of the server.

Example Output:

```java
17:33:04.474 [main] INFO io.sapl.server.lt.SAPLServerLTApplication -- Generating new API Key...
17:33:04.536 [main] INFO io.sapl.server.lt.SAPLServerLTApplication -- API key: h.a.2wOojs6K'qhHip6X$,1kU8vH')UK
```

The new API key can now be added in the configuration. If you want to test whether access via the API key works, you can use the following command:

```json
curl -k -H "API_KEY: MY-API-Key" -H "Content-Type: application/json" -d '{"subject":"WILLI","action":"read","resource":"something"}' https://localhost:8443/api/pdp/decide
```

You should receive something like this:

```json
data:{"decision":"NOT_APPLICABLE"}
```

If you want to check whether your policies are working, you can create a test policy as described under *Managing SAPL policies* and check the server's decision. With the policy mentioned in the section, the following response should now appear:

```json
data:{"decision":"PERMIT"}
```

#### JWT Token Authentication

To use JSON Web Tokens (JWT), you must activate the following properties `allowOauth2Auth: true`. In addition, a valid URI to an identity provider (IDP) must be specified under `spring.security.oauth2.resourceserver.jwt.issuer-uri: <URI>`

For example, the URI for a local Keycloak instance with the realm 'SAPL' must look like this: `http://localhost:9000/realms/SAPL`

You can check whether authentication with JWT works by including the bearer token from the oauth2 server in the HTTP header. To do this, you must first receive a token from your oauth2 server and then execute the following command:

```bash
curl -k -v -X POST -H "Authorisation: Bearer <token>" -H "Content-Type: application/json" -d '{"subject": "WILLI", "action": "read", "resource": "something"}' https://localhost:8443/api/pdp/decide
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

```j
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

For specific recommondations please have a look at the [BSI technical guidelines](https://www.bsi.bund.de/DE/Themen/Unternehmen-und-Organisationen/Standards-und-Zertifizierung/Technische-Richtlinien/TR-nach-Thema-sortiert/tr02102/tr-02102.html).

### Logging Configuration

The SAPL Server LT uses the Simple Logging Facade for Java (SLF4J) and the logging framework Logback for logging. The annotation types for SLF4J are imported via Lombok.

Configuration of logging can be done in the `application.yml` file. Additional logging configurations can be found in the [Spring documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.logging).

#### Logging Level

To configure the logging level for the desired classes, use the `logging.level` property and select one of the following levels:

- `TRACE`:  Detailed information for troubleshooting and debugging during development.
- `DEBUG`: Logs useful information for development and debugging purposes.
- `INFO`: These messages provide updates on the application's progress and significant events.
- `WARN`: Records issues that do not result in errors.
- `ERROR`: The application's errors or exceptions that occurred during execution are logged.

The logging level for the `io.sapl` and `org.springframework` classes is set to `INFO` by default.

To enhance the performance of the SAPL Server LT, consider adjusting the logging level to `WARN`. This will reduce the number of log messages and improve latency. However, it is important to note that decisions made by the PDP will still be logged with the `INFO` level.

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

## Testing the SAPL Server LT

A sample application that can be configured to use this PDP server (remote PDP) can be found in the [demo-applications](https://github.com/heutelbeck/sapl-demos) project for the SAPL Policy Engine in the module `sapl-demo-remote`.

A self-signed certificate for localhost testing can be generated using the JDKs keytool, or [mkcert](https://github.com/FiloSottile/mkcert). Examples:

```shell
keytool -genkeypair -keystore keystore.p12 -dname "CN=localhost, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown" -keypass changeme -storepass changeme -keyalg RSA -alias netty -ext SAN=dns:localhost,ip:127.0.0.1
```

```shell
mkcert -pkcs12 -p12-file self-signed-cert.p12 localhost 127.0.0.1 ::1
```

## Custom Policy Information Points (PIPs) or Function Libraries

To support new attributes and functions, the matching libraries have to be deployed alongside the server application. One way to do so is to create your own server project and add the libraries to the dependencies of the application via Maven dependencies and to add the matching packages to the component scanning of Spring Boot and/or to provide matching configurations. Alternatively, the SAPL Server LT supports side-loading of external JARs.

To load a custom PIP, the PIP has to be built as a JAR, and all dependencies not already provided by the server have to be provided as JARs as well. Alternatively, the PIP can be packaged as a so-called "fat JAR" including all dependencies. This can be achieved using the (Maven Dependency Plugin)[https://maven.apache.org/plugins/maven-dependency-plugin/], and an example for this approach can be found here: <https://github.com/heutelbeck/sapl-demos/tree/master/sapl-demo-extension>.

The SAPL Server LT will scan all packages below `io.sapl.server` for Spring beans or configurations providing PIPs or Function libraries at startup and load them automatically. Thus, the custom libraries must provide at least a matching spring configuration under this package.

The JAR files are to be put into the folder `/pdp/data/lib` in the directory where policies are stored. Changes only take effect upon restart of the server application. To change the folder, overwrite the property `loader.path`.
