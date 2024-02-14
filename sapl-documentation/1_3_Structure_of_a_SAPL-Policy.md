---
layout: default
title: Structure of a SAPL Policy
#permalink: /reference/Structure-of-a-SAPL-Policy/
parent: Introduction
grand_parent: SAPL Reference
nav_order: 3
---

## Structure of a SAPL Policy

A SAPL policy document generally consists of:

- the keyword `policy`, declaring that the document contains a policy (opposed to a policy set; more on policy sets [see below](#policy-set))
- a unique (for the PDP) policy name
- the entitlement, which is the decision result to be returned upon successful evaluation of the policy, i.e., `permit` or `deny`
- an optional target expression for indexing and policy selection
- an optional `where` clause containing the conditions under which the entitlement (`permit` or `deny` as defined above) applies
- optional `advice` and `obligation` clauses to inform the PEP about optional and mandatory requirements for granting access to the resource
- an optional `transformation` clause for defining a transformed resource to be used instead of the original resource

A simple SAPL policy that allows `alice` to `HTTP:GET` the resource `https://medical.org/api/patients/123` would look as follows (in a real-world scenario, this policy is too specific):

---

*Introduction - Sample Policy 1*

```
policy "permit_alice_get_patient123" (1)
permit resource =~ "^https://medical.org/api/patients.*" (2)
where (3)
  subject.username == "alice"; (4)
  action == "HTTP:GET";
  resource == "https://medical.org/api/patients/123";
```

**1**
This statement declares the policy with the name `permit_alice_get_patient123`. The JSON values of the authorization subscription object are bound to the variables `subject`, `action`, `resource`, and `environment` that are directly accessible in the policy. The syntax `.name` accesses attributes of a nested JSON object.

**2**
This statement declares that if the resource is a string starting with `[https://medical.org/api/patients](https://medical.org/api/patients)` (using the regular expression operator `=~`) and the conditions of the `where` clause applies, the subject will be granted access to the resource. Note that the `where` clause is only evaluated if the condition of the target expression evaluates to `true`.

**3**
This statement starts the `where` clause (policy body) consisting of a list of statements. The policy body evaluates to `true` if all statements evaluate to `true`.
