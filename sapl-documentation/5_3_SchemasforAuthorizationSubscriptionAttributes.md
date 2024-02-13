---
layout: default
title: Schemas
#permalink: /reference/Schemas/
parent: The SAPL Policy Language
grand_parent: SAPL Reference
nav_order: 4
---

## Schemas for Authorization Subscription Attributes

SAPL offers the possibility to predefine the structure of an attribute of the authorization subscription using a JSON schema. The attribute name is followed by the keyword `schema` and the schema expression. The schema expression must evaluate to a valid JSON schema.
