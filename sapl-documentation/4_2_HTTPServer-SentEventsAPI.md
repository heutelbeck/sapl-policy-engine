---
layout: default
title: HTTP Server-Sent Events API
#permalink: /reference/HTTP-Server-Sent-Events-API/
parent: PDP APIs
grand_parent: SAPL Reference
nav_order: 2
---

## HTTP Server-Sent Events API

A PDP to be used as a network service must implement some HTTP endpoints. All of them accept `POST` requests and `application/json`. They produce `application/x-ndjson` as [Server-Sent Events (SSE)](https://www.w3.org/TR/eventsource/). A PDP server must be accessed over encrypted TLS connections. All connections should be authenticated. The means of authentications are left open for the organization deploying the PDP to decide or to be defined by a specific server implementation. All endpoints should be located under a shared base URL, e.g., `[https://pdp.sapl.io/api/pdp/](https://pdp.sapl.io/api/pdp/)`.

A PEP which is a client to the SSE PDP API encountering connectivity issues or errors, must interpret this as an INDETERMINATE decision and thus deny access during this time of uncertainty and take appropriate steps to reconnect with the PDP, using a matching back-off strategy to not overload the PDP.

A PEP must determine if it can enforce obligations before granting access. It must enforce obligation upon granting access at the point in time (e.g., before or after granting access) implied by the semantics of the obligation, and it should enforce any advice at their appropriate point in time when possible.

Upon subscription, the PDP server will respond with an unbound stream of decisions. The client must close the connection to stop receiving decision events. A connection termination by the server is an error state and must be handled as discussed.

### Decide

- URL: `{baseURL}/decide`
- Method: `POST`
- Body: A valid JSON authorization subscription
- Produces: A SSE stream of authorization decisions

### Decide Once

- URL: `{baseURL}/decide-once`
- Method: `POST`
- Body: A valid JSON authorization subscription
- Produces: A single authorization decisions

### Multi Decide

- URL: `{baseURL}/multi-decide`
- Method: `POST`
- Body: A valid JSON multi subscription
- Produces: A SSE stream of Single Authorization Decisions with Associated Subscription ID JSON Objects

### Multi Decide All

- URL: `{baseURL}/multi-decide-all`
- Method: `POST`
- Body: A valid JSON multi subscription
- Produces: A SSE stream of Multi Decision JSON Objects

### Implementations

The SAPL Policy engine comes with an implementations ready for deployment in an organization:

- SAPL Node: This light PDP server implementation uses a configuration and policies stored on a file system. The server is available as a docker container. Documentation: <https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-node>
