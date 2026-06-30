# SAPL 4.1.2

This release restores RSocket throughput that had regressed through a transitive dependency, and clears native-image reflection noise on shutdown.

reactor-netty 1.3.6, brought in by the Spring Boot 4.1 dependency set, places connections on only every other event loop and leaves half the I/O threads idle. Measured against the RSocket load generator, this cut its generated throughput by about 30 percent. The PDP server and the remote PEP client use the same library and are likely affected, but that was not measured here. reactor-netty is pinned to 1.3.5, which uses all event loops, until the upstream behavior is fixed. The decision and the workaround are documented at the pin in the root POM.

In a native image, SAPL Node also logged reflection-error stack traces on shutdown and on the fail-closed startup. The Server-Sent-Events executors now shut down through `DisposableBean`, which uses no reflection. The JVM build was never affected.
