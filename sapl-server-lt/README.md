# SAPL Server LT - lightweight authorization server.

This is a lightweight headless PDP server. The server monitors two directories for the PDP settings and SAPL documents, 
allowing for runtime updating of policies which will be reflected in decisions made for ongoing authorization 
subscriptions.

The PDP configuration for combining algorithm and environment variables is expected in a file `pdp.json`. 
All SAPL documents in the folder named `*.sapl` will be published to the PRP.

The server can be run locally via maven or by executing the JAR. 
Alternatively a container image and configurations for deployment 
in Kubernetes is available.

## Local execution

### Running from pre-build JAR

Download the latest build from [here](https://nexus.openconjurer.org/service/rest/v1/search/assets/download?repository=maven-snapshots&group=com.my.company&name=myArtefact&sort=version&direction=desc).
To run the server you need JRE 11 or later installed. Run the server:
```
java -jar sapl-server-lt-2.0.0-SNAPSHOT.jar
```

### Running the server from source

Disclaimer: Running the server from source should only be done if you are a contributor to the policy engine project.
Please use the binary or container for production use.

To run the server from source, first build the complete policy engine project from within the projects root folder
(i.e. ```sapl-policy-engine```). The system requires maven and JDK 11 or later to be installed.
The build is triggered by issuing the command:

```shell
mvn clean install
```

Afterwards, change to the folder ```sapl-policy-engine/sapl-server-lt``` and run the application:

```shell
mvn spring-boot:run
```

### Configuration of locally running server

By default, the server will use a self-signed certificate and expose the PDP API under https://localhost:8443/api/pdp
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
The default client key (username) is: `YJidgyT2mfdkbmL` and the default client secret (password) is : `Fa4zvYQdiwHZVXh`.
To override these settings, use the following properties:
```properties
io.sapl.server-lt.key=YJidgyT2mfdkbmL
io.sapl.server-lt.secret=$2a$10$PhobF71xYb0MK8KubWLB7e0Dpl2AfMiEUi9dkKTbFR4kkWABrbiyO
```
Please note, that the secret has to be BCrypt encoded. For testing, use something like: https://bcrypt-generator.com/

The server is implemented using Spring Boot. Thus, there are a number of ways to configure the 
application. 
Please consult the matching chapter of the [Spring Boot documentation](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#boot-features-external-config).

## Testing the server

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

## Cloud deployment on Kubernetes


