---
layout: default
title: HTTP Server-Sent Events API
#permalink: /reference/HTTP-Server-Sent-Events-API/
parent: PDP APIs
grand_parent: SAPL Reference
nav_order: 2
---

## HTTP API

The SAPL PDP server exposes HTTP endpoints for authorization decisions. All endpoints accept `POST` requests with `application/json` request bodies. Streaming endpoints produce `application/x-ndjson` (Newline-Delimited JSON); one-shot endpoints produce `application/json`.

All connections must use TLS. Authentication is required and configured per deployment (basic auth, API key, or OAuth2). All endpoints are located under a shared base URL, typically `https://<host>:<port>/api/pdp/`.

### Error Handling

A PEP encountering connectivity issues or errors with the PDP server must treat this as an `INDETERMINATE` decision and deny access. The PEP should reconnect using an exponential backoff strategy to avoid overloading the PDP.

### Single Subscription Endpoints

#### Decide (Streaming)

- URL: `{baseURL}/decide`
- Body: An authorization subscription JSON object
- Produces: `application/x-ndjson` (streaming)
- Behavior: Returns an initial decision, then pushes updated decisions whenever policies, attributes, or conditions change. The client must close the connection to stop receiving updates.

#### Decide Once (One-Shot)

- URL: `{baseURL}/decide-once`
- Body: An authorization subscription JSON object
- Produces: `application/json`
- Behavior: Returns a single decision and closes the connection.

### Multi-Subscription Endpoints

#### Multi Decide (Streaming Individual)

- URL: `{baseURL}/multi-decide`
- Body: A multi-subscription JSON object
- Produces: `application/x-ndjson` (streaming)
- Behavior: Returns individual decisions as they change, each tagged with its subscription ID. See [Multi-Subscriptions](../3_5_Multi-Subscriptions/) for the response format.

#### Multi Decide All (Streaming Batch)

- URL: `{baseURL}/multi-decide-all`
- Body: A multi-subscription JSON object
- Produces: `application/x-ndjson` (streaming)
- Behavior: Returns all decisions as a single object whenever any decision changes.

#### Multi Decide All Once (One-Shot Batch)

- URL: `{baseURL}/multi-decide-all-once`
- Body: A multi-subscription JSON object
- Produces: `application/json`
- Behavior: Returns all decisions as a single object and closes the connection.

### Keep-Alive

Streaming connections use periodic keep-alive events to detect stale connections. A PEP should treat a prolonged absence of any events (decisions or keep-alives) as a connection failure.

### Implementations

The SAPL Policy Engine ships with **SAPL Node**, a standalone PDP server ready for deployment. SAPL Node supports filesystem directories, signed bundles, and remote bundle fetching as policy sources. It is available as a Docker container and as a native binary.
