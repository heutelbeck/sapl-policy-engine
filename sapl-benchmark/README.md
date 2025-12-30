# Benchmarking tool for SAPL Policy Decision Points

## Overview
This Benchmarking tool can be used to run performance tests against different types of SAPL Policy Decision Points (PDP). 
The benchmarking tool supports embedded as well as remote (http and rsocket) connections. The benchmark can be used with 
an existing PDP infrastructures (target=remote) or it can spin off the required PDP Server using Docker (target=docker).

It supports different authentication methods (NoAuth, BasicAuth, ApiKeyAuth and Oauth2). 

The Benchmark tool uses [Java Microbenchmark Harness (JMH)](https://github.com/openjdk/jmh) to execute PDP connection 
tests and to measure execution times. The execution results are stored as .json files together with a html report
(Report.html) in the output folder.

## Running the Benchmark

The benchmarking tool reads the benchmark configuration from a file (--cfg parameter) and stores the results in the 
output (--output parameter) folder. 

First build the demo, by changing into the `sapl-demo-benchmark` folder and execute the command:
```
mvn install
```

After the build completes, the `target` folder contains the executable "fat-jar" with all dependencies.
This jar file can easily be copied to another host to execute the benchmark against an existing PDP infrastructure.

Execute the following command to run the benchmark:
```
java -jar target/sapl-demo-benchmark-3.0.0-SNAPSHOT-jar-with-dependencies.jar
```

The benchmark accepts the following command line parameters:
```
Usage: sapl-demo-benchmark [-hV] [--skipBenchmark] [--skipReportGeneration]
                           -c=<cfgFilePath> [-o=<outputPath>]
Performs a benchmark on the PRP indexing data structures.
  -c, --cfg=<cfgFilePath>   YAML file to read json from
  -h, --help                Show this help message and exit.
  -o, --output=<outputPath> Path to the output directory for benchmark results.
      --skipBenchmark
      --skipReportGeneration

  -V, --version             Print version information and exit.
```

Examples 
```
# ------------------------------------------------------------------------
# "small" sample benchmarks are basically only useful for functional tests
# ------------------------------------------------------------------------
# small_embedded_benchmark
java -jar target/sapl-demo-benchmark-3.0.0-SNAPSHOT-jar-with-dependencies.jar --cfg examples/small_embedded_benchmark.yaml --output results/small_embedded_benchmark/

# small_docker_benchmark
java -jar target/sapl-demo-benchmark-3.0.0-SNAPSHOT-jar-with-dependencies.jar --output results/small_docker_benchmark --cfg examples/small_docker_benchmark.yaml

# small_docker_benchmark_oauth2
java -jar target/sapl-demo-benchmark-3.0.0-SNAPSHOT-jar-with-dependencies.jar --output results/small_docker_benchmark_oauth2 --cfg examples/small_docker_benchmark_oauth2.yaml

# small_remote_benchmark
java -jar target/sapl-demo-benchmark-3.0.0-SNAPSHOT-jar-with-dependencies.jar --cfg examples/small_remote_benchmark.yaml --output results/small_remote_benchmark/


# --------------------------------------------------------------------------
# "large" sample benchmarks representative enough to compare pdp performance
# --------------------------------------------------------------------------
# large_docker_benchmark
java -jar target/target/sapl-demo-benchmark-3.0.0-SNAPSHOT-jar-with-dependencies.jar --cfg examples/large_docker_benchmark.yaml --output results/large_docker_benchmark/
```