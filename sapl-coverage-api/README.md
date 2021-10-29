# SAPL Coverage API

This is a utility library used by [sapl-test](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-test) and the [sapl-maven-plugin](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-maven-plugin) for gathering information about policy code coverage.

The main interfaces for this API are defined in the `CoverageHitReader.java` and `CoverageHitWriter.java`.

Currently there is only one implementation `CoverageHitAPIFile.java` of the the API writing and reading the SAPL coverage hits to/from files on the local filesystem. Normally it uses the `target/` path in a standard Maven project structure.
