---
layout: default
title: Authorization Subscriptions
#permalink: /reference/Authorization-Subscriptions/
parent: Introduction
grand_parent: SAPL Reference
nav_order: 2
---

> **Introduction Series:** [1. Overview](../1_1_Introduction/) • **2. Subscriptions** • [3. Policy Structure](../1_3_Structure_of_a_SAPL-Policy/) • [4. Decisions](../1_4_AuthorizationDecisions/) • [5. Attributes](../1_5_AccessingAttributes/) • [6. Getting Started](../1_6_GettingStarted/)

## Authorization Subscriptions

A SAPL authorization subscription is a JSON object, i.e., a set of name/value pairs or *attributes*.

An authorization subscription consists of these **required fields**:
- **subject**: Who is making the request (user, system, service)
- **action**: What operation is being attempted (read, write, delete, etc.)
- **resource**: What is being accessed (document, record, API resource, etc.)

And this **optional field**:
- **environment**: Additional contextual information (time, location, IP address, etc.)

---

*Introduction - Sample Authorization Subscription*

```json
{
  "subject": {
    "username": "alice",
    "role": "doctor",
    "department": "cardiology"
  },
  "action": "read",
  "resource": {
    "type": "patient_record",
    "patientId": 123,
    "department": "cardiology"
  },
  "environment": {
    "timestamp": "2025-10-06T14:30:00Z"
  }
}
```

This authorization subscription expresses the intent of Dr. Alice, a doctor from the cardiology department, to read patient record #123, which belongs to the cardiology department. Notice how this subscription matches the policy from the previous section - it provides all the attributes the policy checks: `subject.role`, `subject.department`, `resource.type`, and `resource.department`. Also note that `patientId` is a number, not a string - subscriptions can use any JSON value type.

The PEP constructs this JSON object from the application context and sends it to the PDP, which evaluates it against all applicable policies to produce an authorization decision.

### Best Practice: Domain-Driven Authorization

The authorization subscription above uses **business domain language**: `action: "read"` and `resource.type: "patient_record"`. This follows Domain-Driven Design principles - policies should speak your business's ubiquitous language, not implementation details like HTTP verbs or URLs.

In practice, a PEP in a REST API would translate infrastructure operations into domain concepts before requesting authorization:

```
GET /api/patients/123  →  {action: "read", resource: {type: "patient_record", patientId: 123}}
```

This keeps policies independent of technology choices - the same policies work whether you use REST, GraphQL, gRPC, or direct database access.

#### Quick Start vs. Production

While you can use **technical subscriptions** (`action: "HTTP:GET"`, `resource: "https://..."`) for rapid prototyping, **domain-driven subscriptions** are strongly recommended for production systems.

**Domain-Driven Acces Control**

**Policy Coupling**: Technical subscriptions lead to technical policies. If your subscription uses `action: "HTTP:GET"`, your policies must check `action == "HTTP:GET"`. Change from REST to GraphQL? All policies break. Domain subscriptions decouple policies from infrastructure.

**Business Communication**: Domain policies like `action == "read"` can be reviewed by compliance officers and business analysts. Technical policies like `action =~ "^(GET|POST).*"` cannot. Domain language enables collaboration with non-technical stakeholders.

**Testable Business Rules**: Tests express business intent rather than technical details. Compare: "Can cardiologists read cardiology records?" vs. "Does GET /api/patients/123 with header X-Role:cardiologist return 200?"

**Technology Independence**: The same policies work across REST APIs, GraphQL endpoints, gRPC services, message queues, or direct database access. Migrate infrastructure without touching policies.

**Example: Policy Evolution**

Technical subscription → Technical policy:
```sapl
permit action =~ "^GET" & resource =~ "^https://medical\.org/api/patients/.*"
```

Domain subscription → Domain policy:
```sapl
permit action == "read" & resource.type == "patient_record"
```

Staying with the domain-driven variant typically will make communication with domain stakeholders, e.g., compliance officer, easier.

**Recommendation**: Use technical subscriptions for rapid prototyping and making first steps in SAPL, but migrate to domain-driven subscriptions before production deployment.
