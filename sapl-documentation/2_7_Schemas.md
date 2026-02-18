---
layout: default
title: Schemas
parent: The SAPL Policy Language
grand_parent: SAPL Reference
nav_order: 107
---

## Schemas for Authorization Subscriptions

SAPL offers the possibility to predefine the structure of the elements of an authorization subscription using [JSON schema](https://json-schema.org/). Schemas used should follow the 2020-12 JSON Schema version. 

There are a few use cases for defining schemas for authorization subscriptions. 

* Policy authors and application developers should always agree on the exact contents of the authorization subscriptions. The authorization subscription is a JSON object. JSON schemas are a practical way of defining this contract between PEP and PDP and between the application development and administration teams. 

* Provided well-defined schemas for the individual elements of the authorization subscription, i.e., subject, action, resource, and environment. The policy engine can validate the individual fields and ensure that the subscription fulfils the contract, i.e., the engine can assert and enforce the contract between PEP and PDP.

* The SAPL policy authoring tools can use the schema definition to provide more meaningful code completion suggestions in editors for elements where they can infer the schema of a JSON value.

The schemas for the individual elements of the authorization subscription are defined independently of each other. Schemas follow the optional imports of the document.

A SAPL document defines a schema by stating the keyword of the subscription element (`subject`, `action`, `resource`, `environment`). Then, `schema` and an optional `enforced` keyword follow. After these keywords, a SAPL expression evaluating to a JSON object defines the schema itself. This expression must not contain any lazy boolean operators (`&&`, `||` use the eager versions `&` and `|`) or references to PIPs (i.e., attribute finder expressions `<attribute.name>`). Instead of repeating schemas in all policies, users can store schemas in environment variables and reference them via variables in the schema assignment. If the schema expression contains references to external schemas, the engine expects these schemas to be present in the environment variable `SCHEMAS`, an array containing the individual schemas. 

Example schema definition for a subject enforced schema:

```json 
{
    "$id": "https://example.com/person.schema.json",
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "title": "Person",
    "type": "object",
    "properties": {
        "firstName": {
            "type": "string",
            "description": "The person's first name."
        },
        "lastName": {
            "type": "string",
            "description": "The person's last name."
        },
        "age": {
            "description": "Age in years which must be equal to or greater than zero.",
            "type": "integer",
            "minimum": 0
        }
    }
}
```

If the `schema` definition does not contain the keyword `enforced`, the only consequence of the schema definition is that the SAPL editors can make code completion suggestions based on the schema. If the `enforced` keyword is present, validation of the engine implicitly adds the schema validation to the target expression of the document (i.e., to the `for` expression of a policy set or the target expression following the entitlement in a simple policy). 

Users can add more than one schema to each subscription keyword. When enforcing schemas, the implied validity check validates if the respective element is valid according to at least one of the provided schemas. If this is not the case, the document is not applicable, and the engine will not evaluate it. If an explicit target expression is present, both the explicit and the implicit target expression must be true for the document to be applicable.
