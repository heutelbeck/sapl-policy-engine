/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.benchmark;

public class BenchmarkConstants {

    public static final int DEFAULT_HEIGHT = 1080;

    public static final int DEFAULT_WIDTH = 1920;

    public static final String ERROR_READING_TEST_CONFIGURATION = "Error reading test configuration";

    public static final String ERROR_WRITING_BITMAP = "Error writing bitmap";

    public static final String EXPORT_PROPERTIES = "number, name, timePreparation, timeDuration, request, response";

    public static final String EXPORT_PROPERTIES_AGGREGATES = "name, min, max, avg, mdn, seed, policyCount, variableCount, runs, iterations";

    public static final String HELP_DOC = "print this message";

    public static final String HELP = "help";

    public static final String INDEX = "index";

    public static final String INDEX_DOC = "index type used (SIMPLE, FAST, IMPROVED)";

    public static final String TEST = "test";

    public static final String TEST_DOC = "JSON file containing test definition";

    public static final String USAGE = "java -jar sapl-benchmark-springboot-1.0.0-SNAPSHOT.jar";

    public static final String PATH = "path";

    public static final String PATH_DOC = "path for output files";

    public static final String ITERATIONS = "iter";

    public static final String ITERATIONS_DOC = "the number how often the benchmark will be generated and executed";

    public static final String FULLY_RANDOM = "random";

    public static final String FULLY_RANDOM_DOC = "true: fully random benchmark, false: structured random benchmark";

}
