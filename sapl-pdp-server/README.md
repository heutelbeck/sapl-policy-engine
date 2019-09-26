# PDP Server

This sub-project provides a Spring-Boot application starting a server which provides REST endpoints for an embedded 
Policy Decision Point (PDP). A Policy Decision Point (PDP) can be integrated in two ways:
1) either directly embedded in the application (embedded PDP),
2) or running on a separate server (remote PDP).

The server provided by this sub-project can be used for the second case. It can be connected by client applications
via HTTPS and is secured with basic authentication.

The server can be configured using the `application.properties` file under `src/main/resources`.

The following properties configure the PDP used by this server:
```properties
io.sapl.pdp-config-type=filesystem
io.sapl.filesystem.config-path=path/to/pdp/configuration/folder
io.sapl.prp-type=filesystem
io.sapl.filesystem.policies-path=path/to/policies/folder
```

The following property configures the type of the policy document index used by the PRP:
```properties
io.sapl.index=[simple|fast]
```

The following properties configure the server providing the REST endpoints
```properties
server.port=8443
server.ssl.enabled=true
server.ssl.key-store-type=PKCS12
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=localhostpassword
server.ssl.key-alias=tomcat
```

The following properties configure the credentials expected by the basic authentication:
```properties
http.basic.auth.client-key=YJidgyT2mfdkbmL
# BCrypt encoded client-secret (raw secret: Fa4zvYQdiwHZVXh)
http.basic.auth.client-secret=$2a$10$PhobF71xYb0MK8KubWLB7e0Dpl2AfMiEUi9dkKTbFR4kkWABrbiyO
```

A sample application that can be configured to use this PDP server (remote PDP) can be found
in the [demo-applications](https://github.com/heutelbeck/sapl-demos) project for the SAPL
Policy Engine in the module `sapl-demo-reactive`.
