# SAPL Server LT - Lightweight Authorization Server.

This server is a lightweight headless Policy Decision Point (PDP) using the Streaming Attribute Policy Language (SAPL). The server provides authorization services through a HTTP and a RSocket API. To learn more about SAPL please visit the [SAPL Homepage](https://sapl.io).

The SAPL server LT monitors two directories for the PDP settings and SAPL documents, 
allowing for runtime updating of policies which will be reflected in decisions made for ongoing authorization 
subscriptions.

## Secure by Design

As a software product, SAPL Server LT follows secure information technology practices and multiple layers of defense following Secure By Design Principles (also see [CISA Secure By Design](https://www.cisa.gov/sites/default/files/2023-10/SecureByDesign_1025_508c.pdf).

Further, SAPL Server LT by itself is a tool for users to implement Secure by Design systems by implementing attribute-based access control (ABAC) into their own products and environments.

SAPL follows the **secure by default** principle. This means, that SAPL Server LT comes with a basic configuration, which has the most important security controls enabled. These settings are pre-configured in a way to make deployments compliant with Germany’s Federal Office for Information Security (BSI) [BSI Baseline Protection Compendium Edition 2022](https://www.bsi.bund.de/SharedDocs/Downloads/EN/BSI/Grundschutz/International/bsi_it_gs_comp_2022.pdf).

In the case of SAPL Server LT, this means that the delivered binary software packages (i.e., OCI Container Images and Java applications delivered as a JAR) do come with a very strict configuration that will require some initial added configuration for configuring authentication and TLS to be able to run the server. This documentation will explain these configuration steps in detail.

The security of the applications is a top priority in the development of SAPL. The SAPL project also embraces radical transparency and accountability. Security issues will be reported and shared using the GitHub advisory feature. For more details, refer to the [SAPL Security Policy](https://github.com/heutelbeck/sapl-policy-engine/blob/master/SECURITY.md).

## Installation

SAPL Server LT is distributed in two forms: an executable Java JAR file for the OpenJDK 17 (or newer) and an OCI container. Additionally, the full source code of the server is also available for building and running from source and auditing. 

### Java OpenJDK

#### Prerequisites

To run SAPL Server LT on a system, first ensure that [OpenJDK 17](https://openjdk.org/projects/jdk/17/) or newer is installed. There are several distributions available, such as [Eclipse Temurin](https://adoptium.net/de/temurin/releases/) suppling binaries for different platforms.

Ensure the Java executables are on the system path.

#### Download

To be done: setup download of release and snapshot vie GitHub packages.

#### Running the Server

**Note:** Without configuration the server will fail to start. This may appear as an inconvenience, however the reason for this is that the server comes with a **security by default** configuration which does not contain any well-known public secrets which may compromise the security of the deployment. Please refer to the section [Configuration](#configuration) for how to properly set up the server.

After configuration the server can be started using the command:

```shell
java -jar sapl-server-lt-3.0.0-SNAPSHOT.jar
```
 

### Docker

To be done.

### Running from Source

**Note:** Running from source is not intended for production environments. If doing so, **you accept the risk** of running with publicly known credentials and TLS certificates if you accidently use the development configuration of the server.

It is likely that you only need to run the server from the source if you are a contributor to the policy engine project. 

The source code includes a small development configuration of the server which contains some 
pre-configured credentials and a sels-signed certificate for setting up TLS.

#### Prerequisites

To build SAPL Server LT from source, first ensure that [OpenJDK 17](https://openjdk.org/projects/jdk/17/) or newer is installed. There are several distributions available, such as [Eclipse Temurin](https://adoptium.net/de/temurin/releases/) suppling binaries for different platforms.

SAPL uses Apache Maven as its build system, which must be installed and can be downloaded from the [Apache Maven Download Page](https://maven.apache.org/download.cgi).

Ensure the Java and Maven executables are on the system path.

#### Download the Source

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

If started from this folder, the application will start with a demonstration configuration located in  `sapl-policy-engine/sapl-server-lt` which sets up TLS with a self-signed certificate. Further, the PDP API will require basic authentication. For the demonstration configuration a default client is configured with the key (username) `xwuUaRD65G` and the secret (password) `3j_PK71bjy!hN3*xq.xZqveU)t5hKLR_`. This means that any API request expects the header `Authorization: Basic eHd1VWFSRDY1Rzozal9QSzcxYmp5IWhOMyp4cS54WnF2ZVUpdDVoS0xSXw==` or else access to the PDP API will be denied. The demonstration configuration also sets up the PDP to monitor the folder `~sapl`, i.e., the folder `sapl` in the users home folder, for PDP configuration and SAPL documents (i.e., policies).

#### Running the Server as a JAR

After the build concludes an executable JAR will be available in the folder `sapl-policy-engine/sapl-server-lt/target`. This JAR can be used in the same was as a downloaded SAPL Server LT binary, as descibed under [Java OpenJDK](#java-openjdk).

**Note:** If the JAR is executed from within the folder `sapl-policy-engine/sapl-server-lt` using the command `java -jar target/sapl-server-lt-3.0.0-SNAPSHOT.jar` the server will pick up the same demonstration configuration as described above.

## Configuration

SAPL Server LT requires some basic configuration to be able to start up. Minimally, authentication of client applications and TLS must be configured. SAPL Server LT is implemented using [Spring Boot](https://spring.io/projects/spring-boot/), which provides flexible tools for application configuration. Generally the principles explained in the Spring Boot documentation about [Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config) apply.

To summarize, the configuration is controlled by so-called properties which are key-value pairs provided to the application via `.properties` files,`.yml` files, or environment variables.
The exact way of providing these values to the application depends on the specific target environment.

As a starting point, you can start by putting an `application.yml` file in a folder `config` within the folder from where the server is started. An annotated example configuration can be found [here](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-server-lt/config).

**Note:** This example configuration is not intended for production. It contains secrets and certificates which are publicly known. Whenever you run a SAPL Server LT with this configuration you accept the resulting risks making the API publicly accessible via the provided credentials and that the server and its decisions cannot be properly authenticated by client applications because of the use of a publicly known self-signed TLS certificate.

### Managing Client applications.

SAPL Server LT supports several authentication schemes for client applications. By default none is activated and the server will deny to start up until at least one scheme is active.

#### Basic Authentication

SAPL Server LT allows for a single client to be authenticated using Basic Authentication [RFC 7617](https://datatracker.ietf.org/doc/html/rfc7617).

**Use case:** The developers of the clients prefer this authentication method. For example most HTTP client libraries support basic authentication out of the box

**Advantage:** The secret is not stored as plain text.

**Disadvantage:** Authentication per request/subscription is costly and introduces significant latency.

To activate this authentication scheme set the property `io.sapl.server-lt.allowBasicAuth` to `true`.

To add the client, set the property `io.sapl.server-lt.key` to an arbitrary name for the client `true`. And set the property `io.sapl.server-lt.secret` to an Argon2 [RFC 9106](https://datatracker.ietf.org/doc/rfc9106/) encoded secret. Please follow best practices also applicable for strong passwords before hashing the secret.

If you do not have a tool at hand for Argon2 password hashing, the SAPL Server LT binary can be used to generate a new reasonably secure random key and secret pair:

```shell
 java -jar sapl-server-lt-3.0.0-SNAPSHOT.jar -basicCredentials
```

This will print the pair to the console. Doing so will not start up an instance of the server.

Example Output:

```
17:14:34.571 [main] INFO io.sapl.server.lt.SAPLServerLTApplication -- Generating new Argon2 encoded secret...
17:14:34.636 [main] INFO io.sapl.server.lt.SAPLServerLTApplication -- Key             : IV73cRiudj
17:14:34.644 [main] INFO io.sapl.server.lt.SAPLServerLTApplication -- Secret Plaintext: FZdvjLKSu*Q'7+4!'zXIC694,a3sY9Sm
17:14:34.788 [main] INFO io.sapl.server.lt.SAPLServerLTApplication -- Secret Encoded  : $argon2id$v=19$m=16384,t=2,p=1$0shkuN10K05DsOPQVgm/Sw$FrWeTPKCfqkUcJ6u0DNKkaKDqkIZC7NefwzW5XYkoRA
```

In this case you would set `io.sapl.server-lt.key` to `IV73cRiudj` and `io.sapl.server-lt.secret` to `$argon2id$v=19$m=16384,t=2,p=1$0shkuN10K05DsOPQVgm/Sw$FrWeTPKCfqkUcJ6u0DNKkaKDqkIZC7NefwzW5XYkoRA`.

Also take not of the plain text of the secret as it will not be stored. Also make sure that the output of this tool is not visible in any logs and you properly clear your screen.

#### API Keys

In SAPL Server LT, API keys are a way of managing more than one client application with individual secrets.

Each clients is assigned to an individual API key and can authenticate by providing the following HTTP header: `API_KEY: <API KEY>`. For example: `API_KEY: phsNZdvQAFX9P2jgGzq9TrzUecQhsnHc`.

**Use Case:** Managing multiple clients with high traffic and low latency requirements.

**Advantage:** Low latency, no cryptography per request necessary.

**Disadvantage:** Credentials are stored in plain text on the server.  

By using this authentication scheme you **accept the risk** that anyone with read access to the configuration file will be able to access the authorization API and issue arbitrary authorization requests and subscriptions. Structured probing of the API may leak information depending on you PDP configuration and policies.

To activate this authentication scheme set the property `io.sapl.server-lt.allowApiKeyAuth` to `true`.

To add a client, create a random key with at least a length of 32 characters. And add it to the list under the property `io.sapl.server-lt.allowedApiKeys`. In case of a `.properties` file the list is comma separated. For `.yml` files you can use a YAML list notation, such as separate lines starting with a dash.

If you do not have a tool at hand to create a good random key, the SAPL Server LT binary can be used to generate a new reasonably secure random key:

```shell
 java -jar sapl-server-lt-3.0.0-SNAPSHOT.jar -newKey
```

This will print the pair to the console. Doing so will not start up an instance of the server.

Example Output:

```
17:33:04.474 [main] INFO io.sapl.server.lt.SAPLServerLTApplication -- Generating new API Key...
17:33:04.536 [main] INFO io.sapl.server.lt.SAPLServerLTApplication -- API key: h.a.2wOojs6K'qhHip6X$,1kU8vH')UK
```

#### JWT Token Authentication

#### No Authentication

### TLS Configuration

# Old Documentation



The PDP configuration for combining algorithm and environment variables is expected in a file `pdp.json`. 
All SAPL documents in the folder named `*.sapl` will be published to the PRP.

The server can be run locally via maven or by executing the JAR. 
Alternatively, a container image and configurations for deployment on Docker and/or Kubernetes is available.

## Local Execution

### Running the Server from Source

Disclaimer: It is likely that you only need to run the server from the source if you are a contributor to the policy engine project.

The source of the policy engine is found on the public [GitHub](https://github.com/) repository: <https://github.com/heutelbeck/sapl-policy-engine>.

First clone the repository:

```shell
git clone https://github.com/heutelbeck/sapl-policy-engine.git
```

Alternatively, download the current version as a source ZIP file: <https://github.com/heutelbeck/sapl-policy-engine/archive/master.zip>.

To run the server from the source, first, build the complete policy engine project from within the project's root folder (i.e., ```sapl-policy-engine```). The system requires maven and JDK 11 or later to be installed.
The build is triggered by issuing the command:

```shell
mvn clean install
```

Afterward, change to the folder ```sapl-policy-engine/sapl-server-lt``` and run the application:

```shell
mvn spring-boot:run
```

### Folder for Policies and PDP Configuration

If the default configuration has not been changed, the server will inspect and monitor the folder ```/sapl/policies```
in the current user's home directory for the PDP configuration `pdp.json` and SAPL documents ending with `*.sapl`. 
Changes will be directly reflected at runtime and for ongoing subscriptions.

> #### Note: Building a Docker Image
> 
> To build the docker image of the server application locally, you need to have docker installed on the build machine.
> The image build is triggered by activating the docker maven profile of the project. This should result with the image installed in your local docker repository. Example:
>
> ```shell
> mvn clean install -Pdocker
> ```

### Configuration of Locally Running Server

By default, the server will use a self-signed certificate and expose the PDP API under <https://localhost:8443/api/pdp>
To override this certificate, use the matching Spring Boot settings, e.g.:

```properties
server.port=8443
server.ssl.enabled=true
server.ssl.key-store-type=PKCS12
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=localhostpassword
server.ssl.key-alias=tomcat
```

API access requires "Basic Auth". Only one set of client credentials is implemented. 
The default client key (username) is: `YJidgyT2mfdkbmL`, and the default client secret (password) is: `Fa4zvYQdiwHZVXh`.
To override these settings, use the following properties:

```properties
io.sapl.server-lt.key=YJidgyT2mfdkbmL
io.sapl.server-lt.secret=$2a$10$PhobF71xYb0MK8KubWLB7e0Dpl2AfMiEUi9dkKTbFR4kkWABrbiyO
```

Please note that the secret has to be BCrypt encoded. For testing, use something like: <https://bcrypt-generator.com/>

The server is implemented using Spring Boot. Thus, there are a number of ways to configure the 
application. 
Please consult the matching chapter of the [Spring Boot documentation](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#boot-features-external-config).

## Testing the Server

A sample application that can be configured to use this PDP server (remote PDP) can be found
in the [demo-applications](https://github.com/heutelbeck/sapl-demos) project for the SAPL
Policy Engine in the module `sapl-demo-reactive`.

A self-signed certificate for localhost testing can be generated using the JDKs keytool, or [mkcert](https://github.com/FiloSottile/mkcert). Examples:

```shell
keytool -genkeypair -keystore keystore.p12 -dname "CN=localhost, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown" -keypass changeme -storepass changeme -keyalg RSA -alias netty -ext SAN=dns:localhost,ip:127.0.0.1
```

```shell
mkcert -pkcs12 -p12-file self-signed-cert.p12 localhost 127.0.0.1 ::1
```

## Containerized Cloud Deployment

The server application is available as container image. Here, the server is not configured with any TLS 
security or authentication. It is expected that in deployment this responsibility is delegated to the 
infrastructure, e.g., a matching Kubernetes Ingress.

### Running Directly as a Docker Container

In order to run the server locally for testing in an environment like Docker Desktop, you can run the current image as follows:

```shell
docker run -d --name sapl-server-lt -p 8080:8080 --mount source=sapl-server-lt,target=/pdp/data ghcr.io/heutelbeck/sapl-server-lt:3.0.0-snapshot
```


```shell
docker run -d --name sapl-server-lt -p 8080:8080 -v c:\sapl\policies:/pdp/data ghcr.io/heutelbeck/sapl-server-lt:3.0.0-snapshot
```

Afterward you can check if the service is online under: http://localhost:8080/actuator/health.

Also, a volume is created for persisting the PDP configuration and policies.

Depending on your host OS and virtualization environment, these volumes may be located at:

* Docker Desktop on Windows WSL2: `\\wsl$\docker-desktop-data\version-pack-data\community\docker\volumes\sapl-server-lt\_data`
* Docker Desktop on Windows Hyper-V: `C:\Users\Public\Documents\Hyper-V\Virtual hard disks\sapl-server-lt\_data`
* Docker on Ubuntu: `/var/lib/docker/volumes/sapl-server-lt/_data`
* Docker Desktop on Windows with shared folder: `c:\sapl\policies` (or as changed)

### Running on Kubernetes

This section will describe the deployment on a bare metal Kubernetes installation which has Port 80 and 443 exposed to the Internet 
as well as Desktop Docker on Windows and will use the Kubernetes nginx-ingress-controller as well as cert-manager to manage the Let's Encrypt certificates (Only if Ports are exposed to the Internet so Let's Encrypt can access the URL)

#### Prerequisites

Installed Kubernetes v1.23 
Install NGINX Ingress Controller according to https://kubernetes.github.io/ingress-nginx/deploy/

```shell
helm upgrade --install ingress-nginx ingress-nginx --repo https://kubernetes.github.io/ingress-nginx --namespace ingress-nginx --create-namespace --set controller.hostNetwork=true,controller.kind=DaemonSet
```

Install Cert-Manager according to https://cert-manager.io/docs/installation/kubernetes/ (Only for Use with exposed Ports and matching DNS Entries)

```shell
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.7.2/cert-manager.yaml
```

Change the Email address in the Clusterissuer.yaml (Line email: user@email.com)

```shell
wget https://raw.githubusercontent.com/heutelbeck/sapl-policy-engine/master/sapl-server-lt/kubernetes/clusterissuer.yml
kubectl apply -f clusterissuer.yml -n your-namespace
```

#### Bare Metal Kubernetes

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

Create the secret with htpasswd, you will be asked to enter the password

```shell
htpasswd -c auth Username
kubectl create secret generic basic-auth --from-file=auth -n sapl-server-lt
```

The service should be reachable under the URL defined in the Ingress section of the sapl-server-lt-baremetal.yml <https://sapl.exampleurl.com/actuator/health>.

#### Docker Desktop Kubernetes

We are still working on the persistent volume solution for the Docker Desktop Kubernetes installation with WSL2 on Windows. 

Apply the sapl.server-lt.yml file 

```shell
kubectl create namespace sapl-server-lt
kubectl apply -f https://raw.githubusercontent.com/heutelbeck/sapl-policy-engine/master/sapl-server-lt/kubernetes/sapl-server-lt.yml -n sapl-server-lt
```

The URL is sapl.lt.local and has to be added to the hosts file (which is located in ```%windir%\system32\drivers\etc```) add the Line 

```
127.0.0.1       sapl.lt.local
```

Create the secret with htpasswd. You will be asked to enter the password.

```shell
htpasswd -c auth Username
kubectl create secret generic basic-auth --from-file=auth -n sapl-server-lt
```

In the meantime, the files are volatile but can be accessed with

```shell
kubectl exec sapl-server-lt-d5d65dd6b-fz29g --stdin --tty -- /bin/sh -n sapl-server-lt
```

You have to use the actual pod name, which can be listed with the command:

```shell
kubectl get pods -n sapl-server-lt
```

At the time of writing, Kubernetes on Docker Desktop has technical limitations mounting volumes via hostPath under WSL2: <https://github.com/docker/for-win/issues/5325>.  

#### Kubernetes Troubleshooting

The service is defined as ClusterIP but can be changed to use NodePort for testing purposes (Line type: ClusterIP to type: NodePort)

```shell
kubectl edit service sapl-server-lt -n sapl-server-lt
```
 
If the Website can't be reached, try installing the NGINX Ingress Controller using helm with the flag ```--set controller.hostNetwork=true,controller.kind=DaemonSet```

## Custom Policy Information Points (PIPs) or Function Libraries

To support new attributes and functions, the matching libraries have to be deployed alongside the server application. One way to do so is to create your own server project and add the libraries to the dependencies of the application via Maven dependencies and to add the matching packages to the component scanning of Spring Boot and/or to provide matching configurations. Alternatively, the SAPL Server LT supports side-loading of external JARs. 

To load a custom PIP, the PIP has to be built as a JAR, and all dependencies not already provided by the server have to be provided as JARs as well. Alternatively, the PIP can be packaged as a so-called "fat JAR" including all dependencies. This can be achieved using the (Maven Dependency Plugin)[https://maven.apache.org/plugins/maven-dependency-plugin/], and an example for this approach can be found here: <https://github.com/heutelbeck/sapl-demos/tree/master/sapl-demo-extension>.

The SAPL Server LT will scan all packages below ```io.sapl.server``` for Spring beans or configurations providing PIPs or Function libraries at startup and load them automatically. Thus, the custom libraries must provide at least a matching spring configuration under this package.

The JAR files are to be put into the folder `/pdp/data/lib` in the directory where policies are stored. Changes only take effect upon restart of the server application. To change the folder, overwrite the property `loader.path`.
