# Remote SAPL Policy Decision Point (PDP) for Spring Boot

This module provides a PDP which is a client for a remote PDP server.

## Configuration

The remote PDP is configured with the following properties stored in an ```application.yml``` or ```application.properties``` file.

### io.sapl.pdp.remote.host

This property defines the fully qualified URL of the PDP Server. E.g., ```https://pdp.example.org:8443```.

### io.sapl.pdp.remote.key

This property defines API key of a key/secret pair for accessing the PDP API.

### io.sapl.pdp.remote.secret

This property defines API key of a key/secret pair for accessing the PDP API.

### io.sapl.pdp.remote.ignoreCertificates

This property disables the validation of TLS certificates. This must only be used for testing purposes and if set to ```true``` you accept the risk that a man in the middle may listen to traffic, inject traffic and malicious decisions into the communication.
