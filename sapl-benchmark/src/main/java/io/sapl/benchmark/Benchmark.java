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

import com.google.common.base.Strings;
import com.google.gson.Gson;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.generator.DomainData.BenchmarkArguments;
import io.sapl.generator.DomainGenerator;
import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.IndexType;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.knowm.xchart.XYChart;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import static io.sapl.benchmark.BenchmarkConstants.DEFAULT_HEIGHT;
import static io.sapl.benchmark.BenchmarkConstants.DEFAULT_WIDTH;
import static io.sapl.benchmark.BenchmarkConstants.ERROR_READING_TEST_CONFIGURATION;
import static io.sapl.benchmark.BenchmarkConstants.FULLY_RANDOM;
import static io.sapl.benchmark.BenchmarkConstants.FULLY_RANDOM_DOC;
import static io.sapl.benchmark.BenchmarkConstants.HELP;
import static io.sapl.benchmark.BenchmarkConstants.HELP_DOC;
import static io.sapl.benchmark.BenchmarkConstants.INDEX;
import static io.sapl.benchmark.BenchmarkConstants.INDEX_DOC;
import static io.sapl.benchmark.BenchmarkConstants.ITERATIONS;
import static io.sapl.benchmark.BenchmarkConstants.ITERATIONS_DOC;
import static io.sapl.benchmark.BenchmarkConstants.PATH;
import static io.sapl.benchmark.BenchmarkConstants.PATH_DOC;
import static io.sapl.benchmark.BenchmarkConstants.TEST;
import static io.sapl.benchmark.BenchmarkConstants.TEST_DOC;
import static io.sapl.benchmark.BenchmarkConstants.USAGE;


@Slf4j
@Component
@RequiredArgsConstructor
public class Benchmark implements CommandLineRunner {

    // public static final String DEFAULT_PATH = "/tmp/sapl/benchmarks";
    public static final String DEFAULT_PATH = "C:\\tmp\\sapl\\benchmarks\\";

    private final TestRunner TEST_RUNNER = new TestRunner();
    private final List<Long> seedList = new LinkedList<>();
    private final DomainGenerator domainGenerator;

    // Switch between benchmark types
    protected static boolean performFullyRandomBenchmark = false;


    //    private String filePrefix;
    //    private final String path = DEFAULT_PATH;
    //    private IndexType indexType = IndexType.CANONICAL;
    //    private int numberOfBenchmarkIterations = 1;
    //    private String benchmarkConfigurationFile = DEFAULT_PATH + "\\tests\\tests.json";

    @Override
    public void run(String... args) throws Exception {
        log.info("command line runner started");

        //        BenchmarkArguments benchmarkArguments = domainGenerator.getDomainData().getBenchmarkArguments();

        domainGenerator.getDomainUtil().cleanPolicyDirectory(domainGenerator.getDomainData().getPolicyDirectoryPath());
        BenchmarkArguments benchmarkArguments = parseCommandLineArguments(args);

        String filePrefix = generateFilePrefix(benchmarkArguments.getIndexType());
        benchmarkArguments.setFilePrefix(filePrefix);

        init(benchmarkArguments);
        runBenchmark(benchmarkArguments);

        System.exit(0);
    }

    private void init(BenchmarkArguments benchmarkArguments) {
        String path = benchmarkArguments.getPath();
        String filePrefix = benchmarkArguments.getFilePrefix();
        int numberOfBenchmarkIterations = benchmarkArguments.getNumberOfBenchmarkIterations();
        log.info(
                "\n randomBenchmark={},\n numberOfBenchmarks={}," + "\n index={},\n initialSeed={},\n runs={},"
                        + "\n testfile={},\n filePrefix={}",
                performFullyRandomBenchmark, numberOfBenchmarkIterations, benchmarkArguments.getIndexType(),
                domainGenerator.getDomainData().getSeed(), domainGenerator.getDomainData().getNumberOfBenchmarkRuns(),
                benchmarkArguments.getBenchmarkConfigurationFile(), filePrefix);

        try {
            final Path dir = Paths.get(path, filePrefix);
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error(ERROR_READING_TEST_CONFIGURATION, e);
        }

        // seed list
        seedList.add(domainGenerator.getDomainData().getSeed()); // initial seed from properties file
        for (int i = 0; i < numberOfBenchmarkIterations - 1; i++) {
            seedList.add((long) domainGenerator.getDomainData().getDice().nextInt());
        }
    }

    private String generateFilePrefix(IndexType indexType) {
        return String.format("%s_%s_%s%s", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME), indexType,
                performFullyRandomBenchmark ? "RANDOM" : "STRUCTURED", File.separator).replace(':', '-');
    }

    public void runBenchmark(BenchmarkArguments benchmarkArguments) throws Exception {
        String path = benchmarkArguments.getPath();
        String filePrefix = benchmarkArguments.getFilePrefix();
        String resultPath = path + filePrefix;

        IndexType indexType = benchmarkArguments.getIndexType();

        XYChart overviewChart = new XYChart(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        ResultWriter resultWriter = new ResultWriter(resultPath, indexType);

        BenchmarkDataContainer benchmarkDataContainer = new BenchmarkDataContainer(indexType,
                domainGenerator.getDomainData(), domainGenerator.getDomainData().getNumberOfBenchmarkRuns(),
                benchmarkArguments.getNumberOfBenchmarkIterations());

        List<PolicyGeneratorConfiguration> configs = generateConfigurations(benchmarkArguments);

        for (PolicyGeneratorConfiguration config : configs) {

            List<XlsRecord> results = benchmarkConfiguration(path, benchmarkDataContainer, config);

            double[] times = new double[results.size()];
            resultWriter.writeDetailsChart(results, times, config.getName());
            overviewChart.addSeries(config.getName(), times);

        }

        resultWriter.writeFinalResults(benchmarkDataContainer, overviewChart);
    }

    private List<PolicyGeneratorConfiguration> generateConfigurations(BenchmarkArguments benchmarkArguments) {
        if (performFullyRandomBenchmark) {
            return generateTestSuite(benchmarkArguments).getCases();
        } else {
            List<PolicyGeneratorConfiguration> configurations = new LinkedList<>();

            for (Long seed : seedList) {

                configurations
                        .add(PolicyGeneratorConfiguration.builder()
                                .name(String.format("Bench_%d_%s", seed, benchmarkArguments.getIndexType()))
                                .path(domainGenerator.getDomainData().getPolicyDirectoryPath()).seed(seed).build());
            }

            return configurations;
        }

    }

    @SneakyThrows
    private TestSuite generateTestSuite(BenchmarkArguments benchmarkArguments) {
        String benchmarkConfigurationFile = benchmarkArguments.getBenchmarkConfigurationFile();
        TestSuite suite;
        if (!Strings.isNullOrEmpty(benchmarkConfigurationFile)) {
            File testFile = new File(benchmarkConfigurationFile);
            log.info("using testfile: {}", testFile);

            List<String> allLines = Files.readAllLines(Paths.get(testFile.toURI()));
            String allLinesAsString = StringUtils.join(allLines, "");

            suite = new Gson().fromJson(allLinesAsString, TestSuite.class);
        } else {
            suite = TestSuiteGenerator
                    .generateN(benchmarkArguments.getPath(), benchmarkArguments.getNumberOfBenchmarkIterations(),
                            domainGenerator.getDomainData().getDice());
        }

        Objects.requireNonNull(suite, "test suite is null");
        Objects.requireNonNull(suite.getCases(), "test cases are null");
        log.info("suite contains {} test cases", suite.getCases().size());
        if (suite.getCases().isEmpty())
            throw new RuntimeException("at least one test case must be present");

        return suite;
    }

    private List<XlsRecord> benchmarkConfiguration(String path, BenchmarkDataContainer benchmarkDataContainer,
                                                   PolicyGeneratorConfiguration config) throws Exception {

        List<XlsRecord> results = null;
        try {
            results = performFullyRandomBenchmark
                    ? TEST_RUNNER.runTest(config, path, benchmarkDataContainer, domainGenerator)
                    : TEST_RUNNER.runTestNew(config, config.getPath(), benchmarkDataContainer, domainGenerator);
        } catch (IOException | PolicyEvaluationException e) {
            log.error("Error running test", e);
            System.exit(1);
        }

        return results;
    }

    private BenchmarkArguments parseCommandLineArguments(String... args) {
        BenchmarkArguments benchmarkArguments = new BenchmarkArguments();

        Options options = new Options();

        options.addOption(PATH, true, PATH_DOC);
        options.addOption(HELP, false, HELP_DOC);
        options.addOption(INDEX, true, INDEX_DOC);
        options.addOption(TEST, true, TEST_DOC);
        options.addOption(ITERATIONS, true, ITERATIONS_DOC);
        options.addOption(FULLY_RANDOM, false, FULLY_RANDOM_DOC);

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args, true);
            if (cmd.hasOption(HELP)) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp(USAGE, options);
                System.exit(-1);
            }

            String pathOption = cmd.getOptionValue(PATH);
            if (!Strings.isNullOrEmpty(pathOption)) {
                if (!Files.exists(Paths.get(pathOption))) {
                    throw new IllegalArgumentException("path provided does not exists");
                }
                benchmarkArguments.setPath(pathOption);
            }

            String indexOption = cmd.getOptionValue(INDEX);

            if (!Strings.isNullOrEmpty(indexOption)) {
                log.debug("using index {}", indexOption);
                switch (indexOption.toUpperCase()) {
                    case "CANONICAL":
                        //fall through
                    case "IMPROVED":
                        benchmarkArguments.setIndexType(IndexType.CANONICAL);
                        break;
                    case "NAIVE":
                        //fall through
                    case "SIMPLE":
                        benchmarkArguments.setIndexType(IndexType.NAIVE);
                        break;
                    default:
                        HelpFormatter formatter = new HelpFormatter();
                        formatter.printHelp(USAGE, options);
                        throw new IllegalArgumentException("invalid index option provided");
                }
            }

            String testOption = cmd.getOptionValue(TEST);
            if (!Strings.isNullOrEmpty(testOption)) {
                if (!Files.exists(Paths.get(testOption))) {
                    throw new IllegalArgumentException("test file provided does not exists");
                }
                benchmarkArguments.setBenchmarkConfigurationFile(testOption);
            }

            String iterOption = cmd.getOptionValue(ITERATIONS);
            if (!Strings.isNullOrEmpty(iterOption)) {
                benchmarkArguments.setNumberOfBenchmarkIterations(Integer.parseInt(iterOption));
            }

            if (cmd.hasOption(FULLY_RANDOM)) {
                log.info("passed random argument");
                Benchmark.performFullyRandomBenchmark = true;
            }

        } catch (ParseException e) {
            log.error("encountered an error running the demo: {}", e.getMessage(), e);
            System.exit(1);
        }


        return benchmarkArguments;
    }

}
