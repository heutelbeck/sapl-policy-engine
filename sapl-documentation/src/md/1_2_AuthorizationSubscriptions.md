---
layout: default
title: Authorization Subscriptions
#permalink: /reference/Authorization-Subscriptions/
parent: Introduction
grand_parent: SAPL Reference
nav_order: 2
---

## Authorization Subscriptions

A SAPL authorization subscription is a JSON object, i.e., a set of name/value pairs or *attributes*. It contains attributes with the names `subject`, `action`, `resource`, and `environment`. The values of these attributes may be any arbitrary JSON value, e.g.:

*Introduction - Sample Authorization Subscription*

```json
{
  "subject"     : {
                    "username"    : "alice",
                    "tracking_id" : 1234321,
                    "nda_signed"  : true
                  },
  "action"      : "HTTP:GET",
  "resource"    : "https://medical.org/api/patients/123",
  "environment" : null
}
```

This authorization subscription expresses the intent of the user `alice`, with the given attributes, to `HTTP:GET` the resource at `https://medical.org/api/patients/123`. This SAPL authorization subscription can be used in a RESTful API, implementing a PEP protecting the API’s request handlers.