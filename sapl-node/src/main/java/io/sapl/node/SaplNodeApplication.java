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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
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

import io.sapl.node.boot.SaplBanner;
import io.sapl.node.cli.SaplNodeCli;
import lombok.val;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@EnableCaching
@ComponentScan({ "io.sapl.node", "io.sapl.server" })
@EnableConfigurationProperties(SaplNodeProperties.class)
@ImportRuntimeHints(SaplNodeApplication.NativeResourceHints.class)
// Transaction + persistence autoconfigure classes live in transitive Spring Boot
// jars not on this module's compile classpath. excludeName by string is the
// only way to reference them without forcing a transitive-to-compile bump.
@SpringBootApplication(excludeName = {
        "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration",
        "org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizationAutoConfiguration",
        "org.springframework.boot.persistence.autoconfigure.PersistenceExceptionTranslationAutoConfiguration" })
public class SaplNodeApplication {

    private static final String SERVER_COMMAND   = "server";
    private static final String NO_AUTH_FLAG     = "--no-auth";
    private static final String NO_AUTH_PROPERTY = "--io.sapl.node.allow-no-auth=true";

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
            val stripped   = args.length > 0 && SERVER_COMMAND.equals(args[0])
                    ? Arrays.copyOfRange(args, 1, args.length)
                    : args;
            val springArgs = expandNoAuthShortcut(stripped);
            val app        = new SpringApplication(SaplNodeApplication.class);
            app.setBanner(new SaplBanner());
            app.run(springArgs);
            return 0;
        }
        configureCliLogging();
        val cli         = new SaplNodeCli();
        val commandLine = new CommandLine(cli);
        commandLine.setExecutionStrategy(parseResult -> {
            cli.applyVerbosityToLogging();
            return new CommandLine.RunLast().execute(parseResult);
        });
        commandLine.setExecutionExceptionHandler(cleanErrorHandler(cli));
        return commandLine.execute(args);
    }

    /**
     * Reports an uncaught command exception as a single {@code Error: ...} line.
     * The full stack trace is printed only when {@code -v}/{@code --verbose} is
     * active, so ordinary failures stay readable instead of dumping a trace.
     *
     * @param cli the root command, consulted for the verbose flag
     * @return the picocli execution exception handler
     */
    static CommandLine.IExecutionExceptionHandler cleanErrorHandler(SaplNodeCli cli) {
        return (ex, cmdLine, parseResult) -> {
            val message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            cmdLine.getErr().println("Error: " + message);
            if (cli.isVerbose()) {
                ex.printStackTrace(cmdLine.getErr());
            }
            return cmdLine.getCommandSpec().exitCodeOnExecutionException();
        };
    }

    /**
     * Configures logback to route all log output to stderr at ERROR level.
     * This prevents CLI commands from polluting stdout, which is reserved
     * for command output (JSON decisions, bundle info, etc.).
     * <p>
     * Called after {@link #isServerMode} but before any subcommand runs. Any
     * log statement emitted between {@link #main} entry and this call uses
     * Logback's bootstrap defaults (root INFO to stdout) and is therefore
     * not subject to the stderr routing above. In practice nothing in
     * {@link #isServerMode} or {@link #run} logs before this method, so the
     * gap is empty. Callers adding new code in that window should keep it
     * silent or expect those messages to escape the stderr discipline.
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

    private static String[] expandNoAuthShortcut(String[] args) {
        val     out      = new ArrayList<String>(args.length + 1);
        boolean expanded = false;
        for (val arg : args) {
            if (!expanded && NO_AUTH_FLAG.equals(arg)) {
                out.add(NO_AUTH_PROPERTY);
                expanded = true;
            } else {
                out.add(arg);
            }
        }
        return out.toArray(new String[0]);
    }

    static boolean isServerMode(String[] args) {
        if (args.length == 0) {
            return true;
        }
        if (SERVER_COMMAND.equals(args[0])) {
            return Arrays.stream(args).noneMatch(HELP_FLAGS::contains);
        }
        if (HELP_FLAGS.contains(args[0])) {
            return false;
        }
        // Server mode for a leading property override. A registered subcommand or a
        // bare word goes to picocli, which reports an unknown command for a typo.
        return args[0].startsWith("--");
    }

    /**
     * GraalVM native-image hints for resources, reflection, and serialization
     * that Spring Boot's AOT processor cannot infer automatically. Covers
     * version banner, Logback version probes, the Scalar configuration types
     * accessed reflectively, the Jetty servlet stack components Boot
     * instantiates by name, and the springdoc actuator handler whose private
     * field it reads via reflection.
     */
    static class NativeResourceHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.resources().registerPattern("banner-color.txt");
            hints.resources().registerPattern("banner-plain.txt");
            hints.resources().registerPattern("saplversion.properties");
            hints.resources().registerPattern("git.properties");
            hints.resources().registerPattern("ch/qos/logback/core/logback-core-version.properties");
            hints.resources().registerPattern("ch/qos/logback/classic/logback-classic-version.properties");
            hints.resources().registerPattern("static/css/sapl-scalar-theme.css");
            hints.resources().registerPattern("static/openapi/pdp-http.yaml");
            registerScalarReflection(hints, classLoader);
        }

        private static void registerScalarReflection(RuntimeHints hints, ClassLoader classLoader) {
            val binder = new BindingReflectionHintsRegistrar();
            for (val className : List.of("com.scalar.maven.core.internal.ScalarConfiguration",
                    "com.scalar.maven.core.ScalarProperties", "com.scalar.maven.webmvc.SpringBootScalarProperties",
                    "com.scalar.maven.core.config.ScalarSource", "com.scalar.maven.core.config.ScalarServer",
                    "com.scalar.maven.core.config.ScalarServerVariable",
                    "com.scalar.maven.core.config.ScalarAgentOptions", "com.scalar.maven.core.config.DefaultHttpClient",
                    "com.scalar.maven.core.authentication.ScalarAuthenticationOptions",
                    "com.scalar.maven.core.authentication.schemes.ScalarSecurityScheme",
                    "com.scalar.maven.core.authentication.schemes.ScalarApiKeySecurityScheme",
                    "com.scalar.maven.core.authentication.schemes.ScalarHttpSecurityScheme",
                    "com.scalar.maven.core.authentication.schemes.ScalarOAuth2SecurityScheme",
                    "com.scalar.maven.core.authentication.flows.ScalarFlows",
                    "com.scalar.maven.core.authentication.flows.OAuthFlow",
                    "com.scalar.maven.core.authentication.flows.AuthorizationCodeFlow",
                    "com.scalar.maven.core.authentication.flows.ClientCredentialsFlow",
                    "com.scalar.maven.core.authentication.flows.ImplicitFlow",
                    "com.scalar.maven.core.authentication.flows.PasswordFlow",
                    "com.scalar.maven.core.enums.ScalarTheme", "com.scalar.maven.core.enums.ThemeMode",
                    "com.scalar.maven.core.enums.Pkce", "com.scalar.maven.core.enums.CredentialsLocation",
                    "com.scalar.maven.core.enums.DeveloperToolsVisibility",
                    "com.scalar.maven.core.enums.DocumentDownloadType", "com.scalar.maven.core.enums.Layout",
                    "com.scalar.maven.core.enums.OperationSorter", "com.scalar.maven.core.enums.OperationTitleSource",
                    "com.scalar.maven.core.enums.SchemaPropertyOrder", "com.scalar.maven.core.enums.TagSorter")) {
                try {
                    binder.registerReflectionHints(hints.reflection(), Class.forName(className, false, classLoader));
                } catch (ClassNotFoundException ignored) {
                    // Optional Scalar type not on the classpath.
                }
            }

            // Jetty servlet stack: Spring Boot's JettyServletWebServerFactory uses
            // reflection on these types' no-arg constructors. Without these hints
            // the native binary fails at startup with NoSuchMethodException for
            // ConstraintSecurityHandler.<init>().
            hints.reflection().registerTypeIfPresent(classLoader,
                    "org.eclipse.jetty.ee11.servlet.security.ConstraintSecurityHandler",
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
            hints.reflection().registerTypeIfPresent(classLoader, "org.eclipse.jetty.ee11.servlet.SessionHandler",
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);

            // springdoc's ActuatorOperationCustomizer reflects on the private
            // 'operation' field of OperationHandler when show-actuator is on.
            hints.reflection().registerTypeIfPresent(classLoader,
                    "org.springframework.boot.webmvc.actuate.endpoint.web.AbstractWebMvcEndpointHandlerMapping$OperationHandler",
                    MemberCategory.ACCESS_DECLARED_FIELDS);

            // Passay reflects on its CharacterData / CharacterRule types when
            // building generators. SAPL's `generate basic`/`generate apikey`
            // CLI commands depend on this. Without the hints the native
            // binary fails at first credential generation.
            for (val passayClass : List.of("org.passay.PasswordGenerator", "org.passay.CharacterRule",
                    "org.passay.EnglishCharacterData")) {
                hints.reflection().registerTypeIfPresent(classLoader, passayClass,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS);
            }
        }

    }

}
