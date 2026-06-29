# SAPL 4.1.1

A patch release. It fixes a native-image-specific startup failure. The JVM build of 4.1.0 was not affected.

## Native image fails to start with non-trivial policy sets

The native image could not start when the policy set was large enough to build an SMTDD index. Any deployment beyond a handful of trivial policies hit this. The JVM build started normally.

The policy index compares operators structurally through `PureOperator.semanticEquals`, which reads a record's components reflectively. Those record components were not registered for reflection in the native image, so index construction threw `UnsupportedFeatureError` and the server never came up. The Linux packages and the native container image were affected. The JVM build and the JVM container image were not.

The operator records are now registered for reflection. The registration discovers them by scanning their packages, so new operators are covered without maintaining a list.

## Misleading stack trace on failed startup

When startup failed before the configuration directory monitor had started, shutdown tried to stop a monitor that was never running and logged a stack trace for it. The shutdown now skips a monitor that never started, so a failed startup no longer logs a spurious error.
