---
layout: default
title: Authorization Decisions
#permalink: /reference/Authorization-Decisions/
parent: Introduction
grand_parent: SAPL Reference
nav_order: 4
---

## Authorization Decisions

The SAPL authorization decision to the authorization subscription is a JSON object as well. It contains the attribute `decision` as well as the optional attributes `resource`, `obligation`, and `advice`. For the introductory sample authorization subscription with the preceding policy, a SAPL authorization decision would look as follows:

---

*Introduction - Sample Authorization Decision*

```json
{
  "decision"   : "PERMIT"
}
```

The PEP evaluates this authorization decision and grants or denies access accordingly.