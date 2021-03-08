# SAPL Server CE

This is a lightweight PDP server storing policies in a MariaDB and offering a simple WebUI for policy and PDP administration.

The server can be run locally via maven or by executing the JAR. 
Alternatively, a container image and configurations for deployment 
on Docker and/or Kubernetes is available.

## Local Execution

### Running from Pre-Build JAR

Download the latest build from [here](https://github.com/heutelbeck/packages/packages/612742).
To run the server, you need JRE 11 or later installed. 

#### Pre-Configured Demo Server

For running the server locally two modes are available. The default mode is a demonstration mode with a self-signed TSL certificate, some demo credentials and a H2 in-memory database. By default, the database is configured to persist data under ```~/sapl/db```.

Run the server:

```
java -jar sapl-server-ce-2.0.0-SNAPSHOT.jar
```

You can access the servers UI under [https://localhost:8443/](https://localhost:8443/)

To login use the demo credentials:

* Username: demo
* Password: demo

#### Run Server with Persistence in MariaDB

The JAR comes with a second demo profile to use a local MariaDB for persistence: 

```
java -Dspring.profiles.active=mariadb -jar sapl-server-ce-2.0.0-SNAPSHOT.jar
```

The login for the Web UI is the same as for the demo profile. It expects MariaDB to run on localhost and 

The host configuration and credentials for both the MariaDB and the Web UI can be fully customized by overriding the respective configuration properties. 

The properties: <https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-server-ce/src/main/resources/application-local.yml>
How to override: <https://docs.spring.io/spring-boot/docs/2.1.9.RELEASE/reference/html/boot-features-external-config.html>

For testing you can use a public bcrypt hashing tool (e.g., <https://bcrypt-generator.com/>) to generate the password configuration for the user. Make sure to prepend `{bcrypt}` to the hash.   


### Running the Server from Source

Disclaimer: It is likely that you only need to run the server from source, if you are a contributor to the policy engine project.

The source of the policy engine is found on the public [GitHub](https://github.com/) repository: <https://github.com/heutelbeck/sapl-policy-engine>.

First clone the repository:

```shell
git clone https://github.com/heutelbeck/sapl-policy-engine.git
```

Alternatively download the current version as a source ZIP file: <https://github.com/heutelbeck/sapl-policy-engine/archive/master.zip>.

To run the server from source, first build the complete policy engine project from within the projects root folder
(i.e., ```sapl-policy-engine```). The system requires maven and JDK 11 or later to be installed.
The build is triggered by issuing the command:

```shell
mvn clean install
```

Afterwards, change to the folder ```sapl-policy-engine/sapl-server-ce``` and run the application:

```shell
mvn spring-boot:run
```

The server will startup in demo mode.

> #### Note: Building a Docker Image
> 
> To build the docker image of the server application locally, you need to have docker installed on the build machine.
> The image build is triggered by activating the docker maven profile of the project. This should result with the image installed in your local docker repository. Example:
>
> ```shell
> mvn clean install -P docker,production,mariadb,!h2
> ```

### Using the PDP

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

API access requires "Basic Auth". Use the Web UI to add client credentials. 


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

TBD
