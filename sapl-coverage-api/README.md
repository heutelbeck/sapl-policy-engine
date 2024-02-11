# SAPL Coverage API

This API is a utility library used by [sapl-test](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-test) and the [sapl-maven-plugin](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-maven-plugin) for gathering information about policy code coverage. It is unlikely that this module is needed outside the SAPL engine.

The classes `CoverageHitReader.java` and `CoverageHitWriter.java` define the primary interfaces for this API.

The only implementation of the API is `CoverageHitAPIFile.java`, writing and reading the SAPL coverage hits to/from files on the local filesystem. It usually uses the `target/` path in a standard Maven project structure.