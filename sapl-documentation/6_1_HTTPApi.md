---
layout: default
title: HTTP Server-Sent Events API
parent: Integration
nav_order: 601
---

## HTTP API

The SAPL PDP server exposes HTTP endpoints for authorization decisions. All endpoints accept `POST` requests with `application/json` request bodies. Streaming endpoints produce `text/event-stream` (Server-Sent Events); one-shot endpoints produce `application/json`.

All connections must use TLS. Authentication is required and configured per deployment (basic auth, API key, or OAuth2). All endpoints are located under a shared base URL, typically `https://<host>:<port>/api/pdp/`.

### Error Handling

A PEP encountering connectivity issues or errors with the PDP server must treat this as an `INDETERMINATE` decision and deny access. The PEP should reconnect using an exponential backoff strategy to avoid overloading the PDP.

### Single Subscription Endpoints

#### Decide (Streaming)

- URL: `{baseURL}/decide`
- Body: An authorization subscription JSON object
- Produces: `text/event-stream` (Server-Sent Events)
- Behavior: Returns an initial decision, then pushes updated decisions whenever policies, attributes, or conditions change. Each decision is a JSON object in the `data` field of an SSE event. The server may send SSE comment events (`: keep-alive`) to keep the connection alive. The client must close the connection to stop receiving updates.

#### Decide Once (One-Shot)

- URL: `{baseURL}/decide-once`
- Body: An authorization subscription JSON object
- Produces: `application/json`
- Behavior: Returns a single decision and closes the connection.

### Multi-Subscription Endpoints

#### Multi Decide (Streaming Individual)

- URL: `{baseURL}/multi-decide`
- Body: A multi-subscription JSON object
- Produces: `text/event-stream` (Server-Sent Events)
- Behavior: Returns individual decisions as they change, each tagged with its subscription ID. See [Multi-Subscriptions](../6_3_MultiSubscriptions/) for the response format.

#### Multi Decide All (Streaming Batch)

- URL: `{baseURL}/multi-decide-all`
- Body: A multi-subscription JSON object
- Produces: `text/event-stream` (Server-Sent Events)
- Behavior: Returns all decisions as a single object whenever any decision changes.

#### Multi Decide All Once (One-Shot Batch)

- URL: `{baseURL}/multi-decide-all-once`
- Body: A multi-subscription JSON object
- Produces: `application/json`
- Behavior: Returns all decisions as a single object and closes the connection.

### Keep-Alive

Streaming connections use periodic SSE comment events (`: keep-alive`) to prevent firewalls and proxies from closing idle connections. A PEP should treat a prolonged absence of any events (decisions or keep-alives) as a connection failure.

### Implementations

The SAPL Policy Engine ships with **SAPL Node**, a standalone PDP server ready for deployment. SAPL Node supports filesystem directories, signed bundles, and remote bundle fetching as policy sources. It is available as a Docker container and as a native binary.
