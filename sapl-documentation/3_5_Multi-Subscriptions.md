---
layout: default
title: Multi Subscriptions
#permalink: /reference/Multi-Subscriptions/
parent: Publish/Subscribe Protocol
grand_parent: SAPL Reference
nav_order: 5
---

## Multi-Subscriptions

SAPL allows for bundling multiple authorization subscriptions into one multi-subscription. A multi-subscription is a JSON object with the following structure:

Multi-Subscriptions - JSON Structure

```json
{
  "subjects"                   : ["bs@simpsons.com", "ms@simpsons.com"],
  "actions"                    : ["read"],
  "resources"                  : ["file://example/med/record/patient/BartSimpson",
                                  "file://example/med/record/patient/MaggieSimpson"],
  "environments"               : [],

  "authorizationSubscriptions" : {
                                   "id-1" : { "subjectId": 0, "actionId": 0, "resourceId": 0 },
                                   "id-2" : { "subjectId": 1, "actionId": 0, "resourceId": 1 }
                                 }
}
```

It contains distinct lists of all subjects, actions, resources, and environments referenced by the single authorization subscriptions being part of the multi-subscription. The authorization subscriptions themselves are stored in a map of subscription IDs pointing to an object defining an authorization subscription by providing indexes into the four lists mentioned before.

The multi-subscription shown in the example above contains two authorization subscriptions. The user `bs@simpsons.com` wants to `read` the file `file://example/med/record/patient/BartSimpson`, and the user `ms@simpsons.com` wants to `read` the file `file://example/med/record/patient/MaggieSimpson`.

The SAPL PDP processes all individual authorization subscriptions contained in the multi-subscription in parallel and returns the related authorization decisions as soon as they are available, or it collects all the authorization decisions of the individual authorization subscriptions and returns them as a multi-decision. In both cases, the authorization decisions are associated with the subscription IDs of the related authorization subscription. The following listings show the JSON structures of the two authorization decision types:

Single Authorization Decision with Associated Subscription ID - JSON Structure

```json
{
"authorizationSubscriptionId" : "id-1",
"authorizationDecision"       : {
                "decision" : "PERMIT",
                "resource" : { ... }
        }
}

```

Multi-Decision - JSON Structure

```json
{
"authorizationDecisions" : {
"id-1" : {
            "decision" : "PERMIT",
            "resource" : { ... }
            },
            "id-2" : {
                      "decision" : "DENY"
            }
      }
}

```
