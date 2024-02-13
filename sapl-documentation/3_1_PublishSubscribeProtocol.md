---
layout: default
title: Publish/Subscribe Protocol
#permalink: /reference/publish-subscribe-protocol/
has_children: true
parent: SAPL Reference
nav_order: 3
has_toc: false
---

## Publish / Subscribe Protocol

The PDP receives an authorization subscription from a PEP and sends an authorization decision. Both subscription and decision are JSON objects consisting of name/value pairs (also called attributes) with predefined names. A PEP must be able to create an authorization subscription and process an authorization decision object.