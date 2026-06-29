# SAPL 4.1.2

A patch release fixing a native-image-only failure. The JVM build was not affected.

In a native image, SAPL Node logged `MissingReflectionRegistrationError` stack traces on shutdown and on the fail-closed startup that occurs when no authentication is configured, burying a correct refusal under unrelated reflection errors. The two Server-Sent-Events executors were Spring beans of an `AutoCloseable` type; Spring AOT drops their `@Bean(destroyMethod = "")` suppression, so the native image re-inferred a reflective shutdown at context destroy and failed to invoke it. They are now owned by their configuration and shut down through `DisposableBean.destroy()`, an interface call that never uses reflection.

The fail-closed behavior is unchanged. Running without an authentication mechanism, and without explicitly allowing none, still refuses to start.
