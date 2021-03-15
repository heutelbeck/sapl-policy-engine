# SAPL BENCHMARK (sapl-benchmark-springboot)

## Benchmark Execution
The home directory of this module contains multiple BASH scripts to make the execution of the benchmark as easy as possible.
While it is perfectly ok to execute the benchmark during development/testing directly inside the IDE, we strongly recommend to build a runnable JAR for the actual benchmarking.

- `runBenchForAllConfigs.sh`
  - executes the benchmark for a single index type using multiple configurations (see below)
- `runBenchForAllTypes.sh`
  - executed the benchmark for multiple index types with the same configuration
- `runBenchFullyRandom.sh`
  - migrated benchmark (deprecated)

At least 8 GB of heap space should be used to run the benchmarks.

### Structured Random Benchmark (New)

**Required Setup**
In most cases, the default setup defined in below property files should be sufficient.

By default, the application writes the generated policies to `/tmp/sapl/policies` and the results to `/tmp/sapl/benchmarks`.

`Benchmark.java` contain a constant `DEFAULT_PATH` that can be used to define the result output directory.

`private static final String DEFAULT_PATH = "/tmp/sapl/benchmarks/";`

The output directory for the policy generator is defined inside the [application.properties](src/main/resources/application.properties)

Make sure this directory exists on your file system

**Benchmark Configurations:**

Several predefined benchmark configurations are available as property files:
- application-15k.properties (will generate approx. 15.000 policies)
- application-30k.properties (will generate approx. 30.000 policies)
- application-40k.properties (will generate approx. 40.000 policies)

**Overwrite application.properties on CLI**

They can be activated by setting the respective profile using the parameter `-Dspring.profiles.active=profile`.

**Provide external configuration**

It is also possible to provide a custom property file using the parameter `-Dspring.config.location=/whereever/appplication.properties`.

## Fully Random Benchmark (Deprecated)
This contains the benchmarking logic migrated from the module [sapl-benchmark](../sapl-benchmark/).
The migration had to be done to at least allow some kind of comparison between the two strategies, as many of the code to run the benchmark is the same.

For more details on the usage of this benchmark type, please refer to [README.MD](../sapl-benchmark/README.MD).