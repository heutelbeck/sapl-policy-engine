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
package io.sapl.node;

import java.util.Arrays;
import java.util.Set;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportRuntimeHints;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import static org.slf4j.Logger.ROOT_LOGGER_NAME;

import io.sapl.node.cli.SaplNodeCli;
import lombok.val;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@EnableCaching
@ImportRuntimeHints(SaplNodeApplication.NativeResourceHints.class)
@SpringBootApplication(excludeName = { "io.sapl.spring.config.AuthorizationManagerConfiguration",
        "io.sapl.spring.config.ConstraintsHandlerAutoconfiguration",
        "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration",
        "org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizationAutoConfiguration",
        "org.springframework.boot.persistence.autoconfigure.PersistenceExceptionTranslationAutoConfiguration" })
@ComponentScan({ "io.sapl.node", "io.sapl.server" })
@EnableConfigurationProperties(SaplNodeProperties.class)
public class SaplNodeApplication {

    private static final String SERVER_COMMAND = "server";

    private static final Set<String> HELP_FLAGS = Set.of("--help", "-h", "--version", "-V");

    public static void main(String[] args) {
        val exitCode = run(args);
        if (exitCode != 0 || !isServerMode(args)) {
            System.exit(exitCode);
        }
    }

    /**
     * Runs the application and returns an exit code. Testable entry point.
     */
    static int run(String[] args) {
        if (isServerMode(args)) {
            val springArgs = args.length > 0 && SERVER_COMMAND.equals(args[0])
                    ? Arrays.copyOfRange(args, 1, args.length)
                    : args;
            SpringApplication.run(SaplNodeApplication.class, springArgs);
            return 0;
        }
        configureCliLogging();
        return new CommandLine(new SaplNodeCli()).execute(args);
    }

    /**
     * Configures logback to route all log output to stderr at ERROR level.
     * This prevents CLI commands from polluting stdout, which is reserved
     * for command output (JSON decisions, bundle info, etc.).
     */
    static void configureCliLogging() {
        val context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        val encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{HH:mm:ss} %-5level %logger{36} - %msg%n");
        encoder.start();

        val appender = new ConsoleAppender<ILoggingEvent>();
        appender.setContext(context);
        appender.setName("STDERR");
        appender.setTarget("System.err");
        appender.setEncoder(encoder);
        appender.start();

        val root = context.getLogger(ROOT_LOGGER_NAME);
        root.setLevel(Level.ERROR);
        root.addAppender(appender);
    }

    private static boolean isServerMode(String[] args) {
        if (args.length == 0) {
            return true;
        }
        if (SERVER_COMMAND.equals(args[0])) {
            return Arrays.stream(args).noneMatch(HELP_FLAGS::contains);
        }
        if (HELP_FLAGS.contains(args[0])) {
            return false;
        }
        return !new CommandLine(new SaplNodeCli()).getSubcommands().containsKey(args[0]);
    }

    static class NativeResourceHints implements RuntimeHintsRegistrar {

        private static final String CLI_PACKAGE = "io.sapl.node.cli.";

        private static final String[] PICOCLI_REFLECTION_CLASSES = { CLI_PACKAGE + "BenchmarkCommand",
                CLI_PACKAGE + "BenchmarkOptions", CLI_PACKAGE + "BundleVerificationOptions",
                CLI_PACKAGE + "CheckCommand", CLI_PACKAGE + "DecideCommand", CLI_PACKAGE + "DecideOnceCommand",
                CLI_PACKAGE + "NamedSubscriptionOptions", CLI_PACKAGE + "PdpOptions",
                CLI_PACKAGE + "PolicySourceOptions", CLI_PACKAGE + "RemoteConnectionOptions",
                CLI_PACKAGE + "RemoteConnectionOptions$AuthOptions", CLI_PACKAGE + "ServerCommand",
                CLI_PACKAGE + "SubscriptionInputOptions", CLI_PACKAGE + "TestCommand" };

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.resources().registerPattern("banner.txt");
            hints.resources().registerPattern("saplversion.properties");
            hints.resources().registerPattern("git.properties");
            hints.resources().registerPattern("ch/qos/logback/core/logback-core-version.properties");
            hints.resources().registerPattern("ch/qos/logback/classic/logback-classic-version.properties");

            for (val className : PICOCLI_REFLECTION_CLASSES) {
                hints.reflection().registerTypeIfPresent(classLoader, className,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.ACCESS_DECLARED_FIELDS);
            }
            hints.reflection().registerTypeIfPresent(classLoader, CLI_PACKAGE + "BenchmarkConfig",
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS);
        }

    }

}
