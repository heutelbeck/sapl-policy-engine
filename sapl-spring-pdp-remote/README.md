# Remote SAPL Policy Decision Point (PDP) for Spring Boot

This module provides a PDP which is a client for a remote PDP server.

## Configuration

The remote PDP is configured with the following properties stored in an ```application.yml``` or ```application.properties``` file.

### io.sapl.pdp.remote.type
Defines the protocol for the remote pdp connection. Valid values are "rsocket" and "http".

### io.sapl.pdp.remote.host (type=http)
When type is "http" this property defines the fully qualified URL of the PDP Server. E.g., ```https://pdp.example.org:8443```.

### io.sapl.pdp.remote.rsocketHost (type=rsocket)
When type is "http" this property defines the PDP Server hostname e.g. "pdp.example.org".

### io.sapl.pdp.remote.rsocketPort (type=rsocket)
When type is "http" this property defines the PDP Server rsocket port e.g. "7000".

### io.sapl.pdp.remote.key
This property enabled Basic Authentication and defines the key of a key/secret pair for accessing the PDP API.

### io.sapl.pdp.remote.secret
This property defines Basic Authentication key of a key/secret pair for accessing the PDP API.

### io.sapl.pdp.remote.apiKey
This property enables Api Key Authentication and defines the API key for accessing the PDP API.

### io.sapl.pdp.remote.ignoreCertificates
This property disables the validation of TLS certificates. This must only be used for testing purposes and if set to ```true``` you accept the risk that a man in the middle may listen to traffic, inject traffic and malicious decisions into the communication.
