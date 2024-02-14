---
layout: default
title: The SAPL Policy Language
#permalink: /reference/The-SAPL-Policy-Language/
has_children: true
parent: SAPL Reference
nav_order: 5
has_toc: false
---

## The SAPL Policy Language

SAPL defines a feature-rich domain-specific language (DSL) for creating access policies. Those access policies describe when access requests will be granted and when access will be denied. The underlying concept to describe these permissions is an attribute-based access control model (ABAC): A SAPL authorization subscription is a JSON object with the attributes `subject`, `action`, `resource` and `environment` each with an assigned JSON value. Each of these values may be a JSON object itself containing multiple attributes. Policies can use of Boolean conditions referring to those attributes (e.g., `subject.username == "admin"`).

However, a role-based access control (RBAC) system in which permissions are assigned to a certain role and roles can be assigned to users can be created with SAPL as well.