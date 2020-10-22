# SAPL Server LT - lightweight authorization server.

This is a lightweight PDP server without any UI. The server monitors the directory defined by the property `io.sapl.pdp.embedded.server-lt.config-path` and `io.sapl.pdp.embedded.config-path`, which defaults to `~/sapl/policies`. 
The PDP configuration for combining algorithm and environment variables is expected in a file `pdp.json`. 
All SAPL documents in the folder named `*.sapl` will be published to the PRP.

The server will monitor these files and update the PDP configuration and PRP immediately if the files change.

Run the server
```
java -jar sapl-server-lt-2.0.0-SNAPSHOT.jar
```

By default the server will use a self-signed certificate and run under https://localhost:8443/api/pdp
To override this certificate, use the matching Spring Boot settings, e.g.:
```properties
server.port=8443
server.ssl.enabled=true
server.ssl.key-store-type=PKCS12
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=localhostpassword
server.ssl.key-alias=tomcat
```

API consumption requires "Basic Auth". Only one set of client credentials is implemented. 
The default client key (user name) is: `YJidgyT2mfdkbmL` and the default client secret (password) is : `Fa4zvYQdiwHZVXh`.
To override these settings, use the following properties:
```properties
io.sapl.server-lt.key=YJidgyT2mfdkbmL
io.sapl.server-lt.secret=$2a$10$PhobF71xYb0MK8KubWLB7e0Dpl2AfMiEUi9dkKTbFR4kkWABrbiyO
```
Please note, that the secret has to be BCrypt encoded. For testing, use something like: https://bcrypt-generator.com/

A sample application that can be configured to use this PDP server (remote PDP) can be found
in the [demo-applications](https://github.com/heutelbeck/sapl-demos) project for the SAPL
Policy Engine in the module `sapl-demo-reactive`.

# Overriding properties

Please consult this chapter of the Spring Boot documentation: https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#boot-features-external-config

# Keystore was creation

keytool -genkeypair -keystore keystore.p12 -dname "CN=localhost, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown" -keypass changeme -storepass changeme -keyalg RSA -alias netty -ext SAN=dns:localhost,ip:127.0.0.1

Use: https://github.com/FiloSottile/mkcert

mkcert -pkcs12 -p12-file self-signed-cert.p12 localhost 127.0.0.1 ::1

