---
layout: default
title: Authorization Subscriptions
parent: The SAPL Policy Language
grand_parent: SAPL Reference
nav_order: 101
---


## Authorization Subscriptions

A SAPL authorization subscription is a JSON object, i.e., a set of name/value pairs or *attributes*.

An authorization subscription consists of these **required fields**:
- **subject**: Who is making the request (user, system, service)
- **action**: What operation is being attempted (read, write, delete, etc.)
- **resource**: What is being accessed (document, record, API resource, etc.)

And these **optional fields**:
- **environment**: Additional contextual information (time, location, IP address, etc.)
- **secrets**: Sensitive data (API keys, tokens, credentials) needed by Policy Information Points

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

This authorization subscription expresses the intent of Dr. Alice, a doctor from the cardiology department, to read patient record #123, which belongs to the cardiology department. Notice how each field provides attributes that policies can check: `subject.role`, `subject.department`, `resource.type`, and `resource.department`. Also note that `patientId` is a number, not a string; subscriptions can use any JSON value type.

The PEP constructs this JSON object from the application context and sends it to the PDP, which evaluates it against all applicable policies to produce an authorization decision.

### The Secrets Field

Policy Information Points (PIPs) often need credentials to access external data sources during policy evaluation. For example, a PIP may need an API key to query a patient database, or a token to call an external risk-scoring service. These credentials are sensitive and must be handled with care:

- They must **never appear in policies** - hardcoding credentials in policy text is a security risk and makes credential rotation impossible.
- They must **never appear in logs or decision output** - SAPL automatically redacts secrets from all serialization and logging.
- They must be **available to PIPs at evaluation time** - without credentials, PIPs cannot fetch the attributes policies need.

The `secrets` field solves this by providing a **secure side-channel** for passing credentials to PIPs without exposing them in policies, logs, or authorization decisions.

SAPL supports two complementary channels for providing secrets:

**Subscription-level secrets** are sent by the PEP as part of each authorization subscription. This is useful when credentials are specific to the current request context, such as an OAuth token that the calling user already possesses:

```json
{
  "subject":  "alice",
  "action":   "read",
  "resource": "patient_record",
  "secrets": {
    "oauth_token": "eyJhbGciOiJSUzI1..."
  }
}
```

**PDP-level secrets** are configured centrally in the [PDP configuration](../2_2_PDPConfiguration/#secrets). This is the right choice for infrastructure credentials shared across all evaluations, such as database connection strings or API keys for external services. PDP-level secrets are configured once and automatically available to all PIPs during evaluation.

Both channels are available to PIPs via the `AttributeAccessContext`, and PIPs can use whichever is appropriate for their use case.

### Best Practice: Domain-Driven Authorization

The authorization subscription above uses **business domain language**: `action: "read"` and `resource.type: "patient_record"`. This follows Domain-Driven Design principles: policies should speak your business's ubiquitous language, not implementation details like HTTP verbs or URLs.

In practice, a PEP in a REST API would translate infrastructure operations into domain concepts before requesting authorization:

```
GET /api/patients/123  becomes  {action: "read", resource: {type: "patient_record", patientId: 123}}
```

This keeps policies independent of technology choices. The same policies work whether you use REST, GraphQL, gRPC, or direct database access.

#### Quick Start vs. Production

While you can use **technical subscriptions** (`action: "HTTP:GET"`, `resource: "https://..."`) for rapid prototyping, **domain-driven subscriptions** are strongly recommended for production systems. Domain-driven subscriptions offer several advantages:

- **Decoupled from infrastructure**: Technical subscriptions lead to technical policies. If your subscription uses `action: "HTTP:GET"`, your policies must check `action == "HTTP:GET"`. Change from REST to GraphQL? All policies break. Domain subscriptions decouple policies from infrastructure.
- **Readable by non-technical stakeholders**: Domain policies like `action == "read"` can be reviewed by compliance officers and business analysts. Technical policies like `action =~ "^(GET|POST).*"` cannot.
- **Testable as business rules**: Tests express business intent rather than technical details. Compare: "Can cardiologists read cardiology records?" vs. "Does GET /api/patients/123 with header X-Role:cardiologist return 200?"
- **Technology-independent**: The same policies work across REST APIs, GraphQL endpoints, gRPC services, message queues, or direct database access. Migrate infrastructure without touching policies.

For example, compare these two equivalent policies:

```sapl
policy "allow GET on patient API"
permit action =~ "^GET" & resource =~ "^https://medical\.org/api/patients/.*";
```

```sapl
policy "allow reading patient records"
permit action == "read" & resource.type == "patient_record";
```

The domain-driven variant communicates intent to domain stakeholders such as compliance officers. Use technical subscriptions for rapid prototyping but migrate to domain-driven subscriptions before production deployment.
