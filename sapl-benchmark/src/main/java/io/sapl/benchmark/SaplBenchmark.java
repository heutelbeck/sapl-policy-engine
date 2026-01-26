/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.BenchmarkList;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.format.OutputFormatFactory;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.runner.options.VerboseMode;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import io.sapl.benchmark.report.ReportGenerator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SaplBenchmark {
    private BenchmarkConfiguration config;
    private final String           cfgFilePath;
    private GenericContainer<?>    pdpContainer;
    private GenericContainer<?>    oauth2Container;
    private final String           benchmarkFolder;

    public SaplBenchmark(String cfgFilePath, String benchmarkFolder) {
        this.benchmarkFolder = benchmarkFolder;
        this.cfgFilePath     = cfgFilePath;
    }

    private void configureAndStartServerLtContainer(GenericContainer<?> container) {
        this.pdpContainer = container;
        if (null == container) {
            return;
        }
        final var encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

        final var dockerKeystoreLocation = "/pdp/keystore.p12";

        final var containerLogLevel = "ERROR";
        container.withClasspathResourceMapping("keystore.p12", dockerKeystoreLocation, BindMode.READ_ONLY)
                .withClasspathResourceMapping("policies/", "/pdp/data/", BindMode.READ_ONLY)
                .withEnv("io_sapl_pdp_embedded_policies-path", "/pdp/data").withEnv("spring_profiles_active", "local")
                .withExposedPorts(BenchmarkConfiguration.DOCKER_DEFAULT_HTTP_PORT,
                        BenchmarkConfiguration.DOCKER_DEFAULT_RSOCKET_PORT,
                        BenchmarkConfiguration.DOCKER_DEFAULT_PROTOBUF_RSOCKET_PORT)
                .waitingFor(Wait.forListeningPorts())

                // http settings
                .withEnv("server_address", "0.0.0.0")
                .withEnv("server_port", String.valueOf(BenchmarkConfiguration.DOCKER_DEFAULT_HTTP_PORT))
                .withEnv("server_ssl_enabled", String.valueOf(config.isDockerUseSsl()))
                .withEnv("server_ssl_key-store-type", "PKCS12").withEnv("server_ssl_key-store", dockerKeystoreLocation)
                .withEnv("server_ssl_key-store-password", "benchmarkonly").withEnv("server_ssl_key-alias", "tomcat")

                // rsocket settings
                .withEnv("spring_rsocket_server_address", "0.0.0.0")
                .withEnv("spring_rsocket_server_port",
                        String.valueOf(BenchmarkConfiguration.DOCKER_DEFAULT_RSOCKET_PORT))
                // protobuf rsocket settings
                .withEnv("sapl_pdp_rsocket_protobuf_enabled", "true")
                .withEnv("sapl_pdp_rsocket_protobuf_port",
                        String.valueOf(BenchmarkConfiguration.DOCKER_DEFAULT_PROTOBUF_RSOCKET_PORT))
                .withEnv("spring_rsocket_server_ssl_enabled", String.valueOf(config.isDockerUseSsl()))
                .withEnv("spring_rsocket_server_ssl_key-store-type", "PKCS12")
                .withEnv("spring_rsocket_server_ssl__key-store", dockerKeystoreLocation)
                .withEnv("spring_rsocket_server_ssl__key-store-password", "benchmarkonly")
                .withEnv("spring_rsocket_server_ssl__key-alias", "tomcat")

                // logging settings
                .withEnv("LOGGING_LEVEL_ROOT", containerLogLevel)
                .withEnv("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK", containerLogLevel)
                .withEnv("LOGGING_LEVEL_IO_SAPL", containerLogLevel);

        // auth Settings
        container.withEnv("io_sapl_server-lt_allowNoAuth", String.valueOf(config.isUseNoAuth()));
        container.withEnv("io_sapl_server-lt_allowBasicAuth", String.valueOf(config.isUseBasicAuth()));
        if (config.isUseBasicAuth()) {
            container.withEnv("io_sapl_server-lt_key", config.getBasicClientKey()).withEnv("io_sapl_server-lt_secret",
                    encoder.encode(config.getBasicClientSecret()));
        }
        container.withEnv("io_sapl_server-lt_allowApiKeyAuth", String.valueOf(config.isUseAuthApiKey()));
        if (config.isUseAuthApiKey()) {
            container.withEnv("io_sapl_server-lt_allowedApiKeys[0]", encoder.encode(config.getApiKeySecret()));
        }
        container.withEnv("io_sapl_server-lt_allowOauth2Auth", String.valueOf(config.isUseOauth2()));
        if (config.isUseOauth2()) {
            String jwtIssuerUrl;
            if (config.isOauth2MockServer()) {
                jwtIssuerUrl = "http://auth-host:" + oauth2Container.getMappedPort(8080) + "/default";
            } else {
                jwtIssuerUrl = config.getOauth2IssuerUrl();
            }
            container.withExtraHost("auth-host", "host-gateway")
                    .withEnv("spring_security_oauth2_resourceserver_jwt_issuer-uri", jwtIssuerUrl);
        }

        // ensure that http/https is reachable before starting the benchmark
        if (config.isDockerUseSsl()) {
            container
                    .waitingFor(Wait.forHttps("/").forPort(8080).allowInsecure().forStatusCode(401).forStatusCode(404));
        } else {
            container.waitingFor(Wait.forHttp("/").forPort(8080).forStatusCode(401).forStatusCode(404));
        }
        container.start();
    }

    /**
     * Estimates the benchmark duration based on the given security and logs this.
     */
    private void logEstimatedDuration() {
        try (PrintStream printStream = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)) {
            final var tmpOutput                  = OutputFormatFactory.createFormatInstance(printStream,
                    VerboseMode.SILENT);
            final var matchedBenchmarkList       = BenchmarkList.defaultList()
                    .find(tmpOutput, List.of(config.getBenchmarkPattern()), List.of()).stream().toList();
            final var estimatedDurationInSeconds = (
            // warmup
            config.getWarmupIterations() * config.getWarmupSeconds()
                    // measures
                    + config.getMeasurementIterations() * config.getMeasurementSeconds()
                    // benchmark initialization
                    + 5) * config.getThreadList().size() * matchedBenchmarkList.size();

            log.info("Executing " + matchedBenchmarkList.size() + " Benchmarks with " + config.getThreadList()
                    + " threads ...");
            int seconds = estimatedDurationInSeconds % 60;
            int minutes = estimatedDurationInSeconds / 60 % 60;
            int hours   = estimatedDurationInSeconds / 60 / 60;
            log.info("Estimated duration: " + String.format("%02d:%02d:%02d", hours, minutes, seconds));
        }
    }

    /**
     * Executes the JMH Benchmarks based on the given configuration. The JMH results
     * and output are written to corresponding files in the benchmarkFolder.
     */
    private void executeJmHBenchmarks() throws RunnerException {
        final var context       = BenchmarkExecutionContext.fromBenchmarkConfiguration(config, pdpContainer,
                oauth2Container);
        final var timeFormatter = new SimpleDateFormat("HH:mm:ss");
        log.info("Benchmark started at " + timeFormatter.format(new Date()));
        logEstimatedDuration();

        // setup builder with base parameters
        final var benchmarkBuilder = new OptionsBuilder().include(config.getBenchmarkPattern())
                .param("contextJsonString", context.toJsonString()).jvmArgs(config.getJvmArgs().toArray(new String[0]))
                .shouldFailOnError(config.isFailOnError()).mode(Mode.Throughput).timeUnit(TimeUnit.SECONDS)
                .resultFormat(ResultFormatType.JSON).shouldDoGC(true).syncIterations(true).forks(config.getForks())
                .warmupIterations(config.getWarmupIterations()).warmupTime(TimeValue.seconds(config.getWarmupSeconds()))
                .measurementIterations(config.getMeasurementIterations())
                .measurementTime(TimeValue.seconds(config.getMeasurementSeconds()));

        // iterate over thread list and start benchmark for each thread parameter
        for (int threads : config.getThreadList()) {
            final var resultFile = benchmarkFolder + "/results_" + threads + "threads.json";
            final var outputFile = benchmarkFolder + "/results_" + threads + "threads.log";
            log.info("Starting Benchmark with " + threads + " threads matching pattern: "
                    + config.getBenchmarkPattern());
            log.info("Writing results to " + resultFile + " and logs to " + outputFile);
            new Runner(benchmarkBuilder.threads(threads).result(resultFile).output(outputFile).build()).run();
        }
        log.info("Benchmark ended at " + timeFormatter.format(new Date()));
    }

    void generateBenchmarkReports() throws IOException {
        new ReportGenerator(benchmarkFolder).generateReport();
    }

    public void startBenchmark() throws RunnerException, IOException {
        this.config = BenchmarkConfiguration.fromFile(cfgFilePath);
        Files.createDirectories(Paths.get(benchmarkFolder));
        final var sourceFile = new File(cfgFilePath);
        FileUtils.copyFile(sourceFile, new File(benchmarkFolder + File.separator + sourceFile.getName()));

        final var useOAuthContainer    = config.isUseOauth2() && config.isOauth2MockServer();
        final var useServerLTContainer = config.requiredDockerEnvironment();

        try (var oauth2Cont = useOAuthContainer
                ? new GenericContainer<>(DockerImageName.parse(config.getOauth2MockImage()))
                : null;
                final var pdpCont = useServerLTContainer
                        ? new GenericContainer<>(DockerImageName.parse(config.getDockerPdpImage()))
                        : null) {
            configureAndStartOAuthContainer(oauth2Cont);
            configureAndStartServerLtContainer(pdpCont);

            executeJmHBenchmarks();
            stopContainersIfRunning(oauth2Cont, pdpCont);
        }
    }

    void configureAndStartOAuthContainer(GenericContainer<?> container) {
        this.oauth2Container = container;
        if (null == container) {
            return;
        }
        container.withExposedPorts(8080).waitingFor(Wait.forListeningPort());
        container.start();
    }

    private void stopContainersIfRunning(GenericContainer<?>... containers) {
        for (var container : containers) {
            if (null != container) {
                container.stop();
            }
        }
    }
}
