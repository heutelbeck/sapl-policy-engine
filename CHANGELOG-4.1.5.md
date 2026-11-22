# SAPL 4.1.5

This release fixes error propagation in priority-based policy combining. When a policy errors and another abstains, the combiner now preserves the error and the set of effects the failing policy could have produced.

In 4.1.4 an abstention could cause a policy set configured to propagate errors to return its default decision. The policy set now returns `INDETERMINATE` in that case, regardless of vote order.
