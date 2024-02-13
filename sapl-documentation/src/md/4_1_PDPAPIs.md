---
layout: default
title: PDP APIs
#permalink: /reference/PDP-APIs/
has_children: true
parent: SAPL Reference
nav_order: 4
has_toc: false
---

## PDP APIs

A SAPL PDP must expose a publish-subscribe API for subscribing via the subscription objects laid out above. SAPL defines two specific APIs for that. One is an HTTP Server-Sent Events (SSE) API for deploying a dedicated PDP Server, the other for using a PDP in reactive Java applications. The Java API may be implemented by an embedded PDP or by using the SSE API of a remote server.