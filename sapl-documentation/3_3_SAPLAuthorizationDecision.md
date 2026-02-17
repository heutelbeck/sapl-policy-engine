---
layout: default
title: SAPL Authorization Decision
#permalink: /reference/SAPL-Authorization-Decision/
parent: Publish/Subscribe Protocol
grand_parent: SAPL Reference
nav_order: 3
---

## SAPL Authorization Decision

The SAPL authorization decision contains the attributes `decision`, `resource`, `obligations`, and `advice`.

### Decision

The `decision` tells the PEP whether to grant or deny access. Access should be granted only if the decision is `"PERMIT"`. The `decision` attribute can be one of the following string values with the described meanings:

- `"PERMIT"`: Access must be granted.
- `"DENY"`: Access must be denied.
- `"NOT_APPLICABLE"`: A decision could not be made because no policy is applicable to the authorization subscription. The PEP should deny access in this case.
- `"INDETERMINATE"`: A decision could not be made because an error occurred. The PEP should deny access in this case.

### Resource

The PEP knows for which resource it requested access. Thus, there usually is no need to return this resource in the authorization decision object. However, SAPL policies may contain a `transform` statement describing how the resource needs to be altered before it is returned to the subject seeking permission. This can be used to remove or blacken certain parts of the resource document (e.g., a policy could allow doctors to view patient data but remove any bank account details as they can only be accessed by the accounting department). If a policy that evaluates to `PERMIT` contains a `transform` statement, the authorization decision attribute `resource` contains the transformed resource. Otherwise, there will not be a `resource` attribute in the authorization decision object.

### Obligations

The value of `obligations` contains assignments that the PEP must fulfill before granting or denying access. As there can be multiple policies applicable to the authorization subscription with different obligations, the `obligations` value in the authorization decision object is an array containing a list of tasks. If the PEP is not able to fulfill these tasks, access must not be granted. The array items can be any JSON value (e.g., a string or an object). Consequently, the PEP must know how to identify and process the obligations contained in the policies. An `obligations` attribute is only included in the authorization decision object if there is at least one obligation.

An authorization decision could, for example, contain the obligation to create a log entry.

In case the obligations are contained in a `DENY` decision, the access must still be denied. An obligation in a `DENY` decision acts like `advice` because the unsuccessful handling of the obligation cannot change the overall decision outcome.

### Advice

The value of `advice` is an array with assignments for the PEP as well and works similar to obligations with one difference: The fulfillment of the tasks is no requirement for granting access. I.e., in case the `decision` is `PERMIT`, the PEP should also grant access if it can not fulfill the tasks contained in `advice`. An `advice` attribute is only included in the authorization decision object if there is at least one element within the `advice` array.

In addition to the obligation to create a log entry, a policy could specify the advice to inform the system administrator via email about the access.