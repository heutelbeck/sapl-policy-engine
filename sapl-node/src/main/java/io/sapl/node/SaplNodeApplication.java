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

import java.util.Set;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportRuntimeHints;

import io.sapl.node.cli.SaplNodeCli;
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

    private static final Set<String> CLI_ONLY_ARGS = Set.of("--help", "-h", "--version", "-V", "bundle", "generate");

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0 || isCliOnlyCommand(args)) {
            System.exit(exitCode);
        }
    }

    /**
     * Runs the application and returns an exit code. Testable entry point.
     */
    static int run(String[] args) {
        if (isCliOnlyCommand(args)) {
            return new CommandLine(new SaplNodeCli()).execute(args);
        }
        SpringApplication.run(SaplNodeApplication.class, args);
        return 0;
    }

    private static boolean isCliOnlyCommand(String[] args) {
        for (var arg : args) {
            if (CLI_ONLY_ARGS.contains(arg)) {
                return true;
            }
        }
        return false;
    }

    static class NativeResourceHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.resources().registerPattern("banner.txt");
            hints.resources().registerPattern("saplversion.properties");
            hints.resources().registerPattern("git.properties");
        }

    }

}
