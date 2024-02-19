---
layout: default
title: Accessing Attributes
#permalink: /reference/Accessing-Attributes/
parent: Introduction
grand_parent: SAPL Reference
nav_order: 5
---

## Accessing Attributes

In many use cases, the authorization subscription contains all the required information for making a decision. However, the PEP is usually not aware of the specifics of the access policies and may not have access to all information required for making the decision. In this case, the PDP can access external attributes. The following example shows how SAPL expresses access to attributes.

Extending the example above, in a real-world application, there will be multiple patients and multiple users. Thus, policies need to be worded more abstractly. In a natural language, a suitable policy could be *Permit doctors to* `HTTP:GET` *data from any patient*. The policy addresses the profile attribute of the subject, stored externally. SAPL allows to express this policy as follows:

---

*Introduction - Sample Policy 2*
{: info }
> ```asciidoc
> policy "doctors_get_patient"
> permit
>   action == "HTTP:GET" &
>   resource =~ "^https://medical\.org/api/patients/\d*$"
> where
>   subject.username.<user.profile>.function == "doctor";
>```

In *line 4* a regular expression is used for identifying a request to any patient’s data (operator `=~`). The authorization subscription resource must match this pattern for the policy to apply.

The policy assumes that the user’s function is not provided in the authorization subscription but stored in the user’s profile. Accordingly, *line 6* accesses the attribute `user.profile` (using an attribute finder step `.<finder.name>`) to retrieve the profile of the user with the username provided in `subject.username`. The fetched profile is a JSON object with a property named `function`. The expression compares it to `"doctor"`.

*Line 6* is placed in the policy body (starting with `where`) instead of the target expression. The reason for this location is that the target expression block is also used for indexing policies efficiently and therefore needs to be evaluated quickly. Hence it is not allowed to include conditions that may need to call an external service.